package tn.esprit.insureflow_back.Service;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
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

import java.util.Locale;

@Slf4j
@Service
@RequiredArgsConstructor
@Async
public class OrchestratorService {

    private final AgentRouteur agentRouteur;
    private final AgentResultRepository agentResultRepository;
    private final ClaimPdfExtractorService pdfExtractorService;
    private final AgentValidateur agentValidateur;
    private final AgentEstimateur agentEstimateur;
    private final ClaimRepository claimRepository;
    private final RapportService rapportService;
    private final RapportClientService rapportClientService;

    public void processClaim(Claim claim) {

        log.info("🚀 Orchestrator — démarrage traitement claim #{}", claim.getId());

        Claim freshClaim = claimRepository.findByIdWithDocuments(claim.getId())
                .orElseThrow(() -> new RuntimeException("Claim introuvable"));

        updateStatus(freshClaim, ClaimStatus.IN_ANALYSIS);

        AgentResult routeResult = null;
        AgentResult validationResult = null;
        AgentResult estimationResult = null;

        log.info("📄 Étape 0: Extraction PDF");
        String claimPdfText = pdfExtractorService.extractTextFromClaim(freshClaim);
        if (claimPdfText == null || claimPdfText.isBlank()) {
            log.warn("⚠️ Aucun PDF exploitable — utilisation de la description");
            claimPdfText = freshClaim.getDescription();
        }

        log.info("🔍 Étape 1: AgentRouteur");
        routeResult = agentRouteur.classifier(freshClaim);
        routeResult.setClaim(freshClaim);
        agentResultRepository.save(routeResult);

        String routedType = extractRoutedType(routeResult);
        log.info("📤 Type transmis au validateur pour claim #{} : {}", freshClaim.getId(), routedType);

        if (routeResult.isNeedsHumanReview()) {
            log.warn("⚠️ Confiance faible au routage → PENDING_VALIDATION");
            updateStatus(freshClaim, ClaimStatus.PENDING_VALIDATION);
            genererEtSauvegarderRapports(freshClaim, routeResult, null, null);
            return;
        }

        log.info("🤖 Étape 2: AgentValidateur");
        validationResult = agentValidateur.validate(freshClaim, claimPdfText, routedType);
        validationResult.setClaim(freshClaim);
        agentResultRepository.save(validationResult);

        if ("EXCLU".equalsIgnoreCase(validationResult.getConclusion())) {
            log.warn("❌ Sinistre EXCLU → REJECTED");
            updateStatus(freshClaim, ClaimStatus.REJECTED);
            genererEtSauvegarderRapports(freshClaim, routeResult, validationResult, null);
            return;
        }

        if ("INCONNU".equalsIgnoreCase(validationResult.getConclusion())
                || validationResult.isNeedsHumanReview()) {
            log.warn("⚠️ Validation contractuelle incertaine → PENDING_VALIDATION");
            updateStatus(freshClaim, ClaimStatus.PENDING_VALIDATION);
            genererEtSauvegarderRapports(freshClaim, routeResult, validationResult, null);
            return;
        }

        log.info("💰 Étape 3: AgentEstimateur");
        estimationResult = agentEstimateur.estimate(freshClaim, routeResult, validationResult);
        estimationResult.setClaim(freshClaim);
        agentResultRepository.save(estimationResult);

        if (estimationResult.isNeedsHumanReview()) {
            log.warn("⚠️ Estimation incertaine → PENDING_VALIDATION");
            updateStatus(freshClaim, ClaimStatus.PENDING_VALIDATION);
            genererEtSauvegarderRapports(freshClaim, routeResult, validationResult, estimationResult);
        } else {
            log.info("✅ Sinistre APPROUVÉ automatiquement");
            updateStatus(freshClaim, ClaimStatus.APPROVED);
            genererEtSauvegarderRapports(freshClaim, routeResult, validationResult, estimationResult);
        }

        log.info("✅ Workflow terminé — claim #{} statut final: {}",
                freshClaim.getId(), freshClaim.getStatus());
    }

    private String extractRoutedType(AgentResult routeResult) {
        if (routeResult == null || routeResult.getConclusion() == null) {
            return "INCONNU";
        }

        String value = routeResult.getConclusion().trim().toUpperCase(Locale.ROOT);

        if (value.contains("AUTO")) return "AUTO";
        if (value.contains("SANTE")) return "SANTE";
        if (value.contains("HABITATION")) return "HABITATION";

        return "INCONNU";
    }

    private void genererEtSauvegarderRapports(Claim claim,
                                              AgentResult routeResult,
                                              AgentResult validationResult,
                                              AgentResult estimationResult) {
        try {
            log.info("🧠 Génération rapport expert");
            String rapportExpert = rapportService.genererRapport(
                    claim, routeResult, validationResult, estimationResult);
            claim.setAiReport(rapportExpert);

            log.info("📨 Génération rapport client");
            String rapportClient = rapportClientService.genererRapportClient(
                    claim, routeResult, validationResult, estimationResult);
            claim.setClientReport(rapportClient);

            claimRepository.save(claim);
            log.info("📄 Rapports sauvegardés — claim #{}", claim.getId());

        } catch (Exception e) {
            log.error("❌ Erreur génération rapports claim #{}: {}", claim.getId(), e.getMessage(), e);
        }
    }

    private void updateStatus(Claim claim, ClaimStatus status) {
        claim.setStatus(status);
        claimRepository.save(claim);
        log.info("📌 Statut claim #{} → {}", claim.getId(), status);
    }
}