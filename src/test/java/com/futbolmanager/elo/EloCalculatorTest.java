package com.futbolmanager.elo;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.futbolmanager.elo.EloCalculator.MatchInput;
import com.futbolmanager.elo.EloCalculator.PlayerInput;
import com.futbolmanager.elo.EloCalculator.PlayerResult;
import com.futbolmanager.elo.EloCalculator.Position;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class EloCalculatorTest {

    private final EloCalculator calculator = new EloCalculator();

    private static PlayerInput player(int elo, int counted, Position position) {
        return new PlayerInput(UUID.randomUUID(), elo, counted, position, 0, 0, 0, 0);
    }

    private static PlayerInput withStats(
            UUID id, Position position, int goals, int assists, int saves, int tackles) {
        return new PlayerInput(id, 1000, 0, position, goals, assists, saves, tackles);
    }

    private static List<PlayerInput> team(int size, int elo, int counted) {
        List<PlayerInput> players = new ArrayList<>();
        for (int i = 0; i < size; i++) {
            players.add(player(elo, counted, Position.MID));
        }
        return players;
    }

    private List<PlayerResult> run(List<PlayerInput> a, List<PlayerInput> b, int scoreA, int scoreB) {
        return calculator.calculate(new MatchInput(a, b, scoreA, scoreB, null));
    }

    @Test
    void awardsPlusAndMinusTwentyWhenEqualTeamsDecideAtK40() {
        List<PlayerResult> results = run(team(5, 1000, 0), team(5, 1000, 0), 2, 1);

        assertThat(results.subList(0, 5)).allSatisfy(r -> assertThat(r.teamDelta()).isEqualTo(20));
        assertThat(results.subList(5, 10)).allSatisfy(r -> assertThat(r.teamDelta()).isEqualTo(-20));
    }

    @Test
    void awardsZeroWhenEqualTeamsDraw() {
        List<PlayerResult> results = run(team(5, 1000, 0), team(5, 1000, 0), 1, 1);

        assertThat(results).allSatisfy(r -> assertThat(r.teamDelta()).isZero());
    }

    @Test
    void scalesDeltaByRatingGapForTheWeakerSideAtK40() {
        assertThat(run(team(1, 1000, 0), team(1, 1400, 0), 1, 0).get(0).teamDelta()).isEqualTo(36);
        assertThat(run(team(1, 1000, 0), team(1, 1400, 0), 0, 1).get(0).teamDelta()).isEqualTo(-4);
        assertThat(run(team(1, 1000, 0), team(1, 1400, 0), 1, 1).get(0).teamDelta()).isEqualTo(16);
    }

    @Test
    void scalesDeltaByRatingGapForTheWeakerSideAtK24() {
        assertThat(run(team(1, 1000, 10), team(1, 1400, 10), 1, 0).get(0).teamDelta()).isEqualTo(22);
        assertThat(run(team(1, 1000, 10), team(1, 1400, 10), 0, 1).get(0).teamDelta()).isEqualTo(-2);
    }

    @Test
    void usesK40UpToNineCountedMatchesAndK24FromTen() {
        List<PlayerResult> nine = run(team(1, 1000, 9), team(1, 1000, 9), 1, 0);
        List<PlayerResult> ten = run(team(1, 1000, 10), team(1, 1000, 10), 1, 0);

        assertThat(nine.get(0).teamDelta()).isEqualTo(20);
        assertThat(ten.get(0).teamDelta()).isEqualTo(12);
    }

    @Test
    void appliesKPerPlayerWithinTheSameTeam() {
        PlayerInput rookie = player(1000, 0, Position.MID);
        PlayerInput veteran = player(1000, 30, Position.MID);

        List<PlayerResult> results = run(List.of(rookie, veteran), team(2, 1000, 0), 1, 0);

        assertThat(results.get(0).teamDelta()).isEqualTo(20);
        assertThat(results.get(1).teamDelta()).isEqualTo(12);
    }

    @Test
    void capsBonusAtTenWhenGoalsAssistsAndMvpExceedIt() {
        UUID id = UUID.randomUUID();
        PlayerInput star = withStats(id, Position.FWD, 4, 2, 0, 0);

        PlayerResult result = calculator
                .calculate(new MatchInput(List.of(star), team(1, 1000, 0), 3, 1, id))
                .get(0);

        assertThat(result.bonus()).isEqualTo(10);
        assertThat(result.breakdown().uncapped()).isEqualTo(13);
        assertThat(result.breakdown().total()).isEqualTo(10);
    }

    @Test
    void countsGoalsAssistsAndMvpPoints() {
        UUID id = UUID.randomUUID();
        PlayerInput player = withStats(id, Position.MID, 1, 1, 0, 0);

        PlayerResult result = calculator
                .calculate(new MatchInput(List.of(player), team(1, 1000, 0), 1, 1, id))
                .get(0);

        assertThat(result.breakdown().goals()).isEqualTo(2);
        assertThat(result.breakdown().assists()).isEqualTo(1);
        assertThat(result.breakdown().mvp()).isEqualTo(3);
        assertThat(result.bonus()).isEqualTo(6);
    }

    @Test
    void capsGoalkeeperSavesAtThree() {
        PlayerInput keeper = withStats(UUID.randomUUID(), Position.GK, 0, 0, 5, 0);

        PlayerResult result = run(List.of(keeper), team(1, 1000, 0), 1, 1).get(0);

        assertThat(result.breakdown().saves()).isEqualTo(3);
        assertThat(result.bonus()).isEqualTo(3);
    }

    @Test
    void ignoresSavesForPlayersThatAreNotTheMatchGoalkeeper() {
        PlayerInput defender = withStats(UUID.randomUUID(), Position.DEF, 0, 0, 5, 0);

        PlayerResult result = run(List.of(defender), team(1, 1000, 0), 1, 1).get(0);

        assertThat(result.breakdown().saves()).isZero();
    }

    @Test
    void capsTacklesAtThree() {
        PlayerInput player = withStats(UUID.randomUUID(), Position.MID, 0, 0, 0, 4);

        PlayerResult result = run(List.of(player), team(1, 1000, 0), 1, 1).get(0);

        assertThat(result.breakdown().tackles()).isEqualTo(3);
        assertThat(result.bonus()).isEqualTo(3);
    }

    @Test
    void neverReturnsNegativeBonusForNegativeCounters() {
        PlayerInput player = withStats(UUID.randomUUID(), Position.GK, -3, -2, -1, -4);

        PlayerResult result = run(List.of(player), team(1, 1000, 0), 0, 1).get(0);

        assertThat(result.bonus()).isZero();
        assertThat(result.breakdown().uncapped()).isZero();
    }

    @Test
    void awardsCleanSheetOnlyToGoalkeepersAndDefenders() {
        List<PlayerInput> teamA = List.of(
                player(1000, 0, Position.GK),
                player(1000, 0, Position.DEF),
                player(1000, 0, Position.MID),
                player(1000, 0, Position.FWD));

        List<PlayerResult> results = run(teamA, team(4, 1000, 0), 1, 0);

        assertThat(results.get(0).breakdown().cleanSheet()).isEqualTo(3);
        assertThat(results.get(1).breakdown().cleanSheet()).isEqualTo(3);
        assertThat(results.get(2).breakdown().cleanSheet()).isZero();
        assertThat(results.get(3).breakdown().cleanSheet()).isZero();
    }

    @Test
    void awardsCleanSheetToDefenderOnATeamWithoutGoalkeeper() {
        List<PlayerInput> teamA = List.of(player(1000, 0, Position.DEF), player(1000, 0, Position.MID));

        List<PlayerResult> results = run(teamA, team(2, 1000, 0), 0, 0);

        assertThat(results.get(0).breakdown().cleanSheet()).isEqualTo(3);
        assertThat(results.get(1).breakdown().cleanSheet()).isZero();
    }

    @Test
    void skipsCleanSheetWhenRivalScored() {
        List<PlayerInput> teamA = List.of(player(1000, 0, Position.GK));

        List<PlayerResult> results = run(teamA, team(1, 1000, 0), 1, 1);

        assertThat(results.get(0).breakdown().cleanSheet()).isZero();
    }

    @Test
    void usesTeamAveragesWhenTeamsHaveDifferentSizes() {
        List<PlayerInput> teamA = team(5, 1000, 0);
        List<PlayerInput> teamB = team(6, 1000, 0);

        List<PlayerResult> results = run(teamA, teamB, 1, 0);

        assertThat(results).hasSize(11);
        assertThat(results.subList(0, 5)).allSatisfy(r -> assertThat(r.teamDelta()).isEqualTo(20));
        assertThat(results.subList(5, 11)).allSatisfy(r -> assertThat(r.teamDelta()).isEqualTo(-20));
    }

    @Test
    void usesAverageRatingNotTotalWhenSizesDiffer() {
        List<PlayerInput> teamA = List.of(player(1000, 0, Position.MID));
        List<PlayerInput> teamB = List.of(player(1400, 0, Position.MID), player(1400, 0, Position.MID));

        List<PlayerResult> results = run(teamA, teamB, 1, 0);

        assertThat(results.get(0).teamDelta()).isEqualTo(36);
        assertThat(results.get(1).teamDelta()).isEqualTo(-36);
    }

    @Test
    void returnsEqualResultsForTheSameInputTwice() {
        MatchInput input = new MatchInput(team(5, 1000, 3), team(6, 1100, 12), 3, 2, null);

        assertThat(calculator.calculate(input)).isEqualTo(calculator.calculate(input));
    }

    @Test
    void countsTacklesBelowTheCapOneForOne() {
        PlayerInput one = withStats(UUID.randomUUID(), Position.MID, 0, 0, 0, 1);
        PlayerInput two = withStats(UUID.randomUUID(), Position.MID, 0, 0, 0, 2);

        List<PlayerResult> results = run(List.of(one, two), team(2, 1000, 0), 1, 1);

        assertThat(results.get(0).breakdown().tackles()).isEqualTo(1);
        assertThat(results.get(1).breakdown().tackles()).isEqualTo(2);
    }

    @Test
    void awardsTacklesToEveryPosition() {
        for (Position position : Position.values()) {
            PlayerInput player = withStats(UUID.randomUUID(), position, 0, 0, 0, 2);

            PlayerResult result = run(List.of(player), team(1, 1000, 0), 1, 1).get(0);

            assertThat(result.breakdown().tackles()).as(position.name()).isEqualTo(2);
        }
    }

    @Test
    void addsCleanSheetAndCappedSavesForAGoalkeeper() {
        PlayerInput keeper = withStats(UUID.randomUUID(), Position.GK, 0, 0, 3, 0);

        PlayerResult result = run(List.of(keeper), team(1, 1000, 0), 1, 0).get(0);

        assertThat(result.breakdown().cleanSheet()).isEqualTo(3);
        assertThat(result.breakdown().saves()).isEqualTo(3);
        assertThat(result.bonus()).isEqualTo(6);
    }

    @Test
    void clampsNegativeGoalsToZero() {
        PlayerInput player = withStats(UUID.randomUUID(), Position.MID, -3, 0, 0, 0);

        assertThat(run(List.of(player), team(1, 1000, 0), 0, 1).get(0).breakdown().goals()).isZero();
    }

    @Test
    void clampsNegativeAssistsToZero() {
        PlayerInput player = withStats(UUID.randomUUID(), Position.MID, 0, -2, 0, 0);

        assertThat(run(List.of(player), team(1, 1000, 0), 0, 1).get(0).breakdown().assists()).isZero();
    }

    @Test
    void clampsNegativeSavesToZero() {
        PlayerInput keeper = withStats(UUID.randomUUID(), Position.GK, 0, 0, -1, 0);

        assertThat(run(List.of(keeper), team(1, 1000, 0), 0, 1).get(0).breakdown().saves()).isZero();
    }

    @Test
    void clampsNegativeTacklesToZero() {
        PlayerInput player = withStats(UUID.randomUUID(), Position.MID, 0, 0, 0, -4);

        assertThat(run(List.of(player), team(1, 1000, 0), 0, 1).get(0).breakdown().tackles()).isZero();
    }

    @Test
    void rejectsAnEmptyTeam() {
        assertThatThrownBy(() -> run(List.of(), team(1, 1000, 0), 0, 0))
                .isInstanceOf(IllegalArgumentException.class);
    }
}
