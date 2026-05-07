package tn.esprit.insureflow_back.Repository;

import org.springframework.data.jpa.repository.JpaRepository;
import tn.esprit.insureflow_back.Domain.Entities.ContratVectorFile;

import java.util.List;

public interface ContratVectorFileRepository extends JpaRepository<ContratVectorFile, Long> {

    List<ContratVectorFile> findByTypeContratIgnoreCase(String typeContrat);
}