package com.repomedic.entity;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;
import lombok.NoArgsConstructor;
import java.time.LocalDateTime;

@Entity @Table(name="model_runs") @Getter @Setter @NoArgsConstructor
public class ModelRun {
    @Id @GeneratedValue(strategy=GenerationType.IDENTITY) private Long id;
    @ManyToOne(fetch=FetchType.EAGER) @JoinColumn(name="scan_id") private ScanRecord scan;
    @ManyToOne(fetch=FetchType.EAGER) @JoinColumn(name="repair_id") private Repair repair;
    @Column(name="model_name", nullable=false) private String modelName;
    @Column(nullable=false) private String operation;
    @Column(nullable=false) private String status;
    @Column(name="duration_ms") private Long durationMs;
    @Column(name="error_summary", columnDefinition="TEXT") private String errorSummary;
    @Column(name="created_at", insertable=false, updatable=false) private LocalDateTime createdAt;
}
