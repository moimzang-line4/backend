package com.moimzang.domain.memberlist.repository;

import com.moimzang.domain.memberlist.entity.Member;
import java.util.List;

public interface MemberRepositoryCustom {

    // 기수의 부원을 이름·파트 검색어로 조회한다(AI 비서 조회용, 읽기 전용)
    List<Member> searchForAssistant(Long groupTermId, String keyword, String part, int limit);

    // 위 조회의 전체 건수. limit에 잘린 경우 "몇 명 중 일부"인지 알리는 데 쓴다
    long countForAssistant(Long groupTermId, String keyword, String part);
}
