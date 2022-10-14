package com.kyber.dex.comparator;

import com.google.cloud.Timestamp;

import java.util.Date;

public class DexSwapInfo {
    private String id;
    private String chainName;
    private String provider;
    private String tokenIn;
    private double amountIn;
    private String tokenOut;
    private double amountOut;
    private Double swapValue;
    private Date createdAt;

    private int rank;
    private Double compareToKyber;

    public DexSwapInfo() {
    }

    public String getId() {
        return id;
    }

    public void setId(String id) {
        this.id = id;
    }

    public String getChainName() {
        return chainName;
    }

    public void setChainName(String chainName) {
        this.chainName = chainName;
    }

    public String getProvider() {
        return provider;
    }

    public void setProvider(String provider) {
        this.provider = provider;
    }

    public String getTokenIn() {
        return tokenIn;
    }

    public void setTokenIn(String tokenIn) {
        this.tokenIn = tokenIn;
    }

    public double getAmountIn() {
        return amountIn;
    }

    public void setAmountIn(double amountIn) {
        this.amountIn = amountIn;
    }

    public String getTokenOut() {
        return tokenOut;
    }

    public void setTokenOut(String tokenOut) {
        this.tokenOut = tokenOut;
    }

    public double getAmountOut() {
        return amountOut;
    }

    public void setAmountOut(double amountOut) {
        this.amountOut = amountOut;
    }

    public Double getSwapValue() {
        return swapValue;
    }

    public void setSwapValue(Double swapValue) {
        this.swapValue = swapValue;
    }

    public Date getCreatedAt() {
        return createdAt;
    }

    public void setCreatedAt(Date createdAt) {
        this.createdAt = createdAt;
    }

    public int getRank() {
        return rank;
    }

    public void setRank(int rank) {
        this.rank = rank;
    }

    public Double getCompareToKyber() {
        return compareToKyber;
    }

    public void setCompareToKyber(Double compareToKyber) {
        this.compareToKyber = compareToKyber;
    }

    @Override
    public String toString() {
        return "DexSwapInfo{" +
                "id='" + id + '\'' +
                ", chainName='" + chainName + '\'' +
                ", provider='" + provider + '\'' +
                ", tokenIn='" + tokenIn + '\'' +
                ", amountIn=" + amountIn +
                ", tokenOut='" + tokenOut + '\'' +
                ", amountOut=" + amountOut +
                ", swapValue=" + swapValue +
                ", createdAt=" + createdAt +
                ", rank=" + rank +
                ", compareToKyber=" + compareToKyber +
                '}';
    }
}
