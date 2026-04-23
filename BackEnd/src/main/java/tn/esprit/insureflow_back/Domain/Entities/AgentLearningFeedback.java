package tn.esprit.insureflow_back.Domain.Entities;

import jakarta.persistence.*;
import lombok.*;
import tn.esprit.insureflow_back.Domain.ENUMS.AgentName;

import java.time.LocalDateTime;

@Entity
@Table(
        name = "agent_learning_feedback",
        uniqueConstraints = @UniqueConstraint(columnNames = {"claim_id", "agent_name"})
)
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class AgentLearningFeedback {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "claim_id", nullable = false)
    private Claim claim;

    @Enumerated(EnumType.STRING)
    @Column(name = "agent_name", nullable = false)
    private AgentName agentName;

    @Column(columnDefinition = "TEXT")
    private String inputData;

    @Column(columnDefinition = "TEXT")
    private String agentOutput;

    @Column(columnDefinition = "TEXT")
    private String finalValidatedOutput;

    private Double confidenceScore;

    private Boolean humanReviewed;

    private Boolean wasCorrect;

    private Boolean useForLearning;

    private LocalDateTime createdAt;

    private LocalDateTime updatedAt;
}