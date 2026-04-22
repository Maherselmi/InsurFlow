package tn.esprit.insureflow_back.Controller;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import tn.esprit.insureflow_back.Domain.Entities.AgentResult;
import tn.esprit.insureflow_back.Repository.AgentResultRepository;

import java.util.List;

@CrossOrigin(origins = "http://localhost:4200")
@RestController
@RequestMapping("/api/agent-results")
@RequiredArgsConstructor
public class AgentResultController {

    private final AgentResultRepository agentResultRepository;

    @GetMapping
    public ResponseEntity<List<AgentResult>> getAll() {
        return ResponseEntity.ok(agentResultRepository.findAll());
    }

    @GetMapping("/claim/{claimId}")
    public ResponseEntity<List<AgentResult>> getByClaim(@PathVariable Long claimId) {
        return ResponseEntity.ok(
                agentResultRepository.findAll().stream()
                        .filter(r -> r.getClaim() != null &&
                                r.getClaim().getId().equals(claimId))
                        .toList()
        );
    }
}