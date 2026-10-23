package com.futbolmanager.elo;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/**
 * Matemática ELO pura: sin Spring, sin JPA, sin estado. La misma entrada produce siempre el mismo resultado.
 */
public final class EloCalculator {

    static final int K_PROVISIONAL = 40;
    static final int K_ESTABLISHED = 24;
    static final int PROVISIONAL_MATCHES = 10;
    static final int BONUS_CAP = 10;
    static final int SAVES_CAP = 3;
    static final int TACKLES_CAP = 3;
    static final int POINTS_PER_GOAL = 2;
    static final int POINTS_PER_ASSIST = 1;
    static final int POINTS_MVP = 3;
    static final int POINTS_CLEAN_SHEET = 3;

    public enum Position { GK, DEF, MID, FWD }

    public record PlayerInput(
            UUID playerId,
            int eloBefore,
            int previousCountedMatches,
            Position position,
            int goals,
            int assists,
            int saves,
            int tackles) {}

    public record MatchInput(
            List<PlayerInput> teamA,
            List<PlayerInput> teamB,
            int scoreA,
            int scoreB,
            UUID mvpPlayerId) {}

    public record BonusBreakdown(
            int goals,
            int assists,
            int mvp,
            int cleanSheet,
            int saves,
            int tackles,
            int uncapped,
            int total) {}

    public record PlayerResult(UUID playerId, int teamDelta, int bonus, BonusBreakdown breakdown) {}

    public List<PlayerResult> calculate(MatchInput input) {
        if (input.teamA().isEmpty() || input.teamB().isEmpty()) {
            throw new IllegalArgumentException("Cada equipo necesita al menos un jugador.");
        }
        double ratingA = averageElo(input.teamA());
        double ratingB = averageElo(input.teamB());
        double scoreForA = score(input.scoreA(), input.scoreB());

        List<PlayerResult> results = new ArrayList<>(input.teamA().size() + input.teamB().size());
        for (PlayerInput player : input.teamA()) {
            results.add(result(player, ratingA, ratingB, scoreForA, input.scoreB(), input.mvpPlayerId()));
        }
        for (PlayerInput player : input.teamB()) {
            results.add(result(player, ratingB, ratingA, 1.0 - scoreForA, input.scoreA(), input.mvpPlayerId()));
        }
        return results;
    }

    private PlayerResult result(
            PlayerInput player, double ownRating, double rivalRating, double score, int goalsAgainst, UUID mvpPlayerId) {
        double expected = 1.0 / (1.0 + Math.pow(10.0, (rivalRating - ownRating) / 400.0));
        int k = player.previousCountedMatches() < PROVISIONAL_MATCHES ? K_PROVISIONAL : K_ESTABLISHED;
        int teamDelta = (int) Math.round(k * (score - expected));
        BonusBreakdown breakdown = bonus(player, goalsAgainst, player.playerId().equals(mvpPlayerId));
        return new PlayerResult(player.playerId(), teamDelta, breakdown.total(), breakdown);
    }

    private BonusBreakdown bonus(PlayerInput player, int goalsAgainst, boolean mvp) {
        int goals = Math.max(0, player.goals()) * POINTS_PER_GOAL;
        int assists = Math.max(0, player.assists()) * POINTS_PER_ASSIST;
        int mvpPoints = mvp ? POINTS_MVP : 0;
        boolean defensive = player.position() == Position.GK || player.position() == Position.DEF;
        int cleanSheet = goalsAgainst == 0 && defensive ? POINTS_CLEAN_SHEET : 0;
        int saves = player.position() == Position.GK ? Math.min(SAVES_CAP, Math.max(0, player.saves())) : 0;
        int tackles = Math.min(TACKLES_CAP, Math.max(0, player.tackles()));
        int uncapped = goals + assists + mvpPoints + cleanSheet + saves + tackles;
        return new BonusBreakdown(
                goals, assists, mvpPoints, cleanSheet, saves, tackles, uncapped, Math.min(BONUS_CAP, uncapped));
    }

    private static double averageElo(List<PlayerInput> team) {
        return team.stream().mapToInt(PlayerInput::eloBefore).average().orElseThrow();
    }

    private static double score(int own, int rival) {
        if (own > rival) {
            return 1.0;
        }
        return own == rival ? 0.5 : 0.0;
    }
}
