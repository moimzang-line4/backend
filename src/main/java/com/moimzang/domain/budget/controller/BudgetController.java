package com.moimzang.domain.budget.controller;

import com.moimzang.domain.budget.service.BudgetService;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequiredArgsConstructor
public class BudgetController implements BudgetApi {

    private final BudgetService budgetService;
}
