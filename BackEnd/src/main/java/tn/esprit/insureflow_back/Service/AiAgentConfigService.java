package tn.esprit.insureflow_back.Service;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import tn.esprit.insureflow_back.Domain.Entities.AiAgentConfig;
import tn.esprit.insureflow_back.Repository.AiAgentConfigRepository;

import java.util.List;

@Service
@RequiredArgsConstructor
public class AiAgentConfigService {

    private final AiAgentConfigRepository repository;

    public double getThreshold(String agentName) {
        return repository.findByAgentName(agentName)
                .map(AiAgentConfig::getConfidenceThreshold)
                .orElseGet(() -> getDefaultThreshold(agentName));
    }

    public List<AiAgentConfig> getAllConfigs() {
        return repository.findAll();
    }

    public AiAgentConfig updateThreshold(String agentName, Double threshold) {
        if (threshold == null || threshold < 0 || threshold > 1) {
            throw new IllegalArgumentException("Le seuil doit être entre 0 et 1.");
        }

        AiAgentConfig config = repository.findByAgentName(agentName)
                .orElse(
                        AiAgentConfig.builder()
                                .agentName(agentName)
                                .confidenceThreshold(threshold)
                                .build()
                );

        config.setConfidenceThreshold(threshold);
        return repository.save(config);
    }

    private double getDefaultThreshold(String agentName) {
        return switch (agentName) {
            case "AGENT_ROUTEUR" -> 0.70;
            case "AGENT_VALIDATION" -> 0.60;
            case "AGENT_ESTIMATEUR" -> 0.70;
            default -> 0.70;
        };
    }
}