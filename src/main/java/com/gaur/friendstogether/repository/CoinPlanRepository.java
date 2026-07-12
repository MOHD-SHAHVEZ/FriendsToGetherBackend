package com.gaur.friendstogether.repository;

import com.gaur.friendstogether.entity.CoinPlan;
import com.gaur.friendstogether.entity.CoinPlanStatus;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

/**
 * CoinPlanRepository
 *
 * CoinPlan entity ke liye database operations.
 */
@Repository
public interface CoinPlanRepository extends JpaRepository<CoinPlan, Long> {

    /** Check karo ke ye code already exist to nahi karta */
    boolean existsByCode(String code);

    /** User side — sirf ACTIVE plans, displayOrder se sorted */
    List<CoinPlan> findAllByStatusOrderByDisplayOrderAsc(CoinPlanStatus status);

    /** Admin side — saare plans, displayOrder se sorted */
    List<CoinPlan> findAllByOrderByDisplayOrderAsc();
}
