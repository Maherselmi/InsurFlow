package tn.esprit.insureflow_back.Service;

import dev.langchain4j.model.chat.ChatLanguageModel;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

@Slf4j
@Service
@RequiredArgsConstructor
public class LLMService {

    private final ChatLanguageModel chatLanguageModel;

    public String genererReponse(String promptText) {
        try {
            log.info("📤 Envoi prompt au LLM ({} chars)", promptText.length());

            String response = chatLanguageModel.generate(promptText);

            log.info("✅ Réponse LLM reçue ({} chars)", response.length());
            return response;

        } catch (Exception e) {
            log.error("❌ Erreur LLM: {}", e.getMessage());
            throw new RuntimeException("Erreur communication LLM: " + e.getMessage());
        }
    }
}