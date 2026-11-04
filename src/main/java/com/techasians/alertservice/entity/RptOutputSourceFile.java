package com.techasians.alertservice.entity;

import javax.persistence.*;
import java.io.Serializable;
import java.time.LocalDateTime;

@Entity
@Table(name = "rpt_output_source_files")
public class RptOutputSourceFile implements Serializable {
    private static final long serialVersionUID = 1L;

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "output_file_id", nullable = false)
    private Long outputFileId;

    @Column(name = "source_file_id", nullable = false)
    private Long sourceFileId;

    @Column(name = "output_block_id")
    private Long outputBlockId;

    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;

    @PrePersist
    protected void prePersist() {
        if (createdAt == null) createdAt = LocalDateTime.now();
    }

    public Long getId() { return id; }
    public RptOutputSourceFile setId(Long value) { this.id = value; return this; }
    public Long getOutputFileId() { return outputFileId; }
    public RptOutputSourceFile setOutputFileId(Long value) { this.outputFileId = value; return this; }
    public Long getSourceFileId() { return sourceFileId; }
    public RptOutputSourceFile setSourceFileId(Long value) { this.sourceFileId = value; return this; }
    public Long getOutputBlockId() { return outputBlockId; }
    public RptOutputSourceFile setOutputBlockId(Long value) { this.outputBlockId = value; return this; }
    public LocalDateTime getCreatedAt() { return createdAt; }
    public RptOutputSourceFile setCreatedAt(LocalDateTime value) { this.createdAt = value; return this; }
}
