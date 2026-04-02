package com.balancify.backend.api;

import static org.assertj.core.api.Assertions.assertThat;

import com.balancify.backend.api.match.dto.BalancePlayerDto;
import com.balancify.backend.api.match.dto.BalanceResponse;
import com.balancify.backend.api.match.dto.MultiBalanceMatchResponse;
import com.balancify.backend.api.match.dto.MultiBalancePenaltySummaryResponse;
import com.balancify.backend.api.match.dto.MultiBalanceRaceSummaryResponse;
import com.balancify.backend.api.match.dto.MultiBalanceResponse;
import com.balancify.backend.api.match.dto.MultiBalanceWaitingPlayerResponse;
import java.util.List;
import org.junit.jupiter.api.Test;

class MmrMaskingMapperTest {

    @Test
    void maskBalanceKeepsExpectedHomeWinRate() {
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

        assertThat(masked.homeMmr()).isZero();
        assertThat(masked.awayMmr()).isZero();
        assertThat(masked.mmrDiff()).isZero();
        assertThat(masked.expectedHomeWinRate()).isEqualTo(0.64);
    }

    @Test
    void maskMultiBalanceKeepsExpectedHomeWinRatePerMatch() {
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

        assertThat(maskedMatch.homeMmr()).isZero();
        assertThat(maskedMatch.awayMmr()).isZero();
        assertThat(maskedMatch.mmrDiff()).isZero();
        assertThat(maskedMatch.expectedHomeWinRate()).isEqualTo(0.58);
    }
}
