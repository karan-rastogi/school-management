package com.school.student.repository;

import com.school.student.domain.Student;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Optional;

public interface StudentRepository extends JpaRepository<Student, Long>, JpaSpecificationExecutor<Student> {

    Optional<Student> findByAdmissionNoIgnoreCase(String admissionNo);

    boolean existsByAdmissionNoIgnoreCase(String admissionNo);

    boolean existsByAdmissionNoIgnoreCaseAndIdNot(String admissionNo, Long id);

    @Modifying(clearAutomatically = true, flushAutomatically = true)
    @Query(value = "update students set qr_token = gen_random_uuid() where id = :id", nativeQuery = true)
    int regenerateQrToken(@Param("id") Long id);
}
