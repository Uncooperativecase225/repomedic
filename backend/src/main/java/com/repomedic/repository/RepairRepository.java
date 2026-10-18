package com.repomedic.repository;
import com.repomedic.entity.Repair;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.List;
public interface RepairRepository extends JpaRepository<Repair, Long> { List<Repair> findByScanIdOrderByCreatedAtDesc(Long scanId); }
