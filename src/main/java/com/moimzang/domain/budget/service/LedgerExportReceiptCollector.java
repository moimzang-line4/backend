package com.moimzang.domain.budget.service;

import com.moimzang.domain.attendance.entity.ScheduleRound;
import com.moimzang.domain.attendance.repository.ScheduleRoundRepository;
import com.moimzang.domain.budget.entity.LedgerEntry;
import com.moimzang.domain.budget.enums.LedgerAutoCategory;
import com.moimzang.global.storage.FileStorage;
import java.awt.image.BufferedImage;
import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import javax.imageio.ImageIO;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.poi.ss.usermodel.Workbook;
import org.springframework.stereotype.Component;

// 장부 엑셀 다운로드(API-075)의 영수증 시트 데이터를 만든다. ledger_entry는 일정 단위로만
// 자동기록을 쌓지만(decisions #63) 영수증 이미지는 차수(ScheduleRound) 단위라, 한 지출 자동기록이
// 그 일정의 차수 여러 개에 걸친 영수증 이미지 전부와 연결될 수 있다. POI 바이트 조작은 전혀 하지
// 않고(그건 LedgerExportExcelGenerator 쪽 책임), 어떤 내역이 어떤 이미지들과 연결되는지만 계산한다
@Slf4j
@Component
@RequiredArgsConstructor
public class LedgerExportReceiptCollector {

    // 영수증 시트 블록이 시작되는 첫 행(0-based, 1-based로는 3행)
    private static final int FIRST_BLOCK_ROW_INDEX = 2;
    // 영수증 1장이 차지하는 행 수(헤더 바로 아래부터). "약 20행 차지"(스펙)를 그대로 쓴다
    private static final int ROWS_PER_IMAGE = 20;
    // 다음 블록 헤더까지의 여백 — 영수증 1장짜리 블록의 전체 간격(3→29 = 26행)과 맞춘다
    private static final int BLOCK_GAP_ROWS = 5;

    // F열 링크 대상은 지출 2종뿐이다(decisions 확정) — PAYMENT_INCOME과 직접 입력은 영수증과
    // 연결하지 않는다
    private static final Set<LedgerAutoCategory> RECEIPT_LINKED_CATEGORIES = Set.of(
            LedgerAutoCategory.RECEIPT_EXPENSE,
            LedgerAutoCategory.CLUB_SUBSIDY_EXPENSE);

    private static final Set<String> SUPPORTED_EXTENSIONS = Set.of("png", "jpg", "jpeg");

    private final ScheduleRoundRepository scheduleRoundRepository;
    private final FileStorage fileStorage;

    public ReceiptLinkingResult collect(List<LedgerEntry> entries) {
        // 일정별로 "이 일정을 가리키는 지출 자동기록이 장부 시트 몇 번째 행에 있는지"를 순서대로 모은다.
        // LinkedHashMap을 써서 첫 등장 순서(=entries 순서, 이미 entryDate asc) 그대로 블록 순서가 된다
        Map<Long, List<Integer>> entryIndicesBySchedule = new LinkedHashMap<>();
        for (int i = 0; i < entries.size(); i++) {
            LedgerEntry entry = entries.get(i);
            if (entry.getSchedule() == null || !RECEIPT_LINKED_CATEGORIES.contains(entry.getAutoCategory())) {
                continue;
            }
            entryIndicesBySchedule.computeIfAbsent(entry.getSchedule().getId(), key -> new ArrayList<>()).add(i);
        }

        List<ReceiptBlock> blocks = new ArrayList<>();
        Map<Integer, Integer> entryIndexToHeaderRow = new LinkedHashMap<>();
        int nextHeaderRowIndex = FIRST_BLOCK_ROW_INDEX;

        for (Map.Entry<Long, List<Integer>> group : entryIndicesBySchedule.entrySet()) {
            List<ReceiptImageData> images = loadImagesForSchedule(group.getKey());
            if (images.isEmpty()) {
                // 그 일정에 영수증이 등록된 차수가 하나도 없으면(decisions 확정) 블록을 만들지 않고
                // 이 그룹의 모든 내역은 F열이 빈칸으로 남는다
                continue;
            }

            List<Integer> entryIndices = group.getValue();
            int firstEntryIndex = entryIndices.get(0);
            int backLinkRowIndex = LedgerExportLayout.LEDGER_DATA_START_ROW_INDEX + firstEntryIndex;
            String headerLabel = entries.get(firstEntryIndex).getItemName();
            int rowSpan = 1 + ROWS_PER_IMAGE * images.size() + BLOCK_GAP_ROWS;

            blocks.add(new ReceiptBlock(nextHeaderRowIndex, headerLabel, images, backLinkRowIndex, rowSpan));
            for (int entryIndex : entryIndices) {
                entryIndexToHeaderRow.put(entryIndex, nextHeaderRowIndex);
            }

            nextHeaderRowIndex += rowSpan;
        }

        return new ReceiptLinkingResult(blocks, entryIndexToHeaderRow);
    }

    // 그 일정의 차수 중 영수증이 등록된(hasReceipt) 차수만 roundNo 순서대로 모아 이미지를 읽는다.
    // 영수증은 등록됐지만 이미지가 없거나(사진 없이 직접 등록) 로딩에 실패해도, "영수증 등록 여부"
    // 자체는 totalAmount로 판단하므로 그 차수는 목록에 포함되고 실패 표시로만 남는다
    private List<ReceiptImageData> loadImagesForSchedule(Long scheduleId) {
        List<ReceiptImageData> images = new ArrayList<>();
        for (ScheduleRound round : scheduleRoundRepository.findOrderedRounds(scheduleId)) {
            if (!round.hasReceipt()) {
                continue;
            }
            images.add(loadImage(round));
        }
        return images;
    }

    private ReceiptImageData loadImage(ScheduleRound round) {
        String imageUrl = round.getImageUrl();
        if (imageUrl == null) {
            return ReceiptImageData.failed();
        }

        // 저장소에 읽어오려면 키가 필요한데 round에는 URL만 남아 있다. LocalFileStorage·S3FileStorage
        // 둘 다 toUrl(key) = baseUrl + "/" + key 형식이라(코드로 확인함), toUrl("")로 baseUrl 접두사를
        // 구해 역으로 키를 복원한다 — FileStorage 인터페이스·구현체는 건드리지 않는다
        String baseUrlPrefix = fileStorage.toUrl("");
        if (!imageUrl.startsWith(baseUrlPrefix)) {
            log.warn("[장부 엑셀 다운로드] 영수증 이미지 URL 형식이 예상과 달라 불러올 수 없습니다. imageUrl = {}", imageUrl);
            return ReceiptImageData.failed();
        }
        String key = imageUrl.substring(baseUrlPrefix.length());

        String extension = extractExtension(key);
        if (!SUPPORTED_EXTENSIONS.contains(extension)) {
            // webp 등 POI가 그림으로 심지 못하는 포맷. 새 의존성을 추가하지 않기로 했으므로 실패로 둔다
            log.warn("[장부 엑셀 다운로드] 엑셀에 삽입할 수 없는 이미지 형식입니다. key = {}, extension = {}", key, extension);
            return ReceiptImageData.failed();
        }

        byte[] bytes;
        try (InputStream in = fileStorage.open(key)) {
            bytes = in.readAllBytes();
        } catch (IOException | RuntimeException e) {
            log.warn("[장부 엑셀 다운로드] 영수증 이미지를 불러오지 못했습니다. key = {}", key, e);
            return ReceiptImageData.failed();
        }

        BufferedImage image;
        try {
            image = ImageIO.read(new ByteArrayInputStream(bytes));
        } catch (IOException e) {
            image = null;
        }
        if (image == null) {
            log.warn("[장부 엑셀 다운로드] 영수증 이미지를 디코딩하지 못했습니다. key = {}", key);
            return ReceiptImageData.failed();
        }

        int pictureType = "png".equals(extension) ? Workbook.PICTURE_TYPE_PNG : Workbook.PICTURE_TYPE_JPEG;
        return new ReceiptImageData(bytes, pictureType, image.getWidth(), image.getHeight());
    }

    private String extractExtension(String key) {
        int dot = key.lastIndexOf('.');
        if (dot < 0 || dot == key.length() - 1) {
            return "";
        }
        return key.substring(dot + 1).toLowerCase();
    }
}
