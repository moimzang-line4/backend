package com.moimzang.domain.chat.service.tool;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.BDDMockito.given;

import com.moimzang.domain.memberlist.entity.Member;
import com.moimzang.domain.memberlist.entity.MemberList;
import com.moimzang.domain.memberlist.enums.MemberListStatus;
import com.moimzang.domain.memberlist.repository.MemberListRepository;
import com.moimzang.domain.memberlist.repository.MemberRepository;
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
class MemberSearchToolTest {

    private final ObjectMapper objectMapper = new ObjectMapper();

    @Mock
    private MemberRepository memberRepository;

    @Mock
    private MemberListRepository memberListRepository;

    @InjectMocks
    private MemberSearchTool tool;

    @Test
    void execute_전화번호는_뒤_4자리만_내려준다() throws Exception {
        given(memberRepository.searchForAssistant(eq(1L), isNull(), isNull(), anyInt()))
                .willReturn(List.of(member(501L, "강민준", "010-1234-1140", "기획")));

        JsonNode member = execute("{}").get("members").get(0);

        assertThat(member.get("phoneLast4").asText()).isEqualTo("1140");
        // 전체 번호는 외부 AI 벤더로 나가지 않는다
        assertThat(member.has("phone")).isFalse();
        assertThat(member.toString()).doesNotContain("010-1234-1140");
    }

    @Test
    void execute_전화번호를_수집하지_않았으면_phoneLast4는_비어_있다() throws Exception {
        given(memberRepository.searchForAssistant(eq(1L), isNull(), isNull(), anyInt()))
                .willReturn(List.of(member(501L, "강민준", null, "기획")));

        JsonNode member = execute("{}").get("members").get(0);

        assertThat(member.get("phoneLast4").isNull()).isTrue();
    }

    @Test
    void execute_부원_정보와_명단방_정보를_함께_내려준다() throws Exception {
        given(memberRepository.searchForAssistant(eq(1L), isNull(), isNull(), anyInt()))
                .willReturn(List.of(member(501L, "강민준", "010-1234-1140", "기획")));
        given(memberListRepository.findByGroupTermIdOrderByIdAsc(1L))
                .willReturn(List.of(memberList(300L, "26기 부원 명단")));

        JsonNode result = execute("{}");
        JsonNode member = result.get("members").get(0);
        JsonNode memberList = result.get("memberLists").get(0);

        assertThat(result.get("totalMemberCount").asLong()).isEqualTo(1);
        assertThat(result.get("matchedCount").asLong()).isEqualTo(1);
        assertThat(result.get("truncated").asBoolean()).isFalse();
        assertThat(member.get("name").asText()).isEqualTo("강민준");
        assertThat(member.get("department").asText()).isEqualTo("광고홍보학과");
        assertThat(member.get("studentId").asText()).isEqualTo("2022305011");
        assertThat(member.get("part").asText()).isEqualTo("기획");
        assertThat(member.get("memberListName").asText()).isEqualTo("26기 부원 명단");
        assertThat(memberList.get("name").asText()).isEqualTo("26기 부원 명단");
        assertThat(memberList.get("status").asText()).isEqualTo("RECRUITING");
        assertThat(memberList.get("collectPhone").asBoolean()).isTrue();
    }

    @Test
    void execute_검색어를_주면_전체_인원과_검색_건수를_구분해_내려준다() throws Exception {
        given(memberRepository.searchForAssistant(eq(1L), eq("강"), isNull(), anyInt()))
                .willReturn(List.of(member(501L, "강민준", "010-1234-1140", "기획")));
        given(memberRepository.countForAssistant(eq(1L), isNull(), isNull())).willReturn(42L);

        JsonNode result = execute("{\"keyword\":\"강\"}");

        assertThat(result.get("matchedCount").asLong()).isEqualTo(1);
        // "42명 중 1명"처럼 답하려면 기수 총원이 따로 있어야 한다
        assertThat(result.get("totalMemberCount").asLong()).isEqualTo(42);
    }

    @Test
    void execute_상한을_넘으면_잘라내고_전체_건수를_알려준다() throws Exception {
        List<Member> many = IntStream.rangeClosed(1, 101)
                .mapToObj(i -> member((long) i, "부원" + i, "010-1234-0000", "기획"))
                .toList();
        given(memberRepository.searchForAssistant(eq(1L), isNull(), isNull(), anyInt())).willReturn(many);
        given(memberRepository.countForAssistant(eq(1L), isNull(), isNull())).willReturn(101L);

        JsonNode result = execute("{}");

        assertThat(result.get("members")).hasSize(100);
        assertThat(result.get("truncated").asBoolean()).isTrue();
        assertThat(result.get("matchedCount").asLong()).isEqualTo(101);
    }

    @Test
    void execute_파트_검색어를_그대로_레포지토리에_넘긴다() throws Exception {
        given(memberRepository.searchForAssistant(eq(1L), isNull(), eq("기획"), anyInt()))
                .willReturn(List.of(member(501L, "강민준", "010-1234-1140", "기획")));
        given(memberRepository.countForAssistant(eq(1L), isNull(), isNull())).willReturn(42L);

        JsonNode result = execute("{\"part\":\"기획\"}");

        assertThat(result.get("members").get(0).get("part").asText()).isEqualTo("기획");
        assertThat(result.get("matchedCount").asLong()).isEqualTo(1);
    }

    private JsonNode execute(String argsJson) throws Exception {
        Object result = tool.execute(1L, objectMapper.readTree(argsJson));
        return objectMapper.readTree(objectMapper.writeValueAsString(result));
    }

    private Member member(Long id, String name, String phone, String part) {
        Member member = BeanUtils.instantiateClass(Member.class);
        ReflectionTestUtils.setField(member, "id", id);
        ReflectionTestUtils.setField(member, "memberList", memberList(300L, "26기 부원 명단"));
        ReflectionTestUtils.setField(member, "name", name);
        ReflectionTestUtils.setField(member, "school", "서경대학교");
        ReflectionTestUtils.setField(member, "department", "광고홍보학과");
        ReflectionTestUtils.setField(member, "phone", phone);
        ReflectionTestUtils.setField(member, "studentId", "2022305011");
        ReflectionTestUtils.setField(member, "part", part);
        ReflectionTestUtils.setField(member, "grade", "3학년");
        return member;
    }

    private MemberList memberList(Long id, String name) {
        MemberList memberList = BeanUtils.instantiateClass(MemberList.class);
        ReflectionTestUtils.setField(memberList, "id", id);
        ReflectionTestUtils.setField(memberList, "name", name);
        ReflectionTestUtils.setField(memberList, "status", MemberListStatus.RECRUITING);
        ReflectionTestUtils.setField(memberList, "collectSchool", true);
        ReflectionTestUtils.setField(memberList, "collectPhone", true);
        ReflectionTestUtils.setField(memberList, "collectStudentId", true);
        ReflectionTestUtils.setField(memberList, "collectPart", true);
        ReflectionTestUtils.setField(memberList, "collectGrade", true);
        return memberList;
    }
}
