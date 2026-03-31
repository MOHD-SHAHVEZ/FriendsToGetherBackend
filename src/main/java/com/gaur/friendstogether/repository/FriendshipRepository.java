package com.gaur.friendstogether.repository;

import com.gaur.friendstogether.entity.Friendship;
import com.gaur.friendstogether.entity.FriendshipStatus;
import com.gaur.friendstogether.entity.User;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface FriendshipRepository extends JpaRepository<Friendship, Long> {

    // 1. Check if a request already exists (Fast Check)
    boolean existsBySenderAndReceiver(User sender, User receiver);

    // 2. Find specific request between two people
    Optional<Friendship> findBySenderAndReceiver(User sender, User receiver);

    // 3. Find all pending requests for a user (Inbox)
    List<Friendship> findByReceiverAndStatus(User receiver, FriendshipStatus status);

    // 4. Find all friends (Where status is ACCEPTED)
    List<Friendship> findBySenderAndStatusOrReceiverAndStatus(User sender, FriendshipStatus s1, User receiver, FriendshipStatus s2);
}