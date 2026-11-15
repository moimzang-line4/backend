package com.moimzang.domain.budget.service;

import java.util.List;
import org.apache.poi.common.usermodel.HyperlinkType;
import org.apache.poi.ss.usermodel.Cell;
import org.apache.poi.ss.usermodel.ClientAnchor;
import org.apache.poi.ss.usermodel.Drawing;
import org.apache.poi.ss.usermodel.Hyperlink;
import org.apache.poi.ss.usermodel.Row;
import org.apache.poi.ss.usermodel.Sheet;
import org.apache.poi.ss.usermodel.Workbook;
import org.apache.poi.ss.util.CellRangeAddress;

// 장부 엑셀 다운로드(API-075)의 "영수증" 시트를 쓴다. 블록 1개는 한 일정의 지출 자동기록이
// 공유하는 영수증 이미지 묶음이고(LedgerExportReceiptCollector가 이미 레이아웃을 계산해 둔다),
// 이 클래스는 그 계산 결과를 셀·그림으로 옮기기만 한다
class ReceiptSheetWriter {

    private static final int[] COLUMN_WIDTHS = {46, 24};
    // 이미지 1장을 460x540px 박스 안에 비율을 유지한 채 채운다
    private static final int MAX_IMAGE_WIDTH_PX = 460;
    private static final int MAX_IMAGE_HEIGHT_PX = 540;
    private static final int EMU_PER_PIXEL = 9525;
    private static final int ROWS_PER_IMAGE = 20;

    void write(Sheet sheet, LedgerExportStyles styles, List<ReceiptBlock> blocks, String ledgerSheetName) {
        for (int col = 0; col < COLUMN_WIDTHS.length; col++) {
            sheet.setColumnWidth(col, COLUMN_WIDTHS[col] * 256);
        }

        writeTitle(sheet, styles);
        for (ReceiptBlock block : blocks) {
            writeBlock(sheet, styles, block, ledgerSheetName);
        }
    }

    private void writeTitle(Sheet sheet, LedgerExportStyles styles) {
        Row row = sheet.createRow(0);
        row.setHeightInPoints(42.75f);
        sheet.addMergedRegion(new CellRangeAddress(0, 0, 0, 1));
        for (int col = 0; col < 2; col++) {
            row.createCell(col).setCellStyle(styles.title());
        }
        row.getCell(0).setCellValue("영수증 모음");
    }

    private void writeBlock(Sheet sheet, LedgerExportStyles styles, ReceiptBlock block, String ledgerSheetName) {
        Workbook workbook = sheet.getWorkbook();
        Row headerRow = sheet.createRow(block.headerRowIndex());
        headerRow.setHeightInPoints(33.25f);

        Cell labelCell = headerRow.createCell(0);
        labelCell.setCellStyle(styles.receiptHeader());
        labelCell.setCellValue(block.headerLabel());

        Cell backLinkCell = headerRow.createCell(1);
        backLinkCell.setCellStyle(styles.linkCenter());
        backLinkCell.setCellValue("장부로 돌아가기");
        Hyperlink link = workbook.getCreationHelper().createHyperlink(HyperlinkType.DOCUMENT);
        link.setAddress("'%s'!F%d".formatted(ledgerSheetName, block.backLinkLedgerRowIndex() + 1));
        backLinkCell.setHyperlink(link);

        List<ReceiptImageData> images = block.images();
        for (int i = 0; i < images.size(); i++) {
            int imageStartRow = block.headerRowIndex() + 1 + i * ROWS_PER_IMAGE;
            writeImage(sheet, styles, images.get(i), imageStartRow);
        }
    }

    private void writeImage(Sheet sheet, LedgerExportStyles styles, ReceiptImageData image, int rowIndex) {
        if (image.isFailed()) {
            Row row = sheet.getRow(rowIndex);
            if (row == null) {
                row = sheet.createRow(rowIndex);
            }
            Cell cell = row.createCell(0);
            cell.setCellStyle(styles.plainText());
            cell.setCellValue("이미지를 불러올 수 없습니다");
            return;
        }

        double scale = Math.min(
                MAX_IMAGE_WIDTH_PX / (double) image.widthPx(),
                MAX_IMAGE_HEIGHT_PX / (double) image.heightPx());
        int targetWidthPx = (int) Math.round(image.widthPx() * scale);
        int targetHeightPx = (int) Math.round(image.heightPx() * scale);

        Workbook workbook = sheet.getWorkbook();
        int pictureIndex = workbook.addPicture(image.bytes(), image.pictureType());
        Drawing<?> drawing = sheet.getDrawingPatriarch() != null
                ? sheet.getDrawingPatriarch()
                : sheet.createDrawingPatriarch();

        ClientAnchor anchor = workbook.getCreationHelper().createClientAnchor();
        anchor.setCol1(0);
        anchor.setRow1(rowIndex);
        anchor.setCol2(0);
        anchor.setRow2(rowIndex);
        anchor.setDx1(0);
        anchor.setDy1(0);
        anchor.setDx2(targetWidthPx * EMU_PER_PIXEL);
        anchor.setDy2(targetHeightPx * EMU_PER_PIXEL);

        drawing.createPicture(anchor, pictureIndex);
    }
}
