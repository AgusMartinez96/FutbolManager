package com.futbolmanager.match.balance;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.assertj.core.api.Assertions.within;
import static org.junit.jupiter.api.Assertions.assertTimeoutPreemptively;

import com.futbolmanager.match.balance.TeamBalancer.Candidate;
import com.futbolmanager.match.balance.TeamBalancer.Result;
import java.time.Duration;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;
import java.util.Random;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class TeamBalancerTest {

    private static final double EPSILON = 1e-9;

    private final TeamBalancer balancer = new TeamBalancer();

    private static List<Candidate> randomCandidates(Random random, int count) {
        List<Candidate> candidates = new ArrayList<>();
        for (int i = 0; i < count; i++) {
            UUID id = new UUID(random.nextLong(), random.nextLong());
            candidates.add(new Candidate(id, 10 + random.nextInt(91), 800 + random.nextInt(600)));
        }
        return candidates;
    }

    private static UUID lowestId(List<Candidate> candidates) {
        return candidates.stream().map(Candidate::id).min(Comparator.naturalOrder()).orElseThrow();
    }

    /**
     * Oráculo independiente: prueba todas las máscaras, calcula las diferencias en double y devuelve
     * {mínima diferencia de nivel, mínima diferencia de ELO entre las empatadas en nivel}.
     */
    private static double[] bruteForceMinimum(List<Candidate> candidates) {
        int n = candidates.size();
        double bestLevel = Double.MAX_VALUE;
        double bestElo = Double.MAX_VALUE;
        for (int mask = 1; mask < (1 << n) - 1; mask++) {
            int sizeA = Integer.bitCount(mask);
            if (Math.abs(n - 2 * sizeA) > 1) {
                continue;
            }
            double levelA = 0;
            double levelB = 0;
            double eloA = 0;
            double eloB = 0;
            for (int i = 0; i < n; i++) {
                if ((mask & (1 << i)) != 0) {
                    levelA += candidates.get(i).levelTenths() / 10.0;
                    eloA += candidates.get(i).elo();
                } else {
                    levelB += candidates.get(i).levelTenths() / 10.0;
                    eloB += candidates.get(i).elo();
                }
            }
            double levelDifference = Math.abs(levelA / sizeA - levelB / (n - sizeA));
            double eloDifference = Math.abs(eloA / sizeA - eloB / (n - sizeA));
            if (levelDifference < bestLevel - EPSILON) {
                bestLevel = levelDifference;
                bestElo = eloDifference;
            } else if (Math.abs(levelDifference - bestLevel) <= EPSILON) {
                bestElo = Math.min(bestElo, eloDifference);
            }
        }
        return new double[] {bestLevel, bestElo};
    }

    private static void assertMatchesOracle(List<Candidate> candidates, Result result) {
        double[] expected = bruteForceMinimum(candidates);
        assertThat(result.levelDifference()).isCloseTo(expected[0], within(EPSILON));
        assertThat(result.eloDifference()).isCloseTo(expected[1], within(EPSILON));
    }

    private static double average(List<Candidate> candidates, List<UUID> team, boolean level) {
        return team.stream()
                .mapToDouble(id -> candidates.stream()
                        .filter(c -> c.id().equals(id))
                        .mapToDouble(c -> level ? c.levelTenths() / 10.0 : c.elo())
                        .findFirst()
                        .orElseThrow())
                .average()
                .orElseThrow();
    }

    @Test
    void matchesBruteForceMinimumLevelThenEloForTenPlayers() {
        Random random = new Random(42);
        for (int round = 0; round < 25; round++) {
            List<Candidate> candidates = randomCandidates(random, 10);

            Result result = balancer.balance(candidates);

            assertThat(result.teamA()).hasSize(5);
            assertThat(result.teamB()).hasSize(5);
            assertMatchesOracle(candidates, result);
        }
    }

    @Test
    void matchesBruteForceMinimumForSmallerSizesToo() {
        Random random = new Random(7);
        for (int count = 2; count <= 9; count++) {
            List<Candidate> candidates = randomCandidates(random, count);

            assertMatchesOracle(candidates, balancer.balance(candidates));
        }
    }

    @Test
    void matchesBruteForceAveragesForElevenPlayersSplitSixAndFive() {
        Random random = new Random(21);
        for (int round = 0; round < 5; round++) {
            List<Candidate> candidates = randomCandidates(random, 11);

            Result result = balancer.balance(candidates);

            assertThat(List.of(result.teamA().size(), result.teamB().size())).containsExactlyInAnyOrder(6, 5);
            assertThat(result.avgLevelA()).isCloseTo(average(candidates, result.teamA(), true), within(EPSILON));
            assertThat(result.avgLevelB()).isCloseTo(average(candidates, result.teamB(), true), within(EPSILON));
            assertThat(result.avgEloA()).isCloseTo(average(candidates, result.teamA(), false), within(EPSILON));
            assertThat(result.avgEloB()).isCloseTo(average(candidates, result.teamB(), false), within(EPSILON));
            assertThat(result.levelDifference())
                    .isCloseTo(Math.abs(result.avgLevelA() - result.avgLevelB()), within(EPSILON));
            assertThat(result.eloDifference())
                    .isCloseTo(Math.abs(result.avgEloA() - result.avgEloB()), within(EPSILON));
            assertMatchesOracle(candidates, result);
        }
    }

    @Test
    void splitsTwoThreeAndElevenPlayersIntoNearEqualSizes() {
        Random random = new Random(1);

        Result two = balancer.balance(randomCandidates(random, 2));
        Result three = balancer.balance(randomCandidates(random, 3));
        Result eleven = balancer.balance(randomCandidates(random, 11));

        assertThat(List.of(two.teamA().size(), two.teamB().size())).containsExactlyInAnyOrder(1, 1);
        assertThat(List.of(three.teamA().size(), three.teamB().size())).containsExactlyInAnyOrder(2, 1);
        assertThat(List.of(eleven.teamA().size(), eleven.teamB().size())).containsExactlyInAnyOrder(6, 5);
    }

    @Test
    void levelDecidesBeforeEloEvenWhenEloWouldBalanceBetter() {
        // Por nivel la única partición perfecta es {1,4} vs {2,3} (promedio 5 y 5), pero el ELO
        // quedaría 1000 vs 1400; {1,2} vs {3,4} iguala el ELO a 1200 pero deja el nivel en 7 vs 3.
        UUID one = new UUID(0, 1);
        UUID two = new UUID(0, 2);
        UUID three = new UUID(0, 3);
        UUID four = new UUID(0, 4);
        List<Candidate> candidates = List.of(
                new Candidate(one, 90, 1000),
                new Candidate(two, 50, 1400),
                new Candidate(three, 50, 1400),
                new Candidate(four, 10, 1000));

        Result result = balancer.balance(candidates);

        assertThat(result.teamA()).containsExactlyInAnyOrder(one, four);
        assertThat(result.teamB()).containsExactlyInAnyOrder(two, three);
        assertThat(result.levelDifference()).isZero();
        assertThat(result.eloDifference()).isCloseTo(400.0, within(EPSILON));
    }

    @Test
    void usesEloAsTieBreakerWhenLevelDifferenceIsEqual() {
        // Todos del mismo nivel: toda partición empata en nivel y decide el ELO.
        UUID a = new UUID(0, 1);
        UUID b = new UUID(0, 2);
        UUID c = new UUID(0, 3);
        UUID d = new UUID(0, 4);
        List<Candidate> candidates = List.of(
                new Candidate(a, 50, 1400),
                new Candidate(b, 50, 1000),
                new Candidate(c, 50, 1300),
                new Candidate(d, 50, 1100));

        Result result = balancer.balance(candidates);

        assertThat(result.levelDifference()).isZero();
        assertThat(result.teamA()).containsExactlyInAnyOrder(a, b);
        assertThat(result.teamB()).containsExactlyInAnyOrder(c, d);
        assertThat(result.eloDifference()).isCloseTo(0.0, within(EPSILON));
    }

    @Test
    void returnsSamePartitionRegardlessOfInputOrderWithLowestIdInTeamA() {
        List<Candidate> random = randomCandidates(new Random(99), 10);
        List<Candidate> tied = random.stream().map(c -> new Candidate(c.id(), 50, 1000)).toList();

        for (List<Candidate> input : List.of(random, tied)) {
            Result first = balancer.balance(input);
            List<Candidate> shuffled = new ArrayList<>(input);
            Collections.shuffle(shuffled, new Random(123));

            Result second = balancer.balance(shuffled);

            assertThat(second).isEqualTo(first);
            assertThat(first.teamA()).contains(lowestId(input));
        }
    }

    @Test
    void breaksTiesWithLexicographicallyLowestIdsInTeamA() {
        List<Candidate> candidates = new ArrayList<>();
        for (int i = 1; i <= 4; i++) {
            candidates.add(new Candidate(new UUID(0, i), 50, 1000));
        }

        Result result = balancer.balance(candidates);

        assertThat(result.teamA()).containsExactly(new UUID(0, 1), new UUID(0, 2));
        assertThat(result.teamB()).containsExactly(new UUID(0, 3), new UUID(0, 4));
    }

    @Test
    void rejectsLevelsOutsideOneToTenInTenths() {
        for (int invalid : new int[] {0, 9, 101, 110, -30}) {
            List<Candidate> candidates =
                    List.of(new Candidate(new UUID(0, 1), 50, 1000), new Candidate(new UUID(0, 2), invalid, 1000));

            assertThatThrownBy(() -> balancer.balance(candidates)).isInstanceOf(IllegalArgumentException.class);
        }
    }

    @Test
    void acceptsTheBoundaryLevelsOneAndTen() {
        List<Candidate> candidates =
                List.of(new Candidate(new UUID(0, 1), 10, 1000), new Candidate(new UUID(0, 2), 100, 1000));

        Result result = balancer.balance(candidates);

        assertThat(result.levelDifference()).isEqualTo(9.0);
    }

    @Test
    void comparesDecimalLevelsExactlyWithoutFloatingPointError() {
        UUID eightAndAHalf = new UUID(0, 1);
        UUID fourPointEight = new UUID(0, 2);

        Result result = balancer.balance(
                List.of(new Candidate(eightAndAHalf, 85, 1000), new Candidate(fourPointEight, 48, 1000)));

        assertThat(result.teamA()).containsExactly(eightAndAHalf);
        assertThat(result.teamB()).containsExactly(fourPointEight);
        assertThat(result.avgLevelA()).isEqualTo(8.5);
        assertThat(result.avgLevelB()).isEqualTo(4.8);
        assertThat(result.levelDifference()).isEqualTo(3.7);
    }

    @Test
    void picksThePartitionThatIsBetterByASingleTenth() {
        // 1,0 / 2,0 / 3,0 / 4,1: {1,4} vs {2,3} difiere en 0,05; {1,3} vs {2,4} en 1,05; {1,2} vs {3,4} en 2,05.
        UUID one = new UUID(0, 1);
        UUID two = new UUID(0, 2);
        UUID three = new UUID(0, 3);
        UUID four = new UUID(0, 4);
        List<Candidate> candidates = List.of(
                new Candidate(one, 10, 1000),
                new Candidate(two, 20, 1000),
                new Candidate(three, 30, 1000),
                new Candidate(four, 41, 1000));

        Result result = balancer.balance(candidates);

        assertThat(result.teamA()).containsExactlyInAnyOrder(one, four);
        assertThat(result.teamB()).containsExactlyInAnyOrder(two, three);
        assertThat(result.levelDifference()).isEqualTo(0.05);
    }

    @Test
    void rejectsTwentyThreePlayers() {
        List<Candidate> candidates = randomCandidates(new Random(2), 23);

        assertThatThrownBy(() -> balancer.balance(candidates)).isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void rejectsFewerThanTwoPlayers() {
        List<Candidate> candidates = randomCandidates(new Random(2), 1);

        assertThatThrownBy(() -> balancer.balance(candidates)).isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void balancesTwentyTwoPlayersInUnderTwoSeconds() {
        List<Candidate> candidates = randomCandidates(new Random(8), 22);

        Result result = assertTimeoutPreemptively(Duration.ofSeconds(2), () -> balancer.balance(candidates));

        assertThat(result.teamA()).hasSize(11);
        assertThat(result.teamB()).hasSize(11);
    }
}
