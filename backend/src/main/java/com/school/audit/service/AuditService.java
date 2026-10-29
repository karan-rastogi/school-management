package com.school.audit.service;

import com.school.audit.domain.AuditLog;
import com.school.audit.repository.AuditLogRepository;
import com.school.auth.security.AuthenticatedUser;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;

/**
 * Records privileged actions to the {@code audit_log} table. Callers pass only non-personal data:
 * an action code, the entity type/id and an optional short detail string. The acting user is taken
 * from the security context, so callers never repeat it.
 */
@Service
public class AuditService {

    private static final int ACTION_MAX = 80;
    private static final int ENTITY_TYPE_MAX = 60;
    private static final int ENTITY_ID_MAX = 80;
    private static final int DETAILS_MAX = 500;

    private final AuditLogRepository auditLogRepository;

    public AuditService(AuditLogRepository auditLogRepository) {
        this.auditLogRepository = auditLogRepository;
    }

    public void record(String action, String entityType, Object entityId) {
        record(action, entityType, entityId, null);
    }

    public void record(String action, String entityType, Object entityId, String details) {
        AuditLog entry = new AuditLog();
        entry.setAction(truncate(action, ACTION_MAX));
        entry.setEntityType(truncate(entityType, ENTITY_TYPE_MAX));
        entry.setEntityId(entityId == null ? null : truncate(String.valueOf(entityId), ENTITY_ID_MAX));
        entry.setDetails(details == null ? null : truncate(details, DETAILS_MAX));
        entry.setActorUserId(currentUserId());
        auditLogRepository.save(entry);
    }

    private static Long currentUserId() {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        if (authentication != null && authentication.getPrincipal() instanceof AuthenticatedUser user) {
            return user.id();
        }
        return null;
    }

    private static String truncate(String value, int max) {
        if (value == null) {
            return null;
        }
        return value.length() > max ? value.substring(0, max) : value;
    }
}
