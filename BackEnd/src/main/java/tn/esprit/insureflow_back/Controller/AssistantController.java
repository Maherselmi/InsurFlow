package tn.esprit.insureflow_back.Controller;

import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import tn.esprit.insureflow_back.DTO.AssistantRequest;
import tn.esprit.insureflow_back.DTO.AssistantResponse;
import tn.esprit.insureflow_back.Service.AssistantService;

@RestController
@RequestMapping("/api/assistant")
@RequiredArgsConstructor
@CrossOrigin(origins = "http://localhost:4200")
public class AssistantController {

    private final AssistantService assistantService;

    @PostMapping("/chat")
    public ResponseEntity<AssistantResponse> chat(@RequestBody AssistantRequest request) {
        return ResponseEntity.ok(assistantService.ask(request));
    }
}