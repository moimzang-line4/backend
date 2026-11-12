package com.moimzang.domain.memberlist.repository;

import com.moimzang.domain.memberlist.entity.MemberList;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;

public interface MemberListRepository extends JpaRepository<MemberList, Long> {

    // 기수의 명단방을 만든 순서대로 조회한다(AI 비서 조회용, 읽기 전용)
    List<MemberList> findByGroupTermIdOrderByIdAsc(Long groupTermId);
}
