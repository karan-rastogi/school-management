package com.school.user.repository;

import com.school.user.domain.UserRole;
import com.school.user.domain.UserRoleId;
import org.springframework.data.jpa.repository.JpaRepository;

public interface UserRoleRepository extends JpaRepository<UserRole, UserRoleId> {
}
