package com.balancify.backend.api.group;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.balancify.backend.api.group.dto.RankingItemResponse;
import com.balancify.backend.security.AuthenticatedRequestResolver;
import com.balancify.backend.security.AuthenticatedRequestResolver.ResolvedRequestIdentity;
import com.balancify.backend.service.AccessControlService;
import com.balancify.backend.service.AccessControlService.AccessProfile;
import com.balancify.backend.service.RankingService;
import jakarta.servlet.http.HttpServletRequest;
import java.util.List;
import org.junit.jupiter.api.Test;

class GroupRankingControllerTest {

    private static final String EMAIL = "YOUR_USERNAME@example.com";

    @Test
    void superAdminSeesRankingMmr() {
        RankingItemResponse item = rankingResponseFor(profile(true, true, true)).get(0);

        assertThat(item.currentMmr()).isEqualTo(1500);
        assertThat(item.mmrDelta()).isEqualTo(12);
    }

    @Test
    void adminGrantedMmrAccessSeesRankingMmr() {
        RankingItemResponse item = rankingResponseFor(profile(true, false, true)).get(0);

        assertThat(item.currentMmr()).isEqualTo(1500);
        assertThat(item.mmrDelta()).isEqualTo(12);
    }

    @Test
    void adminWithoutMmrAccessGetsMaskedRanking() {
        RankingItemResponse item = rankingResponseFor(profile(true, false, false)).get(0);

        assertThat(item.currentMmr()).isNull();
        assertThat(item.mmrDelta()).isNull();
        assertThat(item.wins()).isEqualTo(7);
    }

    @Test
    void memberGetsMaskedRanking() {
        RankingItemResponse item = rankingResponseFor(profile(false, false, false)).get(0);

        assertThat(item.currentMmr()).isNull();
        assertThat(item.mmrDelta()).isNull();
    }

    private List<RankingItemResponse> rankingResponseFor(AccessProfile accessProfile) {
        RankingService rankingService = mock(RankingService.class);
        AccessControlService accessControlService = mock(AccessControlService.class);
        AuthenticatedRequestResolver authenticatedRequestResolver = mock(AuthenticatedRequestResolver.class);
        HttpServletRequest request = mock(HttpServletRequest.class);

        when(authenticatedRequestResolver.resolve(any())).thenReturn(
            new ResolvedRequestIdentity(EMAIL, "YOUR_USERNAME", true)
        );
        when(accessControlService.resolveAccessProfile(EMAIL)).thenReturn(accessProfile);
        when(rankingService.getGroupRanking(1L)).thenReturn(List.of(
            new RankingItemResponse(1, "YOUR_USERNAME", "P", "A", 1500, 7, 3, 10, 0.7, "W2", "WWLWWLWWLW", 12, false)
        ));

        GroupRankingController controller = new GroupRankingController(
            rankingService,
            accessControlService,
            authenticatedRequestResolver
        );
        return controller.getGroupRanking(1L, request);
    }

    private AccessProfile profile(boolean admin, boolean superAdmin, boolean canViewMmr) {
        return new AccessProfile(EMAIL, "YOUR_USERNAME", "MEMBER", admin, superAdmin, true, canViewMmr, null);
    }
}
