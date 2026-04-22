package tn.esprit.insureflow_back.Domain.Entities;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import jakarta.persistence.*;
import lombok.*;
import java.time.LocalDateTime;

@Entity
@Table(name = "agent_results")
@Getter @Setter @NoArgsConstructor @AllArgsConstructor @Builder
@JsonIgnoreProperties({"hibernateLazyInitializer", "handler"})
public class AgentResult {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    private String agentName;
    private String conclusion;
    private Double confidenceScore;
    private boolean needsHumanReview;

    @Column(columnDefinition = "TEXT")
    private String rawLlmResponse;

    private LocalDateTime createdAt;

    // ✅ Charger eagerly + ignorer les champs circulaires
    @ManyToOne(fetch = FetchType.EAGER)
    @JoinColumn(name = "claim_id")
    @JsonIgnoreProperties({"documents", "agentResults", "hibernateLazyInitializer"})
    private Claim claim;

    @PrePersist
    public void prePersist() {
        this.createdAt = LocalDateTime.now();
    }
}