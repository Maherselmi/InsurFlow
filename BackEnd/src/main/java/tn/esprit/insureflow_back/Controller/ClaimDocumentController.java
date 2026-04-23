package tn.esprit.insureflow_back.Controller;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;
import tn.esprit.insureflow_back.Domain.Entities.Claim;
import tn.esprit.insureflow_back.Domain.Entities.ClaimDocument;
import tn.esprit.insureflow_back.Repository.ClaimRepository;
import tn.esprit.insureflow_back.Service.ClaimDocumentService;
import tn.esprit.insureflow_back.Orchestrator.OrchestratorService;

import java.io.IOException;
@CrossOrigin(origins = "http://localhost:4200")
@RestController
@RequestMapping("/api/documents")
@RequiredArgsConstructor
public class ClaimDocumentController {

    private final ClaimDocumentService documentService;
    private final OrchestratorService orchestratorService;   // 🆕
    private final ClaimRepository claimRepository;           // 🆕

    @PostMapping("/upload/{claimId}")
    public ResponseEntity<ClaimDocument> upload(
            @PathVariable Long claimId,
            @RequestParam("file") MultipartFile file
    ) throws IOException {
        return ResponseEntity.ok(documentService.uploadFile(claimId, file));
    }

    // 🆕 Déclencher l'orchestrateur après tous les uploads
    @PostMapping("/process/{claimId}")
    public ResponseEntity<String> processClaim(@PathVariable Long claimId) {
        Claim claim = claimRepository.findById(claimId)
                .orElseThrow(() -> new RuntimeException("Claim not found"));
        orchestratorService.processClaim(claim);
        return ResponseEntity.ok("✅ Traitement démarré pour claim #" + claimId);
    }
}