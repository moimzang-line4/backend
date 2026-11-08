package com.moimzang.domain.settlement.controller;

import com.moimzang.domain.settlement.service.SettlementService;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequiredArgsConstructor
public class SettlementController implements SettlementApi {

    private final SettlementService settlementService;
}
