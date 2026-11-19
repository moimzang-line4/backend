package com.moimzang.domain.budget.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.lenient;

import com.moimzang.domain.attendance.entity.ScheduleRound;
import com.moimzang.domain.attendance.repository.ScheduleRoundRepository;
import com.moimzang.domain.budget.entity.LedgerEntry;
import com.moimzang.domain.budget.enums.LedgerAutoCategory;
import com.moimzang.domain.budget.enums.LedgerType;
import com.moimzang.domain.group.entity.ClubGroup;
import com.moimzang.domain.group.entity.GroupTerm;
import com.moimzang.domain.schedule.entity.Schedule;
import com.moimzang.global.storage.FileStorage;
import java.awt.image.BufferedImage;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.time.LocalDate;
import java.util.List;
import javax.imageio.ImageIO;
import org.apache.poi.ss.usermodel.Workbook;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.beans.BeanUtils;
import org.springframework.test.util.ReflectionTestUtils;

@ExtendWith(MockitoExtension.class)
class LedgerExportReceiptCollectorTest {

    private static final String BASE_URL_PREFIX = "http://localhost:8080/uploads/";

    @Mock
    private ScheduleRoundRepository scheduleRoundRepository;

    @Mock
    private FileStorage fileStorage;

    private LedgerExportReceiptCollector collector;

    private GroupTerm groupTerm;

    @BeforeEach
    void setUp() {
        collector = new LedgerExportReceiptCollector(scheduleRoundRepository, fileStorage);
        groupTerm = GroupTerm.create(ClubGroup.create("모임장"), "26기");
    }

    private Schedule schedule(long id) {
        Schedule schedule = BeanUtils.instantiateClass(Schedule.class);
        ReflectionTestUtils.setField(schedule, "id", id);
        return schedule;
    }

    private LedgerEntry autoEntry(LedgerAutoCategory category, Schedule schedule, String itemName, int amount) {
        return LedgerEntry.createAuto(groupTerm, schedule, category, itemName, amount, LocalDate.of(2026, 11, 1));
    }

    private ScheduleRound roundWithReceipt(int roundNo, Integer totalAmount, String imageUrl) {
        ScheduleRound round = BeanUtils.instantiateClass(ScheduleRound.class);
        ReflectionTestUtils.setField(round, "roundNo", roundNo);
        ReflectionTestUtils.setField(round, "totalAmount", totalAmount);
        ReflectionTestUtils.setField(round, "imageUrl", imageUrl);
        return round;
    }

    private byte[] pngBytes() throws IOException {
        BufferedImage image = new BufferedImage(10, 10, BufferedImage.TYPE_INT_RGB);
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        ImageIO.write(image, "png", out);
        return out.toByteArray();
    }

    @Test
    void collect_PAYMENT_INCOME은_영수증과_연결하지_않는다() {
        Schedule schedule = schedule(1L);
        LedgerEntry income = autoEntry(LedgerAutoCategory.PAYMENT_INCOME, schedule, "가을 공연 부원 납부", 50000);

        ReceiptLinkingResult result = collector.collect(List.of(income));

        assertThat(result.blocks()).isEmpty();
        assertThat(result.entryIndexToHeaderRow()).isEmpty();
    }

    @Test
    void collect_직접_입력_내역은_영수증과_연결하지_않는다() {
        LedgerEntry manual = LedgerEntry.create(groupTerm, LedgerType.EXPENSE, "간식비", 15000, LocalDate.of(2026, 11, 1));

        ReceiptLinkingResult result = collector.collect(List.of(manual));

        assertThat(result.blocks()).isEmpty();
        assertThat(result.entryIndexToHeaderRow()).isEmpty();
    }

    @Test
    void collect_영수증_있는_차수가_0개면_지출_자동기록도_빈칸이다() {
        Schedule schedule = schedule(1L);
        LedgerEntry expense = autoEntry(LedgerAutoCategory.RECEIPT_EXPENSE, schedule, "가을 공연 영수증 지출", 30000);
        given(scheduleRoundRepository.findOrderedRounds(1L)).willReturn(List.of(roundWithReceipt(1, null, null)));

        ReceiptLinkingResult result = collector.collect(List.of(expense));

        assertThat(result.blocks()).isEmpty();
        assertThat(result.entryIndexToHeaderRow()).isEmpty();
    }

    @Test
    void collect_같은_일정의_지출_2종이_같은_블록을_가리킨다() throws IOException {
        Schedule schedule = schedule(1L);
        LedgerEntry receiptExpense = autoEntry(LedgerAutoCategory.RECEIPT_EXPENSE, schedule, "가을 공연 영수증 지출", 40000);
        LedgerEntry subsidyExpense = autoEntry(LedgerAutoCategory.CLUB_SUBSIDY_EXPENSE, schedule, "가을 공연 동아리 지원금", 10000);
        String imageUrl = BASE_URL_PREFIX + "receipts/2026/11/a.png";
        given(scheduleRoundRepository.findOrderedRounds(1L)).willReturn(List.of(roundWithReceipt(1, 40000, imageUrl)));
        given(fileStorage.toUrl("")).willReturn(BASE_URL_PREFIX);
        given(fileStorage.open("receipts/2026/11/a.png")).willReturn(new ByteArrayInputStream(pngBytes()));

        ReceiptLinkingResult result = collector.collect(List.of(receiptExpense, subsidyExpense));

        assertThat(result.blocks()).hasSize(1);
        ReceiptBlock block = result.blocks().get(0);
        assertThat(block.headerLabel()).isEqualTo("가을 공연 영수증 지출");
        // 장부 시트 데이터는 6행(0-based)부터 시작하고, 이 그룹의 첫 행은 인덱스 0 → 6
        assertThat(block.backLinkLedgerRowIndex()).isEqualTo(6);
        assertThat(result.entryIndexToHeaderRow()).containsEntry(0, block.headerRowIndex())
                .containsEntry(1, block.headerRowIndex());
    }

    @Test
    void collect_한_일정에_차수가_여러개면_이미지를_모두_모아_하나의_블록에_담는다() throws IOException {
        Schedule schedule = schedule(1L);
        LedgerEntry receiptExpense = autoEntry(LedgerAutoCategory.RECEIPT_EXPENSE, schedule, "가을 공연 영수증 지출", 50000);
        String imageUrl1 = BASE_URL_PREFIX + "receipts/2026/11/a.png";
        String imageUrl2 = BASE_URL_PREFIX + "receipts/2026/11/b.png";
        given(scheduleRoundRepository.findOrderedRounds(1L)).willReturn(List.of(
                roundWithReceipt(1, 20000, imageUrl1),
                roundWithReceipt(2, 30000, imageUrl2)));
        given(fileStorage.toUrl("")).willReturn(BASE_URL_PREFIX);
        given(fileStorage.open("receipts/2026/11/a.png")).willReturn(new ByteArrayInputStream(pngBytes()));
        given(fileStorage.open("receipts/2026/11/b.png")).willReturn(new ByteArrayInputStream(pngBytes()));

        ReceiptLinkingResult result = collector.collect(List.of(receiptExpense));

        assertThat(result.blocks()).hasSize(1);
        ReceiptBlock block = result.blocks().get(0);
        assertThat(block.images()).hasSize(2);
        assertThat(block.images()).noneMatch(ReceiptImageData::isFailed);
    }

    @Test
    void collect_이미지가_없는_영수증_차수는_실패로_표시되지만_블록에는_포함된다() {
        Schedule schedule = schedule(1L);
        LedgerEntry receiptExpense = autoEntry(LedgerAutoCategory.RECEIPT_EXPENSE, schedule, "가을 공연 영수증 지출", 30000);
        given(scheduleRoundRepository.findOrderedRounds(1L)).willReturn(List.of(roundWithReceipt(1, 30000, null)));

        ReceiptLinkingResult result = collector.collect(List.of(receiptExpense));

        assertThat(result.blocks()).hasSize(1);
        assertThat(result.blocks().get(0).images()).hasSize(1);
        assertThat(result.blocks().get(0).images().get(0).isFailed()).isTrue();
    }

    @Test
    void collect_저장소_URL_접두사가_다르면_로딩_실패로_처리한다() {
        Schedule schedule = schedule(1L);
        LedgerEntry receiptExpense = autoEntry(LedgerAutoCategory.RECEIPT_EXPENSE, schedule, "가을 공연 영수증 지출", 30000);
        given(scheduleRoundRepository.findOrderedRounds(1L)).willReturn(
                List.of(roundWithReceipt(1, 30000, "https://other-host/x.png")));
        given(fileStorage.toUrl("")).willReturn(BASE_URL_PREFIX);

        ReceiptLinkingResult result = collector.collect(List.of(receiptExpense));

        assertThat(result.blocks().get(0).images().get(0).isFailed()).isTrue();
    }

    @Test
    void collect_webp처럼_POI가_지원하지_않는_포맷은_로딩_실패로_처리한다() {
        Schedule schedule = schedule(1L);
        LedgerEntry receiptExpense = autoEntry(LedgerAutoCategory.RECEIPT_EXPENSE, schedule, "가을 공연 영수증 지출", 30000);
        String imageUrl = BASE_URL_PREFIX + "receipts/2026/11/a.webp";
        given(scheduleRoundRepository.findOrderedRounds(1L)).willReturn(List.of(roundWithReceipt(1, 30000, imageUrl)));
        given(fileStorage.toUrl("")).willReturn(BASE_URL_PREFIX);

        ReceiptLinkingResult result = collector.collect(List.of(receiptExpense));

        assertThat(result.blocks().get(0).images().get(0).isFailed()).isTrue();
    }

    @Test
    void collect_저장소에서_파일을_읽지_못하면_로딩_실패로_처리한다() throws IOException {
        Schedule schedule = schedule(1L);
        LedgerEntry receiptExpense = autoEntry(LedgerAutoCategory.RECEIPT_EXPENSE, schedule, "가을 공연 영수증 지출", 30000);
        String imageUrl = BASE_URL_PREFIX + "receipts/2026/11/a.png";
        given(scheduleRoundRepository.findOrderedRounds(1L)).willReturn(List.of(roundWithReceipt(1, 30000, imageUrl)));
        given(fileStorage.toUrl("")).willReturn(BASE_URL_PREFIX);
        InputStream broken = new InputStream() {
            @Override
            public int read() throws IOException {
                throw new IOException("파일 없음");
            }
        };
        given(fileStorage.open("receipts/2026/11/a.png")).willReturn(broken);

        ReceiptLinkingResult result = collector.collect(List.of(receiptExpense));

        assertThat(result.blocks().get(0).images().get(0).isFailed()).isTrue();
    }

    @Test
    void collect_블록_순서는_장부_시트에서_먼저_나오는_내역_순서를_따른다() throws IOException {
        Schedule laterSchedule = schedule(1L);
        Schedule earlierSchedule = schedule(2L);
        LedgerEntry laterEntry = autoEntry(LedgerAutoCategory.RECEIPT_EXPENSE, laterSchedule, "나중 일정", 10000);
        LedgerEntry earlierEntry = autoEntry(LedgerAutoCategory.RECEIPT_EXPENSE, earlierSchedule, "먼저 일정", 20000);
        String imageUrl = BASE_URL_PREFIX + "receipts/2026/11/a.png";
        lenient().when(scheduleRoundRepository.findOrderedRounds(1L)).thenReturn(List.of(roundWithReceipt(1, 10000, imageUrl)));
        lenient().when(scheduleRoundRepository.findOrderedRounds(2L)).thenReturn(List.of(roundWithReceipt(1, 20000, imageUrl)));
        given(fileStorage.toUrl("")).willReturn(BASE_URL_PREFIX);
        given(fileStorage.open("receipts/2026/11/a.png"))
                .willReturn(new ByteArrayInputStream(pngBytes()), new ByteArrayInputStream(pngBytes()));

        // entries 순서(= 장부 시트 순서)는 "나중 일정" 먼저, "먼저 일정" 나중
        ReceiptLinkingResult result = collector.collect(List.of(laterEntry, earlierEntry));

        assertThat(result.blocks()).hasSize(2);
        assertThat(result.blocks().get(0).headerLabel()).isEqualTo("나중 일정");
        assertThat(result.blocks().get(0).headerRowIndex()).isLessThan(result.blocks().get(1).headerRowIndex());
    }
}
