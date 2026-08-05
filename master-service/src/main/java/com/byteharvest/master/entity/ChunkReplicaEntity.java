package com.byteharvest.master.entity;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;

@Entity
@Table(name = "chunk_replica")
@Getter
@Setter
public class ChunkReplicaEntity {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "node_url", nullable = false)
    private String nodeUrl;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "chunk_metadata_id")
    private ChunkMetadataEntity chunkMetadata;
}
