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

    public Claim approuverClaim(Long claimId, String commentaire) {
        Claim claim = claimRepository.findById(claimId)
                .orElseThrow(() -> new RuntimeException("Claim introuvable: " + claimId));

        verifierStatutPending(claim);

        claim.setStatus(ClaimStatus.APPROVED);

        // ✅ Ajout de la décision humaine au rapport existant
        String ancienRapport = claim.getAiReport() != null ? claim.getAiReport() : "";
        claim.setAiReport(ancienRapport + "\n\n" +
                "=== DÉCISION HUMAINE ===\n" +
                "Statut     : APPROUVÉ par gestionnaire\n" +
                "Commentaire: " + commentaire);

        claimRepository.save(claim);
        log.info("✅ Claim #{} APPROUVÉ par gestionnaire", claimId);
        return claim;
    }

    public Claim rejeterClaim(Long claimId, String commentaire) {
        Claim claim = claimRepository.findById(claimId)
                .orElseThrow(() -> new RuntimeException("Claim introuvable: " + claimId));

        verifierStatutPending(claim);

        claim.setStatus(ClaimStatus.REJECTED);

        // ✅ Ajout de la décision humaine au rapport existant
        String ancienRapport = claim.getAiReport() != null ? claim.getAiReport() : "";
        claim.setAiReport(ancienRapport + "\n\n" +
                "=== DÉCISION HUMAINE ===\n" +
                "Statut     : REJETÉ par gestionnaire\n" +
                "Raison     : " + commentaire);

        claimRepository.save(claim);
        log.info("❌ Claim #{} REJETÉ par gestionnaire", claimId);
        return claim;
    }

    private void verifierStatutPending(Claim claim) {
        if (!claim.getStatus().equals(ClaimStatus.PENDING_VALIDATION)) {
            throw new RuntimeException(
                    "Claim #" + claim.getId() +
                            " n'est pas en attente (statut actuel: " + claim.getStatus() + ")"
            );
        }
    }
}