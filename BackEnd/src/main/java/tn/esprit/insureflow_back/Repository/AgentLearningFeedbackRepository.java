package tn.esprit.insureflow_back.Repository;

import org.springframework.data.jpa.repository.JpaRepository;
import tn.esprit.insureflow_back.Domain.ENUMS.AgentName;
import tn.esprit.insureflow_back.Domain.Entities.AgentLearningFeedback;

import java.util.List;
import java.util.Optional;

public interface AgentLearningFeedbackRepository extends JpaRepository<AgentLearningFeedback, Long> {

    Optional<AgentLearningFeedback> findByClaimIdAndAgentName(Long claimId, AgentName agentName);

    List<AgentLearningFeedback> findTop10ByAgentNameAndUseForLearningTrueAndWasCorrectTrueOrderByUpdatedAtDesc(
            AgentName agentName
    );

    List<AgentLearningFeedback> findTop5ByAgentNameAndUseForLearningTrueAndWasCorrectTrueAndClaim_IdNotOrderByUpdatedAtDesc(
            AgentName agentName,
            Long claimId
    );
}