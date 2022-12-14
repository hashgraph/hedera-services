package com.hedera.node.app.service.mono.token.impl;

import com.hedera.node.app.service.token.TokenPreTransactionHandler;
import com.hedera.node.app.spi.PreHandleContext;
import com.hedera.node.app.spi.meta.SigTransactionMetadataBuilder;
import com.hedera.node.app.spi.meta.TransactionMetadata;
import com.hederahashgraph.api.proto.java.ResponseCodeEnum;
import com.hederahashgraph.api.proto.java.TransactionBody;
import edu.umd.cs.findbugs.annotations.NonNull;
import org.apache.commons.lang3.NotImplementedException;

import java.util.Objects;

/**
 * A {@code TokenPreTransactionHandler} implementation that pre-computes the required signing keys
 * (but not the candidate signatures) for each token operation.
 */
public final class TokenPreTransactionHandlerImpl implements TokenPreTransactionHandler {
    private final AccountStore accountStore;
    private final TokenStore tokenStore;
    private final PreHandleContext preHandleContext;

    public TokenPreTransactionHandlerImpl(@NonNull final AccountStore accountStore, @NonNull final TokenStore tokenStore,
                                          @NonNull final PreHandleContext ctx) {
        this.accountStore = Objects.requireNonNull(accountStore);
        this.tokenStore = Objects.requireNonNull(tokenStore);
        this.preHandleContext = Objects.requireNonNull(ctx);
    }

    @Override
    /** {@inheritDoc} */
    public TransactionMetadata preHandleCreateToken(@NonNull final TransactionBody txn) {
        Objects.requireNonNull(txn);
        throw new NotImplementedException();
    }

    @Override
    /** {@inheritDoc} */
    public TransactionMetadata preHandleUpdateToken(@NonNull final TransactionBody txn) {
        Objects.requireNonNull(txn);
        throw new NotImplementedException();
    }

    @Override
    /** {@inheritDoc} */
    public TransactionMetadata preHandleMintToken(@NonNull final TransactionBody txn) {
        Objects.requireNonNull(txn);
        throw new NotImplementedException();
    }

    @Override
    /** {@inheritDoc} */
    public TransactionMetadata preHandleBurnToken(@NonNull final TransactionBody txn) {
        Objects.requireNonNull(txn);
        throw new NotImplementedException();
    }

    @Override
    /** {@inheritDoc} */
    public TransactionMetadata preHandleDeleteToken(@NonNull final TransactionBody txn) {
        Objects.requireNonNull(txn);
        throw new NotImplementedException();
    }

    @Override
    /** {@inheritDoc} */
    public TransactionMetadata preHandleWipeTokenAccount(@NonNull final TransactionBody txn) {
        Objects.requireNonNull(txn);
        final var op = txn.getTokenWipe();
        final var payer = txn.getTransactionID().getAccountID();
        final var meta =
                new SigTransactionMetadataBuilder(accountStore).payerKeyFor(payer).txnBody(txn);

        if (op.hasToken()) {
            final var tokenMeta = tokenStore.getTokenMeta(op.getToken());
            if (!tokenMeta.failed()) {
                if (tokenMeta.metadata().wipeKey().isPresent()) {
                    meta.addToReqKeys(tokenMeta.metadata().wipeKey().get());
                } else {
                    meta.status(ResponseCodeEnum.TOKEN_HAS_NO_WIPE_KEY);
                }
            } else {
                meta.status(tokenMeta.failureReason());
            }
        } else {
            meta.status(ResponseCodeEnum.INVALID_TOKEN_ID);
        }

        return meta.build();
    }

    @Override
    /** {@inheritDoc} */
    public TransactionMetadata preHandleFreezeTokenAccount(@NonNull final TransactionBody txn) {
        Objects.requireNonNull(txn);
        throw new NotImplementedException();
    }

    @Override
    /** {@inheritDoc} */
    public TransactionMetadata preHandleUnfreezeTokenAccount(@NonNull final TransactionBody txn) {
        Objects.requireNonNull(txn);
        throw new NotImplementedException();
    }

    @Override
    /** {@inheritDoc} */
    public TransactionMetadata preHandleGrantKycToTokenAccount(@NonNull final TransactionBody txn) {
        Objects.requireNonNull(txn);
        throw new NotImplementedException();
    }

    @Override
    /** {@inheritDoc} */
    public TransactionMetadata preHandleRevokeKycFromTokenAccount(@NonNull final TransactionBody txn) {
        Objects.requireNonNull(txn);
        throw new NotImplementedException();
    }

    @Override
    /** {@inheritDoc} */
    public TransactionMetadata preHandleAssociateTokens(@NonNull final TransactionBody txn) {
        Objects.requireNonNull(txn);
        throw new NotImplementedException();
    }

    @Override
    /** {@inheritDoc} */
    public TransactionMetadata preHandleDissociateTokens(@NonNull final TransactionBody txn) {
        Objects.requireNonNull(txn);
        throw new NotImplementedException();
    }

    @Override
    /** {@inheritDoc} */
    public TransactionMetadata preHandleUpdateTokenFeeSchedule(@NonNull final TransactionBody txn) {
        Objects.requireNonNull(txn);
        throw new NotImplementedException();
    }

    @Override
    /** {@inheritDoc} */
    public TransactionMetadata preHandlePauseToken(@NonNull final TransactionBody txn) {
        Objects.requireNonNull(txn);
        throw new NotImplementedException();
    }

    @Override
    /** {@inheritDoc} */
    public TransactionMetadata preHandleUnpauseToken(@NonNull final TransactionBody txn) {
        Objects.requireNonNull(txn);
        throw new NotImplementedException();
    }
}
