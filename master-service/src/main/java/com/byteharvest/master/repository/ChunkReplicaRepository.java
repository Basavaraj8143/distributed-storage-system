package com.byteharvest.master.repository;

import com.byteharvest.master.entity.ChunkReplicaEntity;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface ChunkReplicaRepository extends JpaRepository<ChunkReplicaEntity, Long> {
}
