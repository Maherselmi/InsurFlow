package tn.esprit.insureflow_back.Service;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import tn.esprit.insureflow_back.Domain.Entities.AgentResult;
import tn.esprit.insureflow_back.Domain.Entities.Claim;

@Slf4j
@Service
@RequiredArgsConstructor
public class RapportClientService {

    private final LLMService llmService;

    public String genererRapportClient(Claim claim,
                                       AgentResult routeResult,
                                       AgentResult validationResult,
                                       AgentResult estimationResult) {

        String statut = claim.getStatus().name();
        String prompt = buildPromptClient(claim, validationResult, estimationResult, statut);

        log.info("📨 Génération rapport client — claim #{} statut: {}", claim.getId(), statut);

        String raw = llmService.genererReponse(prompt);
        return cleanReport(raw);
    }

    private String buildPromptClient(Claim claim,
                                     AgentResult validationResult,
                                     AgentResult estimationResult,
                                     String statut) {

        String clientName = claim.getClient() != null
                ? safe(claim.getClient().getFirstName()) + " " + safe(claim.getClient().getLastName())
                : "Client";

        String policyType = claim.getPolicy() != null
                ? safe(claim.getPolicy().getType())
                : "Information non disponible";

        String validationConclusion = validationResult != null
                ? safe(validationResult.getConclusion())
                : "Information non disponible";

        String validationReason = validationResult != null
                ? safe(validationResult.getRawLlmResponse())
                : "Information non disponible";

        String estimationConclusion = estimationResult != null
                ? safe(estimationResult.getConclusion())
                : "Information non disponible";

        return """
                Tu es un conseiller client dans une compagnie d'assurance.
                Tu dois rédiger un rapport SIMPLE, CLAIR et COMPRÉHENSIBLE par un client non technique.

                RÈGLES OBLIGATOIRES :
                - Réponds uniquement en texte brut
                - N'utilise pas Markdown
                - N'utilise jamais les termes suivants : agent routeur, agent validateur, agent estimateur, RAG, LLM, score de confiance, workflow
                - N'expose jamais les détails internes du système
                - Utilise un langage humain, rassurant et professionnel
                - Évite les répétitions
                - Le client doit comprendre immédiatement si son dossier est accepté, refusé ou en attente
                - Si le dossier est en attente, explique simplement qu'une vérification complémentaire est en cours
                - Si le dossier est accepté, mentionne qu'une estimation a été réalisée si disponible
                - Si le dossier est refusé, explique le motif de façon simple sans jargon technique

                FORMAT À RESPECTER :
                Rapport client du dossier n°[ID]

                Bonjour [Nom du client],

                Statut de votre dossier :
                [phrase claire]

                Résumé :
                [explication simple]

                Suite prévue :
                [ce que le client doit attendre ou faire]

                Message de clôture :
                [phrase polie et rassurante]

                DONNÉES DU DOSSIER :
                ID dossier : %s
                Nom client : %s
                Type de contrat : %s
                Description du sinistre : %s
                Date du sinistre : %s
                Statut final : %s
                Résultat de couverture : %s
                Motif interne simplifiable : %s
                Estimation disponible : %s

                CONSIGNES SELON LE STATUT :
                - APPROVED : indique clairement que le dossier a été accepté
                - REJECTED : indique clairement que le dossier a été refusé
                - PENDING_VALIDATION : indique qu'une vérification complémentaire est en cours
                """.formatted(
                claim.getId(),
                clientName,
                policyType,
                safe(claim.getDescription()),
                claim.getIncidentDate(),
                statut,
                validationConclusion,
                validationReason,
                estimationConclusion
        );
    }

    private String cleanReport(String raw) {
        if (raw == null || raw.isBlank()) {
            return "Votre rapport est temporairement indisponible.";
        }

        return raw
                .replace("###", "")
                .replace("##", "")
                .replace("#", "")
                .replace("**", "")
                .replace("*", "")
                .replace("---", "")
                .replaceAll("[ \\t]+", " ")
                .replaceAll("\\n{3,}", "\n\n")
                .trim();
    }

    private String safe(String value) {
        return value == null || value.isBlank() ? "Information non disponible" : value.trim();
    }
}