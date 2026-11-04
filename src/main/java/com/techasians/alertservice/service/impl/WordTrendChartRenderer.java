package com.techasians.alertservice.service.impl;

import org.springframework.stereotype.Component;

import javax.imageio.ImageIO;
import java.awt.*;
import java.awt.geom.Line2D;
import java.awt.image.BufferedImage;
import java.io.ByteArrayOutputStream;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.Locale;

/** Vẽ biểu đồ sự cố hệ thống và trạm, dùng chung ảnh PNG, trục, cột, đường và nhãn tuần. */
@Component
public class WordTrendChartRenderer {
    private static final Color BAR = new Color(220, 30, 50), LINE = new Color(80, 80, 80);
    private static final Color LINE_LABEL = new Color(200, 20, 40), GRID = new Color(220, 220, 220);
    private static final Color AXIS = new Color(160, 160, 160), TITLE = new Color(40, 40, 40);
    private static final Color STATION_INCIDENT = Color.BLACK, STATION_PA = Color.RED;
    private static final Color AFFECTED = new Color(64, 64, 64);
    private static final Font TITLE_FONT = new Font("Times New Roman", Font.BOLD, 35);
    private static final Font LABEL_FONT = new Font("Times New Roman", Font.BOLD, 28);
    private static final Font AXIS_FONT = new Font("Times New Roman", Font.PLAIN, 25);
    private static final Font STATION_LABEL_FONT = new Font("Times New Roman", Font.BOLD, 24);
    private static final Font STATION_AXIS_FONT = new Font("Times New Roman", Font.PLAIN, 23);

    /** [8][2]: số sự cố và PAKH. Cột PA trục trái, đường sự cố trục phải. */
    public byte[] render(LocalDate week, Double[][] values) throws Exception {
        return render(week, values, false);
    }

    /** [8][3]: số sự cố, PAKH, TB ảnh hưởng. Hai cột trục trái, đường TB trục phải. */
    public byte[] renderStation(LocalDate week, Double[][] values) throws Exception {
        return render(week, values, true);
    }

    public byte[] renderCable(LocalDate week, Double[][] values) throws Exception {
        return render(week, values, true, "XU THẾ SỰ CỐ ĐỨT CÁP/CHÁY CÁP");
    }

    private byte[] render(LocalDate week, Double[][] values, boolean station) throws Exception {
        return render(week, values, station, "BIỂU ĐỒ XU THẾ LỖI TRẠM");
    }

    private byte[] render(LocalDate week, Double[][] values, boolean station, String title) throws Exception {
        validate(values, station);
        int width = station ? 1700 : 1500, height = station ? 600 : 650;
        BufferedImage image = new BufferedImage(width, height, BufferedImage.TYPE_INT_RGB);
        Graphics2D g = image.createGraphics();
        try {
            g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
            g.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING, RenderingHints.VALUE_TEXT_ANTIALIAS_ON);
            g.setColor(Color.WHITE);
            g.fillRect(0, 0, width, height);
            if (station) drawStation(g, week, values, title);
            else drawSystem(g, week, values);
        } finally { g.dispose(); }
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        ImageIO.write(image, "png", out);
        return out.toByteArray();
    }

    private void drawSystem(Graphics2D g, LocalDate week, Double[][] values) {
        int left = 110, right = 1420, top = 90, bottom = 510, height = bottom - top;
        int step = (right - left) / 8;
        int[] xs = centers(left, right);
        double maxPa = Math.max(1, maxOf(values, 1)), maxIncident = Math.max(1, maxOf(values, 0));
        g.setFont(TITLE_FONT);
        g.setColor(TITLE);
        String title = "Xu thế tiếp nhận sự cố và PAKH";
        g.drawString(title, left + (right - left - g.getFontMetrics().stringWidth(title)) / 2, top - 40);
        drawGrid(g, left, right, top, bottom);
        g.setColor(AXIS);
        g.setStroke(new BasicStroke(1.5f));
        g.drawLine(left, bottom, right, bottom);
        for (int i = 0; i < 8; i++) {
            double value = values[i][1] == null ? 0 : values[i][1];
            int barHeight = (int) (value / maxPa * (height - 20));
            if (barHeight < 1 && value > 0) barHeight = 1;
            drawBar(g, xs[i], (int) (step * 0.55), bottom - barHeight, bottom,
                    number(value), BAR, LABEL_FONT, 6);
        }
        Integer[] ys = linePositions(values, 0, bottom, height - 20, maxIncident);
        drawPolyline(g, xs, ys, LINE, new BasicStroke(2.5f, BasicStroke.CAP_BUTT,
                BasicStroke.JOIN_MITER, 10, new float[] {10, 6}, 0));
        g.setStroke(new BasicStroke(1.5f));
        for (int i = 0; i < 8; i++) {
            if (ys[i] == null) continue;
            g.setColor(Color.WHITE);
            g.fillOval(xs[i] - 7, ys[i] - 7, 14, 14);
            g.setColor(LINE);
            g.drawOval(xs[i] - 7, ys[i] - 7, 14, 14);
            g.setFont(LABEL_FONT);
            g.setColor(LINE_LABEL);
            centered(g, number(values[i][0]), xs[i], ys[i] - 14);
        }
        drawAxis(g, left, top, bottom, maxPa, true, false, Color.RED);
        drawAxis(g, right, top, bottom, maxIncident, false, false, Color.BLACK);
        drawWeekLabels(g, week, xs, bottom, false);
        drawSystemLegend(g, bottom);
    }

    private void drawStation(Graphics2D g, LocalDate week, Double[][] values, String title) {
        int left = 95, right = 1520, top = 105, bottom = 435, height = bottom - top;
        int[] xs = centers(left, right);
        g.setFont(TITLE_FONT);
        g.setColor(Color.BLACK);
        centered(g, title, 850, 58);
        double leftMax = stationScale(Math.max(maxOf(values, 0), maxOf(values, 1)));
        double affectedStep = affectedAxisStep(maxOf(values, 2));
        // Mốc 0 nằm trên đáy đúng hai khoảng lưới, giống biểu đồ mẫu.
        double rightMin = -2 * affectedStep, rightMax = 3 * affectedStep;
        drawGrid(g, left, right, top, bottom);
        drawAxis(g, left, top, bottom, leftMax, true, true, Color.BLACK);
        drawAxis(g, right, top, bottom, rightMin, rightMax, false, true, Color.BLACK);
        for (int i = 0; i < 8; i++) {
            for (int series = 0; series < 2; series++) {
                double value = values[i][series];
                int barTop = bottom - (int) (value / leftMax * height);
                // Nhãn PAKH nằm trong cột đỏ; nhãn sự cố nằm ngay trên cột đen.
                g.setFont(STATION_LABEL_FONT);
                int labelY = series == 1 && value > 0
                        ? Math.min(bottom - 6, barTop + g.getFontMetrics().getAscent() + 10) : barTop - 8;
                drawBar(g, xs[i] + (series == 0 ? -34 : 34), 66,
                        barTop, bottom, number(value), series == 0 ? STATION_INCIDENT : STATION_PA,
                        STATION_LABEL_FONT, series == 1 ? Color.WHITE : Color.BLACK, labelY);
            }
        }
        Integer[] ys = new Integer[8];
        for (int i = 0; i < 8; i++) ys[i] = bottom
                - (int) ((values[i][2] - rightMin) / (rightMax - rightMin) * height);
        drawPolyline(g, xs, ys, AFFECTED, new BasicStroke(3, BasicStroke.CAP_ROUND,
                BasicStroke.JOIN_ROUND, 10, new float[] {10, 6}, 0));
        for (int i = 0; i < 8; i++) {
            g.setColor(AFFECTED);
            g.fillOval(xs[i] - 4, ys[i] - 4, 8, 8);
            g.setFont(STATION_LABEL_FONT);
            g.setColor(Color.BLACK);
            centered(g, grouped(values[i][2]), xs[i], ys[i] - 16);
        }
        drawWeekLabels(g, week, xs, bottom, true);
        drawStationLegend(g);
    }

    private int[] centers(int left, int right) {
        int step = (right - left) / 8;
        int[] xs = new int[8];
        for (int i = 0; i < 8; i++) xs[i] = left + step / 2 + i * step;
        return xs;
    }

    private void drawGrid(Graphics2D g, int left, int right, int top, int bottom) {
        g.setColor(GRID);
        g.setStroke(new BasicStroke(1));
        for (int i = 0; i <= 5; i++) {
            int y = bottom - (int) ((double) i / 5 * (bottom - top));
            g.drawLine(left, y, right, y);
        }
    }

    private void drawBar(Graphics2D g, int center, int width, int top, int bottom, String label,
                         Color color, Font font, int labelGap) {
        drawBar(g, center, width, top, bottom, label, color, font, color, top - labelGap);
    }

    private void drawBar(Graphics2D g, int center, int width, int top, int bottom, String label,
                         Color color, Font font, Color labelColor, int labelY) {
        g.setColor(color);
        g.fillRect(center - width / 2, top, width, bottom - top);
        g.setFont(font);
        g.setColor(labelColor);
        centered(g, label, center, labelY);
    }

    private Integer[] linePositions(Double[][] values, int series, int bottom, int height, double max) {
        Integer[] ys = new Integer[8];
        for (int i = 0; i < 8; i++) if (values[i][series] != null) {
            ys[i] = bottom - (int) (values[i][series] / max * height);
        }
        return ys;
    }

    private void drawPolyline(Graphics2D g, int[] xs, Integer[] ys, Color color, Stroke stroke) {
        g.setColor(color);
        g.setStroke(stroke);
        for (int i = 1; i < 8; i++) if (ys[i - 1] != null && ys[i] != null) {
            g.draw(new Line2D.Double(xs[i - 1], ys[i - 1], xs[i], ys[i]));
        }
    }

    private void drawAxis(Graphics2D g, int x, int top, int bottom, double max,
                          boolean left, boolean station, Color color) {
        drawAxis(g, x, top, bottom, 0, max, left, station, color);
    }

    private void drawAxis(Graphics2D g, int x, int top, int bottom, double min, double max,
                          boolean left, boolean station, Color color) {
        g.setFont(station ? STATION_AXIS_FONT : AXIS_FONT);
        g.setColor(color);
        for (int i = 0; i <= 5; i++) {
            int y = bottom - (int) ((double) i / 5 * (bottom - top));
            double value = min + (max - min) * i / 5;
            String label = station && !left ? (value < 0 ? "(" + grouped(-value) + ")" : grouped(value))
                    : number(value);
            int gap = station ? 12 : 8;
            int textX = left ? x - g.getFontMetrics().stringWidth(label) - gap : x + gap;
            g.drawString(label, textX, y + (station ? 8 : g.getFontMetrics().getAscent() / 2));
        }
    }

    private void drawWeekLabels(Graphics2D g, LocalDate week, int[] xs, int bottom, boolean station) {
        g.setFont(station ? STATION_AXIS_FONT : AXIS_FONT);
        g.setColor(station ? Color.BLACK : TITLE);
        DateTimeFormatter date = DateTimeFormatter.ofPattern("dd/MM");
        for (int i = 0; i < 8; i++) {
            LocalDate start = week.minusWeeks(7 - i);
            if (station) centered(g, start.format(date) + " - " + start.plusDays(6).format(date), xs[i], bottom + 38);
            else {
                centered(g, start.format(date), xs[i], bottom + 28);
                centered(g, "- " + start.plusDays(6).format(date), xs[i], bottom + 50);
            }
        }
    }

    private void drawSystemLegend(Graphics2D g, int bottom) {
        g.setFont(AXIS_FONT);
        FontMetrics fm = g.getFontMetrics();
        int y = bottom + 120;
        int width = 28 + fm.stringWidth("PAKH") + 40 + 34 + fm.stringWidth("Sự cố");
        int x = (1500 - width) / 2;
        g.setColor(BAR);
        g.fillRect(x, y - fm.getAscent(), 22, fm.getAscent());
        g.setColor(TITLE);
        g.drawString("PAKH", x + 28, y);
        x += 28 + fm.stringWidth("PAKH") + 40;
        g.setStroke(new BasicStroke(2.5f, BasicStroke.CAP_BUTT, BasicStroke.JOIN_MITER, 10, new float[] {8, 5}, 0));
        g.setColor(LINE);
        g.draw(new Line2D.Double(x, y - fm.getAscent() / 2.0, x + 28, y - fm.getAscent() / 2.0));
        g.setStroke(new BasicStroke(1));
        g.setColor(TITLE);
        g.drawString("Sự cố", x + 34, y);
    }

    private void drawStationLegend(Graphics2D g) {
        g.setFont(STATION_LABEL_FONT);
        String[] labels = {"Sự cố", "PAKH", "TB ảnh hưởng"};
        Color[] colors = {STATION_INCIDENT, STATION_PA, AFFECTED};
        int width = 0;
        for (String label : labels) width += 60 + g.getFontMetrics().stringWidth(label);
        int x = (1700 - width) / 2;
        for (int i = 0; i < labels.length; i++) {
            g.setColor(colors[i]);
            if (i < 2) g.fillRect(x, 547, 24, 18);
            else g.drawLine(x, 556, x + 24, 556);
            g.setColor(Color.BLACK);
            g.drawString(labels[i], x + 34, 566);
            x += 60 + g.getFontMetrics().stringWidth(labels[i]);
        }
    }

    private double maxOf(Double[][] values, int series) {
        double max = 0;
        for (Double[] row : values) if (row[series] != null && row[series] > max) max = row[series];
        return max;
    }

    private double stationScale(double max) {
        double target = Math.max(5, max * 2);
        double magnitude = Math.pow(10, Math.floor(Math.log10(target)));
        return Math.ceil(target / magnitude * 2) * magnitude / 2;
    }

    private double affectedAxisStep(double max) {
        double target = Math.max(1, max / 2);
        double magnitude = Math.pow(10, Math.floor(Math.log10(target)));
        for (double multiplier : new double[] {1, 2, 5, 10}) {
            if (target <= multiplier * magnitude) return multiplier * magnitude;
        }
        return 10 * magnitude;
    }

    private String grouped(double value) { return String.format(Locale.US, "%,.0f", value); }

    private String number(double value) { return String.format(Locale.ROOT, "%.0f", value); }

    private void centered(Graphics2D g, String text, int x, int y) {
        g.drawString(text, x - g.getFontMetrics().stringWidth(text) / 2, y);
    }

    private void validate(Double[][] values, boolean station) {
        if (values == null || values.length != 8) throw new IllegalArgumentException("Biểu đồ phải có 8 tuần");
        if (!station) return;
        for (Double[] row : values) {
            if (row == null || row.length != 3) throw new IllegalArgumentException("Mỗi tuần phải có 3 chỉ số trạm");
            for (Double value : row) if (value == null || !Double.isFinite(value) || value < 0) {
                throw new IllegalArgumentException("Chỉ số biểu đồ trạm phải là số không âm hợp lệ");
            }
        }
    }
}
