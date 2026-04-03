package com.balancify.backend.api;

import static org.assertj.core.api.Assertions.assertThat;

import com.balancify.backend.api.match.dto.BalancePlayerDto;
import com.balancify.backend.api.match.dto.BalanceResponse;
import com.balancify.backend.api.match.dto.MatchResultParticipantResponse;
import com.balancify.backend.api.match.dto.MatchResultResponse;
import com.balancify.backend.api.match.dto.MultiBalanceMatchResponse;
import com.balancify.backend.api.match.dto.MultiBalancePenaltySummaryResponse;
import com.balancify.backend.api.match.dto.MultiBalanceRaceSummaryResponse;
import com.balancify.backend.api.match.dto.MultiBalanceResponse;
import com.balancify.backend.api.match.dto.MultiBalanceWaitingPlayerResponse;
import java.util.List;
import org.junit.jupiter.api.Test;

class MmrMaskingMapperTest {

    @Test
    void maskBalanceRemovesMmrFields() {
        BalanceResponse source = new BalanceResponse(
            3,
            List.of(
                new BalancePlayerDto(1L, "A", 1200),
                new BalancePlayerDto(2L, "B", 1300),
                new BalancePlayerDto(3L, "C", 1400)
            ),
            List.of(
                new BalancePlayerDto(4L, "D", 1250),
                new BalancePlayerDto(5L, "E", 1270),
                new BalancePlayerDto(6L, "F", 1280)
            ),
            3900,
            3800,
            100,
            0.64
        );

        BalanceResponse masked = MmrMaskingMapper.maskBalance(source);

        assertThat(masked.homeMmr()).isNull();
        assertThat(masked.awayMmr()).isNull();
        assertThat(masked.mmrDiff()).isNull();
        assertThat(masked.expectedHomeWinRate()).isEqualTo(0.64);
        assertThat(masked.homeTeam().getFirst().mmr()).isNull();
        assertThat(masked.awayTeam().getFirst().mmr()).isNull();
    }

    @Test
    void maskMultiBalanceRemovesMmrFields() {
        MultiBalanceResponse source = new MultiBalanceResponse(
            "MMR_FIRST",
            6,
            6,
            List.of(new MultiBalanceWaitingPlayerResponse(7L, "W")),
            1,
            List.of(
                new MultiBalanceMatchResponse(
                    1,
                    "3v3",
                    3,
                    List.of(
                        new BalancePlayerDto(1L, "A", 1200),
                        new BalancePlayerDto(2L, "B", 1300),
                        new BalancePlayerDto(3L, "C", 1400)
                    ),
                    List.of(
                        new BalancePlayerDto(4L, "D", 1250),
                        new BalancePlayerDto(5L, "E", 1270),
                        new BalancePlayerDto(6L, "F", 1280)
                    ),
                    3900,
                    3800,
                    100,
                    0.58,
                    new MultiBalanceRaceSummaryResponse("PPT", "PTZ"),
                    new MultiBalancePenaltySummaryResponse(2, 1, 0)
                )
            )
        );

        MultiBalanceResponse masked = MmrMaskingMapper.maskMultiBalance(source);
        MultiBalanceMatchResponse maskedMatch = masked.matches().getFirst();

        assertThat(maskedMatch.homeMmr()).isNull();
        assertThat(maskedMatch.awayMmr()).isNull();
        assertThat(maskedMatch.mmrDiff()).isNull();
        assertThat(maskedMatch.expectedHomeWinRate()).isEqualTo(0.58);
        assertThat(maskedMatch.homeTeam().getFirst().mmr()).isNull();
    }

    @Test
    void maskMatchResultRemovesMmrFields() {
        MatchResultResponse source = new MatchResultResponse(
            44L,
            "HOME",
            32,
            0.58,
            0.42,
            List.of(
                new MatchResultParticipantResponse(1L, "A", "HOME", 1200, 1216, 16),
                new MatchResultParticipantResponse(2L, "B", "AWAY", 1180, 1164, -16)
            )
        );

        MatchResultResponse masked = MmrMaskingMapper.maskMatchResult(source);

        assertThat(masked.homeExpectedWinRate()).isNull();
        assertThat(masked.awayExpectedWinRate()).isNull();
        assertThat(masked.participants()).allSatisfy(participant -> {
            assertThat(participant.mmrBefore()).isNull();
            assertThat(participant.mmrAfter()).isNull();
            assertThat(participant.mmrDelta()).isNull();
        });
    }
}
