package tn.esprit.insureflow_back.Config;

import dev.langchain4j.model.chat.ChatLanguageModel;
import dev.langchain4j.model.embedding.EmbeddingModel;
import dev.langchain4j.model.ollama.OllamaChatModel;
import dev.langchain4j.model.ollama.OllamaEmbeddingModel;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.time.Duration;

@Configuration
public class OllamaConfig {

    @Value("${ollama.base-url}")
    private String ollamaBaseUrl;

    @Value("${ollama.chat-model}")
    private String chatModelName;

    @Value("${ollama.embedding-model}")
    private String embeddingModelName;

    @Value("${ollama.vision-model}")
    private String visionModelName;

    @Bean(name = "chatLanguageModel")
    public ChatLanguageModel chatLanguageModel() {
        return OllamaChatModel.builder()
                .baseUrl(ollamaBaseUrl)
                .modelName(chatModelName)
                .temperature(0.0)
                .timeout(Duration.ofSeconds(300))
                .build();
    }

    @Bean(name = "embeddingModel")
    public EmbeddingModel embeddingModel() {
        return OllamaEmbeddingModel.builder()
                .baseUrl(ollamaBaseUrl)
                .modelName(embeddingModelName)
                .build();
    }

    @Bean(name = "visionLanguageModel")
    public ChatLanguageModel visionLanguageModel() {
        return OllamaChatModel.builder()
                .baseUrl(ollamaBaseUrl)
                .modelName(visionModelName)
                .temperature(0.1)
                .timeout(Duration.ofMinutes(10))
                .build();
    }
}