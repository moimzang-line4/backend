package com.moimzang.domain.budget.repository;

import com.moimzang.domain.budget.entity.LedgerEntry;
import org.springframework.data.jpa.repository.JpaRepository;

public interface LedgerEntryRepository extends JpaRepository<LedgerEntry, Long>, LedgerEntryRepositoryCustom {
}
