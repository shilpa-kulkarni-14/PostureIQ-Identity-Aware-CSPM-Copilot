package com.cspm.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.*;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestTemplate;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

@Service
@Slf4j
public class EmbeddingService {

    private static final String VOYAGE_API_URL = "https://api.voyageai.com/v1/embeddings";
    private static final String MODEL = "voyage-3";
    private static final int EMBEDDING_DIMENSIONS = 1024;
    private static final int MAX_BATCH_SIZE = 128;

    @Value("${voyage.api-key:}")
    private String voyageApiKey;

    private final RestTemplate restTemplate;
    private final ObjectMapper objectMapper;

    public EmbeddingService(ObjectMapper objectMapper) {
        this.restTemplate = new RestTemplate();
        this.objectMapper = objectMapper;
    }

    public boolean isAvailable() {
        return voyageApiKey != null && !voyageApiKey.isBlank();
    }

    /**
     * Embed a single text for document storage (ingestion).
     */
    public float[] embedDocument(String text) {
        return callVoyageApi(List.of(text), "document").get(0);
    }

    /**
     * Embed a single text for query/retrieval.
     * Uses asymmetric "query" input_type for better retrieval quality.
     */
    public float[] embedQuery(String text) {
        return callVoyageApi(List.of(text), "query").get(0);
    }

    /**
     * Embed a batch of texts for document storage (ingestion).
     * Automatically splits into batches of MAX_BATCH_SIZE.
     */
    public List<float[]> embedDocumentBatch(List<String> texts) {
        List<float[]> allEmbeddings = new ArrayList<>();
        for (int i = 0; i < texts.size(); i += MAX_BATCH_SIZE) {
            List<String> batch = texts.subList(i, Math.min(i + MAX_BATCH_SIZE, texts.size()));
            allEmbeddings.addAll(callVoyageApi(batch, "document"));
        }
        return allEmbeddings;
    }

    /**
     * Build a query string from a finding for retrieval.
     */
    public String buildFindingQuery(String severity, String riskType, String resourceId, String description) {
        return String.format("%s %s finding on resource %s. %s",
                severity != null ? severity : "UNKNOWN",
                riskType != null ? riskType : "security",
                resourceId != null ? resourceId : "unknown",
                description != null ? description : "");
    }

    public int getDimensions() {
        return EMBEDDING_DIMENSIONS;
    }

    private List<float[]> callVoyageApi(List<String> texts, String inputType) {
        if (!isAvailable()) {
            throw new IllegalStateException("Voyage API key not configured");
        }

        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        headers.setBearerAuth(voyageApiKey);

        Map<String, Object> body = Map.of(
                "input", texts,
                "model", MODEL,
                "input_type", inputType
        );

        try {
            HttpEntity<Map<String, Object>> entity = new HttpEntity<>(body, headers);
            ResponseEntity<String> response = restTemplate.exchange(
                    VOYAGE_API_URL,
                    HttpMethod.POST,
                    entity,
                    String.class
            );

            JsonNode root = objectMapper.readTree(response.getBody());
            JsonNode data = root.get("data");

            List<float[]> embeddings = new ArrayList<>();
            for (JsonNode item : data) {
                JsonNode embeddingNode = item.get("embedding");
                float[] embedding = new float[embeddingNode.size()];
                for (int i = 0; i < embeddingNode.size(); i++) {
                    embedding[i] = (float) embeddingNode.get(i).asDouble();
                }
                embeddings.add(embedding);
            }

            log.debug("Generated {} embeddings via Voyage AI (input_type={})", embeddings.size(), inputType);
            return embeddings;

        } catch (Exception e) {
            log.error("Voyage AI embedding call failed: {}", e.getMessage());
            throw new RuntimeException("Failed to generate embeddings via Voyage AI", e);
        }
    }
}
