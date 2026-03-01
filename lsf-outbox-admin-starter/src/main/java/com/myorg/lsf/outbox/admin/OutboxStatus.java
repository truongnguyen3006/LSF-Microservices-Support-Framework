package com.myorg.lsf.outbox.admin;

public enum OutboxStatus {
    NEW, PROCESSING, RETRY, SENT, FAILED;

    public static OutboxStatus from(String s) {
        if (s == null) throw new IllegalArgumentException("status is null");
        return OutboxStatus.valueOf(s.trim().toUpperCase());
    }
}