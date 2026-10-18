package com.repomedic.repository;
import com.repomedic.entity.Diagnostic;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.List;
public interface DiagnosticRepository extends JpaRepository<Diagnostic, Long> { List<Diagnostic> findByScanIdOrderByIdAsc(Long scanId); }
