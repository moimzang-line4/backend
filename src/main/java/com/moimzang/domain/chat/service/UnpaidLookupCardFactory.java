package com.moimzang.domain.chat.service;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.moimzang.domain.chat.dto.response.UnpaidLookupCard;
import com.moimzang.domain.chat.service.tool.SettlementStatusTool;
import com.moimzang.domain.schedule.entity.Schedule;
import java.util.List;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

/**
 * 입금 현황 조회 결과를 '미입금자 조회' 카드로 바꾼다.
 *
 * 카드에 담기는 금액·인원·attendeeId는 전부 도구가 DB에서 집계한 값이다 — AI가 만든 문장과
 * 무관하게 항상 정확해야 카드의 '입금 확인' 버튼이 올바른 사람을 가리킨다.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class UnpaidLookupCardFactory {

    public static final String CARD_TYPE = "미입금자 조회";

    private final ObjectMapper objectMapper;

    // 미입금자가 없으면 카드를 만들지 않는다(null) — 보여줄 목록이 없는 빈 카드는 의미가 없다
    public UnpaidLookupCard create(Schedule schedule, String settlementResultJson) {
        SettlementResult result = readResult(settlementResultJson);
        if (result == null) {
            return null;
        }

        List<UnpaidLookupCard.Item> items = result.members().stream()
                .filter(SettlementStatusTool.Row::isUnpaid)
                .map(row -> new UnpaidLookupCard.Item(
                        row.attendeeId(), row.name(), row.affiliation(), row.phoneLast4(),
                        row.totalBilledAmount()))
                .toList();

        if (items.isEmpty()) {
            return null;
        }

        return new UnpaidLookupCard(
                schedule.getTitle() + "·미입금",
                result.totalUnpaidAmount(),
                items.size(),
                items);
    }

    public JsonNode toJsonNode(UnpaidLookupCard card) {
        return objectMapper.valueToTree(card);
    }

    public String toJsonString(UnpaidLookupCard card) {
        try {
            return objectMapper.writeValueAsString(card);
        } catch (JsonProcessingException e) {
            throw new IllegalStateException("미입금자 카드를 JSON으로 직렬화하지 못했습니다.", e);
        }
    }

    // 도구가 error JSON을 돌려줬거나 형태가 다르면 카드를 만들지 않는다
    private SettlementResult readResult(String json) {
        try {
            SettlementResult result = objectMapper.readValue(json, SettlementResult.class);
            return result.members() == null ? null : result;
        } catch (JsonProcessingException e) {
            log.warn("[AI 비서] 입금 현황 결과를 카드로 변환하지 못했습니다.", e);
            return null;
        }
    }

    // 도구 결과에서 카드에 필요한 두 필드만 읽는다 — 나머지 집계값은 AI가 문장으로 쓰고 카드엔 안 들어간다.
    // 전역 Jackson 설정에 기대지 않도록 미사용 필드 무시를 여기서 명시한다
    @JsonIgnoreProperties(ignoreUnknown = true)
    private record SettlementResult(int totalUnpaidAmount, List<SettlementStatusTool.Row> members) {}
}
