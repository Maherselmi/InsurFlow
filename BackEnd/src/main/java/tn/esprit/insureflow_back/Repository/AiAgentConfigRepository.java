package tn.esprit.insureflow_back.Repository;

import org.springframework.data.jpa.repository.JpaRepository;
import tn.esprit.insureflow_back.Domain.Entities.AiAgentConfig;

import java.util.Optional;

public interface AiAgentConfigRepository extends JpaRepository<AiAgentConfig, Long> {
    Optional<AiAgentConfig> findByAgentName(String agentName);
}