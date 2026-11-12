package com.moimzang.domain.memberlist.repository;

import com.moimzang.domain.memberlist.entity.Member;
import org.springframework.data.jpa.repository.JpaRepository;

public interface MemberRepository extends JpaRepository<Member, Long>, MemberRepositoryCustom {
}
