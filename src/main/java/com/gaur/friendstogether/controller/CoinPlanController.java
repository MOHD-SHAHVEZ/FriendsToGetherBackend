package com.gaur.friendstogether.controller;

import com.gaur.friendstogether.dto.response.ApiResponse;
import com.gaur.friendstogether.dto.response.CoinPlanResponse;
import com.gaur.friendstogether.service.CoinPlanService;
import lombok.RequiredArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;

/**
 * CoinPlanController
 *
 * User ke liye Coin Plan dekhne ki APIs.
 * Sirf ACTIVE plans dikhenge.
 */
@RestController
@RequestMapping("/api/coin-plans")
@RequiredArgsConstructor
public class CoinPlanController {

    private static final Logger logger = LoggerFactory.getLogger(CoinPlanController.class);

    private final CoinPlanService coinPlanService;

    /**
     * GET /api/coin-plans
     *
     * Saare ACTIVE Coin Plans — displayOrder se sorted.
     * User ko sirf purchasable plans dikhenge.
     */
    @GetMapping
    public ResponseEntity<ApiResponse<List<CoinPlanResponse>>> getActiveCoinPlans() {

        logger.info("User fetching active coin plans");

        List<CoinPlanResponse> plans = coinPlanService.getActiveCoinPlansForUser();

        ApiResponse<List<CoinPlanResponse>> response = ApiResponse.<List<CoinPlanResponse>>builder()
                .success(true)
                .message("Active coin plans fetched successfully")
                .data(plans)
                .build();

        return ResponseEntity.ok(response);
    }

    /**
     * GET /api/coin-plans/{id}
     *
     * Single Coin Plan detail.
     * Sirf ACTIVE plan milega, warna 404.
     */
    @GetMapping("/{id}")
    public ResponseEntity<ApiResponse<CoinPlanResponse>> getCoinPlanDetail(@PathVariable Long id) {

        logger.info("User fetching coin plan detail: id={}", id);

        CoinPlanResponse plan = coinPlanService.getCoinPlanByIdForUser(id);

        ApiResponse<CoinPlanResponse> response = ApiResponse.<CoinPlanResponse>builder()
                .success(true)
                .message("Coin plan detail fetched successfully")
                .data(plan)
                .build();

        return ResponseEntity.ok(response);
    }
}
