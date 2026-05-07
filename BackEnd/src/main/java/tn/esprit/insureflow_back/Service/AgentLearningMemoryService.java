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

    /** Nombre max de corrections experts à inclure dans le bloc mémoire. */
    private static final int MAX_CORRECTIONS = 3;

    /** Nombre max d'exemples validés (wasCorrect=true) à inclure. */
    private static final int MAX_VALIDATED = 2;

    private final AgentLearningFeedbackRepository repository;

    /**
     * Construit le bloc mémoire injecté dans le prompt de l'agent IA.
     *
     * CORRECTION PRINCIPALE :
     * - Avant : le tri était ascending sur wasCorrect → les exemples INCORRECTS
     *   (wasCorrect=false) apparaissaient EN PREMIER, ce qui noyait les corrections.
     * - Maintenant : les CORRECTIONS (wasCorrect=false) sont séparées des VALIDATIONS
     *   (wasCorrect=true) et les corrections ont la priorité la plus haute car
     *   elles représentent des ajustements explicites de l'expert.
     *
     * RÉSULTAT : quand le même dossier est resoumis, l'agent voit en priorité
     * "l'expert a corrigé min=100 moy=200 max=300" et adapte son estimation.
     */
    public String buildMemoryBlock(AgentName agentName, Long currentClaimId) {
        List<AgentLearningFeedback> examples = repository.findLearningExamples(
                agentName,
                currentClaimId,
                PageRequest.of(0, 20) // On récupère plus pour avoir assez après filtre
        );

        // Séparer corrections (wasCorrect=false) et validations (wasCorrect=true)
        List<AgentLearningFeedback> corrections = examples.stream()
                .filter(this::isUsable)
                .filter(f -> Boolean.FALSE.equals(f.getWasCorrect()))
                .sorted(Comparator.comparing(AgentLearningFeedback::getUpdatedAt,
                        Comparator.nullsLast(Comparator.reverseOrder())))
                .limit(MAX_CORRECTIONS)
                .collect(Collectors.toList());

        List<AgentLearningFeedback> validated = examples.stream()
                .filter(this::isUsable)
                .filter(f -> Boolean.TRUE.equals(f.getWasCorrect()))
                .sorted(Comparator.comparing(AgentLearningFeedback::getUpdatedAt,
                        Comparator.nullsLast(Comparator.reverseOrder())))
                .limit(MAX_VALIDATED)
                .collect(Collectors.toList());

        // Les corrections passent EN PREMIER pour forcer l'agent à les prendre en compte
        StringBuilder block = new StringBuilder();

        if (!corrections.isEmpty()) {
            block.append("=== CORRECTIONS EXPERTES A APPLIQUER ===\n");
            block.append("IMPORTANT : Ces cas ont été corrigés par l'expert. ")
                    .append("Adapte ton analyse en conséquence.\n\n");
            corrections.stream()
                    .map(this::formatExample)
                    .forEach(s -> block.append(s).append("\n\n"));
        }

        if (!validated.isEmpty()) {
            block.append("=== EXEMPLES VALIDES PAR EXPERT ===\n\n");
            validated.stream()
                    .map(this::formatExample)
                    .forEach(s -> block.append(s).append("\n\n"));
        }

        return block.toString().trim();
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

                Sortie agent initiale (avec justification IA) :
                %s

                Sortie finale validee par expert :
                %s
                """.formatted(
                label,
                correct ? "OUI" : "NON",
                feedback.getSatisfactionScore() == null ? "N/A" : feedback.getSatisfactionScore().toString(),
                truncate(feedback.getExpertComment(), 250),
                truncate(feedback.getInputData(), MAX_FIELD_CHARS),
                truncate(feedback.getAgentOutput(), MAX_FIELD_CHARS),   // contient maintenant la justification IA
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