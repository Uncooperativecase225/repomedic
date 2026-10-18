package com.repomedic.entity;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;
import lombok.NoArgsConstructor;
import java.time.LocalDateTime;

@Entity @Table(name="validation_results") @Getter @Setter @NoArgsConstructor
public class ValidationResult {
    @Id @GeneratedValue(strategy=GenerationType.IDENTITY) private Long id;
    @ManyToOne(fetch=FetchType.EAGER) @JoinColumn(name="repair_id", nullable=false) private Repair repair;
    @Column(name="command_name", nullable=false) private String commandName;
    @Column(nullable=false) private String status;
    @Column(name="exit_code") private Integer exitCode;
    @Column(name="output_summary", columnDefinition="MEDIUMTEXT") private String outputSummary;
    @Column(name="executed_at", insertable=false, updatable=false) private LocalDateTime executedAt;
}
