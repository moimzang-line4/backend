package com.moimzang.domain.memberlist.repository;

import com.moimzang.domain.memberlist.entity.Member;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface MemberRepository extends JpaRepository<Member, Long>, MemberRepositoryCustom {

    // 명단방 하나의 등록 인원
    long countByMemberListId(Long memberListId);

    // 명단방 목록 카드의 등록 인원을 한 번에 센다 — 명단방마다 따로 세면 목록 길이만큼 쿼리가 나간다
    @Query("""
            select m.memberList.id as memberListId, count(m) as memberCount
            from Member m
            where m.memberList.id in :memberListIds
            group by m.memberList.id
            """)
    List<MemberCountRow> countByMemberListIds(@Param("memberListIds") List<Long> memberListIds);

    // 명단방 삭제 시 그 명단방의 부원을 지운다 — 부원을 가리키는 일정 참가자를 먼저 지운 뒤 호출한다
    @Modifying(clearAutomatically = true, flushAutomatically = true)
    @Query("delete from Member m where m.memberList.id = :memberListId")
    void deleteAllByMemberListId(@Param("memberListId") Long memberListId);

    interface MemberCountRow {
        Long getMemberListId();

        Long getMemberCount();
    }
}
