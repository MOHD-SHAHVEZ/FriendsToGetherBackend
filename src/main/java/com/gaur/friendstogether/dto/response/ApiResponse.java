package com.gaur.friendstogether.dto.response;

import lombok.*;

/**
 * ApiResponse: Har API response ka standard format.
 */
@Getter
@Setter
@AllArgsConstructor
@NoArgsConstructor
@Builder
public class ApiResponse<T> {

    private boolean success;   // true ya false
    private String message;   // Kya hua? (e.g. "Room Created")
    private T data;           // Asli data (e.g. Room Object)

    @Builder.Default
    private long timestamp = System.currentTimeMillis();
}