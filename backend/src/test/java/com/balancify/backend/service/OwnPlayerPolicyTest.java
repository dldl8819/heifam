package com.balancify.backend.service;

import static org.assertj.core.api.Assertions.assertThat;

import com.balancify.backend.domain.MatchParticipant;
import com.balancify.backend.domain.Player;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class OwnPlayerPolicyTest {

    private static final UUID LOGIN = UUID.fromString("00000000-0000-0000-0000-000000000001");

    @Test
    void findsThePlayerLinkedToTheLoginFirst() {
        List<MatchParticipant> participants = participants();
        participants.get(1).getPlayer().setNickname("YOUR_USERNAME");
        participants.get(4).getPlayer().setAuthUserId(LOGIN);

        assertThat(OwnPlayerPolicy.findOwn(participants, LOGIN.toString(), "YOUR_USERNAME"))
            .isSameAs(participants.get(4));
    }

    @Test
    void findsThePlayerNamedLikeTheAccountIgnoringCaseAndSpaces() {
        List<MatchParticipant> participants = participants();
        participants.get(2).getPlayer().setNickname(" Your_Username ");

        assertThat(OwnPlayerPolicy.findOwn(participants, null, "your_username")).isSameAs(participants.get(2));
        assertThat(OwnPlayerPolicy.findOwn(participants, "not-a-uuid", " YOUR_USERNAME ")).isSameAs(participants.get(2));
    }

    @Test
    void findsNoOneForSomeoneElseOrAnAccountWithoutANickname() {
        List<MatchParticipant> participants = participants();
        participants.get(0).getPlayer().setNickname(" ");

        assertThat(OwnPlayerPolicy.findOwn(participants, LOGIN.toString(), "someone")).isNull();
        assertThat(OwnPlayerPolicy.findOwn(participants, null, " ")).isNull();
        assertThat(OwnPlayerPolicy.findOwn(participants, null, null)).isNull();
        assertThat(OwnPlayerPolicy.findOwn(List.of(), LOGIN.toString(), "p0")).isNull();
    }

    @Test
    void ownPlayersAreTheLinkedOneAndEveryOneNamedLikeTheAccount() {
        List<Player> players = participants().stream().map(MatchParticipant::getPlayer).toList();
        players.get(1).setAuthUserId(LOGIN);
        players.get(2).setNickname(" Your_Username ");
        players.get(4).setNickname("YOUR_USERNAME");

        assertThat(OwnPlayerPolicy.findOwnPlayers(players, LOGIN.toString(), "your_username"))
            .containsExactly(players.get(1), players.get(2), players.get(4));
        assertThat(OwnPlayerPolicy.findOwnPlayers(players, "not-a-uuid", " your_username "))
            .containsExactly(players.get(2), players.get(4));
    }

    @Test
    void ownPlayersAreNoOneForSomeoneElseOrAnAccountWithoutANickname() {
        List<Player> players = participants().stream().map(MatchParticipant::getPlayer).toList();
        players.get(0).setNickname(" ");

        assertThat(OwnPlayerPolicy.findOwnPlayers(players, LOGIN.toString(), "someone")).isEmpty();
        assertThat(OwnPlayerPolicy.findOwnPlayers(players, null, " ")).isEmpty();
        assertThat(OwnPlayerPolicy.findOwnPlayers(players, null, null)).isEmpty();
    }

    private List<MatchParticipant> participants() {
        List<MatchParticipant> participants = new ArrayList<>();
        for (int index = 0; index < 6; index++) {
            Player player = new Player();
            player.setNickname("p" + index);
            MatchParticipant participant = new MatchParticipant();
            participant.setPlayer(player);
            participant.setTeam(index < 3 ? "HOME" : "AWAY");
            participants.add(participant);
        }
        return participants;
    }
}
