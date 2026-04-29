package tn.esprit.insureflow_back.Service;

import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import tn.esprit.insureflow_back.Domain.ENUMS.AgentName;
import tn.esprit.insureflow_back.Domain.Entities.AgentLearningFeedback;
import tn.esprit.insureflow_back.Repository.AgentLearningFeedbackRepository;

import java.util.Comparator;
import java.util.List;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class AgentLearningMemoryService {

    private static final int MAX_EXAMPLES = 5;
    private static final int MAX_FIELD_CHARS = 900;

    private final AgentLearningFeedbackRepository repository;

    public String buildMemoryBlock(AgentName agentName, Long currentClaimId) {
        List<AgentLearningFeedback> examples = repository.findLearningExamples(
                agentName,
                currentClaimId,
                PageRequest.of(0, 12)
        );

        return examples.stream()
                .filter(this::isUsable)
                .sorted(Comparator
                        .comparing((AgentLearningFeedback f) -> Boolean.TRUE.equals(f.getWasCorrect()))
                        .thenComparing(AgentLearningFeedback::getUpdatedAt, Comparator.nullsLast(Comparator.reverseOrder())))
                .limit(MAX_EXAMPLES)
                .map(this::formatExample)
                .collect(Collectors.joining("\n\n"));
    }

    private boolean isUsable(AgentLearningFeedback feedback) {
        return feedback != null
                && Boolean.TRUE.equals(feedback.getUseForLearning())
                && hasText(feedback.getFinalValidatedOutput());
    }

    private String formatExample(AgentLearningFeedback feedback) {
        boolean correct = Boolean.TRUE.equals(feedback.getWasCorrect());
        String label = correct ? "EXEMPLE VALIDE PAR EXPERT" : "CORRECTION EXPERT A APPRENDRE";

        return """
                %s
                Resultat agent correct : %s
                Satisfaction expert : %s/5
                Commentaire expert : %s

                Entree dossier :
                %s

                Sortie agent initiale :
                %s

                Sortie finale validee par expert :
                %s
                """.formatted(
                label,
                correct ? "OUI" : "NON",
                feedback.getSatisfactionScore() == null ? "N/A" : feedback.getSatisfactionScore().toString(),
                truncate(feedback.getExpertComment(), 250),
                truncate(feedback.getInputData(), MAX_FIELD_CHARS),
                truncate(feedback.getAgentOutput(), MAX_FIELD_CHARS),
                truncate(feedback.getFinalValidatedOutput(), MAX_FIELD_CHARS)
        ).trim();
    }

    private boolean hasText(String value) {
        return value != null && !value.trim().isEmpty();
    }

    private String truncate(String value, int max) {
        String safe = value == null ? "" : value.trim();
        if (safe.length() <= max) return safe;
        return safe.substring(0, max) + "...";
    }
}
