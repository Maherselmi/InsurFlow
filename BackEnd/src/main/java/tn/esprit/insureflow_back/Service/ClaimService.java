package tn.esprit.insureflow_back.Service;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import tn.esprit.insureflow_back.Domain.ENUMS.ClaimStatus;
import tn.esprit.insureflow_back.Domain.Entities.Claim;
import tn.esprit.insureflow_back.Domain.Entities.Policy;
import tn.esprit.insureflow_back.Repository.ClaimRepository;
import tn.esprit.insureflow_back.Repository.PolicyRepository;

import java.util.List;

@Service
@RequiredArgsConstructor
public class ClaimService {

    private final ClaimRepository claimRepository;
    private final PolicyRepository policyRepository;


    @Transactional
    public Claim createClaim(Claim claim) {

        if (claim.getPolicy() != null && claim.getPolicy().getId() != null) {
            Policy existingPolicy = policyRepository.findById(claim.getPolicy().getId())
                    .orElseThrow(() -> new RuntimeException("Policy not found"));
            claim.setPolicy(existingPolicy);
        }

        claim.setStatus(ClaimStatus.PENDING_VALIDATION);

        // 💾 sauvegarde
        Claim savedClaim = claimRepository.save(claim);

        return savedClaim;
    }

    public List<Claim> getAllClaims() {
        return claimRepository.findAllWithClient();
    }

    public Claim getClaimById(Long id) {
        return claimRepository.findById(id)
                .orElseThrow(() -> new RuntimeException("Claim not found with id: " + id));
    }
}