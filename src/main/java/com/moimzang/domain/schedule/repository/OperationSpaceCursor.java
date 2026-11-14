package com.moimzang.domain.schedule.repository;

// 운영 공간 목록 cursor의 내용. sortValue는 날짜 정렬이면 ISO-8601 시각, 이름 정렬이면 제목이다
public record OperationSpaceCursor(String sortValue, int typeOrder, Long id) {
}
