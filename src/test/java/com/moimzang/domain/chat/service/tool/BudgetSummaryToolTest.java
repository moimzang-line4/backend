package com.moimzang.domain.chat.service.tool;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.BDDMockito.given;

import com.moimzang.domain.attendance.entity.ScheduleRound;
import com.moimzang.domain.budget.entity.LedgerEntry;
import com.moimzang.domain.budget.enums.LedgerType;
import com.moimzang.domain.budget.repository.LedgerEntryRepository;
import com.moimzang.domain.budget.repository.LedgerTypeTotal;
import java.time.LocalDate;
import java.util.List;
import java.util.stream.IntStream;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.beans.BeanUtils;
import org.springframework.test.util.ReflectionTestUtils;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

@ExtendWith(MockitoExtension.class)
class BudgetSummaryToolTest {

    private final ObjectMapper objectMapper = new ObjectMapper();

    @Mock
    private LedgerEntryRepository ledgerEntryRepository;

    @InjectMocks
    private BudgetSummaryTool tool;

    @Test
    void execute_수입과_지출과_잔액을_집계해_내려준다() throws Exception {
        given(ledgerEntryRepository.aggregateByType(1L)).willReturn(List.of(
                new LedgerTypeTotal(LedgerType.INCOME, 1200000L, 3L),
                new LedgerTypeTotal(LedgerType.EXPENSE, 450000L, 5L)));

        JsonNode result = execute();

        assertThat(result.get("totalIncome").asLong()).isEqualTo(1200000L);
        assertThat(result.get("totalExpense").asLong()).isEqualTo(450000L);
        // 잔액을 따로 내려줘야 AI가 직접 빼지 않는다
        assertThat(result.get("balance").asLong()).isEqualTo(750000L);
        assertThat(result.get("entryCount").asLong()).isEqualTo(8L);
    }

    @Test
    void execute_지출이_하나도_없으면_0으로_채운다() throws Exception {
        given(ledgerEntryRepository.aggregateByType(1L))
                .willReturn(List.of(new LedgerTypeTotal(LedgerType.INCOME, 1200000L, 3L)));

        JsonNode result = execute();

        // 한쪽 유형이 없으면 group by 결과에 행 자체가 없다
        assertThat(result.get("totalExpense").asLong()).isZero();
        assertThat(result.get("balance").asLong()).isEqualTo(1200000L);
    }

    @Test
    void execute_장부가_비어_있으면_전부_0이다() throws Exception {
        given(ledgerEntryRepository.aggregateByType(1L)).willReturn(List.of());

        JsonNode result = execute();

        assertThat(result.get("totalIncome").asLong()).isZero();
        assertThat(result.get("totalExpense").asLong()).isZero();
        assertThat(result.get("balance").asLong()).isZero();
        assertThat(result.get("entries")).isEmpty();
    }

    @Test
    void execute_장부_항목을_유형과_금액과_거래일로_내려준다() throws Exception {
        given(ledgerEntryRepository.aggregateByType(1L))
                .willReturn(List.of(new LedgerTypeTotal(LedgerType.EXPENSE, 320000L, 1L)));
        given(ledgerEntryRepository.findForAssistant(eq(1L), anyInt()))
                .willReturn(List.of(entry(800L, LedgerType.EXPENSE, "가을공연 뒤풀이", 320000, true)));

        JsonNode entry = execute().get("entries").get(0);

        assertThat(entry.get("entryId").asLong()).isEqualTo(800L);
        assertThat(entry.get("type").asText()).isEqualTo("EXPENSE");
        assertThat(entry.get("itemName").asText()).isEqualTo("가을공연 뒤풀이");
        assertThat(entry.get("amount").asInt()).isEqualTo(320000);
        assertThat(entry.get("entryDate").asText()).isEqualTo("2026-11-21");
        // 차수에서 자동 기록된 항목인지 알면 "이거 내가 적은 거야?"류 질문에 답할 수 있다
        assertThat(entry.get("fromSchedule").asBoolean()).isTrue();
    }

    @Test
    void execute_항목이_상한을_넘으면_잘라내도_합계는_전체_기준이다() throws Exception {
        List<LedgerEntry> many = IntStream.rangeClosed(1, 51)
                .mapToObj(i -> entry((long) i, LedgerType.EXPENSE, "지출" + i, 1000, false))
                .toList();
        given(ledgerEntryRepository.aggregateByType(1L))
                .willReturn(List.of(new LedgerTypeTotal(LedgerType.EXPENSE, 51000L, 51L)));
        given(ledgerEntryRepository.findForAssistant(eq(1L), anyInt())).willReturn(many);

        JsonNode result = execute();

        assertThat(result.get("entries")).hasSize(50);
        assertThat(result.get("truncated").asBoolean()).isTrue();
        // 합계는 DB 집계값이라 잘린 목록의 합과 무관하게 전체를 반영한다
        assertThat(result.get("totalExpense").asLong()).isEqualTo(51000L);
        assertThat(result.get("entryCount").asLong()).isEqualTo(51L);
    }

    private JsonNode execute() throws Exception {
        Object result = tool.execute(1L, objectMapper.createObjectNode());
        return objectMapper.readTree(objectMapper.writeValueAsString(result));
    }

    private LedgerEntry entry(Long id, LedgerType type, String itemName, int amount, boolean fromSchedule) {
        LedgerEntry entry = BeanUtils.instantiateClass(LedgerEntry.class);
        ReflectionTestUtils.setField(entry, "id", id);
        ReflectionTestUtils.setField(entry, "type", type);
        ReflectionTestUtils.setField(entry, "itemName", itemName);
        ReflectionTestUtils.setField(entry, "amount", amount);
        ReflectionTestUtils.setField(entry, "entryDate", LocalDate.of(2026, 11, 21));
        if (fromSchedule) {
            ReflectionTestUtils.setField(entry, "scheduleRound", BeanUtils.instantiateClass(ScheduleRound.class));
        }
        return entry;
    }
}
