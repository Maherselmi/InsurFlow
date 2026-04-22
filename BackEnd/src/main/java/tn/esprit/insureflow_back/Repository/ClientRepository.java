package tn.esprit.insureflow_back.Repository;

import tn.esprit.insureflow_back.Domain.Entities.Client;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface ClientRepository extends JpaRepository<Client, Long> {
}