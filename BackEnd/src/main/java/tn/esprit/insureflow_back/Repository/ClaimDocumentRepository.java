package tn.esprit.insureflow_back.Repository;


import org.springframework.data.jpa.repository.JpaRepository;
import tn.esprit.insureflow_back.Domain.Entities.ClaimDocument;

import java.util.List;

public interface ClaimDocumentRepository extends JpaRepository<ClaimDocument, Long> {
    List<ClaimDocument> findByClaimId(Long claimId);
}