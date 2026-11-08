package com.moimzang.domain.memberlist.controller;

import com.moimzang.domain.memberlist.service.MemberListService;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequiredArgsConstructor
public class MemberListController implements MemberListApi {

    private final MemberListService memberListService;
}
