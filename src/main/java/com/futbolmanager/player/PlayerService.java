package com.futbolmanager.player;

import com.futbolmanager.group.GroupController.MemberJoined;
import com.futbolmanager.group.GroupController.MemberRemoved;
import java.time.Clock;
import java.util.List;
import java.util.UUID;
import org.springframework.context.event.EventListener;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@Transactional
public class PlayerService {

    private static final int DEFAULT_ELO = 1000;

    private final Player.Repo players;
    private final JdbcClient jdbc;
    private final Clock clock;

    public PlayerService(Player.Repo players, JdbcClient jdbc, Clock clock) {
        this.players = players;
        this.jdbc = jdbc;
        this.clock = clock;
    }

    /** Creates a player at the rounded average ELO of the group's active players and freezes it as INITIAL. */
    public Player create(
            UUID groupId,
            UUID userIdOrNull,
            String name,
            String nickname,
            Player.Position position,
            Player.Foot foot) {
        int initialElo = initialElo(groupId);
        Player player =
                players.saveAndFlush(new Player(groupId, userIdOrNull, name, nickname, position, foot, initialElo));
        jdbc.sql("""
                        insert into elo_changes (group_id, player_id, match_id, reason, elo_before, elo_after, delta)
                        values (:groupId, :playerId, null, 'INITIAL', :elo, :elo, 0)
                        """)
                .param("groupId", groupId)
                .param("playerId", player.getId())
                .param("elo", initialElo)
                .update();
        return player;
    }

    public void softDelete(Player player) {
        player.markDeleted(clock.instant());
        players.saveAndFlush(player);
    }

    @EventListener
    public void onMemberJoined(MemberJoined event) {
        players.findByGroupIdAndUserId(event.groupId(), event.userId())
                .ifPresentOrElse(
                        existing -> {
                            existing.restore();
                            players.saveAndFlush(existing);
                        },
                        () -> create(
                                event.groupId(),
                                event.userId(),
                                event.displayName(),
                                null,
                                Player.Position.MID,
                                Player.Foot.RIGHT));
    }

    @EventListener
    public void onMemberRemoved(MemberRemoved event) {
        players.findByGroupIdAndUserId(event.groupId(), event.userId())
                .filter(player -> !player.isDeleted())
                .ifPresent(this::softDelete);
    }

    private int initialElo(UUID groupId) {
        List<Player> active = players.findAllByGroupIdAndDeletedAtIsNullOrderByNameAsc(groupId);
        if (active.isEmpty()) {
            return DEFAULT_ELO;
        }
        double average =
                active.stream().mapToInt(Player::getCurrentElo).average().orElse(DEFAULT_ELO);
        return (int) Math.round(average);
    }
}
