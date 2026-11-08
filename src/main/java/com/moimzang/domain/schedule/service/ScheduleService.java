package com.moimzang.domain.schedule.service;

import com.moimzang.domain.schedule.repository.ScheduleCategoryRepository;
import com.moimzang.domain.schedule.repository.ScheduleRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class ScheduleService {

    private final ScheduleRepository scheduleRepository;
    private final ScheduleCategoryRepository scheduleCategoryRepository;
}
