package tn.esprit.insureflow_back.DTO;

import lombok.Data;
import tn.esprit.insureflow_back.Domain.ENUMS.ClaimConversationStep;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;

@Data
public class ClaimConversationDraft {

    private Long clientId;

    private String claimType;

    private Long policyId;

    private String policyNumber;

    private String policyType;

    private LocalDate incidentDate;

    private String description;

    private ClaimConversationStep step = ClaimConversationStep.NONE;

    private List<DraftDocument> documents = new ArrayList<>();
}