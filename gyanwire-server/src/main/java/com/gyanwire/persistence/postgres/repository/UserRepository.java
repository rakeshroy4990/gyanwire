package com.gyanwire.persistence.postgres.repository;

import com.gyanwire.persistence.postgres.model.UserEntity;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Optional;
import java.util.UUID;

public interface UserRepository extends JpaRepository<UserEntity, UUID> {

    @Query("select u from UserEntity u where lower(u.email) = lower(:email) and u.deletedAt is null")
    Optional<UserEntity> findActiveByEmail(@Param("email") String email);

    @Query("select u from UserEntity u where u.id = :id and u.deletedAt is null")
    Optional<UserEntity> findActiveById(@Param("id") UUID id);

    /** Row lock so parallel idea/search reservations cannot race past the daily limit. */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select u from UserEntity u where u.id = :id")
    Optional<UserEntity> findByIdForUpdate(@Param("id") UUID id);
}
