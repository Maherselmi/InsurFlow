package tn.esprit.insureflow_back.Controller;

import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import tn.esprit.insureflow_back.DTO.ExpertFeedbackRequest;
import tn.esprit.insureflow_back.Domain.Entities.ExpertFeedback;
import tn.esprit.insureflow_back.Service.ExpertFeedbackService;

@RestController
@RequestMapping("/api/admin/expert-feedback")
@CrossOrigin(origins = "http://localhost:4200")
@RequiredArgsConstructor
public class ExpertFeedbackController {

    private final ExpertFeedbackService expertFeedbackService;

    @PostMapping
    public ResponseEntity<ExpertFeedback> saveFeedback(@RequestBody ExpertFeedbackRequest request) {
        return ResponseEntity.ok(expertFeedbackService.saveFeedback(request));
    }

    @GetMapping("/claim/{claimId}")
    public ResponseEntity<ExpertFeedback> getByClaimId(@PathVariable Long claimId) {
        return ResponseEntity.ok(expertFeedbackService.getByClaimId(claimId));
    }
}