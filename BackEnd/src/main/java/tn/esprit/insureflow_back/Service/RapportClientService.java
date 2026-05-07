package tn.esprit.insureflow_back.Service;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import tn.esprit.insureflow_back.Domain.ENUMS.ClaimStatus;
import tn.esprit.insureflow_back.Domain.Entities.AgentResult;
import tn.esprit.insureflow_back.Domain.Entities.Claim;

@Slf4j
@Service
@RequiredArgsConstructor
public class RapportClientService {

    public String genererRapportClient(
            Claim claim,
            AgentResult routeResult,
            AgentResult validationResult,
            AgentResult estimationResult
    ) {
        if (claim == null) {
            throw new IllegalArgumentException("Claim obligatoire");
        }

        ClaimStatus status = claim.getStatus();

        if (status == ClaimStatus.PENDING_VALIDATION) {
            log.info("Pas de rapport client pour claim #{} car statut=PENDING_VALIDATION", claim.getId());
            return null;
        }

        if (status != ClaimStatus.APPROVED && status != ClaimStatus.REJECTED) {
            log.info("Pas de rapport client pour claim #{} car statut={}", claim.getId(), status);
            return null;
        }

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

        String statusPhrase = buildClientStatusPhrase(status);
        String resume = buildClientResume(status, policyType, validationConclusion, estimationConclusion);
        String suite = buildClientNextStep(status);
        String closing = buildClientClosing(status);

        return """
                Rapport client du dossier n°%s

                Bonjour %s,

                Statut de votre dossier :
                %s

                Résumé :
                %s

                Suite prévue :
                %s

                Message :
                %s

                DONNÉES DU DOSSIER :
                ID dossier : %s
                Nom client : %s
                Type de contrat : %s
                Description du sinistre : %s
                Date du sinistre : %s
                Statut final : %s
                Résultat de couverture : %s
                Estimation retenue : %s
                """
                .formatted(
                        claim.getId(),
                        clientName,
                        statusPhrase,
                        resume,
                        suite,
                        closing,
                        claim.getId(),
                        clientName,
                        policyType,
                        safe(claim.getDescription()),
                        claim.getIncidentDate() != null ? claim.getIncidentDate().toString() : "Information non disponible",
                        status.name(),
                        validationConclusion,
                        status == ClaimStatus.APPROVED ? estimationConclusion : "Non applicable"
                )
                .trim();
    }

    public String genererRapportClientApresDecisionHumaine(
            Claim claim,
            String decisionHumaine,
            String commentaire,
            Double montantMinCorrige,
            Double montantMoyenCorrige,
            Double montantMaxCorrige
    ) {
        if (claim == null) {
            throw new IllegalArgumentException("Claim obligatoire");
        }

        if (claim.getStatus() != ClaimStatus.APPROVED && claim.getStatus() != ClaimStatus.REJECTED) {
            return null;
        }

        String clientName = buildClientName(claim);

        String policyType = claim.getPolicy() != null
                ? safe(claim.getPolicy().getType())
                : "Information non disponible";

        String statusPhrase = buildClientStatusPhrase(claim.getStatus());
        String commentaireSafe = safe(commentaire);

        String estimationRetenue = claim.getStatus() == ClaimStatus.APPROVED
                ? buildEstimationRetenue(montantMinCorrige, montantMoyenCorrige, montantMaxCorrige)
                : "Non applicable";

        return """
                Rapport client du dossier n°%s

                Bonjour %s,

                Statut de votre dossier :
                %s

                Décision finale :
                %s

                Commentaire du gestionnaire :
                %s

                Résumé :
                Votre dossier relatif au contrat %s a été examiné par un expert.
                Une décision finale a été prise après vérification humaine.

                Suite prévue :
                %s

                Message :
                %s

                DONNÉES DU DOSSIER :
                ID dossier : %s
                Nom client : %s
                Type de contrat : %s
                Description du sinistre : %s
                Date du sinistre : %s
                Statut final : %s
                Estimation retenue : %s
                """
                .formatted(
                        claim.getId(),
                        clientName,
                        statusPhrase,
                        decisionHumaine,
                        commentaireSafe,
                        policyType,
                        buildClientNextStep(claim.getStatus()),
                        buildClientClosing(claim.getStatus()),
                        claim.getId(),
                        clientName,
                        policyType,
                        safe(claim.getDescription()),
                        claim.getIncidentDate() != null ? claim.getIncidentDate().toString() : "Information non disponible",
                        claim.getStatus().name(),
                        estimationRetenue
                )
                .trim();
    }

    private String buildEstimationRetenue(Double min, Double moyenne, Double max) {
        if (min == null && moyenne == null && max == null) {
            return "Information non disponible";
        }

        return "min=" + formatMontant(min)
                + ", moyenne=" + formatMontant(moyenne)
                + ", max=" + formatMontant(max);
    }

    private String formatMontant(Double value) {
        return value == null ? "-" : String.format(java.util.Locale.US, "%.2f DT", value);
    }

    private String buildClientName(Claim claim) {
        if (claim.getClient() != null) {
            String fullName = (safe(claim.getClient().getFirstName()) + " " + safe(claim.getClient().getLastName())).trim();
            if (!fullName.isBlank()) return fullName;
        }

        if (claim.getPolicy() != null && claim.getPolicy().getClient() != null) {
            String fullName = (safe(claim.getPolicy().getClient().getFirstName()) + " " + safe(claim.getPolicy().getClient().getLastName())).trim();
            if (!fullName.isBlank()) return fullName;
        }

        return "Client";
    }

    private String buildClientStatusPhrase(ClaimStatus status) {
        return switch (status) {
            case APPROVED -> "Votre dossier a été accepté.";
            case REJECTED -> "Votre dossier a été refusé.";
            default -> "Le traitement de votre dossier est en cours.";
        };
    }

    private String buildClientResume(
            ClaimStatus status,
            String policyType,
            String validationConclusion,
            String estimationConclusion
    ) {
        return switch (status) {
            case APPROVED -> """
                    Votre déclaration a été analysée et la prise en charge a été confirmée au regard de votre contrat %s.
                    Une estimation du sinistre a été préparée : %s.
                    """.formatted(policyType, estimationConclusion).trim();

            case REJECTED -> """
                    Après analyse du dossier et du contrat %s, votre demande n’a pas pu être retenue.
                    Résultat de l’analyse de couverture : %s.
                    """.formatted(policyType, validationConclusion).trim();

            default -> "Votre dossier est en cours de traitement.";
        };
    }

    private String buildClientNextStep(ClaimStatus status) {
        return switch (status) {
            case APPROVED -> "Nous allons poursuivre la procédure d’indemnisation selon les éléments validés.";
            case REJECTED -> "Vous pouvez consulter le détail de votre dossier ou contacter votre gestionnaire.";
            default -> "Veuillez suivre l’évolution du dossier depuis votre espace client.";
        };
    }

    private String buildClientClosing(ClaimStatus status) {
        return switch (status) {
            case APPROVED -> "Nous vous remercions pour votre confiance.";
            case REJECTED -> "Nous restons à votre disposition pour toute question complémentaire.";
            default -> "Merci de votre confiance.";
        };
    }

    private String safe(String value) {
        return value == null || value.isBlank() ? "" : value.trim();
    }
}