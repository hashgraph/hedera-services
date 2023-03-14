package com.hedera.node.app.spi.exceptions;

/**
 * A runtime exception caused when the one-of-kind for different entities is not set.
 * For example, an {@link com.hedera.hapi.node.base.AccountID} should have account number
 * or alias set. If both are not set then this exception is thrown.
 */
public class InvalidOneOfKindException extends RuntimeException {
    public InvalidOneOfKindException(final String message) {
        super(message);
    }
}
