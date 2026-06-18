package com.rudra.incidenttriage.repository;

import java.util.Optional;

import com.rudra.incidenttriage.domain.entity.User;
import org.springframework.data.jpa.repository.JpaRepository;

public interface UserRepository extends JpaRepository<User, Long> {

	Optional<User> findByUsername(String username);

	boolean existsByUsername(String username);
}
