package com.techasians.alertservice.service;

import com.techasians.alertservice.entity.RptFile;
import com.techasians.alertservice.entity.RptOutputBlockDef;
import com.techasians.alertservice.entity.RptOutputSourceMap;
import com.techasians.alertservice.entity.RptSectionDef;
import java.time.LocalDate;
import java.util.*;

/** Snapshot chỉ dùng trong một tác vụ; nguồn thiếu được giữ với file null. */
public class WordGenerationSnapshot {
    private final LocalDate weekStartDate;
    private final String templatePath;
    private final List<Block> blocks;

    public WordGenerationSnapshot(LocalDate weekStartDate, String templatePath, List<Block> blocks) {
        this.weekStartDate = weekStartDate;
        this.templatePath = templatePath;
        this.blocks = Collections.unmodifiableList(new ArrayList<>(blocks));
    }
    public LocalDate getWeekStartDate() { return weekStartDate; }
    public String getTemplatePath() { return templatePath; }
    public List<Block> getBlocks() { return blocks; }

    public static class Block {
        private final RptOutputBlockDef definition;
        private final List<Source> sources;
        public Block(RptOutputBlockDef definition, List<Source> sources) {
            this.definition = definition;
            this.sources = Collections.unmodifiableList(new ArrayList<>(sources));
        }
        public RptOutputBlockDef getDefinition() { return definition; }
        public List<Source> getSources() { return sources; }
    }

    public static class Source {
        private final RptOutputSourceMap mapping;
        private final RptSectionDef section;
        private final RptFile file;
        private final LocalDate weekStartDate;
        public Source(RptOutputSourceMap mapping, RptSectionDef section, RptFile file, LocalDate weekStartDate) {
            this.mapping = mapping;
            this.section = section;
            this.file = file;
            this.weekStartDate = weekStartDate;
        }
        public RptOutputSourceMap getMapping() { return mapping; }
        public RptSectionDef getSection() { return section; }
        public RptFile getFile() { return file; }
        public LocalDate getWeekStartDate() { return weekStartDate; }
    }
}