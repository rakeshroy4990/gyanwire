package com.gyanwire.persistence.postgres.repository;

import com.gyanwire.persistence.postgres.model.RefreshTokenEntity;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Optional;
import java.util.UUID;

public interface RefreshTokenRepository extends JpaRepository<RefreshTokenEntity, UUID> {

    @Query("select t from RefreshTokenEntity t where t.token = :token and t.deletedAt is null")
    Optional<RefreshTokenEntity> findActiveByToken(@Param("token") String token);
}
