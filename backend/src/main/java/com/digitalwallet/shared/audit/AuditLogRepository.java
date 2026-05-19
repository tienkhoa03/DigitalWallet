package com.digitalwallet.shared.audit;

import io.quarkus.hibernate.orm.panache.PanacheRepositoryBase;
import jakarta.enterprise.context.ApplicationScoped;

@ApplicationScoped
public class AuditLogRepository implements PanacheRepositoryBase<AuditLog, Long> {
}
