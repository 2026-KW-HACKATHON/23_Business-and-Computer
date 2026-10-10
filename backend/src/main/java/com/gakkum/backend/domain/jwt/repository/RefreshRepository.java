package com.gakkum.backend.domain.jwt.repository;

import com.gakkum.backend.domain.jwt.entity.RefreshToken;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface RefreshRepository extends JpaRepository<RefreshToken, Long> {
    void deleteByRefresh(String refresh);
    void deleteByUsername(String username);

    /*
     * 메서드 이름 삭제(deleteBy...)는 행을 먼저 읽은 뒤 지워서, 같은 토큰으로 동시에 들어온 두 요청이 모두 행을 볼 수 있다.
     * DELETE 한 번으로 지우고 지운 행 수를 돌려주어야 먼저 지운 요청만 1을 받는다 (나중 요청은 행 잠금을 기다린 뒤 0을 받는다).
     */
    @Modifying
    @Query("delete from RefreshToken r where r.refresh = :refresh")
    int deleteAllByRefresh(@Param("refresh") String refresh);
}
