package com.cspm.service;

import com.cspm.model.Finding;
import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;
import jakarta.persistence.Query;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.Collections;
import java.util.List;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
@Slf4j
public class RegulatoryRetrievalService {

    private final EmbeddingService embeddingService;

    @PersistenceContext
    private EntityManager entityManager;

    private static final int TOP_K = 5;
    private static final double SIMILARITY_THRESHOLD = 0.3;

    /**
     * Retrieve the most relevant regulatory chunks for a given finding.
     * Returns empty list if embedding service is unavailable or no chunks match.
     */
    public List<RegulatoryChunkResult> retrieveRelevantControls(Finding finding) {
        if (!embeddingService.isAvailable()) {
            log.debug("Embedding service not available — skipping regulatory retrieval");
            return Collections.emptyList();
        }

        try {
            // Build query text from finding
            String queryText = embeddingService.buildFindingQuery(
                    finding.getSeverity(),
                    finding.getTitle(),
                    finding.getResourceId(),
                    finding.getDescription()
            );

            // Generate query embedding
            float[] queryEmbedding = embeddingService.embedQuery(queryText);
            String embeddingStr = floatArrayToVectorString(queryEmbedding);

            // Execute pgvector similarity search
            String sql = """
                    SELECT id, framework, control_id, control_title, chunk_text,
                           1 - (embedding <=> cast(:embedding as vector)) as similarity
                    FROM regulatory_chunks
                    WHERE embedding IS NOT NULL
                      AND 1 - (embedding <=> cast(:embedding as vector)) > :threshold
                    ORDER BY embedding <=> cast(:embedding as vector)
                    LIMIT :topK
                    """;

            Query query = entityManager.createNativeQuery(sql);
            query.setParameter("embedding", embeddingStr);
            query.setParameter("threshold", SIMILARITY_THRESHOLD);
            query.setParameter("topK", TOP_K);

            @SuppressWarnings("unchecked")
            List<Object[]> results = query.getResultList();

            List<RegulatoryChunkResult> chunkResults = results.stream()
                    .map(row -> new RegulatoryChunkResult(
                            ((Number) row[0]).longValue(),
                            (String) row[1],
                            (String) row[2],
                            (String) row[3],
                            (String) row[4],
                            ((Number) row[5]).doubleValue()
                    ))
                    .collect(Collectors.toList());

            log.debug("Retrieved {} regulatory chunks for finding '{}' (top similarity: {})",
                    chunkResults.size(),
                    finding.getTitle(),
                    chunkResults.isEmpty() ? "N/A" : String.format("%.3f", chunkResults.get(0).similarity()));

            return chunkResults;

        } catch (Exception e) {
            log.warn("Regulatory retrieval failed for finding '{}': {}. Continuing without regulatory context.",
                    finding.getTitle(), e.getMessage());
            return Collections.emptyList();
        }
    }

    /**
     * Build a formatted regulatory context string for injection into Claude prompts.
     */
    public String buildRegulatoryContextPrompt(List<RegulatoryChunkResult> chunks) {
        if (chunks.isEmpty()) {
            return "";
        }

        StringBuilder sb = new StringBuilder();
        sb.append("\n\n=== REGULATORY COMPLIANCE CONTEXT ===\n");
        sb.append("The following regulatory controls may be relevant to this finding. ");
        sb.append("Use these to identify specific compliance violations.\n\n");

        for (int i = 0; i < chunks.size(); i++) {
            RegulatoryChunkResult chunk = chunks.get(i);
            sb.append(String.format("[REG-%d] %s | %s: %s\n%s\n\n",
                    i + 1,
                    chunk.framework(),
                    chunk.controlId(),
                    chunk.controlTitle(),
                    chunk.chunkText()
            ));
        }

        return sb.toString();
    }

    private String floatArrayToVectorString(float[] embedding) {
        StringBuilder sb = new StringBuilder("[");
        for (int i = 0; i < embedding.length; i++) {
            if (i > 0) sb.append(",");
            sb.append(embedding[i]);
        }
        sb.append("]");
        return sb.toString();
    }

    /**
     * Result DTO for regulatory chunk retrieval.
     */
    public record RegulatoryChunkResult(
            Long id,
            String framework,
            String controlId,
            String controlTitle,
            String chunkText,
            double similarity
    ) {}
}
