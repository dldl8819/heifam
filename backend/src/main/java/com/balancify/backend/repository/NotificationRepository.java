package com.balancify.backend.repository;

import com.balancify.backend.domain.Notification;
import java.time.OffsetDateTime;
import java.util.Collection;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface NotificationRepository extends JpaRepository<Notification, Long> {

    List<Notification> findTop30ByGroupIdAndAudienceInOrderByIdDesc(Long groupId, Collection<String> audiences);

    @Query(
        "select new com.balancify.backend.repository.NotificationListMark(count(notification), max(notification.id)) "
            + "from Notification notification "
            + "where notification.groupId = :groupId and notification.audience in :audiences"
    )
    NotificationListMark findListMark(@Param("groupId") Long groupId, @Param("audiences") Collection<String> audiences);

    @Modifying
    @Query("delete from Notification notification where notification.kind = :kind and notification.targetId = :targetId")
    int deleteByKindAndTargetId(@Param("kind") String kind, @Param("targetId") Long targetId);

    @Modifying
    @Query("delete from Notification notification where notification.createdAt < :before")
    int deleteCreatedBefore(@Param("before") OffsetDateTime before);
}
