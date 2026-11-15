package com.moimzang.domain.budget.service;

import java.awt.Color;
import org.apache.poi.ss.usermodel.BorderStyle;
import org.apache.poi.ss.usermodel.CellStyle;
import org.apache.poi.ss.usermodel.DataFormat;
import org.apache.poi.ss.usermodel.FillPatternType;
import org.apache.poi.ss.usermodel.Font;
import org.apache.poi.ss.usermodel.HorizontalAlignment;
import org.apache.poi.ss.usermodel.VerticalAlignment;
import org.apache.poi.xssf.usermodel.XSSFCellStyle;
import org.apache.poi.xssf.usermodel.XSSFColor;
import org.apache.poi.xssf.usermodel.XSSFFont;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;

// 장부 엑셀 다운로드(API-075)의 셀 스타일을 workbook당 한 번만 만들어 재사용한다. 공통 규칙
// (글꼴 맑은 고딕, 금액 서식 "₩"#,##0, 얇은 테두리)을 이 클래스 하나에 모아, LedgerSheetWriter·
// ReceiptSheetWriter는 색상·폰트 숫자를 직접 다루지 않고 이 스타일만 가져다 쓴다
class LedgerExportStyles {

    private static final String FONT_NAME = "맑은 고딕";
    private static final String AMOUNT_FORMAT = "\"₩\"#,##0";

    private final XSSFWorkbook workbook;
    private final DataFormat dataFormat;

    private final CellStyle title;
    private final CellStyle summaryLabelNormal;
    private final CellStyle summaryLabelHighlight;
    private final CellStyle summaryValueNormal;
    private final CellStyle summaryValueHighlight;
    private final CellStyle headerRow;
    private final CellStyle dataDate;
    private final CellStyle dataContent;
    private final CellStyle dataIncome;
    private final CellStyle dataExpense;
    private final CellStyle dataBalance;
    private final CellStyle linkCenter;
    private final CellStyle receiptHeader;
    private final CellStyle plainText;

    LedgerExportStyles(XSSFWorkbook workbook) {
        this.workbook = workbook;
        this.dataFormat = workbook.createDataFormat();

        this.title = buildTitle();
        this.summaryLabelNormal = buildSummaryLabel("#EFF6EA", "#335344");
        this.summaryLabelHighlight = buildSummaryLabel("#C8E2B7", "#000000");
        this.summaryValueNormal = buildSummaryValue("#EFF6EA", "#193B2A");
        this.summaryValueHighlight = buildSummaryValue("#EFF7EA", "#000000");
        this.headerRow = buildHeaderRow();
        this.dataDate = buildDataText(HorizontalAlignment.LEFT, "#222222", false);
        this.dataContent = buildDataText(HorizontalAlignment.LEFT, "#222222", false);
        this.dataIncome = buildAmount(HorizontalAlignment.RIGHT, "#2166C5", false);
        this.dataExpense = buildAmount(HorizontalAlignment.RIGHT, "#B8322A", false);
        this.dataBalance = buildAmount(HorizontalAlignment.RIGHT, "#222222", true);
        this.linkCenter = buildLink();
        this.receiptHeader = buildReceiptHeader();
        this.plainText = buildDataText(HorizontalAlignment.LEFT, "#222222", false);
    }

    CellStyle title() {
        return title;
    }

    CellStyle summaryLabelNormal() {
        return summaryLabelNormal;
    }

    CellStyle summaryLabelHighlight() {
        return summaryLabelHighlight;
    }

    CellStyle summaryValueNormal() {
        return summaryValueNormal;
    }

    CellStyle summaryValueHighlight() {
        return summaryValueHighlight;
    }

    CellStyle headerRow() {
        return headerRow;
    }

    CellStyle dataDate() {
        return dataDate;
    }

    CellStyle dataContent() {
        return dataContent;
    }

    CellStyle dataIncome() {
        return dataIncome;
    }

    CellStyle dataExpense() {
        return dataExpense;
    }

    CellStyle dataBalance() {
        return dataBalance;
    }

    CellStyle linkCenter() {
        return linkCenter;
    }

    CellStyle receiptHeader() {
        return receiptHeader;
    }

    CellStyle plainText() {
        return plainText;
    }

    private CellStyle buildTitle() {
        XSSFCellStyle style = workbook.createCellStyle();
        XSSFFont font = baseFont();
        font.setFontHeightInPoints((short) 19);
        font.setBold(true);
        font.setColor(color("#173327"));
        style.setFont(font);
        style.setFillForegroundColor(color("#C8E2B7"));
        style.setFillPattern(FillPatternType.SOLID_FOREGROUND);
        style.setAlignment(HorizontalAlignment.LEFT);
        style.setVerticalAlignment(VerticalAlignment.CENTER);
        applyThinBorder(style);
        return style;
    }

    private CellStyle buildSummaryLabel(String bgHex, String fontHex) {
        XSSFCellStyle style = workbook.createCellStyle();
        XSSFFont font = baseFont();
        font.setFontHeightInPoints((short) 11);
        font.setBold(true);
        font.setColor(color(fontHex));
        style.setFont(font);
        style.setFillForegroundColor(color(bgHex));
        style.setFillPattern(FillPatternType.SOLID_FOREGROUND);
        style.setAlignment(HorizontalAlignment.CENTER);
        style.setVerticalAlignment(VerticalAlignment.CENTER);
        applyThinBorder(style);
        return style;
    }

    private CellStyle buildSummaryValue(String bgHex, String fontHex) {
        XSSFCellStyle style = workbook.createCellStyle();
        XSSFFont font = baseFont();
        font.setFontHeightInPoints((short) 18);
        font.setBold(true);
        font.setColor(color(fontHex));
        style.setFont(font);
        style.setFillForegroundColor(color(bgHex));
        style.setFillPattern(FillPatternType.SOLID_FOREGROUND);
        style.setAlignment(HorizontalAlignment.CENTER);
        style.setVerticalAlignment(VerticalAlignment.CENTER);
        style.setDataFormat(dataFormat.getFormat(AMOUNT_FORMAT));
        applyThinBorder(style);
        return style;
    }

    private CellStyle buildHeaderRow() {
        XSSFCellStyle style = workbook.createCellStyle();
        XSSFFont font = baseFont();
        font.setBold(true);
        font.setColor(color("#182920"));
        style.setFont(font);
        style.setFillForegroundColor(color("#F1F3F1"));
        style.setFillPattern(FillPatternType.SOLID_FOREGROUND);
        style.setAlignment(HorizontalAlignment.CENTER);
        style.setVerticalAlignment(VerticalAlignment.CENTER);
        applyThinBorder(style);
        return style;
    }

    private CellStyle buildDataText(HorizontalAlignment alignment, String fontHex, boolean bold) {
        XSSFCellStyle style = workbook.createCellStyle();
        XSSFFont font = baseFont();
        font.setBold(bold);
        font.setColor(color(fontHex));
        style.setFont(font);
        style.setAlignment(alignment);
        style.setVerticalAlignment(VerticalAlignment.CENTER);
        applyThinBorder(style);
        return style;
    }

    private CellStyle buildAmount(HorizontalAlignment alignment, String fontHex, boolean bold) {
        XSSFCellStyle style = (XSSFCellStyle) buildDataText(alignment, fontHex, bold);
        style.setDataFormat(dataFormat.getFormat(AMOUNT_FORMAT));
        return style;
    }

    private CellStyle buildLink() {
        XSSFCellStyle style = workbook.createCellStyle();
        XSSFFont font = baseFont();
        font.setColor(color("#2166C5"));
        font.setUnderline(Font.U_SINGLE);
        style.setFont(font);
        style.setAlignment(HorizontalAlignment.CENTER);
        style.setVerticalAlignment(VerticalAlignment.CENTER);
        applyThinBorder(style);
        return style;
    }

    private CellStyle buildReceiptHeader() {
        XSSFCellStyle style = workbook.createCellStyle();
        XSSFFont font = baseFont();
        font.setBold(true);
        style.setFont(font);
        style.setFillForegroundColor(color("#F1F3F1"));
        style.setFillPattern(FillPatternType.SOLID_FOREGROUND);
        style.setAlignment(HorizontalAlignment.CENTER);
        style.setVerticalAlignment(VerticalAlignment.CENTER);
        applyThinBorder(style);
        return style;
    }

    private XSSFFont baseFont() {
        XSSFFont font = workbook.createFont();
        font.setFontName(FONT_NAME);
        return font;
    }

    private void applyThinBorder(XSSFCellStyle style) {
        style.setBorderTop(BorderStyle.THIN);
        style.setBorderBottom(BorderStyle.THIN);
        style.setBorderLeft(BorderStyle.THIN);
        style.setBorderRight(BorderStyle.THIN);
    }

    private XSSFColor color(String hex) {
        return new XSSFColor(Color.decode(hex), null);
    }
}
