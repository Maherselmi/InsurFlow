package tn.esprit.insureflow_back.DTO;

import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
public class AssistantResponse {

    private String answer;

    private boolean claimDeclarationMode;

    private boolean needsFileUpload;

    private boolean declarationCompleted;

    private Long claimId;

    private String status;

    public AssistantResponse(String answer) {
        this.answer = answer;
    }

    public AssistantResponse(
            String answer,
            boolean claimDeclarationMode,
            boolean needsFileUpload
    ) {
        this.answer = answer;
        this.claimDeclarationMode = claimDeclarationMode;
        this.needsFileUpload = needsFileUpload;
    }
}