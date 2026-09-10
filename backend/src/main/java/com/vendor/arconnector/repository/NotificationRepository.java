package com.vendor.arconnector.repository;

import com.vendor.arconnector.domain.Notification;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.Instant;
import java.util.List;

public interface NotificationRepository extends JpaRepository<Notification, Long> {

    @Query("select n from Notification n left join fetch n.person order by n.sentAt desc")
    List<Notification> findRecent(Pageable pageable);

    /** Pares (destinatario, tipo) ya avisados en [from, to), para no repetir el mismo aviso el mismo día. */
    @Query("select n.recipientEmail, n.kind from Notification n where n.sentAt >= :from and n.sentAt < :to")
    List<Object[]> findRecipientAndKindBetween(@Param("from") Instant from, @Param("to") Instant to);
}
