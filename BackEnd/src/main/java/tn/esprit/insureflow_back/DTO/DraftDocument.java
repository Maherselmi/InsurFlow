package tn.esprit.insureflow_back.DTO;

import lombok.AllArgsConstructor;
import lombok.Data;

@Data
@AllArgsConstructor
public class DraftDocument {

    private String fileName;

    private String contentType;

    private byte[] content;
}