package com.gaur.friendstogether.repository;

import com.gaur.friendstogether.entity.Friendship;
import com.gaur.friendstogether.entity.FriendshipStatus;
import com.gaur.friendstogether.entity.User;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
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
    // --- [FIX]: JOIN FETCH use karke Sender aur uska Profile pehle hi load kar lo ---
    @Query("SELECT f FROM Friendship f JOIN FETCH f.sender s JOIN FETCH s.profile WHERE f.receiver = :receiver AND f.status = :status")
    List<Friendship> findByReceiverAndStatus(@Param("receiver") User receiver, @Param("status") FriendshipStatus status);
    // 4. Find all friends (Where status is ACCEPTED)
    List<Friendship> findBySenderAndStatusOrReceiverAndStatus(User sender, FriendshipStatus s1, User receiver, FriendshipStatus s2);

    // 5. GetAllFriends -> Status Friends Request Accepted
    @Query("SELECT f FROM Friendship f " +
            "JOIN FETCH f.sender s JOIN FETCH s.profile " +
            "JOIN FETCH f.receiver r JOIN FETCH r.profile " +
            "WHERE (f.sender = :user OR f.receiver = :user) AND f.status = 'ACCEPTED'")
    List<Friendship> findAllAcceptedFriends(@Param("user") User user);

    // 6. Get All Friends Count:
    // FriendshipRepository.java
    @Query("SELECT COUNT(f) FROM Friendship f WHERE (f.sender = :user OR f.receiver = :user) AND f.status = 'ACCEPTED'")
    long countAcceptedFriends(@Param("user") User user);

}