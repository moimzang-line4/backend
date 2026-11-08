package com.moimzang.domain.settlement.service;

import com.moimzang.domain.settlement.repository.SettlementTargetRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class SettlementService {

    private final SettlementTargetRepository settlementTargetRepository;
}
