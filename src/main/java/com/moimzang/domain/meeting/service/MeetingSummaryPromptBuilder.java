package com.moimzang.domain.meeting.service;

import com.moimzang.domain.meeting.entity.Meeting;
import com.moimzang.domain.meeting.entity.MeetingAgenda;
import java.util.List;
import org.springframework.stereotype.Component;

// 회의 안건 기록(content·discussionNote·decision)을 AI 요약용 프롬프트 텍스트로 바꾼다
@Component
public class MeetingSummaryPromptBuilder {

    public String build(Meeting meeting, List<MeetingAgenda> agendas) {
        StringBuilder sb = new StringBuilder();
        sb.append("다음은 '").append(meeting.getTitle()).append("' 회의의 안건별 기록이다. ")
                .append("전체 내용을 자연스러운 문단으로 요약해줘.\n\n");
        for (MeetingAgenda agenda : agendas) {
            sb.append("- 안건: ").append(agenda.getContent()).append("\n");
            if (agenda.getDiscussionNote() != null && !agenda.getDiscussionNote().isBlank()) {
                sb.append("  논의 내용: ").append(agenda.getDiscussionNote()).append("\n");
            }
            if (agenda.getDecision() != null && !agenda.getDecision().isBlank()) {
                sb.append("  결정 사항: ").append(agenda.getDecision()).append("\n");
            }
        }
        return sb.toString();
    }
}
