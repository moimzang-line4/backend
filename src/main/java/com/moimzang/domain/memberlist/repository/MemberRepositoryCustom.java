package com.moimzang.domain.memberlist.repository;

import com.moimzang.domain.memberlist.entity.Member;
import java.time.LocalDateTime;
import java.util.List;

public interface MemberRepositoryCustom {

    // 기수의 부원을 이름·파트 검색어로 조회한다(AI 비서 조회용, 읽기 전용)
    List<Member> searchForAssistant(Long groupTermId, String keyword, String part, int limit);

    // 위 조회의 전체 건수. limit에 잘린 경우 "몇 명 중 일부"인지 알리는 데 쓴다
    long countForAssistant(Long groupTermId, String keyword, String part);

    // 명단 조회 탭. 등록한 순서대로 cursor 페이지 단위로 조회하고 이름·학과·전화번호로 검색한다
    List<Member> searchInMemberList(Long memberListId, String keyword, LocalDateTime cursorCreatedAt, Long cursorId,
                                    int limit);

    // 위 검색 조건에 맞는 전체 인원('총 N명')
    long countInMemberList(Long memberListId, String keyword);
}
