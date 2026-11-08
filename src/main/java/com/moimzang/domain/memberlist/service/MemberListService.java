package com.moimzang.domain.memberlist.service;

import com.moimzang.domain.memberlist.repository.MemberListRepository;
import com.moimzang.domain.memberlist.repository.MemberRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class MemberListService {

    private final MemberListRepository memberListRepository;
    private final MemberRepository memberRepository;
}
