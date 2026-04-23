package tn.esprit.insureflow_back.Service;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import tn.esprit.insureflow_back.Domain.ENUMS.AgentName;
import tn.esprit.insureflow_back.Domain.Entities.AgentLearningFeedback;
import tn.esprit.insureflow_back.Repository.AgentLearningFeedbackRepository;

import java.util.List;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class AgentLearningMemoryService {

    private final AgentLearningFeedbackRepository repository;

    public String buildMemoryBlock(AgentName agentName, Long currentClaimId) {
        List<AgentLearningFeedback> examples = repository
                .findTop5ByAgentNameAndUseForLearningTrueAndWasCorrectTrueAndClaim_IdNotOrderByUpdatedAtDesc(
                        agentName,
                        currentClaimId
                );

        if (examples.isEmpty()) {
            return "";
        }

        return examples.stream()
                .map(this::formatExample)
                .collect(Collectors.joining("\n\n"));
    }

    private String formatExample(AgentLearningFeedback feedback) {
        return """
                Exemple expert validé
                Entrée dossier :
                %s

                Sortie agent initiale :
                %s

                Sortie finale validée :
                %s
                """.formatted(
                safe(feedback.getInputData()),
                safe(feedback.getAgentOutput()),
                safe(feedback.getFinalValidatedOutput())
        );
    }

    private String safe(String value) {
        return value == null ? "" : value.trim();
    }
}