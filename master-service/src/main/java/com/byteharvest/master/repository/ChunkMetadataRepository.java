package com.byteharvest.master.repository;

import com.byteharvest.master.entity.ChunkMetadataEntity;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface ChunkMetadataRepository extends JpaRepository<ChunkMetadataEntity, Long> {
    List<ChunkMetadataEntity> findByFileMetadata_FileId(String fileId);
    ChunkMetadataEntity findByChunkId(String chunkId);
}
