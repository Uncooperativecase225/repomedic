package com.repomedic.entity;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;
import lombok.NoArgsConstructor;
import java.time.LocalDateTime;

@Entity @Table(name="repositories") @Getter @Setter @NoArgsConstructor
public class RepositoryRecord {
    @Id @GeneratedValue(strategy=GenerationType.IDENTITY) private Long id;
    @Column(nullable=false) private String name;
    @Column(name="local_path", nullable=false, length=1024, unique=true) private String localPath;
    @Column(name="project_type", nullable=false) private String projectType="UNKNOWN";
    @Column(name="created_at", insertable=false, updatable=false) private LocalDateTime createdAt;
}
