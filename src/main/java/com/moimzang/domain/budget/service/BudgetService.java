package com.moimzang.domain.budget.service;

import com.moimzang.domain.budget.repository.LedgerEntryRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class BudgetService {

    private final LedgerEntryRepository ledgerEntryRepository;
}
