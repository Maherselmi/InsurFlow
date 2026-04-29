package tn.esprit.insureflow_back.Domain.Entities;

import jakarta.persistence.*;
import lombok.*;
import tn.esprit.insureflow_back.Domain.ENUMS.AgentName;

import java.time.LocalDateTime;

@Entity
@Table(
        name = "agent_learning_feedback",
        uniqueConstraints = {
                @UniqueConstraint(
                        name = "uk_learning_feedback_claim_agent",
                        columnNames = {"claim_id", "agent_name"}
                )
        }
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
    @Column(name = "agent_name", nullable = false, length = 60)
    private AgentName agentName;

    @Column(name = "input_data", columnDefinition = "TEXT")
    private String inputData;

    @Column(name = "agent_output", columnDefinition = "TEXT")
    private String agentOutput;

    @Column(name = "final_validated_output", columnDefinition = "TEXT")
    private String finalValidatedOutput;

    @Column(name = "was_correct")
    private Boolean wasCorrect;

    @Column(name = "use_for_learning")
    private Boolean useForLearning;

    @Column(name = "predicted_confidence")
    private Double predictedConfidence;

    @Column(name = "satisfaction_score")
    private Integer satisfactionScore;

    @Column(name = "reviewed_by", length = 150)
    private String reviewedBy;

    @Column(name = "expert_comment", columnDefinition = "TEXT")
    private String expertComment;

    @Column(name = "created_at")
    private LocalDateTime createdAt;

    @Column(name = "updated_at")
    private LocalDateTime updatedAt;

    @PrePersist
    public void onCreate() {
        LocalDateTime now = LocalDateTime.now();

        if (createdAt == null) {
            createdAt = now;
        }

        updatedAt = now;

        if (wasCorrect == null) {
            wasCorrect = false;
        }

        if (useForLearning == null) {
            useForLearning = true;
        }

        if (satisfactionScore == null) {
            satisfactionScore = 5;
        }

        if (predictedConfidence == null) {
            predictedConfidence = 0.0;
        }
    }

    @PreUpdate
    public void onUpdate() {
        updatedAt = LocalDateTime.now();

        if (wasCorrect == null) {
            wasCorrect = false;
        }

        if (useForLearning == null) {
            useForLearning = true;
        }

        if (satisfactionScore == null) {
            satisfactionScore = 5;
        }

        if (predictedConfidence == null) {
            predictedConfidence = 0.0;
        }
    }
}