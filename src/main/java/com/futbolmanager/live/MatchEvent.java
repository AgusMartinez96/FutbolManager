package com.futbolmanager.live;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.PostLoad;
import jakarta.persistence.PostPersist;
import jakarta.persistence.Table;
import jakarta.persistence.Transient;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import org.hibernate.annotations.Generated;
import org.hibernate.generator.EventType;
import org.springframework.data.domain.Persistable;
import org.springframework.data.jpa.repository.JpaRepository;

/** Append-only match event. The id is chosen by the client, which is what makes retries idempotent. */
@Entity
@Table(name = "match_events")
public class MatchEvent implements Persistable<UUID> {

    public enum Type {
        GOAL,
        SAVE,
        TACKLE
    }

    @Id
    private UUID id;

    @Column(name = "match_id", nullable = false, updatable = false)
    private UUID matchId;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, updatable = false)
    private Type type;

    @Column(nullable = false, updatable = false, length = 1)
    private String team;

    @Column(name = "player_id", updatable = false)
    private UUID playerId;

    @Column(name = "assist_player_id", updatable = false)
    private UUID assistPlayerId;

    @Column(name = "recorded_by_user_id", nullable = false, updatable = false)
    private UUID recordedByUserId;

    @Column(name = "voided_at")
    private Instant voidedAt;

    @Column(name = "voided_by_user_id")
    private UUID voidedByUserId;

    @Generated(event = EventType.INSERT)
    @Column(name = "created_at", insertable = false, updatable = false)
    private Instant createdAt;

    @Transient
    private boolean isNew = true;

    protected MatchEvent() {}

    public MatchEvent(
            UUID id,
            UUID matchId,
            Type type,
            String team,
            UUID playerId,
            UUID assistPlayerId,
            UUID recordedByUserId) {
        this.id = id;
        this.matchId = matchId;
        this.type = type;
        this.team = team;
        this.playerId = playerId;
        this.assistPlayerId = assistPlayerId;
        this.recordedByUserId = recordedByUserId;
    }

    @Override
    public UUID getId() {
        return id;
    }

    @Override
    public boolean isNew() {
        return isNew;
    }

    @PostPersist
    @PostLoad
    void markNotNew() {
        this.isNew = false;
    }

    public UUID getMatchId() {
        return matchId;
    }

    public Type getType() {
        return type;
    }

    public String getTeam() {
        return team;
    }

    public UUID getPlayerId() {
        return playerId;
    }

    public UUID getAssistPlayerId() {
        return assistPlayerId;
    }

    public UUID getRecordedByUserId() {
        return recordedByUserId;
    }

    public Instant getVoidedAt() {
        return voidedAt;
    }

    public boolean isVoided() {
        return voidedAt != null;
    }

    public void voidBy(UUID userId, Instant at) {
        this.voidedAt = at;
        this.voidedByUserId = userId;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }

    public interface Repo extends JpaRepository<MatchEvent, UUID> {

        List<MatchEvent> findAllByMatchIdOrderByCreatedAtAsc(UUID matchId);
    }
}
