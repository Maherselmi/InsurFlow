package tn.esprit.insureflow_back.Domain.Entities;

import jakarta.persistence.*;
import lombok.*;

@Entity
@Table(name = "ai_agent_config")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class AiAgentConfig {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "agent_name", unique = true, nullable = false)
    private String agentName;

    @Column(name = "confidence_threshold", nullable = false)
    private Double confidenceThreshold;
}