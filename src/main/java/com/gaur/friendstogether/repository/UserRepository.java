package com.gaur.friendstogether.repository;

import com.gaur.friendstogether.entity.User;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;
import java.util.Optional;

@Repository
public interface UserRepository extends JpaRepository<User, Long> {
    Optional<User> findByEmail(String email);
    boolean existsByEmail(String email);

    // --- Ye do lines add karein (Important) ---
    Optional<User> findByUsername(String username);
    boolean existsByUsername(String username);

    // these 2 methods are use to search user based on userId
    Optional<User> findByUserId(String userId);
    boolean existsByUserId(String userId);

}