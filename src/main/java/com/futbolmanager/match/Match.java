package com.futbolmanager.match;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.persistence.Version;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

@Entity
@Table(name = "matches")
public class Match {

    public enum Status {
        SCHEDULED,
        LIVE,
        CLOSED,
        ANNULLED
    }

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(name = "group_id", nullable = false, updatable = false)
    private UUID groupId;

    @Column(name = "played_at", nullable = false)
    private Instant playedAt;

    @Column(nullable = false)
    private String location;

    @Column(nullable = false)
    private int format;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private Status status;

    @Column(name = "score_a")
    private Integer scoreA;

    @Column(name = "score_b")
    private Integer scoreB;

    @Column(name = "mvp_player_id")
    private UUID mvpPlayerId;

    @Column(name = "closed_at")
    private Instant closedAt;

    @Column(name = "annulled_at")
    private Instant annulledAt;

    @Version
    private long version;

    protected Match() {}

    public Match(UUID groupId, Instant playedAt, String location, int format) {
        this.groupId = groupId;
        this.playedAt = playedAt;
        this.location = location;
        this.format = format;
        this.status = Status.SCHEDULED;
    }

    public UUID getId() {
        return id;
    }

    public UUID getGroupId() {
        return groupId;
    }

    public Instant getPlayedAt() {
        return playedAt;
    }

    public void setPlayedAt(Instant playedAt) {
        this.playedAt = playedAt;
    }

    public String getLocation() {
        return location;
    }

    public void setLocation(String location) {
        this.location = location;
    }

    public int getFormat() {
        return format;
    }

    public void setFormat(int format) {
        this.format = format;
    }

    public Status getStatus() {
        return status;
    }

    public void setStatus(Status status) {
        this.status = status;
    }

    public Integer getScoreA() {
        return scoreA;
    }

    public Integer getScoreB() {
        return scoreB;
    }

    public UUID getMvpPlayerId() {
        return mvpPlayerId;
    }

    public Instant getClosedAt() {
        return closedAt;
    }

    public Instant getAnnulledAt() {
        return annulledAt;
    }

    public long getVersion() {
        return version;
    }

    public interface Repo extends JpaRepository<Match, UUID> {

        Optional<Match> findByIdAndGroupId(UUID id, UUID groupId);

        List<Match> findTop200ByGroupIdOrderByPlayedAtDescIdDesc(UUID groupId);
    }
}
