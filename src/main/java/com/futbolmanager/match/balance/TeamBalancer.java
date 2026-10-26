package com.futbolmanager.match.balance;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.UUID;

/**
 * Balanceo exhaustivo y puro de equipos: sin Spring, sin JPA, sin estado. Enumera todas las particiones
 * posibles (el primer id, ordenado, siempre en el equipo A) y elige la de menor diferencia de nivel promedio;
 * ante empate exacto desempata por diferencia de ELO promedio y, si persiste, por la lista de ids de A
 * lexicográficamente menor. Los puestos no intervienen.
 */
public final class TeamBalancer {

    static final int MIN_PLAYERS = 2;
    static final int MAX_PLAYERS = 22;
    static final int MIN_LEVEL = 1;
    static final int MAX_LEVEL = 10;

    public record Candidate(UUID id, int level, int elo) {}

    public record Result(
            List<UUID> teamA,
            List<UUID> teamB,
            double avgLevelA,
            double avgLevelB,
            double levelDifference,
            double avgEloA,
            double avgEloB,
            double eloDifference) {}

    public Result balance(List<Candidate> candidates) {
        if (candidates == null || candidates.size() < MIN_PLAYERS || candidates.size() > MAX_PLAYERS) {
            throw new IllegalArgumentException(
                    "El balanceo necesita entre " + MIN_PLAYERS + " y " + MAX_PLAYERS + " jugadores.");
        }
        List<Candidate> sorted = new ArrayList<>(candidates);
        sorted.sort(Comparator.comparing(Candidate::id));
        int n = sorted.size();

        int[] level = new int[n];
        int[] elo = new int[n];
        long totalLevel = 0;
        long totalElo = 0;
        for (int i = 0; i < n; i++) {
            Candidate candidate = sorted.get(i);
            if (candidate.level() < MIN_LEVEL || candidate.level() > MAX_LEVEL) {
                throw new IllegalArgumentException(
                        "El nivel debe estar entre " + MIN_LEVEL + " y " + MAX_LEVEL + ".");
            }
            level[i] = candidate.level();
            elo[i] = candidate.elo();
            totalLevel += level[i];
            totalElo += elo[i];
        }

        // sizeA * sizeB es igual para |A| = ⌊n/2⌋ y |A| = ⌈n/2⌉: los numeradores son comparables sin dividir.
        long bestLevelNumerator = -1;
        long bestEloNumerator = -1;
        int bestMask = -1;

        for (int size = n / 2; size <= (n + 1) / 2; size++) {
            int limit = 1 << (n - 1);
            int combination = (1 << (size - 1)) - 1;
            while (combination < limit) {
                int mask = (combination << 1) | 1;
                long sumLevelA = 0;
                long sumEloA = 0;
                for (int i = 0; i < n; i++) {
                    if ((mask & (1 << i)) != 0) {
                        sumLevelA += level[i];
                        sumEloA += elo[i];
                    }
                }
                long sizeA = size;
                long sizeB = n - size;
                long levelNumerator = Math.abs(sumLevelA * sizeB - (totalLevel - sumLevelA) * sizeA);
                long eloNumerator = Math.abs(sumEloA * sizeB - (totalElo - sumEloA) * sizeA);
                if (isBetter(levelNumerator, eloNumerator, mask, bestLevelNumerator, bestEloNumerator, bestMask)) {
                    bestLevelNumerator = levelNumerator;
                    bestEloNumerator = eloNumerator;
                    bestMask = mask;
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

        return build(sorted, bestMask);
    }

    private static boolean isBetter(
            long levelNumerator,
            long eloNumerator,
            int mask,
            long bestLevelNumerator,
            long bestEloNumerator,
            int bestMask) {
        if (bestMask < 0) {
            return true;
        }
        if (levelNumerator != bestLevelNumerator) {
            return levelNumerator < bestLevelNumerator;
        }
        if (eloNumerator != bestEloNumerator) {
            return eloNumerator < bestEloNumerator;
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

    private static Result build(List<Candidate> sorted, int mask) {
        List<UUID> teamA = new ArrayList<>();
        List<UUID> teamB = new ArrayList<>();
        long levelA = 0;
        long levelB = 0;
        long eloA = 0;
        long eloB = 0;
        for (int i = 0; i < sorted.size(); i++) {
            Candidate candidate = sorted.get(i);
            if ((mask & (1 << i)) != 0) {
                teamA.add(candidate.id());
                levelA += candidate.level();
                eloA += candidate.elo();
            } else {
                teamB.add(candidate.id());
                levelB += candidate.level();
                eloB += candidate.elo();
            }
        }
        double avgLevelA = (double) levelA / teamA.size();
        double avgLevelB = (double) levelB / teamB.size();
        double avgEloA = (double) eloA / teamA.size();
        double avgEloB = (double) eloB / teamB.size();
        return new Result(
                teamA,
                teamB,
                avgLevelA,
                avgLevelB,
                Math.abs(avgLevelA - avgLevelB),
                avgEloA,
                avgEloB,
                Math.abs(avgEloA - avgEloB));
    }
}
