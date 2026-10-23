package com.futbolmanager.match.balance;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.UUID;

/**
 * Balanceo exhaustivo y puro de equipos: sin Spring, sin JPA, sin estado. Enumera todas las particiones
 * posibles (el primer id, ordenado, siempre en el equipo A) y elige la de menor diferencia de promedios.
 */
public final class TeamBalancer {

    static final int MIN_PLAYERS = 2;
    static final int MAX_PLAYERS = 22;

    public enum Position { GK, DEF, MID, FWD }

    public record Candidate(UUID id, int elo, Position preferredPosition) {}

    public record Assignment(UUID playerId, Position position) {}

    public record Result(
            List<Assignment> teamA,
            List<Assignment> teamB,
            double avgEloA,
            double avgEloB,
            double eloDifference,
            boolean goalkeeperConstraintApplied) {}

    public Result balance(List<Candidate> candidates, boolean oneGoalkeeperPerSide) {
        if (candidates == null || candidates.size() < MIN_PLAYERS || candidates.size() > MAX_PLAYERS) {
            throw new IllegalArgumentException(
                    "El balanceo necesita entre " + MIN_PLAYERS + " y " + MAX_PLAYERS + " jugadores.");
        }
        List<Candidate> sorted = new ArrayList<>(candidates);
        sorted.sort(Comparator.comparing(Candidate::id));
        int n = sorted.size();

        int[] elo = new int[n];
        int goalkeeperMask = 0;
        int goalkeepers = 0;
        int totalElo = 0;
        for (int i = 0; i < n; i++) {
            elo[i] = sorted.get(i).elo();
            totalElo += elo[i];
            if (sorted.get(i).preferredPosition() == Position.GK) {
                goalkeeperMask |= 1 << i;
                goalkeepers++;
            }
        }
        boolean constrained = oneGoalkeeperPerSide && goalkeepers >= 2;
        int fullMask = (1 << n) - 1;

        long bestNumerator = -1;
        long bestDenominator = 1;
        int bestMask = -1;

        for (int size = n / 2; size <= (n + 1) / 2; size++) {
            int limit = 1 << (n - 1);
            int combination = (1 << (size - 1)) - 1;
            while (combination < limit) {
                int mask = (combination << 1) | 1;
                if (!constrained || hasGoalkeeperOnBothSides(mask, fullMask, goalkeeperMask)) {
                    long sumA = 0;
                    for (int i = 0; i < n; i++) {
                        if ((mask & (1 << i)) != 0) {
                            sumA += elo[i];
                        }
                    }
                    long sumB = totalElo - sumA;
                    long sizeA = size;
                    long sizeB = n - size;
                    long numerator = Math.abs(sumA * sizeB - sumB * sizeA);
                    long denominator = sizeA * sizeB;
                    if (isBetter(numerator, denominator, mask, bestNumerator, bestDenominator, bestMask)) {
                        bestNumerator = numerator;
                        bestDenominator = denominator;
                        bestMask = mask;
                    }
                }
                if (combination == 0) {
                    break;
                }
                // Siguiente combinación con el mismo número de bits (Gosper), sin recursión.
                int lowest = combination & -combination;
                int ripple = combination + lowest;
                combination = (((combination ^ ripple) >>> 2) / lowest) | ripple;
            }
        }

        return build(sorted, bestMask, constrained);
    }

    private static boolean hasGoalkeeperOnBothSides(int mask, int fullMask, int goalkeeperMask) {
        return (mask & goalkeeperMask) != 0 && (~mask & fullMask & goalkeeperMask) != 0;
    }

    private static boolean isBetter(
            long numerator, long denominator, int mask, long bestNumerator, long bestDenominator, int bestMask) {
        if (bestMask < 0) {
            return true;
        }
        long left = numerator * bestDenominator;
        long right = bestNumerator * denominator;
        if (left != right) {
            return left < right;
        }
        return lexicographicallyLower(mask, bestMask);
    }

    /** Compara las listas ascendentes de índices de A: la primera diferencia decide; un prefijo es menor. */
    private static boolean lexicographicallyLower(int mask, int other) {
        int differing = mask ^ other;
        if (differing == 0) {
            return false;
        }
        int firstDifference = differing & -differing;
        boolean maskHasIt = (mask & firstDifference) != 0;
        int withoutIt = maskHasIt ? other : mask;
        boolean otherContinues = (withoutIt & -(firstDifference << 1)) != 0;
        // Quien tiene el índice menor gana, salvo que la otra lista haya terminado (es prefijo).
        return otherContinues == maskHasIt;
    }

    private static Result build(List<Candidate> sorted, int mask, boolean constrained) {
        List<Candidate> teamA = new ArrayList<>();
        List<Candidate> teamB = new ArrayList<>();
        long sumA = 0;
        long sumB = 0;
        for (int i = 0; i < sorted.size(); i++) {
            Candidate candidate = sorted.get(i);
            if ((mask & (1 << i)) != 0) {
                teamA.add(candidate);
                sumA += candidate.elo();
            } else {
                teamB.add(candidate);
                sumB += candidate.elo();
            }
        }
        double avgA = (double) sumA / teamA.size();
        double avgB = (double) sumB / teamB.size();
        return new Result(
                assign(teamA, constrained), assign(teamB, constrained), avgA, avgB, Math.abs(avgA - avgB), constrained);
    }

    private static List<Assignment> assign(List<Candidate> team, boolean constrained) {
        List<Assignment> assignments = new ArrayList<>(team.size());
        boolean goalkeeperTaken = false;
        for (Candidate candidate : team) {
            Position position = candidate.preferredPosition();
            if (constrained && position == Position.GK) {
                if (goalkeeperTaken) {
                    position = Position.DEF;
                }
                goalkeeperTaken = true;
            }
            assignments.add(new Assignment(candidate.id(), position));
        }
        return assignments;
    }
}
