package com.repomedic.repository;
import com.repomedic.entity.ScanRecord;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.List;
public interface ScanRecordRepository extends JpaRepository<ScanRecord, Long> { List<ScanRecord> findTop50ByOrderByCreatedAtDesc(); List<ScanRecord> findByRepositoryIdOrderByCreatedAtDesc(Long repositoryId); }
