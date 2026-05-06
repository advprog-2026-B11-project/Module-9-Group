package com.example.bidmart.user.repository;

import com.example.bidmart.user.model.Session;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface SessionRepository extends JpaRepository<Session, UUID> {
    Optional<Session> findByRefreshToken(String refreshToken);
    void deleteAllByUserId(UUID userId);
    List<Session> findByUserIdAndIsRevokedFalseOrderByCreatedAtAsc(UUID userId);
    Optional<Session> findByIdAndUserId(UUID id, UUID userId);
}
