package com.moimzang.domain.group.service;

import com.moimzang.domain.group.repository.ClubGroupRepository;
import com.moimzang.domain.group.repository.GroupTermRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class GroupService {

    private final ClubGroupRepository clubGroupRepository;
    private final GroupTermRepository groupTermRepository;
}
