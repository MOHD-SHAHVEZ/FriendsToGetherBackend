package com.gaur.friendstogether.dto.request;

import com.gaur.friendstogether.entity.CoinPlanStatus;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

/**
 * UpdateCoinPlanStatusRequest
 *
 * Admin sirf plan ka status change karega (ACTIVE / INACTIVE).
 */
@Data
public class UpdateCoinPlanStatusRequest {

    @NotNull(message = "Status is required")
    private CoinPlanStatus status;
}
