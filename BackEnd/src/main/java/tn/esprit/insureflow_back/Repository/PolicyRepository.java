package tn.esprit.insureflow_back.Repository;


import org.springframework.data.jpa.repository.JpaRepository;
import tn.esprit.insureflow_back.Domain.Entities.Policy;

public interface PolicyRepository extends JpaRepository<Policy, Long> {
}
