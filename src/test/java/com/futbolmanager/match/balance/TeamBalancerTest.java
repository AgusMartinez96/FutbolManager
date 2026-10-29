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
import java.util.HashSet;
import java.util.List;
import java.util.Random;
import java.util.Set;
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
     * Oráculo independiente: prueba todas las máscaras con el primer jugador en A y devuelve las (hasta 5)
     * mejores tuplas {diferencia de suma de nivel en décimas, diferencia de suma de ELO}, ordenadas.
     */
    private static List<long[]> bruteForceBest(List<Candidate> candidates) {
        int n = candidates.size();
        List<long[]> all = new ArrayList<>();
        for (int mask = 1; mask < (1 << n); mask += 2) {
            int sizeA = Integer.bitCount(mask);
            if (Math.abs(n - 2 * sizeA) > 1) {
                continue;
            }
            long levelA = 0;
            long levelB = 0;
            long eloA = 0;
            long eloB = 0;
            for (int i = 0; i < n; i++) {
                if ((mask & (1 << i)) != 0) {
                    levelA += candidates.get(i).levelTenths();
                    eloA += candidates.get(i).elo();
                } else {
                    levelB += candidates.get(i).levelTenths();
                    eloB += candidates.get(i).elo();
                }
            }
            all.add(new long[] {Math.abs(levelA - levelB), Math.abs(eloA - eloB)});
        }
        all.sort(Comparator.<long[]>comparingLong(tuple -> tuple[0]).thenComparingLong(tuple -> tuple[1]));
        return all.subList(0, Math.min(5, all.size()));
    }

    private static long sum(List<Candidate> candidates, List<UUID> team, boolean level) {
        return team.stream()
                .mapToLong(id -> candidates.stream()
                        .filter(c -> c.id().equals(id))
                        .mapToLong(c -> level ? c.levelTenths() : c.elo())
                        .findFirst()
                        .orElseThrow())
                .sum();
    }

    private static void assertMatchesOracle(List<Candidate> candidates, List<Result> results) {
        List<long[]> expected = bruteForceBest(candidates);
        assertThat(results).hasSize(expected.size());
        for (int i = 0; i < expected.size(); i++) {
            Result result = results.get(i);
            long levelDifference =
                    Math.abs(sum(candidates, result.teamA(), true) - sum(candidates, result.teamB(), true));
            long eloDifference =
                    Math.abs(sum(candidates, result.teamA(), false) - sum(candidates, result.teamB(), false));
            assertThat(levelDifference).isEqualTo(expected.get(i)[0]);
            assertThat(eloDifference).isEqualTo(expected.get(i)[1]);
            assertThat(result.levelDifference()).isCloseTo(expected.get(i)[0] / 10.0, within(EPSILON));
        }
    }

    private static void assertDistinctWithLowestIdInTeamA(List<Candidate> candidates, List<Result> results) {
        Set<List<UUID>> teams = new HashSet<>();
        for (Result result : results) {
            assertThat(result.teamA()).contains(lowestId(candidates));
            assertThat(Math.abs(result.teamA().size() - result.teamB().size())).isLessThanOrEqualTo(1);
            assertThat(result.teamA().size() + result.teamB().size()).isEqualTo(candidates.size());
            assertThat(teams.add(result.teamA())).isTrue();
        }
    }

    @Test
    void returnsTheFiveBestPartitionsMatchingTheBruteForceOracle() {
        Random random = new Random(42);
        for (int count = 2; count <= 10; count++) {
            for (int round = 0; round < 8; round++) {
                List<Candidate> candidates = randomCandidates(random, count);

                List<Result> results = balancer.balance(candidates);

                assertMatchesOracle(candidates, results);
                assertDistinctWithLowestIdInTeamA(candidates, results);
            }
        }
    }

    @Test
    void returnsOneThreeAndThreeAlternativesForTwoThreeAndFourPlayers() {
        Random random = new Random(1);

        assertThat(balancer.balance(randomCandidates(random, 2))).hasSize(1);
        assertThat(balancer.balance(randomCandidates(random, 3))).hasSize(3);
        assertThat(balancer.balance(randomCandidates(random, 4))).hasSize(3);
    }

    @Test
    void returnsExactlyFiveDistinctAlternativesWhenThereAreMorePartitions() {
        Random random = new Random(5);
        for (int count : new int[] {5, 6, 11, 14}) {
            List<Candidate> candidates = randomCandidates(random, count);

            List<Result> results = balancer.balance(candidates);

            assertThat(results).hasSize(5);
            assertDistinctWithLowestIdInTeamA(candidates, results);
        }
    }

    @Test
    void ordersAlternativesByNonDecreasingLevelDifference() {
        List<Candidate> candidates = randomCandidates(new Random(77), 10);

        List<Result> results = balancer.balance(candidates);

        for (int i = 1; i < results.size(); i++) {
            assertThat(results.get(i).levelDifference())
                    .isGreaterThanOrEqualTo(results.get(i - 1).levelDifference());
        }
    }

    @Test
    void reportsLevelTotalsAndEloAveragesPerTeam() {
        List<Candidate> candidates = randomCandidates(new Random(21), 11);

        for (Result result : balancer.balance(candidates)) {
            assertThat(List.of(result.teamA().size(), result.teamB().size())).containsExactlyInAnyOrder(6, 5);
            assertThat(result.totalLevelA() * 10).isCloseTo(sum(candidates, result.teamA(), true), within(EPSILON));
            assertThat(result.totalLevelB() * 10).isCloseTo(sum(candidates, result.teamB(), true), within(EPSILON));
            assertThat(result.avgEloA())
                    .isCloseTo((double) sum(candidates, result.teamA(), false) / result.teamA().size(), within(EPSILON));
            assertThat(result.avgEloB())
                    .isCloseTo((double) sum(candidates, result.teamB(), false) / result.teamB().size(), within(EPSILON));
            assertThat(result.levelDifference())
                    .isCloseTo(Math.abs(result.totalLevelA() - result.totalLevelB()), within(EPSILON));
            assertThat(result.eloDifference())
                    .isCloseTo(Math.abs(result.avgEloA() - result.avgEloB()), within(EPSILON));
        }
    }

    @Test
    void levelSumDecidesBeforeEloEvenWhenEloWouldBalanceBetter() {
        // Por suma de nivel la única partición perfecta es {1,4} vs {2,3} (10 y 10), pero el ELO
        // quedaría 1000 vs 1400; {1,2} vs {3,4} iguala el ELO pero deja el nivel en 14 vs 6.
        UUID one = new UUID(0, 1);
        UUID two = new UUID(0, 2);
        UUID three = new UUID(0, 3);
        UUID four = new UUID(0, 4);
        List<Candidate> candidates = List.of(
                new Candidate(one, 90, 1000),
                new Candidate(two, 50, 1400),
                new Candidate(three, 50, 1400),
                new Candidate(four, 10, 1000));

        Result result = balancer.balance(candidates).get(0);

        assertThat(result.teamA()).containsExactlyInAnyOrder(one, four);
        assertThat(result.teamB()).containsExactlyInAnyOrder(two, three);
        assertThat(result.levelDifference()).isZero();
        assertThat(result.eloDifference()).isCloseTo(400.0, within(EPSILON));
    }

    @Test
    void usesEloSumAsTieBreakerWhenLevelDifferenceIsEqual() {
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

        Result result = balancer.balance(candidates).get(0);

        assertThat(result.levelDifference()).isZero();
        assertThat(result.teamA()).containsExactlyInAnyOrder(a, b);
        assertThat(result.teamB()).containsExactlyInAnyOrder(c, d);
        assertThat(result.eloDifference()).isCloseTo(0.0, within(EPSILON));
    }

    @Test
    void returnsSameAlternativesRegardlessOfInputOrderWithLowestIdInTeamA() {
        List<Candidate> random = randomCandidates(new Random(99), 10);
        List<Candidate> tied = random.stream().map(c -> new Candidate(c.id(), 50, 1000)).toList();

        for (List<Candidate> input : List.of(random, tied)) {
            List<Result> first = balancer.balance(input);
            List<Candidate> shuffled = new ArrayList<>(input);
            Collections.shuffle(shuffled, new Random(123));

            List<Result> second = balancer.balance(shuffled);

            assertThat(second).isEqualTo(first);
            assertThat(balancer.balance(input)).isEqualTo(first);
            assertDistinctWithLowestIdInTeamA(input, first);
        }
    }

    @Test
    void breaksTiesWithLexicographicallyLowestIdsInTeamA() {
        List<Candidate> candidates = new ArrayList<>();
        for (int i = 1; i <= 4; i++) {
            candidates.add(new Candidate(new UUID(0, i), 50, 1000));
        }

        List<Result> results = balancer.balance(candidates);

        assertThat(results.get(0).teamA()).containsExactly(new UUID(0, 1), new UUID(0, 2));
        assertThat(results.get(0).teamB()).containsExactly(new UUID(0, 3), new UUID(0, 4));
        assertThat(results.get(1).teamA()).containsExactly(new UUID(0, 1), new UUID(0, 3));
        assertThat(results.get(2).teamA()).containsExactly(new UUID(0, 1), new UUID(0, 4));
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

        Result result = balancer.balance(candidates).get(0);

        assertThat(result.levelDifference()).isEqualTo(9.0);
    }

    @Test
    void comparesDecimalLevelsExactlyWithoutFloatingPointError() {
        UUID eightAndAHalf = new UUID(0, 1);
        UUID fourPointEight = new UUID(0, 2);

        Result result = balancer.balance(
                        List.of(new Candidate(eightAndAHalf, 85, 1000), new Candidate(fourPointEight, 48, 1000)))
                .get(0);

        assertThat(result.teamA()).containsExactly(eightAndAHalf);
        assertThat(result.teamB()).containsExactly(fourPointEight);
        assertThat(result.totalLevelA()).isEqualTo(8.5);
        assertThat(result.totalLevelB()).isEqualTo(4.8);
        assertThat(result.levelDifference()).isEqualTo(3.7);
    }

    @Test
    void picksThePartitionThatIsBetterByASingleTenth() {
        // 1,0 / 2,0 / 3,0 / 4,1: {1,4} vs {2,3} difiere en 0,1; {1,3} vs {2,4} en 2,1; {1,2} vs {3,4} en 4,1.
        UUID one = new UUID(0, 1);
        UUID two = new UUID(0, 2);
        UUID three = new UUID(0, 3);
        UUID four = new UUID(0, 4);
        List<Candidate> candidates = List.of(
                new Candidate(one, 10, 1000),
                new Candidate(two, 20, 1000),
                new Candidate(three, 30, 1000),
                new Candidate(four, 41, 1000));

        List<Result> results = balancer.balance(candidates);

        assertThat(results.get(0).teamA()).containsExactlyInAnyOrder(one, four);
        assertThat(results.get(0).teamB()).containsExactlyInAnyOrder(two, three);
        assertThat(results.get(0).levelDifference()).isEqualTo(0.1);
        assertThat(results.get(1).levelDifference()).isEqualTo(2.1);
        assertThat(results.get(2).levelDifference()).isEqualTo(4.1);
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

        List<Result> results = assertTimeoutPreemptively(Duration.ofSeconds(2), () -> balancer.balance(candidates));

        assertThat(results).hasSize(5);
        assertThat(results.get(0).teamA()).hasSize(11);
        assertThat(results.get(0).teamB()).hasSize(11);
    }
}
