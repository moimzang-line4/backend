package com.moimzang.domain.memberlist.repository;

import com.moimzang.domain.memberlist.entity.MemberList;
import org.springframework.data.jpa.repository.JpaRepository;

public interface MemberListRepository extends JpaRepository<MemberList, Long> {
}
