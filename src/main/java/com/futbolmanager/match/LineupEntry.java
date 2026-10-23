package com.futbolmanager.match;

import com.futbolmanager.player.Player;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.util.List;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

@Entity
@Table(name = "match_lineup_entries")
public class LineupEntry {

    public enum Team {
        A,
        B
    }

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(name = "match_id", nullable = false, updatable = false)
    private UUID matchId;

    @Column(name = "player_id", nullable = false, updatable = false)
    private UUID playerId;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private Team team;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private Player.Position position;

    protected LineupEntry() {}

    public LineupEntry(UUID matchId, UUID playerId, Team team, Player.Position position) {
        this.matchId = matchId;
        this.playerId = playerId;
        this.team = team;
        this.position = position;
    }

    public UUID getMatchId() {
        return matchId;
    }

    public UUID getPlayerId() {
        return playerId;
    }

    public Team getTeam() {
        return team;
    }

    public Player.Position getPosition() {
        return position;
    }

    public interface Repo extends JpaRepository<LineupEntry, UUID> {

        List<LineupEntry> findAllByMatchId(UUID matchId);

        void deleteAllByMatchId(UUID matchId);
    }
}
