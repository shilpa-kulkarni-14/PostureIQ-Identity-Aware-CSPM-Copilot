package com.cspm.model;

import jakarta.persistence.*;
import lombok.*;

import java.time.LocalDateTime;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Entity
@Table(name = "regulatory_chunks")
public class RegulatoryChunk {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, length = 50)
    private String framework;

    @Column(name = "control_id", nullable = false, length = 100)
    private String controlId;

    @Column(name = "control_title", length = 500)
    private String controlTitle;

    @Column(name = "chunk_text", columnDefinition = "TEXT", nullable = false)
    private String chunkText;

    @Column(name = "metadata_json", columnDefinition = "TEXT")
    private String metadataJson;

    // Note: embedding column (vector type) is handled via native SQL queries,
    // not mapped here because Hibernate doesn't natively support pgvector types.

    @Builder.Default
    @Column(name = "created_at")
    private LocalDateTime createdAt = LocalDateTime.now();
}
