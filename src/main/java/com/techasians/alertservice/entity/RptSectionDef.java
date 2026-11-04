package com.techasians.alertservice.entity;

import com.techasians.alertservice.enums.RptExtractMode;

import javax.persistence.*;
import java.io.Serializable;
import java.time.LocalDateTime;

/** Định nghĩa cách trích xuất một section trực tiếp từ file Word nguồn. */
@Entity
@Table(name = "rpt_section_defs")
public class RptSectionDef implements Serializable {
    private static final long serialVersionUID = 1L;

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "source_definition_id", nullable = false)
    private Long sourceDefinitionId;

    @Column(name = "section_name", nullable = false, length = 255)
    private String sectionName;

    @Column(name = "extract_mode", nullable = false)
    @Enumerated(EnumType.ORDINAL)
    private RptExtractMode extractMode = RptExtractMode.TEXT_RANGE;

    @Column(name = "start_marker", length = 1000)
    private String startMarker;

    @Column(name = "end_marker", length = 1000)
    private String endMarker;

    @Column(name = "item_count")
    private Integer itemCount;

    @Column(name = "include_start", nullable = false)
    private Integer includeStart = 1;

    @Column(name = "include_end", nullable = false)
    private Integer includeEnd = 0;

    @Column(name = "status", nullable = false)
    private Integer status = 1;

    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;

    @Column(name = "created_by", nullable = false, updatable = false, length = 100)
    private String createdBy = "";

    @PrePersist
    protected void prePersist() {
        if (createdAt == null) createdAt = LocalDateTime.now();
        if (createdBy == null) createdBy = "";
    }

    public Long getId() { return id; }
    public RptSectionDef setId(Long value) { this.id = value; return this; }
    public Long getSourceDefinitionId() { return sourceDefinitionId; }
    public RptSectionDef setSourceDefinitionId(Long value) { this.sourceDefinitionId = value; return this; }
    public String getSectionName() { return sectionName; }
    public RptSectionDef setSectionName(String value) { this.sectionName = value; return this; }
    public RptExtractMode getExtractMode() { return extractMode; }
    public RptSectionDef setExtractMode(RptExtractMode value) { this.extractMode = value; return this; }
    public String getStartMarker() { return startMarker; }
    public RptSectionDef setStartMarker(String value) { this.startMarker = value; return this; }
    public String getEndMarker() { return endMarker; }
    public RptSectionDef setEndMarker(String value) { this.endMarker = value; return this; }
    public Integer getItemCount() { return itemCount; }
    public RptSectionDef setItemCount(Integer value) { this.itemCount = value; return this; }
    public Integer getIncludeStart() { return includeStart; }
    public RptSectionDef setIncludeStart(Integer value) { this.includeStart = value; return this; }
    public Integer getIncludeEnd() { return includeEnd; }
    public RptSectionDef setIncludeEnd(Integer value) { this.includeEnd = value; return this; }
    public Integer getStatus() { return status; }
    public RptSectionDef setStatus(Integer value) { this.status = value; return this; }
    public LocalDateTime getCreatedAt() { return createdAt; }
    public RptSectionDef setCreatedAt(LocalDateTime value) { this.createdAt = value; return this; }
    public String getCreatedBy() { return createdBy; }
    public RptSectionDef setCreatedBy(String value) { this.createdBy = value; return this; }
    public boolean isIncludeStart() { return Integer.valueOf(1).equals(includeStart); }
    public boolean isIncludeEnd() { return Integer.valueOf(1).equals(includeEnd); }
}
