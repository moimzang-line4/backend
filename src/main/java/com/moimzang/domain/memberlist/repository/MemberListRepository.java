package com.moimzang.domain.memberlist.repository;

import com.moimzang.domain.memberlist.entity.MemberList;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

public interface MemberListRepository extends JpaRepository<MemberList, Long> {

    // 기수의 명단방을 만든 순서대로 조회한다(AI 비서 조회용, 읽기 전용)
    List<MemberList> findByGroupTermIdOrderByIdAsc(Long groupTermId);

    // 부원 탭 홈 명단방 카드. 최근에 만든 명단방이 위에 온다
    List<MemberList> findByGroupTermIdOrderByIdDesc(Long groupTermId);

    // 등록 링크 토큰이 이미 쓰이고 있는지 확인한다
    boolean existsByJoinToken(String joinToken);

    // 공개 등록 링크로 명단방을 찾는다
    Optional<MemberList> findByJoinToken(String joinToken);
}
