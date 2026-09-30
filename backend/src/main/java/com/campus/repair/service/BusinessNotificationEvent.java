package com.campus.repair.service;

/** Immutable notification snapshot; the order event ID distinguishes repeated business rounds. */
public record BusinessNotificationEvent(long targetUserId, String eventType, long businessId,
                                        String title, String content, String idempotencyKey) {}
