package com.repomedic.entity;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;
import lombok.NoArgsConstructor;
import java.time.LocalDateTime;

@Entity @Table(name="scans") @Getter @Setter @NoArgsConstructor
public class ScanRecord {
    @Id @GeneratedValue(strategy=GenerationType.IDENTITY) private Long id;
    @ManyToOne(fetch=FetchType.EAGER) @JoinColumn(name="repository_id", nullable=false) private RepositoryRecord repository;
    @Column(nullable=false) private String status="QUEUED";
    @Column(columnDefinition="TEXT") private String summary;
    @Column(name="run_build", nullable=false) private boolean runBuild;
    @Column(name="run_lint", nullable=false) private boolean runLint;
    @Column(name="started_at") private LocalDateTime startedAt;
    @Column(name="completed_at") private LocalDateTime completedAt;
    @Column(name="created_at", insertable=false, updatable=false) private LocalDateTime createdAt;
}
