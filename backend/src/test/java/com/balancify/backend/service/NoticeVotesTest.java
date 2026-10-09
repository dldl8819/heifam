package com.balancify.backend.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import org.junit.jupiter.api.Test;

class NoticeVotesTest {

    @Test
    void aVoteWithoutNamedOptionsIsForOrAgainst() {
        assertThat(NoticeVotes.requireOptions(null)).containsExactly("찬성", "반대");
    }

    @Test
    void keepsTheOptionsInOrderTrimmedAndWithoutTheEmptyOnes() {
        assertThat(NoticeVotes.requireOptions(Arrays.asList(" 30분 ", "", "25분", null, "   ", "24분")))
            .containsExactly("30분", "25분", "24분");
    }

    @Test
    void needsTwoOptionsAndTakesNoMoreThanAVoteHolds() {
        List<String> full = new ArrayList<>();
        for (int index = 0; index < NoticeVotes.MAX_OPTIONS; index++) {
            full.add("option " + index);
        }
        assertThat(NoticeVotes.requireOptions(full)).hasSize(NoticeVotes.MAX_OPTIONS);

        List<String> oneTooMany = new ArrayList<>(full);
        oneTooMany.add("one more");
        for (List<String> options : List.of(List.<String>of(), List.of("alone"), Arrays.asList("alone", " ", null), oneTooMany)) {
            assertThatThrownBy(() -> NoticeVotes.requireOptions(options)).isInstanceOf(IllegalArgumentException.class);
        }
    }

    @Test
    void refusesTheSameOptionTwiceWhateverItsCase() {
        for (List<String> options : List.of(List.of("30분", "30분"), List.of("Yes", " yes "), List.of("a", "b", "A"))) {
            assertThatThrownBy(() -> NoticeVotes.requireOptions(options)).isInstanceOf(IllegalArgumentException.class);
        }
    }

    @Test
    void limitsHowLongAnOptionMayBe() {
        String longest = "x".repeat(NoticeVotes.MAX_OPTION_LENGTH);

        assertThat(NoticeVotes.requireOptionLabel("  " + longest + "  ")).isEqualTo(longest);
        assertThat(NoticeVotes.requireOptions(List.of(longest, "short"))).containsExactly(longest, "short");
        for (String label : new String[] {null, "", "   ", longest + "x"}) {
            assertThatThrownBy(() -> NoticeVotes.requireOptionLabel(label)).isInstanceOf(IllegalArgumentException.class);
        }
        assertThatThrownBy(() -> NoticeVotes.requireOptions(List.of(longest + "x", "short")))
            .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void readsWhatAnOlderPageSendsAsForOrAgainst() {
        assertThat(NoticeVotes.legacyLabel(" agree ")).isEqualTo("찬성");
        assertThat(NoticeVotes.legacyLabel("DISAGREE")).isEqualTo("반대");
        for (String choice : new String[] {null, "", "ABSTAIN", "찬성"}) {
            assertThatThrownBy(() -> NoticeVotes.legacyLabel(choice)).isInstanceOf(IllegalArgumentException.class);
        }
    }

    @Test
    void knowsWhichStatusesAskForAVote() {
        assertThat(NoticeVotes.asksForVote("OPEN")).isTrue();
        assertThat(NoticeVotes.asksForVote("CLOSED")).isTrue();
        assertThat(NoticeVotes.asksForVote("NONE")).isFalse();
        assertThat(NoticeVotes.asksForVote(null)).isFalse();
    }
}
