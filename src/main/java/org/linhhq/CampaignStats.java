package org.linhhq;

public class CampaignStats {
    private long impressions;
    private long clicks;
    private double spend;
    private long conversions;

    public CampaignStats() {
    }

    public void add(long impressions, long clicks, double spend, long conversions) {
        this.impressions += impressions;
        this.clicks += clicks;
        this.spend += spend;
        this.conversions += conversions;
    }

    public double ctr() {
        if (impressions == 0) return 0.0;
        double raw = (double) clicks / impressions;
        return Math.floor(raw * 10000) / 10000;
    }

    public double cpa() {
        if (conversions == 0) return 0.0;
        double raw = spend / conversions;
        return Math.floor(raw * 100) / 100;
    }

    public long getImpressions() {
        return this.impressions;
    }

    public long getClicks() {
        return this.clicks;
    }

    public double getSpend() {
        return this.spend;
    }

    public long getConversions() {
        return this.conversions;
    }
}
