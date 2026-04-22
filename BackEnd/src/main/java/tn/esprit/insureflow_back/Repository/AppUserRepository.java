package tn.esprit.insureflow_back.Repository;

import org.springframework.data.jpa.repository.JpaRepository;
import tn.esprit.insureflow_back.Domain.Entities.AppUser;

import java.util.Optional;

public interface AppUserRepository extends JpaRepository<AppUser, Long> {
    Optional<AppUser> findByEmail(String email);
    boolean existsByEmail(String email);
}