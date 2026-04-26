package tn.esprit.insureflow_back.Config;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.stereotype.Component;
import tn.esprit.insureflow_back.Service.LLMService;

/**
 * OllamaWarmup
 *
 * Force le chargement du modèle LLM en mémoire au démarrage de l'application.
 * Sans ce warmup, le premier appel LLM supporte un surcoût de 30 à 60 secondes
 * de chargement du modèle (qwen2.5:7b).
 *
 * OPTIMISATION 6 — exécuté une seule fois au boot, transparent pour les requêtes suivantes.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class OllamaWarmup implements ApplicationRunner {

    private final LLMService llmService;

    @Override
    public void run(ApplicationArguments args) {
        log.info("OllamaWarmup - pré-chargement du modèle LLM en mémoire...");
        try {
            // Appel minimal pour forcer le chargement du modèle
            llmService.genererReponse("ping");
            log.info("OllamaWarmup - modèle LLM chargé avec succès.");
        } catch (Exception e) {
            // Non bloquant : si Ollama n'est pas disponible au boot, on continue normalement
            log.warn("OllamaWarmup - impossible de pré-charger le modèle LLM : {}", e.getMessage());
        }
    }
}