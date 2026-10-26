package com.futbolmanager.player;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

@Entity
@Table(name = "players")
public class Player {

    public enum Position {
        GK,
        DEF,
        MID,
        FWD
    }

    public enum Foot {
        RIGHT,
        LEFT,
        BOTH
    }

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(name = "group_id", nullable = false, updatable = false)
    private UUID groupId;

    @Column(name = "user_id", updatable = false)
    private UUID userId;

    @Column(nullable = false)
    private String name;

    private String nickname;

    @Enumerated(EnumType.STRING)
    @Column(name = "preferred_position", nullable = false)
    private Position preferredPosition;

    @Enumerated(EnumType.STRING)
    @Column(name = "dominant_foot", nullable = false)
    private Foot dominantFoot;

    @Column(name = "current_elo", nullable = false)
    private int currentElo;

    @Column(name = "level_tenths", nullable = false)
    private int levelTenths;

    @Column(name = "deleted_at")
    private Instant deletedAt;

    protected Player() {}

    public Player(
            UUID groupId,
            UUID userId,
            String name,
            String nickname,
            Position preferredPosition,
            Foot dominantFoot,
            int currentElo,
            int levelTenths) {
        this.levelTenths = levelTenths;
        this.groupId = groupId;
        this.userId = userId;
        this.name = name;
        this.nickname = nickname;
        this.preferredPosition = preferredPosition;
        this.dominantFoot = dominantFoot;
        this.currentElo = currentElo;
    }

    public UUID getId() {
        return id;
    }

    public UUID getGroupId() {
        return groupId;
    }

    public UUID getUserId() {
        return userId;
    }

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public String getNickname() {
        return nickname;
    }

    public void setNickname(String nickname) {
        this.nickname = nickname;
    }

    public Position getPreferredPosition() {
        return preferredPosition;
    }

    public void setPreferredPosition(Position preferredPosition) {
        this.preferredPosition = preferredPosition;
    }

    public Foot getDominantFoot() {
        return dominantFoot;
    }

    public void setDominantFoot(Foot dominantFoot) {
        this.dominantFoot = dominantFoot;
    }

    public int getCurrentElo() {
        return currentElo;
    }

    /** Nivel en décimas enteras: 85 = 8,5. */
    public int getLevelTenths() {
        return levelTenths;
    }

    public void setLevelTenths(int levelTenths) {
        this.levelTenths = levelTenths;
    }

    public Instant getDeletedAt() {
        return deletedAt;
    }

    public boolean isDeleted() {
        return deletedAt != null;
    }

    public void markDeleted(Instant at) {
        this.deletedAt = at;
    }

    public void restore() {
        this.deletedAt = null;
    }

    public interface Repo extends JpaRepository<Player, UUID> {

        Optional<Player> findByIdAndGroupId(UUID id, UUID groupId);

        List<Player> findAllByGroupIdAndDeletedAtIsNullOrderByNameAsc(UUID groupId);

        Optional<Player> findByGroupIdAndUserId(UUID groupId, UUID userId);
    }
}
