package com.balancify.backend.repository;

import com.balancify.backend.domain.Match;
import java.util.List;
import java.util.Optional;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.data.jpa.repository.JpaRepository;

public interface MatchRepository extends JpaRepository<Match, Long> {

    Optional<Match> findTopByGroup_IdOrderByPlayedAtDescIdDesc(Long groupId);

    @Query("""
        select m
        from Match m
        where m.group.id = :groupId
          and m.winningTeam is not null
          and m.winningTeam <> ''
        order by m.playedAt desc, m.id desc
        """)
    List<Match> findRecentByGroupId(@Param("groupId") Long groupId, Pageable pageable);
}
