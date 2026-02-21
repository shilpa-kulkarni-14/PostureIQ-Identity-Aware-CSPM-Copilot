package com.cspm.service;

import com.cspm.model.Finding;
import com.cspm.model.ScanResult;
import com.lowagie.text.*;
import com.lowagie.text.Font;
import com.lowagie.text.pdf.PdfPCell;
import com.lowagie.text.pdf.PdfPTable;
import com.lowagie.text.pdf.PdfWriter;
import org.springframework.stereotype.Service;

import java.awt.Color;
import java.io.ByteArrayOutputStream;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.List;

@Service
public class ReportService {

    private static final Font TITLE_FONT = new Font(Font.HELVETICA, 22, Font.BOLD, new Color(26, 26, 46));
    private static final Font HEADER_FONT = new Font(Font.HELVETICA, 14, Font.BOLD, new Color(26, 26, 46));
    private static final Font NORMAL_FONT = new Font(Font.HELVETICA, 10, Font.NORMAL, Color.BLACK);
    private static final Font SMALL_FONT = new Font(Font.HELVETICA, 9, Font.NORMAL, new Color(100, 100, 100));
    private static final Font TABLE_HEADER_FONT = new Font(Font.HELVETICA, 10, Font.BOLD, Color.WHITE);

    private static final Color HIGH_COLOR = new Color(211, 47, 47);
    private static final Color MEDIUM_COLOR = new Color(245, 124, 0);
    private static final Color LOW_COLOR = new Color(25, 118, 210);
    private static final Color TABLE_HEADER_BG = new Color(55, 71, 79);

    private static final DateTimeFormatter DATE_FORMATTER =
            DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss z").withZone(ZoneId.systemDefault());

    public byte[] generatePdfReport(ScanResult scanResult) {
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        Document document = new Document(PageSize.A4, 40, 40, 50, 40);

        try {
            PdfWriter.getInstance(document, out);
            document.open();

            addReportHeader(document, scanResult);
            addSeveritySummary(document, scanResult);
            addFindingsTable(document, scanResult.getFindings());
            addRemediationSection(document, scanResult.getFindings());

            document.close();
        } catch (DocumentException e) {
            throw new RuntimeException("Error generating PDF report", e);
        }

        return out.toByteArray();
    }

    private void addReportHeader(Document document, ScanResult scanResult) throws DocumentException {
        Paragraph title = new Paragraph("CSPM Security Scan Report", TITLE_FONT);
        title.setAlignment(Element.ALIGN_CENTER);
        title.setSpacingAfter(10);
        document.add(title);

        Paragraph scanInfo = new Paragraph();
        scanInfo.setAlignment(Element.ALIGN_CENTER);
        scanInfo.add(new Chunk("Scan ID: " + scanResult.getScanId(), SMALL_FONT));
        scanInfo.add(Chunk.NEWLINE);
        String dateStr = scanResult.getTimestamp() != null
                ? DATE_FORMATTER.format(scanResult.getTimestamp())
                : "N/A";
        scanInfo.add(new Chunk("Date: " + dateStr, SMALL_FONT));
        scanInfo.add(Chunk.NEWLINE);
        scanInfo.add(new Chunk("Status: " + scanResult.getStatus(), SMALL_FONT));
        scanInfo.setSpacingAfter(20);
        document.add(scanInfo);

        // Horizontal line
        PdfPTable line = new PdfPTable(1);
        line.setWidthPercentage(100);
        PdfPCell lineCell = new PdfPCell();
        lineCell.setBorderWidthBottom(1);
        lineCell.setBorderColorBottom(new Color(200, 200, 200));
        lineCell.setBorderWidthTop(0);
        lineCell.setBorderWidthLeft(0);
        lineCell.setBorderWidthRight(0);
        lineCell.setFixedHeight(1);
        line.addCell(lineCell);
        line.setSpacingAfter(15);
        document.add(line);
    }

    private void addSeveritySummary(Document document, ScanResult scanResult) throws DocumentException {
        Paragraph summaryTitle = new Paragraph("Executive Summary", HEADER_FONT);
        summaryTitle.setSpacingAfter(10);
        document.add(summaryTitle);

        PdfPTable summaryTable = new PdfPTable(4);
        summaryTable.setWidthPercentage(100);
        summaryTable.setWidths(new float[]{1, 1, 1, 1});

        addSeverityCell(summaryTable, "HIGH", scanResult.getHighSeverity(), HIGH_COLOR);
        addSeverityCell(summaryTable, "MEDIUM", scanResult.getMediumSeverity(), MEDIUM_COLOR);
        addSeverityCell(summaryTable, "LOW", scanResult.getLowSeverity(), LOW_COLOR);
        addSeverityCell(summaryTable, "TOTAL", scanResult.getTotalFindings(), new Color(97, 97, 97));

        summaryTable.setSpacingAfter(20);
        document.add(summaryTable);
    }

    private void addSeverityCell(PdfPTable table, String label, int count, Color color) {
        PdfPCell cell = new PdfPCell();
        cell.setBorder(0);
        cell.setHorizontalAlignment(Element.ALIGN_CENTER);
        cell.setPadding(10);

        Paragraph content = new Paragraph();
        content.setAlignment(Element.ALIGN_CENTER);
        Font countFont = new Font(Font.HELVETICA, 24, Font.BOLD, color);
        Font labelFont = new Font(Font.HELVETICA, 10, Font.NORMAL, color);
        content.add(new Chunk(String.valueOf(count), countFont));
        content.add(Chunk.NEWLINE);
        content.add(new Chunk(label, labelFont));

        cell.addElement(content);
        table.addCell(cell);
    }

    private void addFindingsTable(Document document, List<Finding> findings) throws DocumentException {
        Paragraph tableTitle = new Paragraph("Findings Detail", HEADER_FONT);
        tableTitle.setSpacingAfter(10);
        document.add(tableTitle);

        PdfPTable table = new PdfPTable(5);
        table.setWidthPercentage(100);
        table.setWidths(new float[]{1.2f, 1.5f, 2.5f, 3f, 3.5f});

        // Table headers
        String[] headers = {"Severity", "Resource Type", "Resource ID", "Title", "Description"};
        for (String header : headers) {
            PdfPCell cell = new PdfPCell(new Phrase(header, TABLE_HEADER_FONT));
            cell.setBackgroundColor(TABLE_HEADER_BG);
            cell.setPadding(6);
            cell.setHorizontalAlignment(Element.ALIGN_CENTER);
            table.addCell(cell);
        }

        // Table rows
        for (Finding finding : findings) {
            // Severity cell with color
            PdfPCell severityCell = new PdfPCell(new Phrase(finding.getSeverity(),
                    new Font(Font.HELVETICA, 9, Font.BOLD, getSeverityColor(finding.getSeverity()))));
            severityCell.setPadding(5);
            severityCell.setHorizontalAlignment(Element.ALIGN_CENTER);
            severityCell.setBackgroundColor(getSeverityBgColor(finding.getSeverity()));
            table.addCell(severityCell);

            addTableCell(table, finding.getResourceType());
            addTableCell(table, finding.getResourceId());
            addTableCell(table, finding.getTitle());
            addTableCell(table, finding.getDescription());
        }

        table.setSpacingAfter(20);
        document.add(table);
    }

    private void addTableCell(PdfPTable table, String text) {
        PdfPCell cell = new PdfPCell(new Phrase(text != null ? text : "", NORMAL_FONT));
        cell.setPadding(5);
        table.addCell(cell);
    }

    private void addRemediationSection(Document document, List<Finding> findings) throws DocumentException {
        Paragraph title = new Paragraph("Remediation Recommendations", HEADER_FONT);
        title.setSpacingAfter(10);
        document.add(title);

        for (Finding finding : findings) {
            Paragraph findingTitle = new Paragraph(
                    finding.getTitle(),
                    new Font(Font.HELVETICA, 11, Font.BOLD, getSeverityColor(finding.getSeverity()))
            );
            findingTitle.setSpacingAfter(3);
            document.add(findingTitle);

            Paragraph resource = new Paragraph(
                    "Resource: " + finding.getResourceType() + " - " + finding.getResourceId(),
                    SMALL_FONT
            );
            resource.setSpacingAfter(3);
            document.add(resource);

            String remediationText = finding.getRemediation() != null
                    ? finding.getRemediation()
                    : "No specific remediation available. Review the finding description and apply security best practices.";

            Paragraph remediation = new Paragraph(remediationText, NORMAL_FONT);
            remediation.setSpacingAfter(12);
            document.add(remediation);
        }
    }

    private Color getSeverityColor(String severity) {
        return switch (severity) {
            case "HIGH" -> HIGH_COLOR;
            case "MEDIUM" -> MEDIUM_COLOR;
            case "LOW" -> LOW_COLOR;
            default -> Color.GRAY;
        };
    }

    private Color getSeverityBgColor(String severity) {
        return switch (severity) {
            case "HIGH" -> new Color(255, 235, 238);
            case "MEDIUM" -> new Color(255, 243, 224);
            case "LOW" -> new Color(227, 242, 253);
            default -> Color.WHITE;
        };
    }
}
