package com.futbolmanager.group;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.security.SecureRandom;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

// "Group" is a reserved word in JPQL, hence the entity name.
@Entity(name = "FriendGroup")
@Table(name = "groups")
public class FriendGroup {

    static final String INVITE_ALPHABET = "ABCDEFGHJKMNPQRSTUVWXYZ23456789";
    static final int INVITE_LENGTH = 8;

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(nullable = false)
    private String name;

    @Column(name = "invite_code", nullable = false)
    private String inviteCode;

    @Column(name = "members_can_score", nullable = false)
    private boolean membersCanScore;

    @Column(name = "created_by_user_id", nullable = false, updatable = false)
    private UUID createdByUserId;

    protected FriendGroup() {}

    public FriendGroup(String name, String inviteCode, UUID createdByUserId) {
        this.name = name;
        this.inviteCode = inviteCode;
        this.createdByUserId = createdByUserId;
    }

    public static String newInviteCode(SecureRandom random) {
        StringBuilder code = new StringBuilder(INVITE_LENGTH);
        for (int i = 0; i < INVITE_LENGTH; i++) {
            code.append(INVITE_ALPHABET.charAt(random.nextInt(INVITE_ALPHABET.length())));
        }
        return code.toString();
    }

    public UUID getId() {
        return id;
    }

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public String getInviteCode() {
        return inviteCode;
    }

    public void setInviteCode(String inviteCode) {
        this.inviteCode = inviteCode;
    }

    public boolean isMembersCanScore() {
        return membersCanScore;
    }

    public void setMembersCanScore(boolean membersCanScore) {
        this.membersCanScore = membersCanScore;
    }

    public UUID getCreatedByUserId() {
        return createdByUserId;
    }

    public interface Repo extends JpaRepository<FriendGroup, UUID> {

        Optional<FriendGroup> findByInviteCode(String inviteCode);

        boolean existsByInviteCode(String inviteCode);
    }
}
