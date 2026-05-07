package tn.esprit.insureflow_back.Service;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import tn.esprit.insureflow_back.Domain.ENUMS.ClaimStatus;
import tn.esprit.insureflow_back.Domain.Entities.Claim;
import tn.esprit.insureflow_back.Repository.ClaimRepository;

@Slf4j
@Service
@RequiredArgsConstructor
public class HumanValidationService {

    private final ClaimRepository claimRepository;
    private final RapportClientService rapportClientService;

    public Claim approuverClaim(Long claimId, String commentaire) {
        return approuverClaimAvecCorrection(claimId, commentaire, null, null, null);
    }

    public Claim approuverClaimAvecCorrection(
            Long claimId,
            String commentaire,
            Double montantMinCorrige,
            Double montantMoyenCorrige,
            Double montantMaxCorrige
    ) {
        Claim claim = claimRepository.findById(claimId)
                .orElseThrow(() -> new RuntimeException("Claim introuvable: " + claimId));

        verifierStatutPending(claim);

        claim.setStatus(ClaimStatus.APPROVED);

        boolean hasCorrection =
                montantMinCorrige != null ||
                        montantMoyenCorrige != null ||
                        montantMaxCorrige != null;

        String decisionLabel = hasCorrection
                ? "APPROUVÉ avec correction par gestionnaire"
                : "APPROUVÉ par gestionnaire";

        String ancienRapport = claim.getAiReport() != null ? claim.getAiReport() : "";

        claim.setAiReport(ancienRapport + "\n\n" +
                "=== DÉCISION HUMAINE ===\n" +
                "Statut     : " + decisionLabel + "\n" +
                (hasCorrection
                        ? "Montant min corrigé   : " + formatMontant(montantMinCorrige) + "\n" +
                        "Montant moyen corrigé : " + formatMontant(montantMoyenCorrige) + "\n" +
                        "Montant max corrigé   : " + formatMontant(montantMaxCorrige) + "\n"
                        : "") +
                "Commentaire: " + safe(commentaire));

        String clientReport = rapportClientService.genererRapportClientApresDecisionHumaine(
                claim,
                decisionLabel,
                safe(commentaire),
                montantMinCorrige,
                montantMoyenCorrige,
                montantMaxCorrige
        );

        claim.setClientReport(clientReport);

        Claim saved = claimRepository.save(claim);

        log.info("Claim #{} {} + rapport client généré", claimId, decisionLabel);

        return saved;
    }

    public Claim rejeterClaim(Long claimId, String commentaire) {
        Claim claim = claimRepository.findById(claimId)
                .orElseThrow(() -> new RuntimeException("Claim introuvable: " + claimId));

        verifierStatutPending(claim);

        claim.setStatus(ClaimStatus.REJECTED);

        String ancienRapport = claim.getAiReport() != null ? claim.getAiReport() : "";

        claim.setAiReport(ancienRapport + "\n\n" +
                "=== DÉCISION HUMAINE ===\n" +
                "Statut     : REJETÉ par gestionnaire\n" +
                "Raison     : " + safe(commentaire));

        String clientReport = rapportClientService.genererRapportClientApresDecisionHumaine(
                claim,
                "REJETÉ par gestionnaire",
                safe(commentaire),
                null,
                null,
                null
        );

        claim.setClientReport(clientReport);

        Claim saved = claimRepository.save(claim);

        log.info("Claim #{} REJETÉ par gestionnaire + rapport client généré", claimId);

        return saved;
    }

    private void verifierStatutPending(Claim claim) {
        if (!ClaimStatus.PENDING_VALIDATION.equals(claim.getStatus())) {
            throw new RuntimeException(
                    "Claim #" + claim.getId() +
                            " n'est pas en attente (statut actuel: " + claim.getStatus() + ")"
            );
        }
    }

    private String formatMontant(Double value) {
        return value == null ? "-" : String.format(java.util.Locale.US, "%.2f DT", value);
    }

    private String safe(String value) {
        return value == null ? "" : value.trim();
    }
}