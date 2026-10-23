package com.futbolmanager.match.balance;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.assertj.core.api.Assertions.within;
import static org.junit.jupiter.api.Assertions.assertTimeoutPreemptively;

import com.futbolmanager.match.balance.TeamBalancer.Assignment;
import com.futbolmanager.match.balance.TeamBalancer.Candidate;
import com.futbolmanager.match.balance.TeamBalancer.Position;
import com.futbolmanager.match.balance.TeamBalancer.Result;
import java.time.Duration;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;
import java.util.Random;
import java.util.UUID;
import java.util.stream.Stream;
import org.junit.jupiter.api.Test;

class TeamBalancerTest {

    private final TeamBalancer balancer = new TeamBalancer();

    private static List<Candidate> randomCandidates(Random random, int count, Position position) {
        List<Candidate> candidates = new ArrayList<>();
        for (int i = 0; i < count; i++) {
            UUID id = new UUID(random.nextLong(), random.nextLong());
            candidates.add(new Candidate(id, 800 + random.nextInt(600), position));
        }
        return candidates;
    }

    private static List<UUID> ids(List<Assignment> team) {
        return team.stream().map(Assignment::playerId).toList();
    }

    private static UUID lowestId(List<Candidate> candidates) {
        return candidates.stream().map(Candidate::id).min(Comparator.naturalOrder()).orElseThrow();
    }

    /** Oráculo independiente: prueba todas las máscaras y compara promedios en double con tolerancia. */
    private static double bruteForceMinimumDifference(List<Candidate> candidates) {
        int n = candidates.size();
        double best = Double.MAX_VALUE;
        for (int mask = 1; mask < (1 << n) - 1; mask++) {
            int sizeA = Integer.bitCount(mask);
            if (Math.abs(n - 2 * sizeA) > 1) {
                continue;
            }
            double sumA = 0;
            double sumB = 0;
            for (int i = 0; i < n; i++) {
                if ((mask & (1 << i)) != 0) {
                    sumA += candidates.get(i).elo();
                } else {
                    sumB += candidates.get(i).elo();
                }
            }
            best = Math.min(best, Math.abs(sumA / sizeA - sumB / (n - sizeA)));
        }
        return best;
    }

    @Test
    void matchesBruteForceMinimumForTenPlayers() {
        Random random = new Random(42);
        for (int round = 0; round < 25; round++) {
            List<Candidate> candidates = randomCandidates(random, 10, Position.MID);

            Result result = balancer.balance(candidates, false);

            assertThat(result.teamA()).hasSize(5);
            assertThat(result.teamB()).hasSize(5);
            assertThat(result.eloDifference())
                    .isCloseTo(bruteForceMinimumDifference(candidates), within(1e-9));
        }
    }

    @Test
    void matchesBruteForceMinimumForSmallerSizesToo() {
        Random random = new Random(7);
        for (int count = 2; count <= 9; count++) {
            List<Candidate> candidates = randomCandidates(random, count, Position.MID);

            Result result = balancer.balance(candidates, false);

            assertThat(result.eloDifference())
                    .isCloseTo(bruteForceMinimumDifference(candidates), within(1e-9));
        }
    }

    @Test
    void matchesBruteForceAverageEloForElevenPlayersSplitSixAndFive() {
        Random random = new Random(21);
        for (int round = 0; round < 5; round++) {
            List<Candidate> candidates = randomCandidates(random, 11, Position.MID);

            Result result = balancer.balance(candidates, false);

            assertThat(List.of(result.teamA().size(), result.teamB().size())).containsExactlyInAnyOrder(6, 5);
            assertThat(result.avgEloA()).isCloseTo(averageElo(candidates, result.teamA()), within(1e-9));
            assertThat(result.avgEloB()).isCloseTo(averageElo(candidates, result.teamB()), within(1e-9));
            assertThat(result.eloDifference())
                    .isCloseTo(Math.abs(result.avgEloA() - result.avgEloB()), within(1e-9))
                    .isCloseTo(bruteForceMinimumDifference(candidates), within(1e-9));
        }
    }

    private static double averageElo(List<Candidate> candidates, List<Assignment> team) {
        return team.stream()
                .mapToInt(a -> candidates.stream()
                        .filter(c -> c.id().equals(a.playerId()))
                        .findFirst()
                        .orElseThrow()
                        .elo())
                .average()
                .orElseThrow();
    }

    @Test
    void splitsTwoThreeAndElevenPlayersIntoNearEqualSizes() {
        Random random = new Random(1);

        Result two = balancer.balance(randomCandidates(random, 2, Position.MID), false);
        Result three = balancer.balance(randomCandidates(random, 3, Position.MID), false);
        Result eleven = balancer.balance(randomCandidates(random, 11, Position.MID), false);

        assertThat(List.of(two.teamA().size(), two.teamB().size())).containsExactlyInAnyOrder(1, 1);
        assertThat(List.of(three.teamA().size(), three.teamB().size())).containsExactlyInAnyOrder(2, 1);
        assertThat(List.of(eleven.teamA().size(), eleven.teamB().size())).containsExactlyInAnyOrder(6, 5);
    }

    @Test
    void putsExactlyOneGoalkeeperPerSideWhenTwoOrMoreAreAvailable() {
        List<Candidate> candidates = new ArrayList<>(randomCandidates(new Random(3), 8, Position.MID));
        for (int i = 0; i < 3; i++) {
            candidates.set(i, new Candidate(candidates.get(i).id(), 1500, Position.GK));
        }
        List<UUID> goalkeepers = candidates.stream()
                .filter(c -> c.preferredPosition() == Position.GK)
                .map(Candidate::id)
                .toList();

        Result result = balancer.balance(candidates, true);

        assertThat(result.goalkeeperConstraintApplied()).isTrue();
        assertThat(result.teamA()).filteredOn(a -> a.position() == Position.GK).hasSize(1);
        assertThat(result.teamB()).filteredOn(a -> a.position() == Position.GK).hasSize(1);
        assertThat(ids(result.teamA()).stream().filter(goalkeepers::contains).count()).isGreaterThanOrEqualTo(1);
        assertThat(ids(result.teamB()).stream().filter(goalkeepers::contains).count()).isGreaterThanOrEqualTo(1);
    }

    @Test
    void demotesExtraPreferredGoalkeepersToDefenders() {
        List<Candidate> candidates = new ArrayList<>();
        for (int i = 0; i < 6; i++) {
            candidates.add(new Candidate(new UUID(0, i + 1), 1000, Position.GK));
        }

        Result result = balancer.balance(candidates, true);

        assertThat(result.teamA()).filteredOn(a -> a.position() == Position.GK).hasSize(1);
        assertThat(result.teamB()).filteredOn(a -> a.position() == Position.GK).hasSize(1);
        assertThat(result.teamA()).filteredOn(a -> a.position() == Position.DEF).hasSize(2);
        assertThat(result.teamB()).filteredOn(a -> a.position() == Position.DEF).hasSize(2);
    }

    @Test
    void ignoresGoalkeeperOptionWithASinglePreferredGoalkeeper() {
        List<Candidate> candidates = new ArrayList<>(randomCandidates(new Random(5), 6, Position.FWD));
        candidates.set(0, new Candidate(candidates.get(0).id(), 1200, Position.GK));

        Result result = balancer.balance(candidates, true);

        assertThat(result.goalkeeperConstraintApplied()).isFalse();
        assertThat(result.eloDifference())
                .isCloseTo(bruteForceMinimumDifference(candidates), within(1e-9));
    }

    @Test
    void keepsPreferredPositionsWhenOptionIsOff() {
        List<Candidate> candidates = new ArrayList<>(randomCandidates(new Random(11), 6, Position.DEF));
        candidates.set(0, new Candidate(candidates.get(0).id(), 1000, Position.GK));
        candidates.set(1, new Candidate(candidates.get(1).id(), 1000, Position.GK));

        Result result = balancer.balance(candidates, false);

        assertThat(result.goalkeeperConstraintApplied()).isFalse();
        long goalkeepers = Stream.concat(result.teamA().stream(), result.teamB().stream())
                .filter(a -> a.position() == Position.GK)
                .count();
        assertThat(goalkeepers).isEqualTo(2);
    }

    @Test
    void returnsSamePartitionRegardlessOfInputOrderWithLowestIdInTeamA() {
        List<Candidate> random = randomCandidates(new Random(99), 10, Position.MID);
        List<Candidate> tied = random.stream()
                .map(c -> new Candidate(c.id(), 1000, c.preferredPosition()))
                .toList();

        for (List<Candidate> input : List.of(random, tied)) {
            Result first = balancer.balance(input, false);
            List<Candidate> shuffled = new ArrayList<>(input);
            Collections.shuffle(shuffled, new Random(123));

            Result second = balancer.balance(shuffled, false);

            assertThat(second).isEqualTo(first);
            assertThat(ids(first.teamA())).contains(lowestId(input));
        }
    }

    @Test
    void breaksTiesWithLexicographicallyLowestIdsInTeamA() {
        List<Candidate> candidates = new ArrayList<>();
        for (int i = 1; i <= 4; i++) {
            candidates.add(new Candidate(new UUID(0, i), 1000, Position.MID));
        }

        Result result = balancer.balance(candidates, false);

        assertThat(ids(result.teamA())).containsExactly(new UUID(0, 1), new UUID(0, 2));
        assertThat(ids(result.teamB())).containsExactly(new UUID(0, 3), new UUID(0, 4));
    }

    @Test
    void rejectsTwentyThreePlayers() {
        List<Candidate> candidates = randomCandidates(new Random(2), 23, Position.MID);

        assertThatThrownBy(() -> balancer.balance(candidates, false)).isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void rejectsFewerThanTwoPlayers() {
        List<Candidate> candidates = randomCandidates(new Random(2), 1, Position.MID);

        assertThatThrownBy(() -> balancer.balance(candidates, false)).isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void balancesTwentyTwoPlayersInUnderTwoSeconds() {
        List<Candidate> candidates = randomCandidates(new Random(8), 22, Position.MID);

        Result result = assertTimeoutPreemptively(Duration.ofSeconds(2), () -> balancer.balance(candidates, false));

        assertThat(result.teamA()).hasSize(11);
        assertThat(result.teamB()).hasSize(11);
    }
}
