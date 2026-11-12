package com.moimzang.domain.chat.service.tool;

import com.moimzang.domain.memberlist.entity.Member;
import com.moimzang.domain.memberlist.entity.MemberList;
import com.moimzang.domain.memberlist.repository.MemberListRepository;
import com.moimzang.domain.memberlist.repository.MemberRepository;
import com.moimzang.global.ai.AiTool;
import java.util.List;
import java.util.Map;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;
import tools.jackson.databind.JsonNode;

// 부원 명단 조회 도구.
// 전화번호는 뒤 4자리만 내려준다 — 전체 번호를 외부 AI 벤더로 보내지 않기 위해서다
@Component
@RequiredArgsConstructor
public class MemberSearchTool implements ChatTool {

    // 기수 전체를 대상으로 하는 검색이라 명단이 커지면 그대로 프롬프트 비용이 된다
    private static final int MAX_ROWS = 100;

    private static final int PHONE_SUFFIX_LENGTH = 4;

    private final MemberRepository memberRepository;
    private final MemberListRepository memberListRepository;

    @Override
    public AiTool definition() {
        return new AiTool(
                "search_members",
                "이 기수의 부원 명단을 이름이나 파트로 검색해 명단방 정보·총원과 함께 "
                        + "부원별 이름·학교·학과·학번·파트·학년을 반환한다. "
                        + "'누가 있어', '몇 명이야', '어느 파트야' 같은 사람에 대한 질문에 쓴다. "
                        + "전화번호는 뒤 4자리만 제공되며 전체 번호는 제공하지 않는다.",
                Map.of(
                        "type", "object",
                        "properties", Map.of(
                                "keyword", Map.of(
                                        "type", "string",
                                        "description", "부원 이름 부분 일치 검색어. 생략하면 전체 부원"),
                                "part", Map.of(
                                        "type", "string",
                                        "description", "파트 부분 일치 검색어. 사용자가 '기획파트'라고 하면 '기획'")),
                        "required", List.of()));
    }

    @Override
    @Transactional(readOnly = true)
    public Object execute(Long termId, JsonNode args) {
        String keyword = ChatToolArgs.optionalText(args, "keyword");
        String part = ChatToolArgs.optionalText(args, "part");

        // 상한보다 1건 더 조회해서 잘렸는지 판단한다 — AI가 "일부만 보여드렸다"고 말할 수 있어야 한다
        List<Member> found = memberRepository.searchForAssistant(termId, keyword, part, MAX_ROWS + 1);
        boolean truncated = found.size() > MAX_ROWS;
        List<Member> page = truncated ? found.subList(0, MAX_ROWS) : found;

        // 잘리지 않았으면 가져온 건수가 곧 전체 건수다 — 같은 답을 얻으려고 count 쿼리를 더 날리지 않는다
        long matchedCount = truncated ? memberRepository.countForAssistant(termId, keyword, part) : page.size();
        long totalMemberCount = (keyword == null && part == null)
                ? matchedCount
                : memberRepository.countForAssistant(termId, null, null);

        return new Result(
                totalMemberCount,
                matchedCount,
                truncated,
                memberListRepository.findByGroupTermIdOrderByIdAsc(termId).stream()
                        .map(MemberListRow::from).toList(),
                page.stream().map(MemberRow::from).toList());
    }

    // 하이픈이 섞인 번호("010-1234-5678")도 들어오므로 숫자만 남기고 뒤 4자리를 자른다.
    // 번호를 아직 안 받았거나 4자리가 안 되면 빈 값으로 둔다
    private static String phoneLast4(String phone) {
        if (phone == null) {
            return null;
        }
        String digits = phone.replaceAll("\\D", "");
        return digits.length() < PHONE_SUFFIX_LENGTH ? null : digits.substring(digits.length() - PHONE_SUFFIX_LENGTH);
    }

    private record Result(long totalMemberCount, long matchedCount, boolean truncated,
                          List<MemberListRow> memberLists, List<MemberRow> members) {}

    // 수집 토글을 함께 내려준다 — 학번·파트가 비어 있는 게 '안 받은 항목'이라서인지
    // '아직 안 적은 사람'인지 AI가 구분해 답할 수 있어야 한다
    private record MemberListRow(Long memberListId, String name, String status, String registerDeadline,
                                 boolean collectSchool, boolean collectPhone, boolean collectStudentId,
                                 boolean collectPart, boolean collectGrade) {

        static MemberListRow from(MemberList memberList) {
            return new MemberListRow(
                    memberList.getId(),
                    memberList.getName(),
                    memberList.getStatus().name(),
                    memberList.getRegisterDeadline() == null ? null : memberList.getRegisterDeadline().toString(),
                    Boolean.TRUE.equals(memberList.getCollectSchool()),
                    Boolean.TRUE.equals(memberList.getCollectPhone()),
                    Boolean.TRUE.equals(memberList.getCollectStudentId()),
                    Boolean.TRUE.equals(memberList.getCollectPart()),
                    Boolean.TRUE.equals(memberList.getCollectGrade()));
        }
    }

    private record MemberRow(Long memberId, String memberListName, String name, String school, String department,
                             String studentId, String part, String grade, String phoneLast4) {

        static MemberRow from(Member member) {
            return new MemberRow(
                    member.getId(),
                    member.getMemberList().getName(),
                    member.getName(),
                    member.getSchool(),
                    member.getDepartment(),
                    member.getStudentId(),
                    member.getPart(),
                    member.getGrade(),
                    MemberSearchTool.phoneLast4(member.getPhone()));
        }
    }
}
