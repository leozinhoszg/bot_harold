package com.promagroup.apibridge.repository;

import com.promagroup.apibridge.entity.Notification;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface NotificationRepository extends JpaRepository<Notification, Long> {

    List<Notification> findByIntegrationId(Long integrationId);
}
