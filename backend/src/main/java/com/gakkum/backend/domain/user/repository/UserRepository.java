package com.gakkum.backend.domain.user.repository;

import com.gakkum.backend.domain.user.entity.User;
import com.gakkum.backend.domain.user.entity.UserRole;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import jakarta.persistence.LockModeType;

import java.time.LocalDateTime;
import java.util.Optional;

public interface UserRepository extends JpaRepository<User, String> {
    Optional<User> findByUsername(String username);
    Optional<User> findByUsernameAndIsLock(String username, Boolean isLock);
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    Optional<User> findByUsernameAndIsLockFalse(String username);
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    Optional<User> findByIdAndIsLockFalse(String id);
    boolean existsByEmailIgnoreCase(String email);
    Optional<User> findByDemoSessionIdAndRoleAndIsLock(String demoSessionId, UserRole role, Boolean isLock);
    long countByDemoSessionIdIsNotNullAndRoleAndIsLockAndCreatedAtAfter(
            UserRole role, Boolean isLock, LocalDateTime createdAt);

    /**
     * 잠기지 않은 사용자의 역할이 아직 expected일 때만 role로 바꾸고 바뀐 행 수를 돌려준다.
     * 같은 행을 먼저 바꾼 트랜잭션이 있으면 PostgreSQL이 그 커밋을 기다린 뒤 조건을 다시 보므로 둘 중 하나만 1을 받는다.
     * 잠금 조회(SELECT ... FOR UPDATE)는 OSIV로 이미 읽어 둔 엔티티의 예전 역할을 그대로 돌려줄 수 있어,
     * DB가 조건을 판단하는 조건부 UPDATE를 쓴다. 메서드 이름 쿼리로는 UPDATE를 표현할 수 없어 @Query를 쓴다.
     */
    @Modifying
    @Query("update User u set u.role = :role where u.id = :id and u.role = :expected and u.isLock = false")
    int updateRoleIfCurrent(@Param("id") String id, @Param("expected") UserRole expected, @Param("role") UserRole role);
}
