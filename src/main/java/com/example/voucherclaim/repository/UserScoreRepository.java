package com.example.voucherclaim.repository;

import com.example.voucherclaim.entity.UserScore;
import org.springframework.data.jpa.repository.JpaRepository;

/** JPA boundary for durable user priority scores. */
public interface UserScoreRepository extends JpaRepository<UserScore, String> {
}
