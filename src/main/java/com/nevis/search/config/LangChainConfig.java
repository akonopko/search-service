package com.nevis.search.config;

import dev.langchain4j.model.chat.ChatModel;
import dev.langchain4j.model.embedding.EmbeddingModel;
import dev.langchain4j.model.googleai.GoogleAiEmbeddingModel;
import dev.langchain4j.model.googleai.GoogleAiGeminiChatModel;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.time.Duration;

@Configuration
public class LangChainConfig {

    @Value("${app.gemini.api-key}")
    private String apiKey;

    @Bean
    public ChatModel chatLanguageModel() {
        return GoogleAiGeminiChatModel.builder()
            .apiKey(apiKey)
            .modelName("gemini-3-flash-preview")
            .timeout(Duration.ofSeconds(60))
            .maxRetries(5)
            .logRequests(true)
            .logResponses(true)
            .build();
    }

    @Bean
    public EmbeddingModel embeddingModel() {
        return GoogleAiEmbeddingModel.builder()
            .apiKey(apiKey)
            .modelName("gemini-embedding-001")
            .outputDimensionality(768)
            .maxRetries(5)
            .build();
    }
}