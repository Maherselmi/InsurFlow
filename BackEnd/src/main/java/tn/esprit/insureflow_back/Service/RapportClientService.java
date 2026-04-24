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

    public String genererRapportClient(Claim claim,
                                       AgentResult routeResult,
                                       AgentResult validationResult,
                                       AgentResult estimationResult) {

        String statut = claim.getStatus() != null ? claim.getStatus().name() : "INCONNU";

        log.info("Génération rapport client rapide - claim #{} statut: {}", claim.getId(), statut);

        String clientName = buildClientName(claim);
        String policyType = claim.getPolicy() != null
                ? safe(claim.getPolicy().getType())
                : "Information non disponible";

        String validationConclusion = validationResult != null
                ? safe(validationResult.getConclusion())
                : "Information non disponible";

        String estimationConclusion = estimationResult != null
                ? safe(estimationResult.getConclusion())
                : "Information non disponible";

        String statutPhrase = buildClientStatusPhrase(statut);
        String resume = buildClientResume(statut, policyType, validationConclusion, estimationConclusion);
        String suite = buildClientNextStep(statut);
        String closing = buildClientClosing(statut);

        return """
                Rapport client du dossier n°%s

                Bonjour %s,

                Statut de votre dossier :
                %s

                Résumé :
                %s

                Suite prévue :
                %s

                Message de clôture :
                %s

                DONNÉES DU DOSSIER :
                ID dossier : %s
                Nom client : %s
                Type de contrat : %s
                Description du sinistre : %s
                Date du sinistre : %s
                Statut final : %s
                Résultat de couverture : %s
                Estimation disponible : %s
                """
                .formatted(
                        claim.getId(),
                        clientName,
                        statutPhrase,
                        resume,
                        suite,
                        closing,
                        claim.getId(),
                        clientName,
                        policyType,
                        safe(claim.getDescription()),
                        claim.getIncidentDate() != null ? claim.getIncidentDate().toString() : "Information non disponible",
                        statut,
                        validationConclusion,
                        estimationConclusion
                )
                .trim();
    }

    private String buildClientName(Claim claim) {
        if (claim.getClient() != null) {
            String firstName = safe(claim.getClient().getFirstName());
            String lastName = safe(claim.getClient().getLastName());
            String fullName = (firstName + " " + lastName).trim();
            if (!fullName.isBlank() && !"Information non disponible Information non disponible".equals(fullName)) {
                return fullName;
            }
        }

        if (claim.getPolicy() != null && claim.getPolicy().getClient() != null) {
            String firstName = safe(claim.getPolicy().getClient().getFirstName());
            String lastName = safe(claim.getPolicy().getClient().getLastName());
            String fullName = (firstName + " " + lastName).trim();
            if (!fullName.isBlank() && !"Information non disponible Information non disponible".equals(fullName)) {
                return fullName;
            }
        }

        return "Client";
    }

    private String buildClientStatusPhrase(String statut) {
        return switch (statut) {
            case "APPROVED" -> "Votre dossier a été accepté.";
            case "REJECTED" -> "Votre dossier a été refusé.";
            case "PENDING_VALIDATION" -> "Votre dossier est en cours de vérification complémentaire.";
            default -> "Le traitement de votre dossier est en cours.";
        };
    }

    private String buildClientResume(String statut,
                                     String policyType,
                                     String validationConclusion,
                                     String estimationConclusion) {
        return switch (statut) {
            case "APPROVED" -> """
                    Votre déclaration a été analysée et la prise en charge a été confirmée au regard de votre contrat %s.
                    Une estimation du sinistre a également été préparée : %s.
                    """.formatted(policyType, estimationConclusion).trim();

            case "REJECTED" -> """
                    Après analyse du dossier et du contrat %s, votre demande n’a pas pu être retenue.
                    Résultat de l’analyse de couverture : %s.
                    """.formatted(policyType, validationConclusion).trim();

            case "PENDING_VALIDATION" -> """
                    Votre dossier nécessite encore une vérification complémentaire avant décision finale.
                    Le contrat concerné est de type %s et une revue humaine est en cours.
                    """.formatted(policyType).trim();

            default -> """
                    Votre dossier est en cours de traitement.
                    Le contrat concerné est de type %s.
                    """.formatted(policyType).trim();
        };
    }

    private String buildClientNextStep(String statut) {
        return switch (statut) {
            case "APPROVED" -> "Nous allons poursuivre la procédure d’indemnisation selon les éléments validés dans votre dossier.";
            case "REJECTED" -> "Vous pouvez consulter le détail de votre dossier ou contacter votre gestionnaire pour toute précision complémentaire.";
            case "PENDING_VALIDATION" -> "Une vérification complémentaire est en cours. Vous serez informé dès qu’une décision finale sera prise.";
            default -> "Veuillez suivre l’évolution du dossier depuis votre espace client.";
        };
    }

    private String buildClientClosing(String statut) {
        return switch (statut) {
            case "APPROVED" -> "Nous vous remercions pour votre confiance et restons à votre disposition pour la suite de votre prise en charge.";
            case "REJECTED" -> "Nous restons à votre disposition pour toute question complémentaire concernant votre dossier.";
            case "PENDING_VALIDATION" -> "Merci de votre patience. Nous reviendrons vers vous dès que l’analyse sera finalisée.";
            default -> "Merci de votre confiance.";
        };
    }

    private String safe(String value) {
        return value == null || value.isBlank() ? "Information non disponible" : value.trim();
    }
}