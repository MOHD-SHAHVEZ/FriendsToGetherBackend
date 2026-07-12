package com.gaur.friendstogether.entity;

/**
 * ============================================================================
 * WebhookStatus
 * ============================================================================
 *
 * Represents the processing state of an incoming webhook.
 */
public enum WebhookStatus {

    /**
     * Webhook received successfully.
     * Processing has not yet started.
     */
    RECEIVED,

    /**
     * Processing currently in progress.
     */
    PROCESSING,

    /**
     * Successfully processed.
     */
    PROCESSED,

    /**
     * Processing failed.
     */
    FAILED,

    /**
     * Duplicate webhook ignored.
     */
    IGNORED

}
