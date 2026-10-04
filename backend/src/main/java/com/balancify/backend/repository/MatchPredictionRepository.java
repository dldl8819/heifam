package com.balancify.backend.repository;

import com.balancify.backend.domain.MatchPrediction;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface MatchPredictionRepository extends JpaRepository<MatchPrediction, Long> {

    Optional<MatchPrediction> findByMatchIdAndPredictorEmail(Long matchId, String predictorEmail);

    List<MatchPrediction> findByMatchIdOrderByPredictorEmailAsc(Long matchId);

    List<MatchPrediction> findByMatchIdIn(Collection<Long> matchIds);

    @Query("""
        select prediction
        from MatchPrediction prediction, Match game
        where game.id = prediction.matchId
          and prediction.predictorEmail = :email
          and game.winningTeam is not null
        order by prediction.matchId desc
        """)
    List<MatchPrediction> findResolvedByPredictor(@Param("email") String email, Pageable pageable);

    @Query("""
        select count(prediction) as total,
               coalesce(sum(case when prediction.predictedTeam = game.winningTeam then 1 else 0 end), 0) as hits
        from MatchPrediction prediction, Match game
        where game.id = prediction.matchId
          and prediction.predictorEmail = :email
          and game.winningTeam is not null
        """)
    PredictionRecord summarizeResolved(@Param("email") String email);

    interface PredictionRecord {
        Long getTotal();

        Long getHits();
    }
}
