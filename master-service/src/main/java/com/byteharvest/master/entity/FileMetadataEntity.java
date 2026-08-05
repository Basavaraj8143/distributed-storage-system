package com.byteharvest.master.entity;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;
import java.util.ArrayList;
import java.util.List;

@Entity
@Table(name = "file_metadata")
@Getter
@Setter
public class FileMetadataEntity {
    @Id
    @Column(name = "file_id", nullable = false, unique = true)
    private String fileId;

    @Column(name = "original_filename")
    private String originalFilename;

    @OneToMany(mappedBy = "fileMetadata", cascade = CascadeType.ALL, orphanRemoval = true, fetch = FetchType.EAGER)
    private List<ChunkMetadataEntity> chunks = new ArrayList<>();
}
