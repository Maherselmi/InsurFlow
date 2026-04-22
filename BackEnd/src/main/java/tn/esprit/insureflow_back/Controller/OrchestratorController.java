package tn.esprit.insureflow_back.Controller;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import tn.esprit.insureflow_back.Agent.AgentRouteur;
import tn.esprit.insureflow_back.Domain.Entities.AgentResult;
import tn.esprit.insureflow_back.Domain.Entities.Claim;
import tn.esprit.insureflow_back.Repository.AgentResultRepository;
import tn.esprit.insureflow_back.Repository.ClaimRepository;

@Slf4j
@RestController
@RequestMapping("/api/orchestrator")
@CrossOrigin(origins = "http://localhost:4200")
@RequiredArgsConstructor
public class OrchestratorController {

    private final AgentRouteur    agentRouteur;
    private final ClaimRepository claimRepository;
    private final AgentResultRepository agentResultRepository;

    // ✅ Test AgentRouteur sur un claim existant
    @PostMapping("/classify/{claimId}")
    public ResponseEntity<AgentResult> classifyClaim(@PathVariable Long claimId) {

        Claim claim = claimRepository.findById(claimId)
                .orElseThrow(() -> new RuntimeException("Claim introuvable : " + claimId));

        AgentResult result = agentRouteur.classifier(claim);

        log.info("✅ Classification terminée: {}", result.getConclusion());
        // 🔥 SAUVEGARDE EN BASE
        result.setClaim(claim); // IMPORTANT
        result = agentResultRepository.save(result);
        return ResponseEntity.ok(result);
    }
}