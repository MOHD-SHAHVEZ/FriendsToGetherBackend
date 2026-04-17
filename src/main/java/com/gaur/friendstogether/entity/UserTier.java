package com.gaur.friendstogether.entity;

public enum UserTier {

    SILVER(0, 500),
    GOLD(501, 1000),
    DIAMOND(1001, 2000),
    CROWN(2001, 3200),
    ACE(3201, 4500),
    CONQUEROR(4501, Integer.MAX_VALUE);

    private final int minPoints;
    private final int maxPoints;

    UserTier(int minPoints, int maxPoints) {
        this.minPoints = minPoints;
        this.maxPoints = maxPoints;
    }

    // CORE: Points → Tier
    public static UserTier getTierByPoints(int points) {
        for (UserTier tier : values()) {
            if (points >= tier.minPoints && points <= tier.maxPoints) {
                return tier;
            }
        }
        return SILVER;
    }

    // Compare tiers
    public boolean isHigherThan(UserTier other) {
        return this.ordinal() > other.ordinal();
    }

    public boolean isLowerThan(UserTier other) {
        return this.ordinal() < other.ordinal();
    }

    // Next Tier (Upgrade)
    public UserTier next() {
        int nextOrdinal = this.ordinal() + 1;
        return nextOrdinal < values().length ? values()[nextOrdinal] : this;
    }

    // Previous Tier (Downgrade)
    public UserTier previous() {
        int prevOrdinal = this.ordinal() - 1;
        return prevOrdinal >= 0 ? values()[prevOrdinal] : this;
    }

    // Check if max tier
    public boolean isTopTier() {
        return this == CONQUEROR;
    }

    // UI Friendly Name
    public String displayName() {
        return this.name().charAt(0) + this.name().substring(1).toLowerCase();
    }
}