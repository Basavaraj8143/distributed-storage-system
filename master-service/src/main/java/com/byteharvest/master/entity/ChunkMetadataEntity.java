package com.byteharvest.master.entity;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;
import java.util.ArrayList;
import java.util.List;

@Entity
@Table(name = "chunk_metadata")
@Getter
@Setter
public class ChunkMetadataEntity {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "chunk_id", nullable = false, unique = true)
    private String chunkId;

    @Column(name = "chunk_index", nullable = false)
    private int chunkIndex;

    @Column(name = "checksum")
    private String checksum;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "file_id")
    private FileMetadataEntity fileMetadata;

    @OneToMany(mappedBy = "chunkMetadata", cascade = CascadeType.ALL, orphanRemoval = true, fetch = FetchType.EAGER)
    private List<ChunkReplicaEntity> replicas = new ArrayList<>();
}
