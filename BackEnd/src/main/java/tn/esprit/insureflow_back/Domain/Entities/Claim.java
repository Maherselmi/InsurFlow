package tn.esprit.insureflow_back.Domain.Entities;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import jakarta.persistence.*;
import lombok.Getter;
import lombok.RequiredArgsConstructor;
import lombok.Setter;
import tn.esprit.insureflow_back.Domain.ENUMS.ClaimStatus;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

@Entity
@Getter
@Setter
@JsonIgnoreProperties({"hibernateLazyInitializer", "handler"})
@RequiredArgsConstructor
public class Claim {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    private String description;
    private LocalDate incidentDate;
    private ClaimStatus status;

    @ManyToOne(fetch = FetchType.EAGER)
    @JsonIgnoreProperties({"claims"})  // ✅ "client" retiré — il doit apparaître
    private Policy policy;

    @ManyToOne(fetch = FetchType.EAGER)
    @JoinColumn(name = "client_id")
    @JsonIgnoreProperties({"policies", "claims"})
    private Client client;

    @OneToMany(mappedBy = "claim", cascade = CascadeType.ALL, fetch = FetchType.LAZY)
    @JsonIgnoreProperties("claim")
    private List<ClaimDocument> documents = new ArrayList<>();

    private LocalDateTime createdAt = LocalDateTime.now();
    @Column(columnDefinition = "TEXT")
    private String aiReport;
    @Column(columnDefinition = "TEXT")
    private String clientReport;
}