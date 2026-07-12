package com.gaur.friendstogether.repository;

import com.gaur.friendstogether.entity.CoinOrder;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface CoinOrderRepository extends JpaRepository<CoinOrder, Long> {
}
