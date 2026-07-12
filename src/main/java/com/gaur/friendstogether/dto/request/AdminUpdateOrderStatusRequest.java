package com.gaur.friendstogether.dto.request;

import jakarta.validation.constraints.Size;
import lombok.Data;

/**
 * =============================================================================
 * AdminUpdateOrderStatusRequest
 * =============================================================================
 *
 * Request body for admin order status changes:
 *
 * PATCH /api/admin/orders/{orderNumber}/cancel
 * PATCH /api/admin/orders/{orderNumber}/expire
 * PATCH /api/admin/orders/{orderNumber}/fail
 *
 * Only contains optional remarks.
 * The target status is determined by the endpoint itself,
 * not by the request body — this prevents admin from
 * setting arbitrary statuses.
 * =============================================================================
 */
@Data
public class AdminUpdateOrderStatusRequest {

    /**
     * Optional admin remarks.
     *
     * Example:
     * "Cancelled per customer request via support ticket #1234"
     * "Expired manually — customer confirmed they won't pay"
     * "Failed — suspected fraudulent activity"
     */
    @Size(max = 500, message = "Remarks must not exceed 500 characters")
    private String remarks;
}
