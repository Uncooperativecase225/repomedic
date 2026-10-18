package com.repomedic.entity;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;
import lombok.NoArgsConstructor;
import java.time.LocalDateTime;

@Entity @Table(name="repairs") @Getter @Setter @NoArgsConstructor
public class Repair {
    @Id @GeneratedValue(strategy=GenerationType.IDENTITY) private Long id;
    @ManyToOne(fetch=FetchType.EAGER) @JoinColumn(name="scan_id", nullable=false) private ScanRecord scan;
    @ManyToOne(fetch=FetchType.EAGER) @JoinColumn(name="diagnostic_id") private Diagnostic diagnostic;
    @Column(nullable=false) private String status="PROPOSED";
    @Column(columnDefinition="TEXT") private String explanation;
    @Column(name="patch_json", columnDefinition="MEDIUMTEXT") private String patchJson;
    @Column(name="model_name") private String modelName;
    @Column(name="validation_summary", columnDefinition="TEXT") private String validationSummary;
    @Column(name="created_at", insertable=false, updatable=false) private LocalDateTime createdAt;
    @Column(name="updated_at", insertable=false, updatable=false) private LocalDateTime updatedAt;
}
