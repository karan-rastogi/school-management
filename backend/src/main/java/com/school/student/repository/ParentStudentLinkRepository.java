package com.school.student.repository;

import com.school.student.domain.ParentStudentLink;
import com.school.student.domain.ParentStudentLinkId;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;

public interface ParentStudentLinkRepository extends JpaRepository<ParentStudentLink, ParentStudentLinkId> {

    List<ParentStudentLink> findByStudentId(Long studentId);

    List<ParentStudentLink> findByParentUserId(Long parentUserId);

    boolean existsByParentUserIdAndStudentId(Long parentUserId, Long studentId);

    @Query("""
            select l from ParentStudentLink l
            join Student s on s.id = l.studentId
            where l.parentUserId = :parentUserId
            order by s.fullName
            """)
    List<ParentStudentLink> findByParentUserIdOrderedByStudentName(@Param("parentUserId") Long parentUserId);
}
