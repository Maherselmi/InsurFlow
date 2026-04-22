package tn.esprit.insureflow_back.Domain.Entities;

import lombok.*;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class ContratDocument {

    private String id;
    private String fileName;
    private String content;

    private String typeContrat;   // AUTO, HABITATION...
    private String pageNumber;

    private String source;        // PDF original
}