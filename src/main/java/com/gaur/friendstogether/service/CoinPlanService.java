package com.gaur.friendstogether.service;

import com.gaur.friendstogether.dto.request.CreateCoinPlanRequest;
import com.gaur.friendstogether.dto.request.UpdateCoinPlanRequest;
import com.gaur.friendstogether.dto.request.UpdateCoinPlanStatusRequest;
import com.gaur.friendstogether.dto.response.CoinPlanResponse;
import com.gaur.friendstogether.entity.CoinPlan;
import com.gaur.friendstogether.entity.CoinPlanStatus;
import com.gaur.friendstogether.exception.BusinessLogicException;
import com.gaur.friendstogether.exception.ResourceNotFoundException;
import com.gaur.friendstogether.repository.CoinPlanRepository;
import lombok.RequiredArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.stream.Collectors;

/**
 * CoinPlanService
 *
 * Coin Plan module ki saari business logic yahan hai.
 * Admin side — CRUD operations
 * User side  — Active plans dekhna
 */
@Service
@RequiredArgsConstructor
public class CoinPlanService {

    private static final Logger logger = LoggerFactory.getLogger(CoinPlanService.class);

    private final CoinPlanRepository coinPlanRepository;

    // =========================================================================
    // ADMIN SIDE
    // =========================================================================

    /**
     * Naya Coin Plan create karo.
     *
     * Duplicate code check hota hai — agar code pehle se exist karta hai to error.
     */
    @Transactional
    public CoinPlanResponse createCoinPlan(CreateCoinPlanRequest request) {

        // Duplicate code check
        if (coinPlanRepository.existsByCode(request.getCode())) {
            throw new BusinessLogicException(
                    "Coin Plan with code '" + request.getCode() + "' already exists",
                    HttpStatus.CONFLICT
            );
        }

        CoinPlan coinPlan = CoinPlan.builder()
                .code(request.getCode())
                .name(request.getName())
                .description(request.getDescription())
                .imageUrl(request.getImageUrl())
                .coins(request.getCoins())
                .bonusCoins(request.getBonusCoins())
                .price(request.getPrice())
                .currency(request.getCurrency())
                .badge(request.getBadge())
                .status(request.getStatus())
                .displayOrder(request.getDisplayOrder())
                .offerStartAt(request.getOfferStartAt())
                .offerEndAt(request.getOfferEndAt())
                .purchaseLimit(request.getPurchaseLimit())
                .build();

        CoinPlan saved = coinPlanRepository.save(coinPlan);
        logger.info("Coin Plan created: code={}, name={}", saved.getCode(), saved.getName());

        return mapToResponse(saved);
    }

    /**
     * Existing Coin Plan ko fully update karo (PUT).
     *
     * Code change karne par duplicate check hota hai.
     */
    @Transactional
    public CoinPlanResponse updateCoinPlan(Long id, UpdateCoinPlanRequest request) {

        CoinPlan coinPlan = coinPlanRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Coin Plan not found with id: " + id));

        // Agar code change ho raha hai to duplicate check karo
        if (!coinPlan.getCode().equals(request.getCode())
                && coinPlanRepository.existsByCode(request.getCode())) {
            throw new BusinessLogicException(
                    "Coin Plan with code '" + request.getCode() + "' already exists",
                    HttpStatus.CONFLICT
            );
        }

        coinPlan.setCode(request.getCode());
        coinPlan.setName(request.getName());
        coinPlan.setDescription(request.getDescription());
        coinPlan.setImageUrl(request.getImageUrl());
        coinPlan.setCoins(request.getCoins());
        coinPlan.setBonusCoins(request.getBonusCoins());
        coinPlan.setPrice(request.getPrice());
        coinPlan.setCurrency(request.getCurrency());
        coinPlan.setBadge(request.getBadge());
        coinPlan.setStatus(request.getStatus());
        coinPlan.setDisplayOrder(request.getDisplayOrder());
        coinPlan.setOfferStartAt(request.getOfferStartAt());
        coinPlan.setOfferEndAt(request.getOfferEndAt());
        coinPlan.setPurchaseLimit(request.getPurchaseLimit());

        // Version increment on update
        coinPlan.setVersion(coinPlan.getVersion() + 1);

        CoinPlan saved = coinPlanRepository.save(coinPlan);
        logger.info("Coin Plan updated: id={}, code={}", saved.getId(), saved.getCode());

        return mapToResponse(saved);
    }

    /**
     * Sirf status change karo (ACTIVE / INACTIVE).
     *
     * PATCH endpoint ke liye.
     */
    @Transactional
    public CoinPlanResponse updateCoinPlanStatus(Long id, UpdateCoinPlanStatusRequest request) {

        CoinPlan coinPlan = coinPlanRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Coin Plan not found with id: " + id));

        coinPlan.setStatus(request.getStatus());

        CoinPlan saved = coinPlanRepository.save(coinPlan);
        logger.info("Coin Plan status updated: id={}, newStatus={}", saved.getId(), saved.getStatus());

        return mapToResponse(saved);
    }

    /**
     * Admin ke liye saare plans (har status ke).
     *
     * Display order se sorted.
     */
    @Transactional(readOnly = true)
    public List<CoinPlanResponse> getAllCoinPlansForAdmin() {

        return coinPlanRepository.findAllByOrderByDisplayOrderAsc()
                .stream()
                .map(this::mapToResponse)
                .collect(Collectors.toList());
    }

    /**
     * Soft delete — plan ka status INACTIVE kar do.
     *
     * Data DB me rehta hai, sirf users ko nahi dikhega.
     */
    @Transactional
    public void deleteCoinPlan(Long id) {

        CoinPlan coinPlan = coinPlanRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Coin Plan not found with id: " + id));

        coinPlan.setStatus(CoinPlanStatus.INACTIVE);
        coinPlanRepository.save(coinPlan);

        logger.info("Coin Plan soft deleted (INACTIVE): id={}, code={}", coinPlan.getId(), coinPlan.getCode());
    }

    // =========================================================================
    // USER SIDE
    // =========================================================================

    /**
     * User ke liye sirf ACTIVE plans.
     *
     * Display order se sorted.
     */
    @Transactional(readOnly = true)
    public List<CoinPlanResponse> getActiveCoinPlansForUser() {

        return coinPlanRepository.findAllByStatusOrderByDisplayOrderAsc(CoinPlanStatus.ACTIVE)
                .stream()
                .map(this::mapToResponse)
                .collect(Collectors.toList());
    }

    /**
     * User ke liye single plan detail.
     *
     * Sirf ACTIVE plan return hoga, warna 404.
     */
    @Transactional(readOnly = true)
    public CoinPlanResponse getCoinPlanByIdForUser(Long id) {

        CoinPlan coinPlan = coinPlanRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Coin Plan not found with id: " + id));

        if (coinPlan.getStatus() != CoinPlanStatus.ACTIVE) {
            throw new ResourceNotFoundException("Coin Plan not found with id: " + id);
        }

        return mapToResponse(coinPlan);
    }

    // =========================================================================
    // HELPER
    // =========================================================================

    /**
     * Entity ko Response DTO me convert karo.
     */
    private CoinPlanResponse mapToResponse(CoinPlan plan) {

        return CoinPlanResponse.builder()
                .id(plan.getId())
                .code(plan.getCode())
                .name(plan.getName())
                .description(plan.getDescription())
                .imageUrl(plan.getImageUrl())
                .coins(plan.getCoins())
                .bonusCoins(plan.getBonusCoins())
                .totalCoins(plan.getCoins() + plan.getBonusCoins())
                .price(plan.getPrice())
                .currency(plan.getCurrency())
                .badge(plan.getBadge())
                .status(plan.getStatus())
                .displayOrder(plan.getDisplayOrder())
                .offerStartAt(plan.getOfferStartAt())
                .offerEndAt(plan.getOfferEndAt())
                .purchaseLimit(plan.getPurchaseLimit())
                .createdAt(plan.getCreatedAt())
                .updatedAt(plan.getUpdatedAt())
                .build();
    }
}
