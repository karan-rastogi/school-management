package com.school.student.repository;

import com.school.student.domain.ParentStudentLink;
import com.school.student.domain.ParentStudentLinkId;
import org.springframework.data.jpa.repository.JpaRepository;

public interface ParentStudentLinkRepository extends JpaRepository<ParentStudentLink, ParentStudentLinkId> {
}
