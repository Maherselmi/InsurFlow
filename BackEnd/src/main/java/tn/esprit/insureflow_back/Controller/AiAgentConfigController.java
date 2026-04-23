package tn.esprit.insureflow_back.Controller;

import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import tn.esprit.insureflow_back.DTO.AiAgentConfigRequest;
import tn.esprit.insureflow_back.Domain.Entities.AiAgentConfig;
import tn.esprit.insureflow_back.Service.AiAgentConfigService;

import java.util.List;

@RestController
@RequestMapping("/api/admin/ai-config")
@CrossOrigin(origins = "http://localhost:4200")
@RequiredArgsConstructor
public class AiAgentConfigController {

    private final AiAgentConfigService service;

    @GetMapping
    public ResponseEntity<List<AiAgentConfig>> getAllConfigs() {
        return ResponseEntity.ok(service.getAllConfigs());
    }

    @PutMapping
    public ResponseEntity<AiAgentConfig> updateThreshold(@RequestBody AiAgentConfigRequest request) {
        AiAgentConfig updated = service.updateThreshold(
                request.getAgentName(),
                request.getConfidenceThreshold()
        );
        return ResponseEntity.ok(updated);
    }
}