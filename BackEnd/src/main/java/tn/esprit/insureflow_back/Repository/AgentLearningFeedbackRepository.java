package tn.esprit.insureflow_back.Repository;

import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import tn.esprit.insureflow_back.Domain.ENUMS.AgentName;
import tn.esprit.insureflow_back.Domain.Entities.AgentLearningFeedback;

import java.util.List;
import java.util.Optional;

public interface AgentLearningFeedbackRepository extends JpaRepository<AgentLearningFeedback, Long> {

    Optional<AgentLearningFeedback> findByClaim_IdAndAgentName(Long claimId, AgentName agentName);

    @Query("""
            select f
            from AgentLearningFeedback f
            where f.agentName = :agentName
              and f.useForLearning = true
              and f.finalValidatedOutput is not null
              and (:currentClaimId is null or f.claim.id <> :currentClaimId)
            order by
              case when f.wasCorrect = false then 0 else 1 end,
              f.updatedAt desc
            """)
    List<AgentLearningFeedback> findLearningExamples(
            @Param("agentName") AgentName agentName,
            @Param("currentClaimId") Long currentClaimId,
            Pageable pageable
    );

    @Query("""
            select f
            from AgentLearningFeedback f
            where f.agentName = :agentName
              and f.useForLearning = true
              and f.finalValidatedOutput is not null
            order by f.updatedAt desc
            """)
    List<AgentLearningFeedback> findLatestLearningExamples(
            @Param("agentName") AgentName agentName,
            Pageable pageable
    );
    List<AgentLearningFeedback> findTop10ByAgentNameAndUseForLearningTrueOrderByUpdatedAtDesc(
            AgentName agentName
    );

    List<AgentLearningFeedback> findTop5ByAgentNameAndUseForLearningTrueAndClaim_IdNotOrderByUpdatedAtDesc(
            AgentName agentName,
            Long claimId
    );
}
