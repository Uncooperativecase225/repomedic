package com.repomedic.repository;
import com.repomedic.entity.ValidationResult;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.List;
public interface ValidationResultRepository extends JpaRepository<ValidationResult, Long> { List<ValidationResult> findByRepairIdOrderByExecutedAtDesc(Long repairId); }
