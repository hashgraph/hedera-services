package com.hedera.node.app.workflows.common;

import com.hederahashgraph.api.proto.java.ResponseCodeEnum;

import javax.annotation.Nonnull;

/**
 * An {@code InsufficientBalanceException} is a {@link PreCheckException} that is thrown, when the balance
 * is not sufficient. It provides the {@link #estimatedFee}.
 */
public class InsufficientBalanceException extends PreCheckException {

    private final long estimatedFee;

    /**
     * Constructor of {@code InsufficientBalanceException}
     *
     * @param responseCode the {@link ResponseCodeEnum responseCode}
     * @param estimatedFee the estimated fee
     * @throws NullPointerException if {@code responseCode} is {@code null}
     */
    public InsufficientBalanceException(@Nonnull final ResponseCodeEnum responseCode, final long estimatedFee) {
        super(responseCode);
        this.estimatedFee = estimatedFee;
    }

    /**
     * Returns the estimated fee
     *
     * @return the estimated fee
     */
    public long getEstimatedFee() {
        return estimatedFee;
    }
}
