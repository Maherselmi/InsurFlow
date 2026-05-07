package tn.esprit.insureflow_back.DTO;

import lombok.Data;

@Data
public class AssistantRequest {
    private String message;
    private Long clientId;
    private String conversationId;

}