package com.moimzang.domain.meeting.service;

import static org.assertj.core.api.Assertions.assertThat;

import com.moimzang.domain.group.entity.GroupTerm;
import com.moimzang.domain.meeting.entity.Meeting;
import com.moimzang.domain.meeting.entity.MeetingAgenda;
import com.moimzang.domain.meeting.entity.MeetingCategory;
import java.time.LocalDate;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;

class MeetingSummaryPromptBuilderTest {

    private final MeetingSummaryPromptBuilder promptBuilder = new MeetingSummaryPromptBuilder();

    @Test
    void build_회의_제목이_맨_앞에_들어간다() {
        Meeting meeting = meeting("가을 정기공연 운영진 회의");

        String prompt = promptBuilder.build(meeting, List.of());

        assertThat(prompt).startsWith("다음은 '가을 정기공연 운영진 회의' 회의의 안건별 기록이다.");
    }

    @Test
    void build_논의내용과_결정사항이_모두_있으면_둘_다_포함한다() {
        Meeting meeting = meeting("회의");
        MeetingAgenda agenda = MeetingAgenda.create(meeting, "관객 참여 이벤트", 0);
        agenda.updateContent(null, "참여 이벤트 2종 진행하기로 함", "경품은 홍보팀이 준비", null);

        String prompt = promptBuilder.build(meeting, List.of(agenda));

        assertThat(prompt).contains("- 안건: 관객 참여 이벤트");
        assertThat(prompt).contains("  논의 내용: 참여 이벤트 2종 진행하기로 함");
        assertThat(prompt).contains("  결정 사항: 경품은 홍보팀이 준비");
    }

    @Test
    void build_논의내용만_있으면_결정사항_줄은_안_나온다() {
        Meeting meeting = meeting("회의");
        MeetingAgenda agenda = MeetingAgenda.create(meeting, "공연 홍보 계획", 0);
        agenda.updateContent(null, "포스터 시안 검토 중", null, null);

        String prompt = promptBuilder.build(meeting, List.of(agenda));

        assertThat(prompt).contains("  논의 내용: 포스터 시안 검토 중");
        assertThat(prompt).doesNotContain("결정 사항");
    }

    @Test
    void build_논의내용과_결정사항이_둘_다_없으면_안건_제목_줄만_나온다() {
        Meeting meeting = meeting("회의");
        MeetingAgenda agenda = MeetingAgenda.create(meeting, "예산 점검", 0);

        String prompt = promptBuilder.build(meeting, List.of(agenda));

        assertThat(prompt).contains("- 안건: 예산 점검");
        assertThat(prompt).doesNotContain("논의 내용");
        assertThat(prompt).doesNotContain("결정 사항");
    }

    @Test
    void build_여러_안건을_순서대로_이어붙인다() {
        Meeting meeting = meeting("회의");
        MeetingAgenda first = MeetingAgenda.create(meeting, "안건1", 0);
        MeetingAgenda second = MeetingAgenda.create(meeting, "안건2", 1);

        String prompt = promptBuilder.build(meeting, List.of(first, second));

        assertThat(prompt.indexOf("안건1")).isLessThan(prompt.indexOf("안건2"));
    }

    private Meeting meeting(String title) {
        GroupTerm groupTerm = Mockito.mock(GroupTerm.class);
        MeetingCategory category = Mockito.mock(MeetingCategory.class);
        return Meeting.create(groupTerm, category, title, LocalDate.now(), null, null);
    }
}
