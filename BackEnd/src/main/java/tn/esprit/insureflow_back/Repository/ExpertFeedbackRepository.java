package tn.esprit.insureflow_back.Repository;

import org.springframework.data.jpa.repository.JpaRepository;
import tn.esprit.insureflow_back.Domain.Entities.ExpertFeedback;

import java.util.Optional;

public interface ExpertFeedbackRepository extends JpaRepository<ExpertFeedback, Long> {
    Optional<ExpertFeedback> findByClaimId(Long claimId);
}