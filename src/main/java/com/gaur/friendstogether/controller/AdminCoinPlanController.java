package com.gaur.friendstogether.controller;

import com.gaur.friendstogether.dto.request.CreateCoinPlanRequest;
import com.gaur.friendstogether.dto.request.UpdateCoinPlanRequest;
import com.gaur.friendstogether.dto.request.UpdateCoinPlanStatusRequest;
import com.gaur.friendstogether.dto.response.ApiResponse;
import com.gaur.friendstogether.dto.response.CoinPlanResponse;
import com.gaur.friendstogether.service.CoinPlanService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.List;

/**
 * AdminCoinPlanController
 *
 * Admin ke liye Coin Plan CRUD APIs.
 * Sirf ADMIN role wale users hi access kar sakte hain.
 */
@RestController
@RequestMapping("/api/admin/coin-plans")
@RequiredArgsConstructor
@PreAuthorize("hasRole('ADMIN')")
public class AdminCoinPlanController {

    private static final Logger logger = LoggerFactory.getLogger(AdminCoinPlanController.class);

    private final CoinPlanService coinPlanService;

    /**
     * POST /api/admin/coin-plans
     *
     * Naya Coin Plan create karo.
     */
    @PostMapping
    public ResponseEntity<ApiResponse<CoinPlanResponse>> createCoinPlan(
            @Valid @RequestBody CreateCoinPlanRequest request) {

        logger.info("Admin creating coin plan: code={}", request.getCode());

        CoinPlanResponse created = coinPlanService.createCoinPlan(request);

        ApiResponse<CoinPlanResponse> response = ApiResponse.<CoinPlanResponse>builder()
                .success(true)
                .message("Coin Plan created successfully")
                .data(created)
                .build();

        return new ResponseEntity<>(response, HttpStatus.CREATED);
    }

    /**
     * PUT /api/admin/coin-plans/{id}
     *
     * Existing Coin Plan ko fully update karo.
     */
    @PutMapping("/{id}")
    public ResponseEntity<ApiResponse<CoinPlanResponse>> updateCoinPlan(
            @PathVariable Long id,
            @Valid @RequestBody UpdateCoinPlanRequest request) {

        logger.info("Admin updating coin plan: id={}", id);

        CoinPlanResponse updated = coinPlanService.updateCoinPlan(id, request);

        ApiResponse<CoinPlanResponse> response = ApiResponse.<CoinPlanResponse>builder()
                .success(true)
                .message("Coin Plan updated successfully")
                .data(updated)
                .build();

        return ResponseEntity.ok(response);
    }

    /**
     * PATCH /api/admin/coin-plans/{id}/status
     *
     * Sirf status toggle karo (ACTIVE / INACTIVE).
     */
    @PatchMapping("/{id}/status")
    public ResponseEntity<ApiResponse<CoinPlanResponse>> updateCoinPlanStatus(
            @PathVariable Long id,
            @Valid @RequestBody UpdateCoinPlanStatusRequest request) {

        logger.info("Admin updating coin plan status: id={}, newStatus={}", id, request.getStatus());

        CoinPlanResponse updated = coinPlanService.updateCoinPlanStatus(id, request);

        ApiResponse<CoinPlanResponse> response = ApiResponse.<CoinPlanResponse>builder()
                .success(true)
                .message("Coin Plan status updated successfully")
                .data(updated)
                .build();

        return ResponseEntity.ok(response);
    }

    /**
     * GET /api/admin/coin-plans
     *
     * Saare Coin Plans (har status ke) — displayOrder se sorted.
     */
    @GetMapping
    public ResponseEntity<ApiResponse<List<CoinPlanResponse>>> getAllCoinPlans() {

        logger.info("Admin fetching all coin plans");

        List<CoinPlanResponse> plans = coinPlanService.getAllCoinPlansForAdmin();

        ApiResponse<List<CoinPlanResponse>> response = ApiResponse.<List<CoinPlanResponse>>builder()
                .success(true)
                .message("All coin plans fetched successfully")
                .data(plans)
                .build();

        return ResponseEntity.ok(response);
    }

    /**
     * DELETE /api/admin/coin-plans/{id}
     *
     * Soft delete — plan ka status INACTIVE ho jayega.
     * Database se delete karne ki liya hard delete logic add kardo only. 
     * Data DB me rehta hai.
     */
    @DeleteMapping("/{id}")
    public ResponseEntity<ApiResponse<Void>> deleteCoinPlan(@PathVariable Long id) {

        logger.info("Admin soft deleting coin plan: id={}", id);

        coinPlanService.deleteCoinPlan(id);

        ApiResponse<Void> response = ApiResponse.<Void>builder()
                .success(true)
                .message("Coin Plan deleted (deactivated) successfully")
                .build();

        return ResponseEntity.ok(response);
    }
}
