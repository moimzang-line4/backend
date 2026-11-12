package com.moimzang.domain.chat.service;

import static org.assertj.core.api.Assertions.assertThat;

import tools.jackson.databind.ObjectMapper;
import com.moimzang.domain.chat.dto.response.UnpaidLookupCard;
import com.moimzang.domain.chat.service.tool.SettlementStatusTool;
import com.moimzang.domain.schedule.entity.Schedule;
import com.moimzang.domain.settlement.enums.PaymentStatus;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.beans.BeanUtils;
import org.springframework.test.util.ReflectionTestUtils;

class UnpaidLookupCardFactoryTest {

    private final ObjectMapper objectMapper = new ObjectMapper();
    private final UnpaidLookupCardFactory factory = new UnpaidLookupCardFactory(objectMapper);

    @Test
    void create_도구_결과_JSON에서_미입금자만_카드에_담는다() throws Exception {
        String json = toolResultJson(
                row(401L, "강민준", "광고홍보학과", "1140", 47000, PaymentStatus.UNPAID),
                row(402L, "홍길동", "경영학과", "2250", 47000, PaymentStatus.PAID),
                row(403L, "김가은", "미디어학과", "3360", 47000, PaymentStatus.UNPAID));

        UnpaidLookupCard card = factory.create(schedule("가을공연"), json);

        assertThat(card.title()).isEqualTo("가을공연·미입금");
        assertThat(card.count()).isEqualTo(2);
        assertThat(card.items()).extracting(UnpaidLookupCard.Item::name)
                .containsExactly("강민준", "김가은");
        // 입금 확인 버튼이 쓰는 식별자가 그대로 실려야 한다
        assertThat(card.items().get(0).attendeeId()).isEqualTo(401L);
    }

    @Test
    void create_총액은_도구가_집계한_값을_그대로_쓴다() throws Exception {
        String json = toolResultJson(row(401L, "강민준", "광고홍보학과", "1140", 47000, PaymentStatus.UNPAID));

        UnpaidLookupCard card = factory.create(schedule("가을공연"), json);

        assertThat(card.totalAmount()).isEqualTo(94000);
    }

    @Test
    void create_미입금자가_없으면_카드를_만들지_않는다() throws Exception {
        String json = toolResultJson(row(402L, "홍길동", "경영학과", "2250", 47000, PaymentStatus.PAID));

        assertThat(factory.create(schedule("가을공연"), json)).isNull();
    }

    @Test
    void create_도구가_error_JSON을_돌려주면_카드를_만들지_않는다() {
        assertThat(factory.create(schedule("가을공연"), "{\"error\":\"잘못된 인자\"}")).isNull();
    }

    @Test
    void create_JSON이_깨져_있으면_카드를_만들지_않는다() {
        assertThat(factory.create(schedule("가을공연"), "이건 JSON이 아니다")).isNull();
    }

    // 카드 총액이 도구 집계값을 그대로 쓰는지 보려고 94000으로 고정한다
    private String toolResultJson(SettlementStatusTool.Row... rows) throws Exception {
        return objectMapper.writeValueAsString(
                new java.util.LinkedHashMap<>(java.util.Map.of(
                        "totalUnpaidAmount", 94000,
                        "members", List.of(rows))));
    }

    private SettlementStatusTool.Row row(Long id, String name, String affiliation, String phoneLast4,
                                          int amount, PaymentStatus status) {
        int unpaid = status == PaymentStatus.UNPAID ? amount : 0;
        return new SettlementStatusTool.Row(id, name, affiliation, phoneLast4, amount, unpaid,
                status.name(), List.of());
    }

    private Schedule schedule(String title) {
        Schedule schedule = BeanUtils.instantiateClass(Schedule.class);
        ReflectionTestUtils.setField(schedule, "title", title);
        return schedule;
    }
}
