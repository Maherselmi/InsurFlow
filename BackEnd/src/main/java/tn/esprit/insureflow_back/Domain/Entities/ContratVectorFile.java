package tn.esprit.insureflow_back.Domain.Entities;

import jakarta.persistence.*;
import lombok.*;

import java.time.LocalDateTime;

@Entity
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class ContratVectorFile {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    private String fileName;

    private String typeContrat;

    private String source;

    private Integer pagesCount;

    private Integer chunksCount;

    private LocalDateTime uploadedAt;
}