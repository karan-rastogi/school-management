package com.school.student.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.IdClass;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Entity
@Table(name = "parent_student_links")
@IdClass(ParentStudentLinkId.class)
@Getter
@Setter
@NoArgsConstructor
public class ParentStudentLink {

    @Id
    @Column(name = "parent_user_id", nullable = false)
    private Long parentUserId;

    @Id
    @Column(name = "student_id", nullable = false)
    private Long studentId;

    @Column(name = "relationship", length = 50)
    private String relationship;
}
