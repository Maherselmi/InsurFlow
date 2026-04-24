package tn.esprit.insureflow_back.Orchestrator;

import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;
import tn.esprit.insureflow_back.Agent.AgentEstimateur;
import tn.esprit.insureflow_back.Agent.AgentRouteur;
import tn.esprit.insureflow_back.Agent.AgentValidateur;
import tn.esprit.insureflow_back.Domain.ENUMS.ClaimStatus;
import tn.esprit.insureflow_back.Domain.Entities.AgentResult;
import tn.esprit.insureflow_back.Domain.Entities.Claim;
import tn.esprit.insureflow_back.Repository.AgentResultRepository;
import tn.esprit.insureflow_back.Repository.ClaimRepository;
import tn.esprit.insureflow_back.Service.ClaimPdfExtractorService;
import tn.esprit.insureflow_back.Service.RapportClientService;
import tn.esprit.insureflow_back.Service.RapportService;

import java.time.Duration;
import java.time.Instant;
import java.util.Locale;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Executor;

@Slf4j
@Service
public class OrchestratorService {

    private final AgentRouteur agentRouteur;
    private final AgentValidateur agentValidateur;
    private final AgentEstimateur agentEstimateur;

    private final AgentResultRepository agentResultRepository;
    private final ClaimRepository claimRepository;

    private final ClaimPdfExtractorService pdfExtractorService;
    private final RapportService rapportService;
    private final RapportClientService rapportClientService;

    private final Executor agentExecutor;

    public OrchestratorService(
            AgentRouteur agentRouteur,
            AgentValidateur agentValidateur,
            AgentEstimateur agentEstimateur,
            AgentResultRepository agentResultRepository,
            ClaimRepository claimRepository,
            ClaimPdfExtractorService pdfExtractorService,
            RapportService rapportService,
            RapportClientService rapportClientService,
            @Qualifier("agentExecutor") Executor agentExecutor
    ) {
        this.agentRouteur = agentRouteur;
        this.agentValidateur = agentValidateur;
        this.agentEstimateur = agentEstimateur;
        this.agentResultRepository = agentResultRepository;
        this.claimRepository = claimRepository;
        this.pdfExtractorService = pdfExtractorService;
        this.rapportService = rapportService;
        this.rapportClientService = rapportClientService;
        this.agentExecutor = agentExecutor;
    }

    @Async("agentExecutor")
    public void processClaim(Claim claim) {
        Instant totalStart = Instant.now();

        if (claim == null || claim.getId() == null) {
            log.error("Orchestrator - claim null ou id null");
            return;
        }

        log.info("Orchestrator - démarrage traitement claim #{}", claim.getId());

        Claim freshClaim = claimRepository.findByIdWithDocuments(claim.getId())
                .orElseThrow(() -> new RuntimeException("Claim introuvable : " + claim.getId()));

        updateStatus(freshClaim, ClaimStatus.IN_ANALYSIS);

        AgentResult routeResult = null;
        AgentResult validationResult = null;
        AgentResult estimationResult = null;

        try {
            Instant parallelStart = Instant.now();

            CompletableFuture<String> pdfFuture = CompletableFuture.supplyAsync(
                    () -> safeExtractPdfText(freshClaim),
                    agentExecutor
            );

            CompletableFuture<AgentResult> routeFuture = CompletableFuture.supplyAsync(
                    () -> safeRunRouteur(freshClaim),
                    agentExecutor
            );

            routeResult = routeFuture.join();
            routeResult.setClaim(freshClaim);
            agentResultRepository.save(routeResult);

            logDuration("Routeur terminé", parallelStart);

            String routedType = extractRoutedType(routeResult);
            log.info("Type transmis au validateur pour claim #{} : {}", freshClaim.getId(), routedType);

            if (routeResult.isNeedsHumanReview()) {
                log.warn("Confiance faible au routage -> PENDING_VALIDATION");
                finalizeClaim(
                        freshClaim,
                        ClaimStatus.PENDING_VALIDATION,
                        routeResult,
                        null,
                        null,
                        totalStart
                );
                return;
            }

            String claimPdfText = pdfFuture.join();
            if (claimPdfText == null || claimPdfText.isBlank()) {
                log.warn("Aucun PDF exploitable - utilisation de la description");
                claimPdfText = safeText(freshClaim.getDescription());
            }

            Instant validationStart = Instant.now();

            validationResult = safeRunValidateur(freshClaim, claimPdfText, routedType);
            validationResult.setClaim(freshClaim);
            agentResultRepository.save(validationResult);

            logDuration("Validateur terminé", validationStart);

            String validationDecision = safeText(validationResult.getConclusion()).toUpperCase(Locale.ROOT);

            if ("EXCLU".equals(validationDecision)) {
                log.warn("Sinistre EXCLU -> REJECTED");
                finalizeClaim(
                        freshClaim,
                        ClaimStatus.REJECTED,
                        routeResult,
                        validationResult,
                        null,
                        totalStart
                );
                return;
            }

            if ("INCONNU".equals(validationDecision) || validationResult.isNeedsHumanReview()) {
                log.warn("Validation contractuelle incertaine -> PENDING_VALIDATION");
                finalizeClaim(
                        freshClaim,
                        ClaimStatus.PENDING_VALIDATION,
                        routeResult,
                        validationResult,
                        null,
                        totalStart
                );
                return;
            }

            Instant estimationStart = Instant.now();

            estimationResult = safeRunEstimateur(freshClaim, routeResult, validationResult);
            estimationResult.setClaim(freshClaim);
            agentResultRepository.save(estimationResult);

            logDuration("Estimateur terminé", estimationStart);

            if (estimationResult.isNeedsHumanReview()) {
                log.warn("Estimation incertaine -> PENDING_VALIDATION");
                finalizeClaim(
                        freshClaim,
                        ClaimStatus.PENDING_VALIDATION,
                        routeResult,
                        validationResult,
                        estimationResult,
                        totalStart
                );
            } else {
                log.info("Sinistre APPROUVÉ automatiquement");
                finalizeClaim(
                        freshClaim,
                        ClaimStatus.APPROVED,
                        routeResult,
                        validationResult,
                        estimationResult,
                        totalStart
                );
            }

        } catch (Exception e) {
            log.error("Erreur orchestrateur claim #{}: {}", freshClaim.getId(), e.getMessage(), e);

            updateStatus(freshClaim, ClaimStatus.PENDING_VALIDATION);

            try {
                genererEtSauvegarderRapports(freshClaim, routeResult, validationResult, estimationResult);
            } catch (Exception reportError) {
                log.error("Erreur sauvegarde rapports après échec orchestrateur claim #{}: {}",
                        freshClaim.getId(), reportError.getMessage(), reportError);
            }

            logDuration("Workflow terminé en erreur", totalStart);
        }
    }

    private String safeExtractPdfText(Claim claim) {
        Instant start = Instant.now();
        try {
            log.info("Extraction PDF démarrée pour claim #{}", claim.getId());
            String text = pdfExtractorService.extractTextFromClaim(claim);
            logDuration("Extraction PDF terminée", start);
            return text;
        } catch (Exception e) {
            log.error("Erreur extraction PDF claim #{}: {}", claim.getId(), e.getMessage(), e);
            return safeText(claim.getDescription());
        }
    }

    private AgentResult safeRunRouteur(Claim claim) {
        Instant start = Instant.now();
        try {
            log.info("AgentRouteur démarré pour claim #{}", claim.getId());
            AgentResult result = agentRouteur.classifier(claim);
            logDuration("AgentRouteur terminé", start);
            return result;
        } catch (Exception e) {
            log.error("Erreur AgentRouteur claim #{}: {}", claim.getId(), e.getMessage(), e);
            return buildFallbackAgentResult(
                    "AgentRouteur",
                    "Type de sinistre classifié : INCONNU | Justification : erreur routeur",
                    0.0,
                    true,
                    claim,
                    "{\"type\":\"INCONNU\",\"confidence\":0.0,\"justification\":\"erreur routeur\"}"
            );
        }
    }

    private AgentResult safeRunValidateur(Claim claim, String claimPdfText, String routedType) {
        try {
            log.info("AgentValidateur démarré pour claim #{}", claim.getId());
            return agentValidateur.validate(claim, claimPdfText, routedType);
        } catch (Exception e) {
            log.error("Erreur AgentValidateur claim #{}: {}", claim.getId(), e.getMessage(), e);
            return buildFallbackAgentResult(
                    "AgentValidateur",
                    "INCONNU",
                    0.0,
                    true,
                    claim,
                    "{\"decision\":\"INCONNU\",\"confidence\":0.0,\"justification\":\"erreur validateur\",\"needsHumanReview\":true}"
            );
        }
    }

    private AgentResult safeRunEstimateur(Claim claim, AgentResult routeResult, AgentResult validationResult) {
        try {
            log.info("AgentEstimateur démarré pour claim #{}", claim.getId());
            return agentEstimateur.estimate(claim, routeResult, validationResult);
        } catch (Exception e) {
            log.error("Erreur AgentEstimateur claim #{}: {}", claim.getId(), e.getMessage(), e);
            return buildFallbackAgentResult(
                    "AgentEstimateur",
                    "Estimation min: 0.00 DT | moyenne: 0.00 DT | max: 0.00 DT",
                    0.0,
                    true,
                    claim,
                    "{\"estimationMin\":0.0,\"estimationMax\":0.0,\"estimationMoyenne\":0.0,\"confidence\":0.0,\"analyse\":\"erreur estimateur\",\"needsHumanReview\":true}"
            );
        }
    }

    private void finalizeClaim(Claim claim,
                               ClaimStatus finalStatus,
                               AgentResult routeResult,
                               AgentResult validationResult,
                               AgentResult estimationResult,
                               Instant totalStart) {

        updateStatus(claim, finalStatus);
        genererEtSauvegarderRapports(claim, routeResult, validationResult, estimationResult);

        log.info("Workflow terminé - claim #{} statut final: {}", claim.getId(), claim.getStatus());
        logDuration("Temps total workflow", totalStart);
    }

    private String extractRoutedType(AgentResult routeResult) {
        if (routeResult == null || routeResult.getConclusion() == null) {
            return "INCONNU";
        }

        String value = routeResult.getConclusion().trim().toUpperCase(Locale.ROOT);

        if (value.contains("AUTO")) return "AUTO";
        if (value.contains("SANTE")) return "SANTE";
        if (value.contains("HABITATION")) return "HABITATION";
        if (value.contains("VOYAGE")) return "VOYAGE";

        return "INCONNU";
    }

    private void genererEtSauvegarderRapports(Claim claim,
                                              AgentResult routeResult,
                                              AgentResult validationResult,
                                              AgentResult estimationResult) {
        try {
            log.info("Génération rapport expert pour claim #{}", claim.getId());
            String rapportExpert = rapportService.genererRapport(
                    claim, routeResult, validationResult, estimationResult);
            claim.setAiReport(rapportExpert);

            log.info("Génération rapport client pour claim #{}", claim.getId());
            String rapportClient = rapportClientService.genererRapportClient(
                    claim, routeResult, validationResult, estimationResult);
            claim.setClientReport(rapportClient);

            claimRepository.save(claim);
            log.info("Rapports sauvegardés - claim #{}", claim.getId());

        } catch (Exception e) {
            log.error("Erreur génération rapports claim #{}: {}", claim.getId(), e.getMessage(), e);
        }
    }

    private void updateStatus(Claim claim, ClaimStatus status) {
        claim.setStatus(status);
        claimRepository.save(claim);
        log.info("Statut claim #{} -> {}", claim.getId(), status);
    }

    private void logDuration(String label, Instant start) {
        long ms = Duration.between(start, Instant.now()).toMillis();
        log.info("{} en {} ms", label, ms);
    }

    private String safeText(String value) {
        return value == null ? "" : value.trim();
    }

    private AgentResult buildFallbackAgentResult(String agentName,
                                                 String conclusion,
                                                 double confidenceScore,
                                                 boolean needsHumanReview,
                                                 Claim claim,
                                                 String rawLlmResponse) {
        AgentResult result = new AgentResult();
        result.setAgentName(agentName);
        result.setConclusion(conclusion);
        result.setConfidenceScore(confidenceScore);
        result.setNeedsHumanReview(needsHumanReview);
        result.setClaim(claim);
        result.setRawLlmResponse(rawLlmResponse);
        return result;
    }
}