package com.techasians.alertservice.service.impl;

import com.aspose.words.*;
import com.techasians.alertservice.enums.RptRenderType;
import com.techasians.alertservice.enums.RptSourceRole;
import com.techasians.alertservice.service.impl.WordSourceDocuments.ExcelSheet;
import com.techasians.alertservice.service.*;
import org.springframework.core.io.ClassPathResource;
import org.springframework.stereotype.Service;
import java.awt.Color;
import java.io.InputStream;
import java.nio.file.*;
import java.time.format.DateTimeFormatter;
import java.util.*;
import java.util.List;

/** Sinh báo cáo chính từ snapshot; chỉ lỗi thiếu dữ liệu đã biết mới được xuất tiếp. */
@Service
public class WeeklyWordOutputGenerator {
    private static final double CONTENT_FONT_SIZE = 14;
    private final ReportFileService files;
    private final WordSectionExtractor extractor;
    private final WordBookmarkRenderer renderer;
    private final WordProductIssueReader issueReader;
    private final WordIncidentChartReader chartReader;
    private final WordTrendChartRenderer chartRenderer;
    private final WordSystemIncidentReader systemIncidentReader;
    private final WordMobileFeedbackReader mobileFeedbackReader;
    private final WordNetworkQualityReader networkQualityReader;
    private final WordTv360FeedbackReader tv360FeedbackReader;
    private final WordSmeFeedbackReader smeFeedbackReader;
    private final WordFixedFeedbackReader fixedFeedbackReader;

    public WeeklyWordOutputGenerator(ReportFileService files, WordSectionExtractor extractor,
            WordBookmarkRenderer renderer, WordProductIssueReader issueReader,
            WordIncidentChartReader chartReader,
            WordTrendChartRenderer chartRenderer, WordSystemIncidentReader systemIncidentReader,
            WordMobileFeedbackReader mobileFeedbackReader, WordNetworkQualityReader networkQualityReader,
            WordTv360FeedbackReader tv360FeedbackReader, WordSmeFeedbackReader smeFeedbackReader,
            WordFixedFeedbackReader fixedFeedbackReader) {
        this.files = files;
        this.extractor = extractor;
        this.renderer = renderer;
        this.issueReader = issueReader;
        this.chartReader = chartReader;
        this.chartRenderer = chartRenderer;
        this.systemIncidentReader = systemIncidentReader;
        this.mobileFeedbackReader = mobileFeedbackReader;
        this.networkQualityReader = networkQualityReader;
        this.tv360FeedbackReader = tv360FeedbackReader;
        this.smeFeedbackReader = smeFeedbackReader;
        this.fixedFeedbackReader = fixedFeedbackReader;
    }

    public Path generate(WordGenerationSnapshot snapshot) {
        Path output = null;
        List<String> warnings = new ArrayList<>();
        try (InputStream template = new ClassPathResource(snapshot.getTemplatePath()).getInputStream();
             WordSourceDocuments sources = new WordSourceDocuments(files)) {
            Document document = new Document(template);
            String period = snapshot.getWeekStartDate().format(DateTimeFormatter.ofPattern("dd/MM/yyyy"))
                    + " - " + snapshot.getWeekStartDate().plusDays(6).format(DateTimeFormatter.ofPattern("dd/MM/yyyy"));
            for (String name : Arrays.asList("BM_REPORT_PERIOD_TITLE", "BM_REPORT_PERIOD_INTRO", "BM_REPORT_PERIOD_CLOSING")) {
                Bookmark bookmark = document.getRange().getBookmarks().get(name);
                if (bookmark == null) throw new IllegalArgumentException("Template thiếu bookmark: " + name);
                // Một số template đặt bookmark bao quanh cả câu, chỉ thay phần kỳ trong câu đó.
                String text = bookmark.getText();
                if (text.contains("[DD/MM-DD/MM/YYYY]")) text = text.replace("[DD/MM-DD/MM/YYYY]", period);
                else text = period;
                renderer.replaceText(document, name, text);
                // Màu xám chỉ dành cho placeholder; ngày tháng đầu ra dùng màu đen như báo cáo mẫu.
                for (Node node = bookmark.getBookmarkStart().nextPreOrder(document);
                     node != null && node != bookmark.getBookmarkEnd(); node = node.nextPreOrder(document)) {
                    if (node instanceof Run) ((Run) node).getFont().setColor(Color.BLACK);
                }
            }
            Set<String> targets = new HashSet<>();
            for (WordGenerationSnapshot.Block block : snapshot.getBlocks()) {
                String target = block.getDefinition().getTargetName();
                if (!targets.add(target)) throw new IllegalArgumentException("Bookmark bị cấu hình trùng: " + target);
                if (block.getDefinition().getRenderType() == RptRenderType.COPY_SECTION) {
                    copySections(document, block, sources, warnings);
                } else if ("PRODUCT_ISSUE".equals(block.getDefinition().getBlockCode())) {
                    productIssues(document, block, snapshot, sources, warnings);
                } else if ("MOBILE".equals(block.getDefinition().getBlockCode())) {
                    mobileFeedback(document, block, snapshot, sources, warnings);
                } else if ("CLM".equals(block.getDefinition().getBlockCode())) {
                    networkQuality(document, block, snapshot, sources, warnings);
                } else if ("TV360".equals(block.getDefinition().getBlockCode())) {
                    tv360Feedback(document, block, snapshot, sources, warnings);
                } else if ("SME".equals(block.getDefinition().getBlockCode())) {
                    smeFeedback(document, block, sources, warnings);
                } else if ("FIXED".equals(block.getDefinition().getBlockCode())) {
                    fixedFeedback(document, block, snapshot, sources, warnings);
                } else if ("SYSTEM_INCIDENT_CHART".equals(block.getDefinition().getBlockCode())) {
                    incidentChart(document, block, snapshot, sources, warnings);
                } else if ("SYSTEM_INCIDENT".equals(block.getDefinition().getBlockCode())) {
                    systemIncident(document, block, snapshot, sources, warnings);
                } else if ("STATION_OVERVIEW".equals(block.getDefinition().getBlockCode())
                        || "STATION_INCIDENT".equals(block.getDefinition().getBlockCode())) {
                    stationOverview(document, block, snapshot, sources, warnings);
                } else if ("STATION_INCIDENT_CHART".equals(block.getDefinition().getBlockCode())
                        || "CABLE_INCIDENT_CHART".equals(block.getDefinition().getBlockCode())) {
                    stationChart(document, block, snapshot, sources, warnings);
                } else {
                    throw new IllegalArgumentException("Block chưa được hỗ trợ: " + block.getDefinition().getBlockCode());
                }
            }
            for (String name : List.of(
                    "BM_SYSTEM_INCIDENT",
                    "BM_STATION_OVERVIEW",
                    "BM_STATION_INCIDENT",
                    "BM_PROGRAM_EVENT", "BM_CUSTOMER_REACTION", "BM_PRODUCT_ISSUE",
                    "BM_MOBILE", "BM_CLM", "BM_TV360", "BM_SME", "BM_FIXED")) {

                if (targets.contains(name)
                        || document.getRange().getBookmarks().get(name) == null) {
                    continue;
                }

                String warning = "Chưa cấu hình nội dung: " + name;
                if (!warnings.contains(warning)) {
                    warnings.add(warning);
                }

                renderer.replaceText(document, name, "Chưa có dữ liệu");
            }
            if (!warnings.isEmpty()) {
                DocumentBuilder builder = new DocumentBuilder(document);
                builder.moveToDocumentEnd();
                builder.writeln("Lưu ý: Báo cáo còn thiếu dữ liệu.");
                for (String warning : warnings) builder.writeln("- " + warning);
            }
            output = Files.createTempFile("weekly-word-", ".docx");
            document.save(output.toString(), SaveFormat.DOCX);
            return output;
        } catch (Exception exception) {
            if (output != null) {
                try { Files.deleteIfExists(output); }
                catch (Exception cleanup) { exception.addSuppressed(cleanup); }
            }
            throw new IllegalStateException("Không thể tạo báo cáo Word", exception);
        }
    }

    private void copySections(Document target, WordGenerationSnapshot.Block block, WordSourceDocuments documents,
                              List<String> warnings) throws Exception {
        Document fragment = new Document();
        Body body = fragment.getFirstSection().getBody();
        body.removeAllChildren();
        Bookmark bookmark = target.getRange().getBookmarks().get(block.getDefinition().getTargetName());
        if (bookmark == null) throw new IllegalArgumentException(
                "Template thiếu bookmark: " + block.getDefinition().getTargetName());
        double targetPosition = WordSectionAlignment.targetPosition(bookmark);
        for (WordGenerationSnapshot.Source source : block.getSources()) {
            if (source.getSection() == null) throw new IllegalArgumentException("COPY_SECTION thiếu section");
            if (source.getFile() == null) {
                missingParagraph(fragment, body, block, "Thiếu file nguồn " + source.getMapping().getSourceDefinitionId(), warnings);
                continue;
            }
            Document input = documents.word(source.getFile());
            try {
                List<Node> nodes = extractor.extract(input, source.getSection());
                NodeImporter importer = new NodeImporter(input, fragment, ImportFormatMode.KEEP_SOURCE_FORMATTING);
                List<Node> copies = new ArrayList<>();
                for (Node node : nodes) {
                    Node copy = importer.importNode(node, true);
                    preserveSourceFonts(node, copy);
                    body.appendChild(copy);
                    copies.add(copy);
                }
                WordSectionAlignment.align(nodes, copies, targetPosition);
            } catch (WordSectionExtractor.MissingWordSectionException exception) {
                missingParagraph(fragment, body, block, exception.getMessage(), warnings);
            }
        }
        setContentFontSize(body);
        List<Node> nodes = new ArrayList<>();
        for (Node node = body.getFirstChild(); node != null; node = node.getNextSibling()) nodes.add(node);
        if (nodes.isEmpty()) renderer.replaceText(target, block.getDefinition().getTargetName(), "Chưa có dữ liệu");
        else renderer.insertNodes(target, block.getDefinition().getTargetName(), nodes);
    }

    private void missingParagraph(Document fragment, Body body, WordGenerationSnapshot.Block block,
                                  String reason, List<String> warnings) throws Exception {
        warnings.add(block.getDefinition().getBlockCode() + ": " + reason);
        Paragraph paragraph = new Paragraph(fragment);
        paragraph.appendChild(new Run(fragment, "Chưa có dữ liệu"));
        body.appendChild(paragraph);
    }

    /** Aspose có thể đổi style nguồn thành Normal; giữ font hiệu lực trước khi mất kế thừa style. */
    private void preserveSourceFonts(Node source, Node copy) throws Exception {
        if (source instanceof Run) {
            copyFontNames(((Run) source).getFont(), ((Run) copy).getFont());
        } else if (source instanceof Paragraph) {
            copyFontNames(((Paragraph) source).getParagraphBreakFont(), ((Paragraph) copy).getParagraphBreakFont());
        }
        if (source instanceof CompositeNode) {
            Node original = ((CompositeNode) source).getFirstChild();
            Node imported = ((CompositeNode) copy).getFirstChild();
            while (original != null && imported != null) {
                preserveSourceFonts(original, imported);
                original = original.getNextSibling();
                imported = imported.getNextSibling();
            }
        }
    }

    private void copyFontNames(Font source, Font copy) {
        copy.setNameAscii(source.getNameAscii());
        copy.setNameOther(source.getNameOther());
        copy.setNameFarEast(source.getNameFarEast());
        copy.setNameBi(source.getNameBi());
    }

    /** Chỉ đổi cỡ chữ trên bản sao đầu ra, gồm cả nội dung trong bảng. */
    private void setContentFontSize(Node node) throws Exception {
        if (node instanceof Run) {
            ((Run) node).getFont().setSize(CONTENT_FONT_SIZE);
            ((Run) node).getFont().setSizeBi(CONTENT_FONT_SIZE);
        } else if (node instanceof Paragraph) {
            ((Paragraph) node).getParagraphBreakFont().setSize(CONTENT_FONT_SIZE);
            ((Paragraph) node).getParagraphBreakFont().setSizeBi(CONTENT_FONT_SIZE);
        }
        if (node instanceof CompositeNode) {
            for (Node child = ((CompositeNode) node).getFirstChild(); child != null; child = child.getNextSibling()) {
                setContentFontSize(child);
            }
        }
    }

    private void productIssues(Document target, WordGenerationSnapshot.Block block, WordGenerationSnapshot snapshot,
                               WordSourceDocuments documents, List<String> warnings) throws Exception {
        if (block.getSources().size() != 1) throw new IllegalArgumentException("Bất cập phải có một nguồn");
        WordGenerationSnapshot.Source source = block.getSources().get(0);
        if (source.getMapping().getSourceRole() != RptSourceRole.DATA || source.getSection() != null) {
            throw new IllegalArgumentException("PRODUCT_ISSUE phải dùng nguồn DATA Excel");
        }
        String sheetName = source.getMapping().getSourceSheetName();
        if (sheetName == null || sheetName.trim().isEmpty()) {
            throw new IllegalArgumentException("PRODUCT_ISSUE thiếu cấu hình sheet");
        }
        String text = "Chưa có dữ liệu";
        if (source.getFile() == null) warnings.add("PRODUCT_ISSUE: Thiếu file bất cập");
        else {
            try {
                WordProductIssueReader.Result result = issueReader.read(documents.excel(source.getFile()),
                        sheetName.trim(), snapshot.getWeekStartDate());
                warnings.addAll(result.getWarnings());
                int total = result.getIssues().size();
                if (total == 0) text = result.getWarnings().isEmpty()
                        ? "Không phát sinh bất cập có ngày phát hiện trong tuần."
                        : "Chưa đủ dữ liệu hợp lệ để kết luận số lượng bất cập trong tuần.";
                else {
                    renderer.replaceText(target, "BM_PRODUCT_ISSUE_TOTAL", Integer.toString(total));
                    renderer.replaceText(target, "BM_PRODUCT_ISSUE_OPTIMIZED_RATE", result.getOptimizedRate());
                    renderer.replaceText(target, "BM_PRODUCT_ISSUE_OPTIMIZED", Integer.toString(result.getOptimized()));
                    renderer.replaceText(target, "BM_PRODUCT_ISSUE_COMPLETED", Integer.toString(result.getCompleted()));
                    renderer.replaceText(target, "BM_PRODUCT_ISSUE_IN_PROGRESS", Integer.toString(result.getInProgress()));
                    renderer.replaceText(target, "BM_PRODUCT_ISSUE_EVALUATING_RATE", result.getEvaluatingRate());
                    renderer.replaceText(target, "BM_PRODUCT_ISSUE_EVALUATING", Integer.toString(result.getEvaluating()));
                    WordProductIssueReader.Issue selected = result.getIssues().get(0);
                    productIssuePart(target, "BM_PRODUCT_ISSUE_ACTUALITY", "Thực trạng", selected.getActuality(), selected, warnings);
                    productIssuePart(target, "BM_PRODUCT_ISSUE_CAUSE", "Nguyên nhân", selected.getCause(), selected, warnings);
                    productIssuePart(target, "BM_PRODUCT_ISSUE_PROPOSAL", "Kết quả", selected.getResult(), selected, warnings);
                    target.getCustomDocumentProperties().add("ProductIssueSourceFileId", source.getFile().getId().toString());
                    target.getCustomDocumentProperties().add("ProductIssueSourceRow", selected.getRowNumber());
                    return;
                }
            } catch (WordProductIssueReader.MissingIssueDataException exception) {
                warnings.add("PRODUCT_ISSUE: " + exception.getMessage());
            }
        }
        renderer.replaceText(target, block.getDefinition().getTargetName(), text);
    }

    private void productIssuePart(Document target, String bookmark, String label, String text,
                                  WordProductIssueReader.Issue issue, List<String> warnings) throws Exception {
        if (text.isEmpty()) {
            warnings.add("PRODUCT_ISSUE: Dòng " + issue.getRowNumber() + " thiếu nội dung " + label);
            text = "Chưa có dữ liệu";
        }
        renderer.replaceText(target, bookmark, text);
    }

    private void fixedFeedback(Document target, WordGenerationSnapshot.Block block, WordGenerationSnapshot snapshot,
                               WordSourceDocuments documents, List<String> warnings) throws Exception {
        Map<Integer, WordGenerationSnapshot.Source> configured = excelSources(block, RptSourceRole.DATA, 1, 2);
        WordGenerationSnapshot.Source notes = sourceAt(block, configured, 1);
        WordGenerationSnapshot.Source kpis = sourceAt(block, configured, 2);
        if (notes.getFile() == null) {
            warnings.add("FIXED: Thiếu file nguồn " + notes.getMapping().getSourceDefinitionId());
            renderer.replaceText(target, block.getDefinition().getTargetName(), "Chưa có dữ liệu");
            return;
        }
        WordFixedFeedbackReader.Result result = fixedFeedbackReader.read(
                documents.excelSheet(notes, snapshot.getWeekStartDate()),
                documents.excelSheet(kpis, snapshot.getWeekStartDate()), snapshot.getWeekStartDate(), warnings);
        if (result.hasReceived()) renderer.replaceText(target, "BM_FIXED_DAILY", result.getReceivedDaily());
        else renderer.replaceText(target, "BM_FIXED_RECEIVED", "Chưa có dữ liệu tiếp nhận. ");
        if (result.getTechnicalDaily() == null) {
            renderer.replaceText(target, "BM_FIXED_TECH_SENTENCE", "Chưa có dữ liệu sự cố kỹ thuật. ");
        } else {
            renderer.replaceText(target, "BM_FIXED_TECH_DAILY", result.getTechnicalDaily());
            if (result.getTechnicalChange() != null) renderer.replaceText(target, "BM_FIXED_TECH_CHANGE", result.getTechnicalChange());
            else renderer.replaceText(target, "BM_FIXED_TECH_COMPARISON", ". Chưa đủ dữ liệu so sánh với tuần trước. ");
        }
        if (result.getRate() == null) {
            renderer.replaceText(target, "BM_FIXED_RATE_SENTENCE", "Chưa có dữ liệu TLPA.");
        } else {
            renderer.replaceText(target, "BM_FIXED_RATE", result.getRate());
            if (result.getRateChange() != null) renderer.replaceText(target, "BM_FIXED_RATE_CHANGE", result.getRateChange());
            else renderer.replaceText(target, "BM_FIXED_RATE_COMPARISON", ". Chưa đủ dữ liệu so sánh với tuần trước.");
        }
        fixedService(target, "BM_FIXED_GPON", result.getGpon());
        fixedService(target, "BM_FIXED_THS", result.getTelevision());
    }

    private void fixedService(Document target, String prefix, WordFixedFeedbackReader.Service service) throws Exception {
        if (service == null) {
            renderer.replaceText(target, prefix, "Chưa có dữ liệu");
            return;
        }
        renderer.replaceText(target, prefix + "_RATE", service.getRate());
        if (!service.hasKpi()) renderer.replaceText(target, prefix + "_KPI_PART", " – Chưa đủ dữ liệu đánh giá KPI.");
        else {
            renderer.replaceText(target, prefix + "_ASSESSMENT", service.getAssessment());
            renderer.replaceText(target, prefix + "_KPI", service.getKpi());
        }
    }

    private void smeFeedback(Document target, WordGenerationSnapshot.Block block,
                             WordSourceDocuments documents, List<String> warnings) throws Exception {
        if (block.getSources().size() != 1) throw new IllegalArgumentException("SME phải có đúng một nguồn");
        WordGenerationSnapshot.Source source = block.getSources().get(0);
        if (source.getMapping().getSourceRole() != RptSourceRole.TEXT || source.getSection() == null
                || source.getMapping().getSourceSectionDefId() == null
                || !source.getMapping().getSourceSectionDefId().equals(source.getSection().getId())
                || !Objects.equals(source.getMapping().getSourceDefinitionId(), source.getSection().getSourceDefinitionId())
                || !Integer.valueOf(1).equals(source.getSection().getStatus())
                || source.getMapping().getSourceSheetName() != null) {
            throw new IllegalArgumentException("SME phải dùng nguồn Word, role TEXT, section đang hoạt động thuộc nguồn, không dùng sheet");
        }
        smeFeedbackReader.validateSection(source.getSection());
        String name = block.getDefinition().getTargetName();
        if (source.getFile() == null) {
            warnings.add("SME: Thiếu file nguồn " + source.getMapping().getSourceDefinitionId());
            renderer.replaceText(target, name, "Chưa có dữ liệu");
        } else {
            try {
                Paragraph content = smeFeedbackReader.read(documents.word(source.getFile()), source.getSection());
                renderer.replaceInlineRuns(target, name, content);
            } catch (WordSectionExtractor.MissingWordSectionException exception) {
                warnings.add("SME: " + exception.getMessage());
                renderer.replaceText(target, name, "Chưa có dữ liệu");
            }
        }
        setContentFontSize(target.getRange().getBookmarks().get(name).getBookmarkStart().getParentNode());
    }

    private void tv360Feedback(Document target, WordGenerationSnapshot.Block block, WordGenerationSnapshot snapshot,
                               WordSourceDocuments documents, List<String> warnings) throws Exception {
        Map<Integer, WordGenerationSnapshot.Source> configured = excelSources(block, RptSourceRole.DATA, 1, 2);
        WordGenerationSnapshot.Source source = sourceAt(block, configured, 1);
        WordGenerationSnapshot.Source causes = sourceAt(block, configured, 2);
        if (source.getFile() == null) {
            warnings.add("TV360: Thiếu file nguồn " + source.getMapping().getSourceDefinitionId());
            renderer.replaceText(target, block.getDefinition().getTargetName(), "Chưa có dữ liệu");
            return;
        }
        try {
            WordTv360FeedbackReader.Result result = tv360FeedbackReader.read(
                    documents.excelSheet(source, snapshot.getWeekStartDate()),
                    documents.excelSheet(causes, snapshot.getWeekStartDate()), snapshot.getWeekStartDate(), warnings);
            renderer.replaceText(target, "BM_TV360_DAILY", result.getDaily());
            if (result.getChange() != null) renderer.replaceText(target, "BM_TV360_CHANGE", result.getChange());
            else renderer.replaceText(target, "BM_TV360_COMPARISON", ". Chưa đủ dữ liệu so sánh với tuần trước.");
            List<WordTv360FeedbackReader.Service> top = result.getTop();
            if (!top.isEmpty()) {
                renderer.replaceText(target, "BM_TV360_SERVICE_1", top.get(0).getName());
                renderer.replaceText(target, "BM_TV360_SHARE_1", top.get(0).getShare());
                if (top.size() > 1) {
                    renderer.replaceText(target, "BM_TV360_SERVICE_2", top.get(1).getName());
                    renderer.replaceText(target, "BM_TV360_SHARE_2", top.get(1).getShare());
                } else renderer.replaceText(target, "BM_TV360_SECOND_SERVICE", "");
            } else renderer.replaceText(target, "BM_TV360_TOP_SENTENCE", result.isZero()
                    ? " Không phát sinh PAKH trong tuần." : " Chưa đủ dữ liệu để xác định PAKH tập trung.");
            if (result.hasCauses()) {
                renderer.replaceText(target, "BM_TV360_SUPPORT_SHARE", result.getSupportShare());
                renderer.replaceText(target, "BM_TV360_CUSTOMER_SHARE", result.getCustomerShare());
            } else renderer.replaceText(target, "BM_TV360_CAUSES", " Chưa đủ dữ liệu nguyên nhân lỗi.");
        } catch (WordTv360FeedbackReader.MissingTv360DataException exception) {
            warnings.add("TV360: " + exception.getMessage());
            renderer.replaceText(target, block.getDefinition().getTargetName(), "Chưa có dữ liệu");
        }
    }

    private void networkQuality(Document target, WordGenerationSnapshot.Block block, WordGenerationSnapshot snapshot,
                                WordSourceDocuments documents, List<String> warnings) throws Exception {
        if (block.getSources().size() != 1) throw new IllegalArgumentException("CLM phải có đúng một nguồn");
        WordGenerationSnapshot.Source source = block.getSources().get(0);
        if (source.getMapping().getSourceRole() != RptSourceRole.DATA
                || source.getSection() != null) {
            throw new IllegalArgumentException("CLM phải dùng nguồn DATA Excel, không dùng section Word");
        }
        if (source.getFile() == null) {
            warnings.add("CLM: Thiếu file nguồn " + source.getMapping().getSourceDefinitionId());
            renderer.replaceText(target, block.getDefinition().getTargetName(), "Chưa có dữ liệu");
            return;
        }
        String sheet = source.getMapping().getSourceSheetName();
        if (sheet == null || sheet.trim().isEmpty()) throw new IllegalArgumentException("CLM thiếu cấu hình sheet");
        try {
            WordNetworkQualityReader.Result result = networkQualityReader.read(documents.excel(source.getFile()),
                    sheet, snapshot.getWeekStartDate(), warnings);
            WordNetworkQualityReader.Overview overview = result.getOverview();
            if (overview == null) renderer.replaceText(target, "BM_CLM_OVERVIEW", "Chưa có dữ liệu");
            else {
                renderer.replaceText(target, "BM_CLM_DAILY_PA", overview.getDailyPa());
                renderer.replaceText(target, "BM_CLM_RATE", overview.getRate());
                renderer.replaceText(target, "BM_CLM_PREVIOUS_RATE", overview.getPreviousRate());
                renderer.replaceText(target, "BM_CLM_TREND", overview.getTrend());
            }
            qualityService(target, "BM_CLM_VOICE", result.getVoice());
            qualityService(target, "BM_CLM_DATA", result.getData());
        } catch (WordNetworkQualityReader.MissingQualityDataException exception) {
            warnings.add("CLM: " + exception.getMessage());
            renderer.replaceText(target, block.getDefinition().getTargetName(), "Chưa có dữ liệu");
        }
    }

    private void qualityService(Document target, String prefix, WordNetworkQualityReader.ServiceResult result) throws Exception {
        if (result == null) {
            renderer.replaceText(target, prefix, "Chưa có dữ liệu");
            return;
        }
        renderer.replaceText(target, prefix + "_RATE", result.getRate());
        renderer.replaceText(target, prefix + "_KPI", result.getKpi());
        renderer.replaceText(target, prefix + "_ASSESSMENT", result.getAssessment());
        if (result.hasWarnings()) {
            renderer.replaceText(target, prefix + "_WARNING_COUNT", result.getWarningCount());
            renderer.replaceText(target, prefix + "_WARNINGS", result.getWarningsText());
        } else renderer.replaceText(target, prefix + "_WARNING_SENTENCE", "Chưa đủ dữ liệu cảnh báo tỉnh.");
    }

    private void mobileFeedback(Document target, WordGenerationSnapshot.Block block, WordGenerationSnapshot snapshot,
                                WordSourceDocuments documents, List<String> warnings) throws Exception {
        if (block.getSources().size() != 1) throw new IllegalArgumentException("MOBILE phải có đúng một nguồn");
        WordGenerationSnapshot.Source source = block.getSources().get(0);
        if (source.getMapping().getSourceRole() != RptSourceRole.DATA
                || source.getSection() != null) {
            throw new IllegalArgumentException("MOBILE phải dùng nguồn DATA Excel, không dùng section Word");
        }
        if (source.getFile() == null) {
            warnings.add("MOBILE: Thiếu file nguồn " + source.getMapping().getSourceDefinitionId());
            renderer.replaceText(target, block.getDefinition().getTargetName(), "Chưa có dữ liệu");
            return;
        }
        String sheet = source.getMapping().getSourceSheetName();
        if (sheet == null || sheet.trim().isEmpty()) throw new IllegalArgumentException("MOBILE thiếu cấu hình sheet");
        try {
            WordMobileFeedbackReader.Result result = mobileFeedbackReader.read(documents.excel(source.getFile()),
                    sheet, snapshot.getWeekStartDate(), warnings);
            renderer.replaceText(target, "BM_MOBILE_AVERAGE", result.getAverage());
            renderer.replaceText(target, "BM_MOBILE_CHANGE", result.getChange());
            renderer.replaceText(target, "BM_MOBILE_CHANGE_DETAILS", result.getChangeDetails());
            if (result.hasTypicalService()) {
                renderer.replaceText(target, "BM_MOBILE_DIRECTION", result.getDirection());
                renderer.replaceText(target, "BM_MOBILE_SERVICE", result.getTypicalService());
                renderer.replaceText(target, "BM_MOBILE_SERVICE_CHANGE", result.getTypicalChange());
            } else {
                renderer.replaceText(target, "BM_MOBILE_TYPICAL_SENTENCE", result.isUnchanged()
                        ? "Không có PA biến động điển hình."
                        : "Chưa đủ dữ liệu để xác định PA điển hình.");
            }
        } catch (WordMobileFeedbackReader.MissingMobileDataException exception) {
            warnings.add("MOBILE: " + exception.getMessage());
            renderer.replaceText(target, block.getDefinition().getTargetName(), "Chưa có dữ liệu");
        }
    }

    private void systemIncident(Document target, WordGenerationSnapshot.Block block, WordGenerationSnapshot snapshot,
                                WordSourceDocuments documents, List<String> warnings) throws Exception {
        if (block.getSources().size() != 1) throw new IllegalArgumentException("SYSTEM_INCIDENT phải có đúng một nguồn");
        WordGenerationSnapshot.Source source = block.getSources().get(0);
        if (source.getMapping().getSourceRole() != com.techasians.alertservice.enums.RptSourceRole.DATA
                || source.getSection() != null) {
            throw new IllegalArgumentException("SYSTEM_INCIDENT phải dùng nguồn DATA Excel, không dùng section Word");
        }
        if (source.getFile() == null) {
            warnings.add("SYSTEM_INCIDENT: Thiếu file nguồn " + source.getMapping().getSourceDefinitionId());
            renderer.replaceText(target, block.getDefinition().getTargetName(), "Chưa có dữ liệu");
            return;
        }
        try {
            String sheet = source.getMapping().getSourceSheetName();
            if (sheet == null || sheet.trim().isEmpty()) throw new IllegalArgumentException("SYSTEM_INCIDENT thiếu cấu hình sheet");
            WordSystemIncidentReader.Result result = systemIncidentReader.read(
                    documents.excel(source.getFile()), sheet, snapshot.getWeekStartDate());
            renderer.replaceText(target, "BM_SYSTEM_INCIDENT_COUNT", Long.toString(result.getIncidentCount()));
            renderer.replaceText(target, "BM_SYSTEM_INCIDENT_PA", Long.toString(result.getComplaintCount()));
            renderer.replaceText(target, "BM_SYSTEM_INCIDENT_SC_CHANGE", result.getIncidentChange());
            renderer.replaceText(target, "BM_SYSTEM_INCIDENT_PA_CHANGE", result.getComplaintChange());
        } catch (WordSystemIncidentReader.MissingIncidentDataException exception) {
            warnings.add("SYSTEM_INCIDENT: " + exception.getMessage());
            renderer.replaceText(target, block.getDefinition().getTargetName(), "Chưa có dữ liệu");
        }
    }

    private WordGenerationSnapshot.Source stationSource(WordGenerationSnapshot.Block block,
                                                       com.techasians.alertservice.enums.RptSourceRole role) {
        if (block.getSources().size() != 1) throw new IllegalArgumentException(
                block.getDefinition().getBlockCode() + " phải có đúng một nguồn");
        WordGenerationSnapshot.Source source = block.getSources().get(0);
        if (source.getMapping().getSourceRole() != role || source.getSection() != null) {
            throw new IllegalArgumentException(block.getDefinition().getBlockCode() + " phải dùng nguồn Excel, role " + role);
        }
        return source;
    }

    /** sort_order là chức năng nguồn trong từng block, không phụ thuộc thứ tự danh sách snapshot. */
    private Map<Integer, WordGenerationSnapshot.Source> excelSources(WordGenerationSnapshot.Block block,
            RptSourceRole role, Integer... allowedOrders) {
        Map<Integer, WordGenerationSnapshot.Source> result = new HashMap<>();
        for (WordGenerationSnapshot.Source source : block.getSources()) {
            Integer order = source.getMapping().getSortOrder();
            if (source.getMapping().getSourceRole() != role || source.getSection() != null
                    || !Arrays.asList(allowedOrders).contains(order)) {
                throw new IllegalArgumentException(block.getDefinition().getBlockCode()
                        + " phải dùng nguồn Excel, role " + role + ", sort_order " + Arrays.toString(allowedOrders));
            }
            if (result.putIfAbsent(order, source) != null) throw new IllegalArgumentException(
                    block.getDefinition().getBlockCode() + " trùng nguồn sort_order=" + order);
        }
        return result;
    }

    private WordGenerationSnapshot.Source sourceAt(WordGenerationSnapshot.Block block,
            Map<Integer, WordGenerationSnapshot.Source> sources, int order) {
        WordGenerationSnapshot.Source source = sources.get(order);
        if (source == null) throw new IllegalArgumentException(block.getDefinition().getBlockCode() + " thiếu nguồn sort_order=" + order);
        return source;
    }

    private void stationOverview(Document target, WordGenerationSnapshot.Block block, WordGenerationSnapshot snapshot,
                                 WordSourceDocuments documents, List<String> warnings) throws Exception {
        String code = block.getDefinition().getBlockCode();
        boolean cable = "STATION_INCIDENT".equals(code);
        String prefix = cable ? "BM_CABLE_" : "BM_STATION_";
        Map<Integer, WordGenerationSnapshot.Source> configured = cable ? excelSources(block, RptSourceRole.DATA, 1, 2, 3) : null;
        WordGenerationSnapshot.Source source = cable ? sourceAt(block, configured, 1) : stationSource(block, RptSourceRole.DATA);
        if (source.getFile() == null) {
            warnings.add(code + ": Thiếu file nguồn " + source.getMapping().getSourceDefinitionId());
            renderer.replaceText(target, block.getDefinition().getTargetName(), "Chưa có dữ liệu");
            return;
        }
        String sheet = source.getMapping().getSourceSheetName();
        if (sheet == null || sheet.trim().isEmpty()) throw new IllegalArgumentException(code + " thiếu cấu hình sheet");
        try {
            org.apache.poi.ss.usermodel.Workbook workbook = documents.excel(source.getFile());
            WordSystemIncidentReader.StationResult result = cable
                    ? systemIncidentReader.readCable(workbook, sheet, snapshot.getWeekStartDate())
                    : systemIncidentReader.readStation(workbook, sheet, snapshot.getWeekStartDate());
            renderer.replaceText(target, prefix + "COUNT", result.getIncidentCount());
            renderer.replaceText(target, prefix + "PA", result.getComplaintCount());
            renderer.replaceText(target, prefix + "AFFECTED", result.getAffectedCount());
            renderer.replaceText(target, prefix + "SC_CHANGE", result.getIncidentChange());
            renderer.replaceText(target, prefix + "PA_CHANGE", result.getComplaintChange());
            renderer.replaceText(target, prefix + "AFFECTED_CHANGE", result.getAffectedChange());
            if (cable) {
                String provinces = "Chưa có dữ liệu";
                try {
                    ExcelSheet current = documents.excelSheet(sourceAt(block, configured, 2), snapshot.getWeekStartDate());
                    ExcelSheet previous = snapshot.getWeekStartDate().getYear() < snapshot.getWeekStartDate().plusDays(6).getYear()
                            ? documents.excelSheet(sourceAt(block, configured, 3), snapshot.getWeekStartDate()) : null;
                    provinces = chartReader.readTopCableProvinces(current, previous, snapshot.getWeekStartDate(), warnings);
                    if (provinces.isEmpty()) {
                        if ("0".equals(result.getIncidentCount())) provinces = "Không phát sinh sự cố";
                        else {
                            provinces = "Chưa có dữ liệu";
                            warnings.add(code + ": Không có dòng sự cố cáp có mã tỉnh trong tuần dù bảng TH có sự cố");
                        }
                    }
                } catch (WordIncidentChartReader.MissingChartDataException exception) {
                    warnings.add(code + ": " + exception.getMessage());
                }
                renderer.replaceText(target, "BM_CABLE_TOP_PROVINCES", provinces);
            }
        } catch (WordSystemIncidentReader.MissingIncidentDataException exception) {
            warnings.add(code + ": " + exception.getMessage());
            renderer.replaceText(target, block.getDefinition().getTargetName(), "Chưa có dữ liệu");
        }
    }

    private void stationChart(Document target, WordGenerationSnapshot.Block block, WordGenerationSnapshot snapshot,
                              WordSourceDocuments documents, List<String> warnings) throws Exception {
        String code = block.getDefinition().getBlockCode();
        boolean cable = "CABLE_INCIDENT_CHART".equals(code);
        try {
            Double[][] values = chartValues(block, snapshot, documents, warnings);
            renderer.insertImage(target, block.getDefinition().getTargetName(),
                    cable ? chartRenderer.renderCable(snapshot.getWeekStartDate(), values)
                            : chartRenderer.renderStation(snapshot.getWeekStartDate(), values));
        } catch (WordIncidentChartReader.MissingChartDataException exception) {
            warnings.add(code + ": " + exception.getMessage());
            renderer.replaceText(target, block.getDefinition().getTargetName(), "Chưa đủ dữ liệu để tạo biểu đồ");
        }
    }

    private void incidentChart(Document target, WordGenerationSnapshot.Block block, WordGenerationSnapshot snapshot,
                               WordSourceDocuments documents, List<String> warnings) throws Exception {
        try {
            Double[][] values = chartValues(block, snapshot, documents, warnings);
            renderer.insertImage(target, block.getDefinition().getTargetName(),
                    chartRenderer.render(snapshot.getWeekStartDate(), values));
        } catch (WordIncidentChartReader.MissingChartDataException exception) {
            warnings.add("SYSTEM_INCIDENT_CHART: " + exception.getMessage());
            renderer.replaceText(target, block.getDefinition().getTargetName(), "Chưa đủ dữ liệu để tạo biểu đồ");
        }
    }

    private Double[][] chartValues(WordGenerationSnapshot.Block block, WordGenerationSnapshot snapshot,
                                  WordSourceDocuments documents, List<String> warnings) throws Exception {
        Map<Integer, WordGenerationSnapshot.Source> configured = excelSources(block, RptSourceRole.CHART_ACCUMULATED, 1, 2);
        ExcelSheet current = documents.excelSheet(sourceAt(block, configured, 1), snapshot.getWeekStartDate());
        ExcelSheet previous = snapshot.getWeekStartDate().minusWeeks(7).getYear() < snapshot.getWeekStartDate().plusDays(6).getYear()
                ? documents.excelSheet(sourceAt(block, configured, 2), snapshot.getWeekStartDate()) : null;
        switch (block.getDefinition().getBlockCode()) {
            case "STATION_INCIDENT_CHART": return chartReader.readStation(current, previous, snapshot.getWeekStartDate(), warnings);
            case "CABLE_INCIDENT_CHART": return chartReader.readCable(current, previous, snapshot.getWeekStartDate(), warnings);
            default: return chartReader.read(current, previous, snapshot.getWeekStartDate(), warnings);
        }
    }
}
