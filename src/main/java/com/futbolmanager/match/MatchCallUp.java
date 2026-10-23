package com.futbolmanager.match;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

@Entity
@Table(name = "match_call_ups")
public class MatchCallUp {

    public enum Status {
        CONFIRMED,
        MAYBE,
        NOT_GOING
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
    private Status status;

    protected MatchCallUp() {}

    public MatchCallUp(UUID matchId, UUID playerId, Status status) {
        this.matchId = matchId;
        this.playerId = playerId;
        this.status = status;
    }

    public UUID getMatchId() {
        return matchId;
    }

    public UUID getPlayerId() {
        return playerId;
    }

    public Status getStatus() {
        return status;
    }

    public void setStatus(Status status) {
        this.status = status;
    }

    public interface Repo extends JpaRepository<MatchCallUp, UUID> {

        List<MatchCallUp> findAllByMatchId(UUID matchId);

        Optional<MatchCallUp> findByMatchIdAndPlayerId(UUID matchId, UUID playerId);
    }
}
