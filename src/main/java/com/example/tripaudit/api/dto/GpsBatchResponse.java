package com.example.tripaudit.api.dto;

public record GpsBatchResponse(
        int accepted,
        int duplicatesIgnored
) {}
