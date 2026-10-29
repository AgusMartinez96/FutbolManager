package com.futbolmanager.config;

import com.futbolmanager.elo.EloReplayService;
import com.futbolmanager.group.GroupController.MemberJoined;
import com.futbolmanager.match.balance.TeamBalancer;
import com.futbolmanager.player.Player;
import com.futbolmanager.player.PlayerService;
import java.sql.Timestamp;
import java.time.DayOfWeek;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.time.temporal.ChronoUnit;
import java.time.temporal.TemporalAdjusters;
import java.util.ArrayList;
import java.util.List;
import java.util.Random;
import java.util.UUID;
import org.springframework.beans.factory.SmartInitializingSingleton;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.context.annotation.Profile;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;
import org.springframework.transaction.support.TransactionTemplate;

/**
 * Siembra el grupo «Los del jueves» con 14 jugadores y 6 partidos cerrados. Es un {@link SmartInitializingSingleton}
 * y no un {@code ApplicationRunner}: corre después de Flyway y antes de que el conector web acepte tráfico, así el
 * primer 200 de {@code /api/health} implica que la siembra ya está commiteada.
 */
@Component
@Profile("demo")
public class DemoDataSeeder implements SmartInitializingSingleton {

    static final String GROUP_NAME = "Los del jueves";
    private static final String USERNAME = "demo";
    private static final String INVITE_ALPHABET = "ABCDEFGHJKMNPQRSTUVWXYZ23456789";
    private static final int MATCHES = 6;

    private record Seed(String name, String nickname, Player.Position position, Player.Foot foot, int levelTenths) {}

    private record Participant(UUID id, int levelTenths, int elo, String position) {}

    private static final List<Seed> GUESTS = List.of(
            new Seed("Martín Gómez", "Tincho", Player.Position.GK, Player.Foot.RIGHT, 62),
            new Seed("Lucas Pereyra", null, Player.Position.GK, Player.Foot.LEFT, 48),
            new Seed("Nicolás Rivas", "Nico", Player.Position.DEF, Player.Foot.RIGHT, 55),
            new Seed("Federico Ledesma", "Fede", Player.Position.DEF, Player.Foot.BOTH, 71),
            new Seed("Matías Acosta", null, Player.Position.DEF, Player.Foot.LEFT, 43),
            new Seed("Santiago Núñez", "Santi", Player.Position.MID, Player.Foot.RIGHT, 84),
            new Seed("Joaquín Benítez", "Joaco", Player.Position.MID, Player.Foot.RIGHT, 66),
            new Seed("Tomás Ferreyra", null, Player.Position.MID, Player.Foot.LEFT, 52),
            new Seed("Agustín Sosa", "Agus", Player.Position.MID, Player.Foot.BOTH, 77),
            new Seed("Facundo Molina", "Facu", Player.Position.FWD, Player.Foot.RIGHT, 90),
            new Seed("Ramiro Ibarra", null, Player.Position.FWD, Player.Foot.LEFT, 58),
            new Seed("Gonzalo Vera", "Gonza", Player.Position.FWD, Player.Foot.RIGHT, 45),
            new Seed("Bruno Cabrera", null, Player.Position.DEF, Player.Foot.RIGHT, 61));

    private final JdbcClient jdbc;
    private final TransactionTemplate tx;
    private final PasswordEncoder passwordEncoder;
    private final PlayerService playerService;
    private final EloReplayService replay;
    private final ApplicationEventPublisher events;
    private final String demoPassword;

    public DemoDataSeeder(
            JdbcClient jdbc,
            TransactionTemplate tx,
            PasswordEncoder passwordEncoder,
            PlayerService playerService,
            EloReplayService replay,
            ApplicationEventPublisher events,
            @Value("${DEMO_PASSWORD}") String demoPassword) {
        this.jdbc = jdbc;
        this.tx = tx;
        this.passwordEncoder = passwordEncoder;
        this.playerService = playerService;
        this.replay = replay;
        this.events = events;
        this.demoPassword = demoPassword;
    }

    @Override
    public void afterSingletonsInstantiated() {
        boolean exists = jdbc.sql("select count(*) from groups where name = :name")
                        .param("name", GROUP_NAME)
                        .query(Long.class)
                        .single()
                > 0;
        if (!exists) {
            tx.executeWithoutResult(status -> seed());
        }
    }

    private void seed() {
        Random random = new Random(42);
        UUID userId = findOrCreateDemoUser();
        UUID groupId = UUID.randomUUID();
        jdbc.sql("""
                        insert into groups (id, name, invite_code, created_by_user_id)
                        values (:id, :name, :code, :userId)
                        """)
                .param("id", groupId)
                .param("name", GROUP_NAME)
                .param("code", inviteCode(random))
                .param("userId", userId)
                .update();
        jdbc.sql("insert into memberships (group_id, user_id, role) values (:groupId, :userId, 'ADMIN')")
                .param("groupId", groupId)
                .param("userId", userId)
                .update();
        events.publishEvent(new MemberJoined(groupId, userId, "Demo"));
        for (Seed guest : GUESTS) {
            playerService.create(
                    groupId, null, guest.name(), guest.nickname(), guest.position(), guest.foot(), guest.levelTenths());
        }
        List<Participant> participants = jdbc.sql("""
                        select id, level_tenths, current_elo, preferred_position
                        from players where group_id = :groupId and deleted_at is null
                        """)
                .param("groupId", groupId)
                .query((rs, row) -> new Participant(
                        rs.getObject("id", UUID.class),
                        rs.getInt("level_tenths"),
                        rs.getInt("current_elo"),
                        rs.getString("preferred_position")))
                .list();

        Instant lastThursday = LocalDate.now(ZoneOffset.UTC)
                .with(TemporalAdjusters.previous(DayOfWeek.THURSDAY))
                .atTime(21, 0)
                .toInstant(ZoneOffset.UTC);
        UUID firstMatch = null;
        for (int i = 0; i < MATCHES; i++) {
            Instant playedAt = lastThursday.minus(7L * (MATCHES - 1 - i), ChronoUnit.DAYS);
            UUID matchId = seedMatch(groupId, userId, participants, playedAt, random);
            if (firstMatch == null) {
                firstMatch = matchId;
            }
        }
        replay.replayFrom(groupId, firstMatch);
    }

    private UUID findOrCreateDemoUser() {
        return jdbc.sql("select id from users where lower(username) = :username")
                .param("username", USERNAME)
                .query(UUID.class)
                .optional()
                .orElseGet(() -> {
                    UUID id = UUID.randomUUID();
                    jdbc.sql("""
                                    insert into users (id, username, display_name, password_hash)
                                    values (:id, :username, 'Demo', :hash)
                                    """)
                            .param("id", id)
                            .param("username", USERNAME)
                            .param("hash", passwordEncoder.encode(demoPassword))
                            .update();
                    return id;
                });
    }

    private UUID seedMatch(UUID groupId, UUID userId, List<Participant> participants, Instant playedAt, Random random) {
        List<TeamBalancer.Candidate> candidates = new ArrayList<>();
        for (Participant p : participants) {
            int jittered = Math.max(10, Math.min(100, p.levelTenths() + random.nextInt(21) - 10));
            candidates.add(new TeamBalancer.Candidate(p.id(), jittered, p.elo()));
        }
        TeamBalancer.Result teams = new TeamBalancer().balance(candidates).get(0);

        UUID matchId = UUID.randomUUID();
        List<UUID> mvpTeam = random.nextBoolean() ? teams.teamA() : teams.teamB();
        UUID mvp = mvpTeam.get(random.nextInt(mvpTeam.size()));
        jdbc.sql("""
                        insert into matches (id, group_id, played_at, location, format, status, mvp_player_id, closed_at)
                        values (:id, :groupId, :playedAt, 'Cancha del barrio', 7, 'CLOSED', :mvp, :closedAt)
                        """)
                .param("id", matchId)
                .param("groupId", groupId)
                .param("playedAt", Timestamp.from(playedAt))
                .param("mvp", mvp)
                .param("closedAt", Timestamp.from(playedAt.plus(2, ChronoUnit.HOURS)))
                .update();
        insertLineup(matchId, "A", teams.teamA(), participants);
        insertLineup(matchId, "B", teams.teamB(), participants);
        insertEvents(matchId, userId, "A", teams.teamA(), random);
        insertEvents(matchId, userId, "B", teams.teamB(), random);
        return matchId;
    }

    private void insertLineup(UUID matchId, String team, List<UUID> ids, List<Participant> participants) {
        for (UUID id : ids) {
            String position = participants.stream()
                    .filter(p -> p.id().equals(id))
                    .findFirst()
                    .orElseThrow()
                    .position();
            jdbc.sql("insert into match_lineup_entries (match_id, player_id, team, position) values (:m, :p, :t, :pos)")
                    .param("m", matchId)
                    .param("p", id)
                    .param("t", team)
                    .param("pos", position)
                    .update();
        }
    }

    private void insertEvents(UUID matchId, UUID userId, String team, List<UUID> lineup, Random random) {
        int goals = random.nextInt(6);
        for (int g = 0; g < goals; g++) {
            UUID scorer = lineup.get(random.nextInt(lineup.size()));
            UUID assist = random.nextBoolean() ? lineup.get(random.nextInt(lineup.size())) : null;
            insertEvent(matchId, userId, "GOAL", team, scorer, scorer.equals(assist) ? null : assist);
        }
        for (String type : List.of("SAVE", "TACKLE", "PLAY")) {
            int count = random.nextInt(4);
            for (int i = 0; i < count; i++) {
                insertEvent(matchId, userId, type, team, lineup.get(random.nextInt(lineup.size())), null);
            }
        }
    }

    private void insertEvent(UUID matchId, UUID userId, String type, String team, UUID player, UUID assist) {
        jdbc.sql("""
                        insert into match_events (id, match_id, type, team, player_id, assist_player_id, recorded_by_user_id)
                        values (:id, :matchId, :type, :team, :player, :assist, :userId)
                        """)
                .param("id", UUID.randomUUID())
                .param("matchId", matchId)
                .param("type", type)
                .param("team", team)
                .param("player", player)
                .param("assist", assist)
                .param("userId", userId)
                .update();
    }

    private static String inviteCode(Random random) {
        StringBuilder code = new StringBuilder();
        for (int i = 0; i < 8; i++) {
            code.append(INVITE_ALPHABET.charAt(random.nextInt(INVITE_ALPHABET.length())));
        }
        return code.toString();
    }
}
