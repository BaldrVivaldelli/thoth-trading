package com.aaa.thoth.core.enums;

public enum OrderType {
    MARKET("Market", false),
    LIMIT("Limit", true),
    STOP("Stop", true),
    STOP_LIMIT("Stop Limit", true),
    IOC("Immediate or Cancel", true),    // Immediate or Cancel
    FOK("Fill or Kill", true),          // Fill or Kill
    ICEBERG("Iceberg", true);           // Orden oculta parcialmente

    private final String displayName;
    private final boolean requiresPrice;

    OrderType(String displayName, boolean requiresPrice) {
        this.displayName = displayName;
        this.requiresPrice = requiresPrice;
    }

    public String getDisplayName() {
        return displayName;
    }

    public boolean requiresPrice() {
        return requiresPrice;
    }
}
