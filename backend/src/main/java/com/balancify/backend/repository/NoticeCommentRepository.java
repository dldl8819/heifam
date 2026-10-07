package com.balancify.backend.repository;

import com.balancify.backend.domain.NoticeComment;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface NoticeCommentRepository extends JpaRepository<NoticeComment, Long> {

    List<NoticeComment> findByNoticeIdOrderByIdAsc(Long noticeId);

    Optional<NoticeComment> findByIdAndNoticeId(Long id, Long noticeId);

    boolean existsByParentId(Long parentId);

    @Query("""
        select comment.noticeId as noticeId, count(comment) as total
        from NoticeComment comment
        where comment.noticeId in :noticeIds and comment.deletedAt is null
        group by comment.noticeId
        """)
    List<NoticeCount> countByNotice(@Param("noticeIds") Collection<Long> noticeIds);

    interface NoticeCount {
        Long getNoticeId();

        Long getTotal();
    }
}
