package tn.esprit.insureflow_back.Service;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import tn.esprit.insureflow_back.Domain.Entities.AgentResult;
import tn.esprit.insureflow_back.Domain.Entities.Claim;

@Slf4j
@Service
@RequiredArgsConstructor
public class RapportService {

    private final LLMService llmService;

    public String genererRapport(Claim claim,
                                 AgentResult routeResult,
                                 AgentResult validationResult,
                                 AgentResult estimationResult) {

        String statut = claim.getStatus().name();
        String prompt = buildPrompt(claim, routeResult, validationResult, estimationResult, statut);

        log.info(" Génération rapport IA — claim #{} statut: {}", claim.getId(), statut);

        String raw = llmService.genererReponse(prompt);
        return cleanReport(raw);
    }

    private String buildPrompt(Claim claim,
                               AgentResult routeResult,
                               AgentResult validationResult,
                               AgentResult estimationResult,
                               String statut) {

        StringBuilder sb = new StringBuilder();

        sb.append("""
                Tu es un expert senior en assurance.
                Tu dois rédiger un rapport professionnel, clair, concis et exploitable par un gestionnaire humain.

                RÈGLES OBLIGATOIRES DE RÉDACTION :
                - Réponds uniquement en texte brut
                - N'utilise jamais Markdown
                - Interdiction d'utiliser les symboles suivants comme titres ou listes : #, ##, ###, *, **, -, ---
                - N'écris pas deux fois la même idée
                - N'utilise pas de phrases redondantes
                - Chaque section doit être courte, claire et utile
                - Si une information n'est pas disponible, indique simplement : "Information non disponible"
                - Ne répète pas les conclusions déjà évidentes
                - Le ton doit être professionnel, sobre, clair et orienté métier
                - Le rapport final doit être directement exploitable dans une interface de gestion de sinistres

                FORMAT STRICT À RESPECTER :
                Rapport de synthèse du sinistre n°[ID]

                1. Résumé du dossier
                [paragraphe court]

                2. Analyse des agents
                Agent routeur : ...
                Agent validateur : ...
                Agent estimateur : ...

                3. Points de vigilance
                [paragraphe ou lignes courtes]

                4. Recommandation finale
                [paragraphe court]

                5. Décision humaine
                Statut : ...
                Commentaire : ...

                IMPORTANT :
                - Pas de titres décoratifs
                - Pas de puces Markdown
                - Pas de répétition entre "résumé", "analyse" et "recommandation"
                - Si le statut est APPROVED, insiste sur la justification de couverture et l'estimation
                - Si le statut est REJECTED, insiste sur le motif de rejet
                - Si le statut est PENDING_VALIDATION, insiste sur les incertitudes et les vérifications nécessaires

                DONNÉES DU DOSSIER :
                """);

        sb.append("\nID Sinistre : ").append(claim.getId());
        sb.append("\nDescription : ").append(safe(claim.getDescription()));
        sb.append("\nDate du sinistre : ").append(claim.getIncidentDate());
        sb.append("\nStatut final : ").append(statut);

        if (routeResult != null) {
            sb.append("\n\nDONNÉES AGENT ROUTEUR");
            sb.append("\nConclusion : ").append(safe(routeResult.getConclusion()));
            sb.append("\nScore de confiance : ").append(routeResult.getConfidenceScore());
            sb.append("\nDétail brut : ").append(safe(routeResult.getRawLlmResponse()));
        }

        if (validationResult != null) {
            sb.append("\n\nDONNÉES AGENT VALIDATEUR");
            sb.append("\nConclusion : ").append(safe(validationResult.getConclusion()));
            sb.append("\nScore de confiance : ").append(validationResult.getConfidenceScore());
            sb.append("\nDétail brut : ").append(safe(validationResult.getRawLlmResponse()));
        }

        if (estimationResult != null) {
            sb.append("\n\nDONNÉES AGENT ESTIMATEUR");
            sb.append("\nConclusion : ").append(safe(estimationResult.getConclusion()));
            sb.append("\nScore de confiance : ").append(estimationResult.getConfidenceScore());
            sb.append("\nDétail brut : ").append(safe(estimationResult.getRawLlmResponse()));
        }

        sb.append("\n\nINSTRUCTIONS SPÉCIFIQUES SELON LE STATUT :\n");

        switch (statut) {
            case "APPROVED" -> sb.append("""
                    Le dossier est approuvé.
                    Le rapport doit :
                    - confirmer clairement la couverture
                    - résumer le fondement de l'approbation
                    - intégrer l'estimation financière sans répéter les mêmes montants plusieurs fois
                    - proposer une recommandation opérationnelle simple
                    """);

            case "REJECTED" -> sb.append("""
                    Le dossier est rejeté.
                    Le rapport doit :
                    - expliquer clairement le motif principal du rejet
                    - mentionner la logique de non-couverture
                    - éviter tout ton ambigu
                    - proposer, si pertinent, une orientation sur les suites possibles
                    """);

            case "PENDING_VALIDATION" -> sb.append("""
                    Le dossier est en attente de validation humaine.
                    Le rapport doit :
                    - expliquer la cause du blocage ou de l'incertitude
                    - indiquer les points qui demandent une vérification humaine
                    - aider le gestionnaire à prendre une décision
                    - rester synthétique et orienté action
                    """);

            default -> sb.append("""
                    Rédige un rapport neutre, structuré et professionnel.
                    """);
        }

        sb.append("""
                
                
                GÉNÈRE MAINTENANT LE RAPPORT FINAL EN TEXTE BRUT UNIQUEMENT.
                """);

        return sb.toString();
    }

    private String cleanReport(String raw) {
        if (raw == null || raw.isBlank()) {
            return "Rapport indisponible.";
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