package tn.esprit.insureflow_back.Controller;

import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import tn.esprit.insureflow_back.Domain.ENUMS.ClaimStatus;
import tn.esprit.insureflow_back.Domain.Entities.Claim;
import tn.esprit.insureflow_back.Repository.ClaimRepository;
import tn.esprit.insureflow_back.Service.ClaimService;
import tn.esprit.insureflow_back.Service.HumanValidationService;

import java.util.List;
import java.util.Map;

@CrossOrigin(origins = "http://localhost:4200")
@RestController
@RequestMapping("/api/claims")
@RequiredArgsConstructor
public class ClaimController {

    @Autowired
    private ClaimService claimService;

    private final ClaimRepository claimRepository;
    private final HumanValidationService humanValidationService;

    // ─── endpoints existants ───────────────────────────────────────────────

    @PostMapping
    public Claim createClaim(@RequestBody Claim claim) {
        return claimService.createClaim(claim);
    }

    @GetMapping
    public List<Claim> getAllClaims() {
        return claimService.getAllClaims();
    }

    @GetMapping("/{id}")
    public Claim getClaim(@PathVariable Long id) {
        return claimService.getClaimById(id);
    }

    // ─── NOUVEAUX endpoints validation humaine ────────────────────────────

    // ✅ Liste tous les claims en attente de validation humaine
    @GetMapping("/pending-validation")
    public ResponseEntity<List<Claim>> getPendingValidation() {
        List<Claim> pending = claimRepository.findByStatus(ClaimStatus.PENDING_VALIDATION);
        return ResponseEntity.ok(pending);
    }

    // ✅ Détail d'un claim + rapport IA pour le gestionnaire
    @GetMapping("/{id}/review")
    public ResponseEntity<Map<String, Object>> getClaimForReview(@PathVariable Long id) {
        Claim claim = claimRepository.findById(id)
                .orElseThrow(() -> new RuntimeException("Claim introuvable"));

        if (!claim.getStatus().equals(ClaimStatus.PENDING_VALIDATION)) {
            return ResponseEntity.badRequest()
                    .body(Map.of("error", "Ce claim n'est pas en attente de validation",
                            "statut", claim.getStatus().name()));
        }

        return ResponseEntity.ok(Map.of(
                "id",           claim.getId(),
                "description",  claim.getDescription(),
                "status",       claim.getStatus(),
                "incidentDate", claim.getIncidentDate(),
                "createdAt",    claim.getCreatedAt(),
                "aiReport",     claim.getAiReport() != null
                        ? claim.getAiReport()
                        : "Aucun rapport disponible"
        ));
    }

    // ✅ Gestionnaire APPROUVE
    @PostMapping("/{id}/approve")
    public ResponseEntity<Map<String, Object>> approveClaim(
            @PathVariable Long id,
            @RequestBody HumanDecisionRequest request) {

        Claim updated = humanValidationService.approuverClaim(id, request.comment());
        return ResponseEntity.ok(Map.of(
                "message", "Claim approuvé avec succès",
                "claimId", updated.getId(),
                "status",  updated.getStatus().name()
        ));
    }

    // ✅ Gestionnaire REJETTE
    @PostMapping("/{id}/reject")
    public ResponseEntity<Map<String, Object>> rejectClaim(
            @PathVariable Long id,
            @RequestBody HumanDecisionRequest request) {

        Claim updated = humanValidationService.rejeterClaim(id, request.comment());
        return ResponseEntity.ok(Map.of(
                "message", "Claim rejeté avec succès",
                "claimId", updated.getId(),
                "status",  updated.getStatus().name()
        ));
    }

    // DTO décision humaine
    public record HumanDecisionRequest(String comment) {}
}