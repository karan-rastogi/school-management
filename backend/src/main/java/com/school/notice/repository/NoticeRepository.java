package com.school.notice.repository;

import com.school.notice.domain.Notice;
import com.school.notice.domain.NoticeScope;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.OffsetDateTime;
import java.util.Collection;
import java.util.List;
import java.util.Optional;

public interface NoticeRepository extends JpaRepository<Notice, Long> {

    Optional<Notice> findByIdAndDeletedAtIsNull(Long id);

    Page<Notice> findByDeletedAtIsNull(Pageable pageable);

    Page<Notice> findByScopeAndDeletedAtIsNull(NoticeScope scope, Pageable pageable);

    Page<Notice> findByScopeAndSchoolClassIdInAndDeletedAtIsNull(
            NoticeScope scope, Collection<Long> classIds, Pageable pageable);

    @Query("""
            select n from Notice n
            where n.deletedAt is null
              and n.scope = :scope
              and (n.expiresAt is null or n.expiresAt > :now)
            order by n.createdAt desc
            """)
    List<Notice> findVisibleGlobal(@Param("scope") NoticeScope scope, @Param("now") OffsetDateTime now);

    @Query("""
            select n from Notice n
            left join fetch n.schoolClass
            where n.deletedAt is null
              and (n.expiresAt is null or n.expiresAt > :now)
              and (n.scope = :global or n.schoolClass.id in :classIds)
            order by n.createdAt desc
            """)
    List<Notice> findVisibleForClasses(
            @Param("global") NoticeScope global,
            @Param("classIds") Collection<Long> classIds,
            @Param("now") OffsetDateTime now);
}
