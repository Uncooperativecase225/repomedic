package com.repomedic.repository;
import com.repomedic.entity.RepositoryRecord;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.Optional;
public interface RepositoryRecordRepository extends JpaRepository<RepositoryRecord, Long> { Optional<RepositoryRecord> findByLocalPath(String localPath); }
