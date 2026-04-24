package tn.esprit.insureflow_back.Config;

import lombok.RequiredArgsConstructor;
import org.springframework.boot.CommandLineRunner;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.annotation.EnableAsync;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;
import org.springframework.stereotype.Component;
import tn.esprit.insureflow_back.Domain.Entities.AiAgentConfig;
import tn.esprit.insureflow_back.Repository.AiAgentConfigRepository;
import java.util.concurrent.Executor;

@Component
@RequiredArgsConstructor
public class AiAgentConfigInitializer implements CommandLineRunner {

    private final AiAgentConfigRepository repository;

    @Override
    public void run(String... args) {
        createIfNotExists("AGENT_ROUTEUR", 0.70);
        createIfNotExists("AGENT_VALIDATION", 0.60);
        createIfNotExists("AGENT_ESTIMATEUR", 0.70);
    }

    private void createIfNotExists(String agentName, double threshold) {
        repository.findByAgentName(agentName)
                .orElseGet(() ->
                        repository.save(
                                AiAgentConfig.builder()
                                        .agentName(agentName)
                                        .confidenceThreshold(threshold)
                                        .build()
                        )
                );
    }
}