package com.moimzang.domain.group.repository;

import com.moimzang.domain.group.entity.GroupTerm;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

public interface GroupTermRepository extends JpaRepository<GroupTerm, Long> {

    // 기수 선택 드롭다운에 만든 순서대로 보여주기 위해 id 오름차순으로 조회한다
    List<GroupTerm> findAllByGroupIdOrderByIdAsc(Long groupId);

    // 가장 최근에 만든 기수를 현재 기수로 본다
    Optional<GroupTerm> findFirstByGroupIdOrderByIdDesc(Long groupId);
}
