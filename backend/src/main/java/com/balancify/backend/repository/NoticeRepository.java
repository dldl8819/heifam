package com.balancify.backend.repository;

import com.balancify.backend.domain.Notice;
import java.util.List;
import java.util.Optional;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface NoticeRepository extends JpaRepository<Notice, Long> {

    List<Notice> findByGroupIdOrderByCreatedAtDescIdDesc(Long groupId);

    Optional<Notice> findByIdAndGroupId(Long id, Long groupId);

    /**
     * For opening a notice: held until the read is saved, so an edit announced again either waits
     * for this read or is already visible to it. A read never lands on the wrong side of a revision.
     */
    @Lock(LockModeType.PESSIMISTIC_READ)
    @Query("select n from Notice n where n.id = :id and n.groupId = :groupId")
    Optional<Notice> findByIdAndGroupIdForShare(@Param("id") Long id, @Param("groupId") Long groupId);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select n from Notice n where n.id = :id and n.groupId = :groupId")
    Optional<Notice> findByIdAndGroupIdForUpdate(@Param("id") Long id, @Param("groupId") Long groupId);
}
