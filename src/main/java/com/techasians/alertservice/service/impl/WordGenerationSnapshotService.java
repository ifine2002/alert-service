package com.techasians.alertservice.service.impl;

import com.techasians.alertservice.entity.*;
import com.techasians.alertservice.enums.*;
import com.techasians.alertservice.repository.*;
import com.techasians.alertservice.service.WordGenerationSnapshot;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import java.time.LocalDate;
import java.util.*;

@Service
public class WordGenerationSnapshotService {
    private final RptDefinitionRepository definitions;
    private final RptOutputBlockDefRepository blocks;
    private final RptOutputSourceMapRepository mappings;
    private final RptSectionDefRepository sections;
    private final RptInstanceRepository instances;
    private final RptFileRepository files;

    public WordGenerationSnapshotService(RptDefinitionRepository definitions, RptOutputBlockDefRepository blocks,
            RptOutputSourceMapRepository mappings, RptSectionDefRepository sections,
            RptInstanceRepository instances, RptFileRepository files) {
        this.definitions = definitions;
        this.blocks = blocks;
        this.mappings = mappings;
        this.sections = sections;
        this.instances = instances;
        this.files = files;
    }

    @Transactional(readOnly = true)
    public WordGenerationSnapshot load(Long definitionId, LocalDate weekStartDate) {
        RptDefinition output = definitions.findById(definitionId)
                .orElseThrow(() -> new IllegalArgumentException("Không tìm thấy definition Word"));
        if (output.getFileKind() != RptFileKind.WORD || !output.isActive()
                || output.getDirection() != RptDirection.OUTPUT || output.getSourceMode() != RptSourceMode.GENERATE
                || output.getTemplateRelativePath() == null || output.getTemplateRelativePath().isEmpty()) {
            throw new IllegalArgumentException("Definition Word không hợp lệ hoặc thiếu template");
        }
        List<WordGenerationSnapshot.Block> result = new ArrayList<>();
        Map<String, RptFile> fileBySourceWeek = new HashMap<>();
        for (RptOutputBlockDef block : blocks.findByOutputDefinitionIdAndStatusOrderBySortOrder(definitionId, 1)) {
            List<WordGenerationSnapshot.Source> sources = new ArrayList<>();
            for (RptOutputSourceMap mapping : mappings.findByOutputBlockIdAndStatusOrderBySortOrder(block.getId(), 1)) {
                RptSectionDef section = null;
                if (mapping.getSourceSectionDefId() != null) {
                    section = sections.findById(mapping.getSourceSectionDefId())
                            .orElseThrow(() -> new IllegalArgumentException("Section cấu hình không tồn tại"));
                    if (!mapping.getSourceDefinitionId().equals(section.getSourceDefinitionId())
                            || !Integer.valueOf(1).equals(section.getStatus())) {
                        throw new IllegalArgumentException("Section không thuộc nguồn hoặc không hoạt động");
                    }
                }
                int numberOfWeeks = mapping.getSourceRole() == RptSourceRole.CHART ? 8 : 1;
                for (int offset = numberOfWeeks - 1; offset >= 0; offset--) {
                    LocalDate sourceWeek = weekStartDate.minusWeeks(offset);
                    String key = mapping.getSourceDefinitionId() + ":" + sourceWeek;
                    if (!fileBySourceWeek.containsKey(key)) {
                        RptFile file = null;
                        Optional<RptInstance> instance = instances.findByDefinitionIdAndWeekStartDate(
                                mapping.getSourceDefinitionId(), sourceWeek);
                        if (instance.isPresent() && instance.get().getStatus() == RptInstanceStatus.COMPLETED
                                && instance.get().getCurrentFileId() != null) {
                            file = files.findById(instance.get().getCurrentFileId())
                                    .orElseThrow(() -> new IllegalStateException("Metadata file nguồn không tồn tại"));
                            if (!instance.get().getId().equals(file.getReportInstanceId())) {
                                throw new IllegalStateException("File nguồn không thuộc instance đã chọn");
                            }
                        }
                        fileBySourceWeek.put(key, file);
                    }
                    sources.add(new WordGenerationSnapshot.Source(mapping, section, fileBySourceWeek.get(key), sourceWeek));
                }
            }
            if (sources.isEmpty()) throw new IllegalArgumentException("Block thiếu mapping: " + block.getBlockCode());
            result.add(new WordGenerationSnapshot.Block(block, sources));
        }
        if (result.isEmpty()) throw new IllegalArgumentException("Word chưa cấu hình block");
        return new WordGenerationSnapshot(weekStartDate, output.getTemplateRelativePath(), result);
    }
}