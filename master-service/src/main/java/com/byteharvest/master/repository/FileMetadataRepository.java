package com.byteharvest.master.repository;

import com.byteharvest.master.entity.FileMetadataEntity;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface FileMetadataRepository extends JpaRepository<FileMetadataEntity, String> {
}
