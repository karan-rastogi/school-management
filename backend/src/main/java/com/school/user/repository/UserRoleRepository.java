package com.school.user.repository;

import com.school.user.domain.UserRole;
import com.school.user.domain.UserRoleId;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Collection;
import java.util.List;

public interface UserRoleRepository extends JpaRepository<UserRole, UserRoleId> {

    List<UserRole> findByUserId(Long userId);

    List<UserRole> findByUserIdIn(Collection<Long> userIds);
}
