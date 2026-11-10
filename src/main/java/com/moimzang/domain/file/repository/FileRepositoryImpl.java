package com.moimzang.domain.file.repository;

import static com.moimzang.domain.file.entity.QFile.file;

import com.moimzang.domain.file.entity.File;
import com.moimzang.domain.file.enums.FileCategory;
import com.moimzang.domain.file.enums.FileSortOption;
import com.querydsl.core.types.OrderSpecifier;
import com.querydsl.core.types.dsl.BooleanExpression;
import com.querydsl.jpa.impl.JPAQueryFactory;
import java.time.LocalDateTime;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.util.StringUtils;

@RequiredArgsConstructor
public class FileRepositoryImpl implements FileRepositoryCustom {

    private final JPAQueryFactory queryFactory;

    @Override
    public List<File> search(Long groupTermId, FileCategory category, String keyword, Long scheduleId, FileSortOption sort,
                              LocalDateTime cursorCreatedAt, String cursorName, Long cursorId, int limit) {
        return queryFactory.selectFrom(file)
                .where(baseConditions(groupTermId, category, keyword, scheduleId))
                .where(cursorCondition(sort, cursorCreatedAt, cursorName, cursorId))
                .orderBy(orderSpecifiers(sort))
                .limit(limit)
                .fetch();
    }

    @Override
    public List<File> searchAll(Long groupTermId, FileCategory category, String keyword) {
        return queryFactory.selectFrom(file)
                .where(baseConditions(groupTermId, category, keyword, null))
                .orderBy(file.name.asc(), file.id.asc())
                .fetch();
    }

    @Override
    public long count(Long groupTermId, FileCategory category, String keyword, Long scheduleId) {
        Long count = queryFactory.select(file.count())
                .from(file)
                .where(baseConditions(groupTermId, category, keyword, scheduleId))
                .fetchOne();
        return count == null ? 0 : count;
    }

    // search()/searchAll()/count()가 공유하는 조건 — scheduleId를 안 쓰는 호출(searchAll)은 null로 넘기면
    // scheduleIdEq(null)이 조건을 빼준다
    private BooleanExpression[] baseConditions(Long groupTermId, FileCategory category, String keyword, Long scheduleId) {
        return new BooleanExpression[]{
                file.groupTerm.id.eq(groupTermId),
                file.deletedAt.isNull(),
                categoryEq(category),
                nameContains(keyword),
                scheduleIdEq(scheduleId)
        };
    }

    private BooleanExpression categoryEq(FileCategory category) {
        return category == null ? null : file.category.eq(category);
    }

    private BooleanExpression nameContains(String keyword) {
        return StringUtils.hasText(keyword) ? file.name.containsIgnoreCase(keyword) : null;
    }

    private BooleanExpression scheduleIdEq(Long scheduleId) {
        return scheduleId == null ? null : file.schedule.id.eq(scheduleId);
    }

    // 다음 페이지 조건 — (정렬 기준 컬럼, id) 복합 비교. 첫 페이지(cursorId == null)는 조건 없음
    private BooleanExpression cursorCondition(FileSortOption sort, LocalDateTime cursorCreatedAt, String cursorName, Long cursorId) {
        if (cursorId == null) {
            return null;
        }
        return switch (sort == null ? FileSortOption.LATEST : sort) {
            case OLDEST -> file.createdAt.gt(cursorCreatedAt)
                    .or(file.createdAt.eq(cursorCreatedAt).and(file.id.gt(cursorId)));
            case NAME -> file.name.gt(cursorName)
                    .or(file.name.eq(cursorName).and(file.id.gt(cursorId)));
            case LATEST -> file.createdAt.lt(cursorCreatedAt)
                    .or(file.createdAt.eq(cursorCreatedAt).and(file.id.lt(cursorId)));
        };
    }

    private OrderSpecifier<?>[] orderSpecifiers(FileSortOption sort) {
        return switch (sort == null ? FileSortOption.LATEST : sort) {
            case OLDEST -> new OrderSpecifier<?>[]{file.createdAt.asc(), file.id.asc()};
            case NAME -> new OrderSpecifier<?>[]{file.name.asc(), file.id.asc()};
            case LATEST -> new OrderSpecifier<?>[]{file.createdAt.desc(), file.id.desc()};
        };
    }
}
