package com.byteharvest.master.entity;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;
import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;

@Entity
// A file cannot have two chunks at the same position. Declared as a unique index (not uniqueConstraints)
// because SQLite cannot add a constraint to an existing table, which is what ddl-auto=update would try.
@Table(name = "chunk_metadata", indexes = {
        @Index(name = "ux_chunk_file_index", columnList = "file_id, chunk_index", unique = true)
})
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

    /** The distinct nodes that hold a copy of this chunk. Behaviour that belongs to the chunk lives on the chunk. */
    public List<String> replicaUrls() {
        return replicas.stream()
                .map(ChunkReplicaEntity::getNodeUrl)
                .distinct()
                .collect(Collectors.toList());
    }
}
