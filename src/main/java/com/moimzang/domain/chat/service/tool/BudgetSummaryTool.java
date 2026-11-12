package com.moimzang.domain.chat.service.tool;

import com.moimzang.domain.budget.entity.LedgerEntry;
import com.moimzang.domain.budget.enums.LedgerType;
import com.moimzang.domain.budget.repository.LedgerEntryRepository;
import com.moimzang.domain.budget.repository.LedgerTypeTotal;
import com.moimzang.global.ai.AiTool;
import java.util.List;
import java.util.Map;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;
import tools.jackson.databind.JsonNode;

// 장부 조회 도구.
// 수입·지출·잔액은 DB에서 전체를 집계해 내려준다 — 항목이 상한에 잘려도 합계가 맞아야 하고,
// AI가 금액을 직접 더하게 두면 틀리기 때문이다
@Component
@RequiredArgsConstructor
public class BudgetSummaryTool implements ChatTool {

    // 장부는 기수 내내 쌓이는 데이터라 최근 것만 싣는다
    private static final int MAX_ROWS = 50;

    private final LedgerEntryRepository ledgerEntryRepository;

    @Override
    public AiTool definition() {
        return new AiTool(
                "get_budget_summary",
                "이 기수의 장부를 조회해 누적 수입·누적 지출·잔액과 최근 장부 항목(유형·항목명·금액·거래일)을 "
                        + "반환한다. '회비 얼마 남았어', '얼마 썼어' 같은 예산 질문에 쓴다. "
                        + "인자가 필요 없고 항상 기수 전체 장부를 본다.",
                Map.of(
                        "type", "object",
                        "properties", Map.of(),
                        "required", List.of()));
    }

    @Override
    @Transactional(readOnly = true)
    public Object execute(Long termId, JsonNode args) {
        List<LedgerTypeTotal> totals = ledgerEntryRepository.aggregateByType(termId);
        long totalIncome = amountOf(totals, LedgerType.INCOME);
        long totalExpense = amountOf(totals, LedgerType.EXPENSE);
        long entryCount = totals.stream().mapToLong(LedgerTypeTotal::count).sum();

        // 상한보다 1건 더 조회해서 잘렸는지 판단한다 — AI가 "일부만 보여드렸다"고 말할 수 있어야 한다
        List<LedgerEntry> found = ledgerEntryRepository.findForAssistant(termId, MAX_ROWS + 1);
        boolean truncated = found.size() > MAX_ROWS;
        List<LedgerEntry> page = truncated ? found.subList(0, MAX_ROWS) : found;

        return new Result(
                totalIncome,
                totalExpense,
                totalIncome - totalExpense,
                entryCount,
                truncated,
                page.stream().map(Row::from).toList());
    }

    // 수입만 있고 지출이 없는 기수처럼 한쪽 유형이 아예 없으면 집계 결과에 행이 없다
    private long amountOf(List<LedgerTypeTotal> totals, LedgerType type) {
        return totals.stream()
                .filter(total -> total.type() == type)
                .mapToLong(LedgerTypeTotal::totalAmount)
                .sum();
    }

    // balance(잔액)를 따로 내려준다 — "얼마 남았어"에 AI가 수입에서 지출을 직접 빼지 않게 한다
    private record Result(long totalIncome, long totalExpense, long balance, long entryCount,
                          boolean truncated, List<Row> entries) {}

    private record Row(Long entryId, String type, String itemName, int amount, String entryDate,
                       boolean fromSchedule) {

        static Row from(LedgerEntry entry) {
            return new Row(
                    entry.getId(),
                    entry.getType().name(),
                    entry.getItemName(),
                    entry.getAmount(),
                    entry.getEntryDate().toString(),
                    entry.getScheduleRound() != null);
        }
    }
}
