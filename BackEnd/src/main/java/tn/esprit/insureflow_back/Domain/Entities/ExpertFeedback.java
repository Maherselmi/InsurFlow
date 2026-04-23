package tn.esprit.insureflow_back.Domain.Entities;

import jakarta.persistence.*;
import lombok.*;

import java.time.LocalDateTime;

@Entity
@Table(
        name = "expert_feedback",
        uniqueConstraints = @UniqueConstraint(columnNames = "claim_id")
)
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class ExpertFeedback {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @OneToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "claim_id", nullable = false, unique = true)
    private Claim claim;

    private String reviewedBy;

    private LocalDateTime reviewedAt;

    private Boolean useForLearning;

    @Column(columnDefinition = "TEXT")
    private String globalComment;

    // ROUTEUR
    private String predictedType;
    private Double routeurConfidence;
    private Boolean routeurCorrect;
    private String finalType;

    @Column(columnDefinition = "TEXT")
    private String routeurComment;

    // VALIDATION
    private String predictedDecision;
    private Double validationConfidence;
    private Boolean validationCorrect;
    private String finalDecision;

    @Column(columnDefinition = "TEXT")
    private String validationComment;

    //ESTIMATEUR
    private Double predictedEstimationMin;
    private Double predictedEstimationMoyenne;
    private Double predictedEstimationMax;
    private Double estimateurConfidence;

    private String estimateEvaluation;

    private Double finalEstimationMin;
    private Double finalEstimationMoyenne;
    private Double finalEstimationMax;

    @Column(columnDefinition = "TEXT")
    private String estimateurComment;
}