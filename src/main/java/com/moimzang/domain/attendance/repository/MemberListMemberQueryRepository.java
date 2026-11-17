package com.moimzang.domain.attendance.repository;

import static com.moimzang.domain.memberlist.entity.QMember.member;

import com.moimzang.domain.memberlist.entity.Member;
import com.querydsl.jpa.impl.JPAQueryFactory;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Repository;

@Repository
@RequiredArgsConstructor
public class MemberListMemberQueryRepository {

    private final JPAQueryFactory queryFactory;

    // 명단방 불러오기(API-084)용 — memberlist 도메인의 MemberRepository를 건드리지 않고
    // attendance 쪽에서 직접 그 명단방의 부원 전체를 조회한다
    public List<Member> findAllByMemberListId(Long memberListId) {
        return queryFactory.selectFrom(member)
                .where(member.memberList.id.eq(memberListId))
                .orderBy(member.id.asc())
                .fetch();
    }
}