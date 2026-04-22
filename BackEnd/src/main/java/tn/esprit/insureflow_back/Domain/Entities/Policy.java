package tn.esprit.insureflow_back.Domain.Entities;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;

@Entity
@Table(name = "policy")
@Getter
@Setter
@JsonIgnoreProperties({"hibernateLazyInitializer", "handler"})
public class Policy {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, unique = true, length = 100)
    private String policyNumber;

    @Column(nullable = false, length = 50)
    private String type; // AUTO, SANTE, HABITATION

    @Column(length = 100)
    private String formule; // ESSENTIEL, CONFORT, PREMIUM

    @Column(length = 100)
    private String productCode; // ex: SANTE_PREMIUM_2024

    @Column(nullable = false)
    private LocalDate startDate;

    @Column(nullable = false)
    private LocalDate endDate;

    @Column(length = 2000)
    private String coverageDetails;

    @ManyToOne(fetch = FetchType.EAGER)
    @JoinColumn(name = "client_id", nullable = false)
    @JsonIgnoreProperties({"policies", "claims", "user"})
    private Client client;

    @OneToMany(mappedBy = "policy", cascade = CascadeType.ALL, orphanRemoval = true, fetch = FetchType.LAZY)
    @JsonIgnoreProperties({"policy"})
    private List<Claim> claims = new ArrayList<>();
}