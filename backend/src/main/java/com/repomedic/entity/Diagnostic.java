package com.repomedic.entity;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;
import lombok.NoArgsConstructor;
import java.time.LocalDateTime;

@Entity @Table(name="diagnostics") @Getter @Setter @NoArgsConstructor
public class Diagnostic {
    @Id @GeneratedValue(strategy=GenerationType.IDENTITY) private Long id;
    @ManyToOne(fetch=FetchType.EAGER) @JoinColumn(name="scan_id", nullable=false) private ScanRecord scan;
    @Column(name="error_type", nullable=false) private String errorType;
    @Column(nullable=false) private String severity="ERROR";
    @Column(name="file_path", length=1024) private String filePath;
    @Column(name="line_number") private Integer lineNumber;
    @Column(nullable=false, columnDefinition="TEXT") private String message;
    @Column(columnDefinition="TEXT") private String evidence;
    @Column(name="created_at", insertable=false, updatable=false) private LocalDateTime createdAt;
}
