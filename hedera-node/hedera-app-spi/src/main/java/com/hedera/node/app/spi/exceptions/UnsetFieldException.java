package com.hedera.node.app.spi.exceptions;

/**
 * A runtime exception caused when a field in one-of-kind for an entity is not set.
 * For example, an {@link com.hedera.hapi.node.base.AccountID} should have account number
 * or alias set. If both are not set then this exception is thrown.
 */
public class UnsetFieldException extends RuntimeException {
    public UnsetFieldException(final String message) {
        super(message);
    }
}
