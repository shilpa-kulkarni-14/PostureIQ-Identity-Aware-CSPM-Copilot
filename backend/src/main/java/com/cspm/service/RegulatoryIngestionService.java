package com.cspm.service;

import com.cspm.model.RegulatoryChunk;
import com.cspm.repository.RegulatoryChunkRepository;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.annotation.PostConstruct;
import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.core.io.ClassPathResource;
import org.springframework.core.io.Resource;
import org.springframework.core.io.support.PathMatchingResourcePatternResolver;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.io.InputStream;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Map;

@Service
@RequiredArgsConstructor
@Slf4j
public class RegulatoryIngestionService {

    private final RegulatoryChunkRepository regulatoryChunkRepository;
    private final EmbeddingService embeddingService;
    private final ObjectMapper objectMapper;

    @PersistenceContext
    private EntityManager entityManager;

    private static final String[] REGULATORY_FILES = {
            "regulatory/pci-dss-controls.json",
            "regulatory/hipaa-controls.json",
            "regulatory/ffiec-controls.json",
            "regulatory/nydfs-500-controls.json",
            "regulatory/sox-controls.json",
            "regulatory/cis-aws-controls.json"
    };

    @PostConstruct
    public void init() {
        if (!embeddingService.isAvailable()) {
            log.info("Voyage API key not configured — skipping regulatory chunk ingestion");
            return;
        }

        long count = regulatoryChunkRepository.count();
        if (count > 0) {
            log.info("Regulatory chunks already loaded: {} chunks present", count);
            return;
        }

        try {
            ingestAll();
        } catch (Exception e) {
            log.warn("Failed to ingest regulatory chunks on startup: {}. RAG will be unavailable.", e.getMessage());
        }
    }

    @Transactional
    public void ingestAll() {
        log.info("Starting regulatory chunk ingestion...");

        // Ensure pgvector extension exists (safe for repeated calls)
        try {
            entityManager.createNativeQuery("CREATE EXTENSION IF NOT EXISTS vector").executeUpdate();
        } catch (Exception e) {
            log.warn("Could not create vector extension (may already exist or not available): {}", e.getMessage());
        }

        List<RegulatoryChunk> allChunks = new ArrayList<>();
        List<String> allTexts = new ArrayList<>();

        for (String file : REGULATORY_FILES) {
            try {
                Resource resource = new ClassPathResource(file);
                if (!resource.exists()) {
                    log.warn("Regulatory file not found: {}", file);
                    continue;
                }

                try (InputStream is = resource.getInputStream()) {
                    List<Map<String, Object>> controls = objectMapper.readValue(
                            is, new TypeReference<List<Map<String, Object>>>() {});

                    for (Map<String, Object> control : controls) {
                        String framework = (String) control.get("framework");
                        String controlId = (String) control.get("controlId");
                        String controlTitle = (String) control.get("controlTitle");
                        String text = (String) control.get("text");
                        Object metadata = control.get("metadata");

                        // Build chunk text for embedding: includes framework context
                        String chunkText = String.format("[%s] Control %s: %s\n%s",
                                framework, controlId, controlTitle, text);

                        RegulatoryChunk chunk = RegulatoryChunk.builder()
                                .framework(framework)
                                .controlId(controlId)
                                .controlTitle(controlTitle)
                                .chunkText(chunkText)
                                .metadataJson(metadata != null ? objectMapper.writeValueAsString(metadata) : null)
                                .build();

                        allChunks.add(chunk);
                        allTexts.add(chunkText);
                    }

                    log.info("Loaded {} controls from {}", controls.size(), file);
                }
            } catch (Exception e) {
                log.error("Error reading regulatory file {}: {}", file, e.getMessage());
            }
        }

        if (allChunks.isEmpty()) {
            log.warn("No regulatory chunks loaded from any file");
            return;
        }

        // Generate embeddings in batch
        log.info("Generating embeddings for {} regulatory chunks...", allChunks.size());
        List<float[]> embeddings = embeddingService.embedDocumentBatch(allTexts);

        // Persist chunks with embeddings via native SQL
        log.info("Persisting {} chunks with embeddings to database...", allChunks.size());
        for (int i = 0; i < allChunks.size(); i++) {
            RegulatoryChunk chunk = allChunks.get(i);
            float[] embedding = embeddings.get(i);

            // Save the entity first (without embedding)
            regulatoryChunkRepository.save(chunk);

            // Add the embedding column via native query
            String embeddingStr = floatArrayToVectorString(embedding);
            entityManager.createNativeQuery(
                    "UPDATE regulatory_chunks SET embedding = cast(:embedding as vector) WHERE id = :id")
                    .setParameter("embedding", embeddingStr)
                    .setParameter("id", chunk.getId())
                    .executeUpdate();
        }

        // Create IVFFlat index for similarity search
        try {
            entityManager.createNativeQuery(
                    "CREATE INDEX IF NOT EXISTS idx_regulatory_chunks_embedding " +
                    "ON regulatory_chunks USING ivfflat (embedding vector_cosine_ops) WITH (lists = 20)")
                    .executeUpdate();
            log.info("Created IVFFlat index on regulatory_chunks.embedding");
        } catch (Exception e) {
            log.warn("Could not create IVFFlat index (may need more rows or already exists): {}", e.getMessage());
        }

        log.info("Regulatory chunk ingestion complete: {} chunks ingested", allChunks.size());
    }

    /**
     * Convert float array to pgvector string format: [0.1,0.2,0.3,...]
     */
    private String floatArrayToVectorString(float[] embedding) {
        StringBuilder sb = new StringBuilder("[");
        for (int i = 0; i < embedding.length; i++) {
            if (i > 0) sb.append(",");
            sb.append(embedding[i]);
        }
        sb.append("]");
        return sb.toString();
    }
}
