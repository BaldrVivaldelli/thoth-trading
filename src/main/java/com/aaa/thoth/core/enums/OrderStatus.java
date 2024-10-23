package com.aaa.thoth.core.enums;

public enum OrderStatus {
    NEW("New"),
    VALIDATED("Validated"),
    REJECTED("Rejected", true),
    PARTIALLY_FILLED("Partially Filled"),
    FILLED("Filled", true),
    CANCELLED("Cancelled", true),
    EXPIRED("Expired", true);

    private final String displayName;
    private final boolean isFinal;

    OrderStatus(String displayName) {
        this(displayName, false);
    }

    OrderStatus(String displayName, boolean isFinal) {
        this.displayName = displayName;
        this.isFinal = isFinal;
    }

    public String getDisplayName() {
        return displayName;
    }

    public boolean isFinal() {
        return isFinal;
    }
}
