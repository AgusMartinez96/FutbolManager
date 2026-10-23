package com.futbolmanager.group;

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
@Table(name = "memberships")
public class Membership {

    public enum Role {
        ADMIN,
        MEMBER
    }

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(name = "group_id", nullable = false, updatable = false)
    private UUID groupId;

    @Column(name = "user_id", nullable = false, updatable = false)
    private UUID userId;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private Role role;

    protected Membership() {}

    public Membership(UUID groupId, UUID userId, Role role) {
        this.groupId = groupId;
        this.userId = userId;
        this.role = role;
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

    public Role getRole() {
        return role;
    }

    public void setRole(Role role) {
        this.role = role;
    }

    public interface Repo extends JpaRepository<Membership, UUID> {

        Optional<Membership> findByGroupIdAndUserId(UUID groupId, UUID userId);

        List<Membership> findAllByGroupId(UUID groupId);

        List<Membership> findAllByUserId(UUID userId);

        long countByGroupIdAndRole(UUID groupId, Role role);
    }
}
