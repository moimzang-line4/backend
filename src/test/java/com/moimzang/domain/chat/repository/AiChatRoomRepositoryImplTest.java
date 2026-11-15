package com.moimzang.domain.chat.repository;

import static org.assertj.core.api.Assertions.assertThat;

import com.moimzang.domain.chat.entity.AiChatRoom;
import com.moimzang.domain.group.entity.ClubGroup;
import com.moimzang.domain.group.entity.GroupTerm;
import com.moimzang.global.config.JpaAuditingConfig;
import com.moimzang.global.config.QuerydslConfig;
import java.time.LocalDateTime;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.BeanUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest;
import org.springframework.boot.jdbc.test.autoconfigure.AutoConfigureTestDatabase;
import org.springframework.boot.jpa.test.autoconfigure.TestEntityManager;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.context.annotation.Import;
import org.springframework.test.util.ReflectionTestUtils;
import org.testcontainers.containers.MySQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

// cursor tiebreaker(updatedAt 동률 시 id 비교)는 Mockito로는 검증할 수 없어 실제 MySQL로 확인한다
@DataJpaTest
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@Testcontainers
@Import({QuerydslConfig.class, JpaAuditingConfig.class})
class AiChatRoomRepositoryImplTest {

    @Container
    @ServiceConnection
    static MySQLContainer<?> mysql = new MySQLContainer<>("mysql:8.0").withReuse(true);

    @Autowired
    private TestEntityManager em;

    @Autowired
    private AiChatRoomRepository aiChatRoomRepository;

    private GroupTerm groupTerm;

    @BeforeEach
    void setUp() {
        ClubGroup clubGroup = persistClubGroup("테스트 동아리");
        groupTerm = persistGroupTerm(clubGroup, "26기");
    }

    @Test
    void search_updatedAt_내림차순으로_최근_대화한_방을_먼저_반환한다() {
        AiChatRoom old = persistChatRoom("오래된 대화", LocalDateTime.of(2026, 11, 1, 10, 0));
        AiChatRoom recent = persistChatRoom("최근 대화", LocalDateTime.of(2026, 11, 13, 21, 0));
        AiChatRoom middle = persistChatRoom("중간 대화", LocalDateTime.of(2026, 11, 7, 15, 0));

        List<AiChatRoom> result = aiChatRoomRepository.search(groupTerm.getId(), null, null, null, 10);

        assertThat(result).extracting(AiChatRoom::getId)
                .containsExactly(recent.getId(), middle.getId(), old.getId());
    }

    @Test
    void search_다른_기수의_대화방은_반환하지_않는다() {
        AiChatRoom mine = persistChatRoom("우리 기수 대화", LocalDateTime.of(2026, 11, 13, 21, 0));
        GroupTerm otherTerm = persistGroupTerm(persistClubGroup("다른 동아리"), "25기");
        AiChatRoom other = em.persistAndFlush(AiChatRoom.create(otherTerm, "다른 기수 대화"));

        List<AiChatRoom> result = aiChatRoomRepository.search(groupTerm.getId(), null, null, null, 10);

        assertThat(result).extracting(AiChatRoom::getId)
                .containsExactly(mine.getId())
                .doesNotContain(other.getId());
    }

    @Test
    void search_동일한_updatedAt에서는_cursor가_id로_다음_페이지를_구분한다() {
        LocalDateTime sameTime = LocalDateTime.of(2026, 11, 13, 21, 0);
        AiChatRoom first = persistChatRoom("1번째", sameTime);
        AiChatRoom second = persistChatRoom("2번째", sameTime);
        persistChatRoom("3번째", sameTime);

        List<AiChatRoom> result = aiChatRoomRepository.search(
                groupTerm.getId(), null, sameTime, second.getId(), 10);

        // (updatedAt, id) 내림차순이므로 cursor(second) 다음은 id가 더 작은 first만 남는다
        assertThat(result).extracting(AiChatRoom::getId).containsExactly(first.getId());
    }

    @Test
    void search_cursor는_더_오래된_대화방까지_포함한다() {
        LocalDateTime newerTime = LocalDateTime.of(2026, 11, 13, 21, 0);
        AiChatRoom newer = persistChatRoom("최근 대화", newerTime);
        AiChatRoom older = persistChatRoom("이전 대화", LocalDateTime.of(2026, 11, 7, 15, 0));

        List<AiChatRoom> result = aiChatRoomRepository.search(
                groupTerm.getId(), null, newerTime, newer.getId(), 10);

        assertThat(result).extracting(AiChatRoom::getId).containsExactly(older.getId());
    }

    @Test
    void search_limit만큼만_반환한다() {
        persistChatRoom("1번째", LocalDateTime.of(2026, 11, 13, 21, 0));
        persistChatRoom("2번째", LocalDateTime.of(2026, 11, 12, 21, 0));
        persistChatRoom("3번째", LocalDateTime.of(2026, 11, 11, 21, 0));

        List<AiChatRoom> result = aiChatRoomRepository.search(groupTerm.getId(), null, null, null, 2);

        assertThat(result).hasSize(2);
    }

    @Test
    void search_keyword는_제목에_부분_일치하는_방만_반환한다() {
        AiChatRoom matched = persistChatRoom("가을 공연 미입금자 확인", LocalDateTime.of(2026, 11, 13, 21, 0));
        AiChatRoom other = persistChatRoom("신입생 공연 공지문 작성", LocalDateTime.of(2026, 11, 12, 21, 0));

        List<AiChatRoom> result = aiChatRoomRepository.search(groupTerm.getId(), "미입금", null, null, 10);

        assertThat(result).extracting(AiChatRoom::getId)
                .containsExactly(matched.getId())
                .doesNotContain(other.getId());
    }

    @Test
    void search_제목이_없는_방은_keyword_검색에서_빠진다() {
        // 대화방만 만들고 첫 질문을 보내지 않으면 title이 null로 남는다
        persistChatRoom(null, LocalDateTime.of(2026, 11, 13, 21, 0));

        List<AiChatRoom> result = aiChatRoomRepository.search(groupTerm.getId(), "공연", null, null, 10);

        assertThat(result).isEmpty();
    }

    @Test
    void search_keyword가_비어_있으면_조건을_걸지_않는다() {
        persistChatRoom("가을 공연", LocalDateTime.of(2026, 11, 13, 21, 0));
        persistChatRoom(null, LocalDateTime.of(2026, 11, 12, 21, 0));

        List<AiChatRoom> result = aiChatRoomRepository.search(groupTerm.getId(), "   ", null, null, 10);

        assertThat(result).hasSize(2);
    }

    @Test
    void count_cursor와_무관하게_검색어에_맞는_전체_건수를_센다() {
        persistChatRoom("가을 공연 미입금자 확인", LocalDateTime.of(2026, 11, 13, 21, 0));
        persistChatRoom("가을 공연 지출 검색", LocalDateTime.of(2026, 11, 12, 21, 0));
        persistChatRoom("신입생 공지문 작성", LocalDateTime.of(2026, 11, 11, 21, 0));

        assertThat(aiChatRoomRepository.count(groupTerm.getId(), null)).isEqualTo(3);
        assertThat(aiChatRoomRepository.count(groupTerm.getId(), "가을 공연")).isEqualTo(2);
    }

    @Test
    void count_다른_기수의_대화방은_세지_않는다() {
        persistChatRoom("우리 기수 대화", LocalDateTime.of(2026, 11, 13, 21, 0));
        GroupTerm otherTerm = persistGroupTerm(persistClubGroup("다른 동아리"), "25기");
        em.persistAndFlush(AiChatRoom.create(otherTerm, "다른 기수 대화"));

        assertThat(aiChatRoomRepository.count(groupTerm.getId(), null)).isEqualTo(1);
    }

    private ClubGroup persistClubGroup(String name) {
        ClubGroup group = BeanUtils.instantiateClass(ClubGroup.class);
        ReflectionTestUtils.setField(group, "name", name);
        return em.persistAndFlush(group);
    }

    private GroupTerm persistGroupTerm(ClubGroup group, String name) {
        GroupTerm term = BeanUtils.instantiateClass(GroupTerm.class);
        ReflectionTestUtils.setField(term, "group", group);
        ReflectionTestUtils.setField(term, "name", name);
        return em.persistAndFlush(term);
    }

    // updatedAt은 @LastModifiedDate라 엔티티로는 원하는 값을 넣을 수 없다 —
    // 정렬·cursor 검증에 필요한 시각을 벌크 update로 직접 박아 넣는다
    private AiChatRoom persistChatRoom(String title, LocalDateTime updatedAt) {
        AiChatRoom room = em.persistAndFlush(AiChatRoom.create(groupTerm, title));
        em.getEntityManager()
                .createQuery("update AiChatRoom r set r.updatedAt = :updatedAt where r.id = :id")
                .setParameter("updatedAt", updatedAt)
                .setParameter("id", room.getId())
                .executeUpdate();
        em.clear();
        return room;
    }
}
