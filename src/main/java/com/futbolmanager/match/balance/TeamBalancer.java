package com.futbolmanager.match.balance;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.UUID;

/**
 * Balanceo exhaustivo y puro de equipos: sin Spring, sin JPA, sin estado. Enumera todas las particiones
 * posibles (el primer id, ordenado, siempre en el equipo A, con tamaños que difieren en 1 como máximo) y
 * devuelve las {@value #MAX_ALTERNATIVES} mejores, la mejor primero. La fuerza de un equipo es la SUMA del nivel de
 * sus jugadores: ordena la menor diferencia de suma de nivel; ante empate exacto, la menor diferencia de suma
 * de ELO; y si persiste, la lista de ids de A lexicográficamente menor. Los puestos no intervienen. El nivel
 * entra en décimas enteras (8,5 → 85) para que toda comparación sea exacta con {@code long}, sin coma flotante.
 */
public final class TeamBalancer {

    static final int MIN_PLAYERS = 2;
    static final int MAX_PLAYERS = 22;
    static final int MIN_LEVEL_TENTHS = 10;
    static final int MAX_LEVEL_TENTHS = 100;
    static final int MAX_ALTERNATIVES = 5;
    private static final int TENTHS_PER_LEVEL = 10;

    public record Candidate(UUID id, int levelTenths, int elo) {}

    public record Result(
            List<UUID> teamA,
            List<UUID> teamB,
            double totalLevelA,
            double totalLevelB,
            double levelDifference,
            double avgEloA,
            double avgEloB,
            double eloDifference) {}

    /** Partición candidata: diferencias exactas de las sumas y máscara de los índices (ordenados por id) de A. */
    private record Partition(long levelDifference, long eloDifference, int mask) {}

    public List<Result> balance(List<Candidate> candidates) {
        if (candidates == null || candidates.size() < MIN_PLAYERS || candidates.size() > MAX_PLAYERS) {
            throw new IllegalArgumentException(
                    "El balanceo necesita entre " + MIN_PLAYERS + " y " + MAX_PLAYERS + " jugadores.");
        }
        List<Candidate> sorted = new ArrayList<>(candidates);
        sorted.sort(Comparator.comparing(Candidate::id));
        int n = sorted.size();

        int[] level = new int[n];  // en décimas
        int[] elo = new int[n];
        long totalLevel = 0;
        long totalElo = 0;
        for (int i = 0; i < n; i++) {
            Candidate candidate = sorted.get(i);
            if (candidate.levelTenths() < MIN_LEVEL_TENTHS || candidate.levelTenths() > MAX_LEVEL_TENTHS) {
                throw new IllegalArgumentException("El nivel debe estar entre 1,0 y 10,0.");
            }
            level[i] = candidate.levelTenths();
            elo[i] = candidate.elo();
            totalLevel += level[i];
            totalElo += elo[i];
        }

        List<Partition> best = new ArrayList<>(MAX_ALTERNATIVES + 1);
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
                long levelDifference = Math.abs(2 * sumLevelA - totalLevel);
                long eloDifference = Math.abs(2 * sumEloA - totalElo);
                offer(best, levelDifference, eloDifference, mask);
                if (combination == 0) {
                    break;
                }
                // Siguiente combinación con el mismo número de bits (Gosper), sin recursión.
                int lowest = combination & -combination;
                int ripple = combination + lowest;
                combination = (((combination ^ ripple) >>> 2) / lowest) | ripple;
            }
        }

        return best.stream().map(partition -> build(sorted, partition.mask())).toList();
    }

    /** Inserta la partición en la lista ordenada de las mejores si entra, sin crecer más de {@value #MAX_ALTERNATIVES}. */
    private static void offer(List<Partition> best, long levelDifference, long eloDifference, int mask) {
        if (best.size() == MAX_ALTERNATIVES) {
            Partition worst = best.get(MAX_ALTERNATIVES - 1);
            if (compare(levelDifference, eloDifference, mask, worst.levelDifference(), worst.eloDifference(), worst.mask())
                    >= 0) {
                return;
            }
        }
        int position = best.size();
        while (position > 0) {
            Partition previous = best.get(position - 1);
            if (compare(levelDifference, eloDifference, mask, previous.levelDifference(), previous.eloDifference(), previous.mask())
                    >= 0) {
                break;
            }
            position--;
        }
        best.add(position, new Partition(levelDifference, eloDifference, mask));
        if (best.size() > MAX_ALTERNATIVES) {
            best.remove(MAX_ALTERNATIVES);
        }
    }

    private static int compare(
            long levelDifference, long eloDifference, int mask, long otherLevel, long otherElo, int otherMask) {
        if (levelDifference != otherLevel) {
            return Long.compare(levelDifference, otherLevel);
        }
        if (eloDifference != otherElo) {
            return Long.compare(eloDifference, otherElo);
        }
        if (mask == otherMask) {
            return 0;
        }
        return lexicographicallyLower(mask, otherMask) ? -1 : 1;
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
                levelA += candidate.levelTenths();
                eloA += candidate.elo();
            } else {
                teamB.add(candidate.id());
                levelB += candidate.levelTenths();
                eloB += candidate.elo();
            }
        }
        // Una sola división por 10 sobre un entero exacto: 8,5 contra 4,8 da 3.7 y no 3.7000000000000002.
        double levelDifference = (double) Math.abs(levelA - levelB) / TENTHS_PER_LEVEL;
        double avgEloA = (double) eloA / teamA.size();
        double avgEloB = (double) eloB / teamB.size();
        return new Result(
                teamA,
                teamB,
                (double) levelA / TENTHS_PER_LEVEL,
                (double) levelB / TENTHS_PER_LEVEL,
                levelDifference,
                avgEloA,
                avgEloB,
                Math.abs(avgEloA - avgEloB));
    }
}
