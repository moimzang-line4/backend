package com.moimzang.domain.settlement.repository;

import com.moimzang.domain.settlement.entity.SettlementTarget;
import org.springframework.data.jpa.repository.JpaRepository;

public interface SettlementTargetRepository extends JpaRepository<SettlementTarget, Long>,
        SettlementTargetRepositoryCustom {
}
