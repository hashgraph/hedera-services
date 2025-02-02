package com.hedera.node.app.service.contract.impl.handlers;

import com.hedera.hapi.node.transaction.TransactionBody;
import com.hedera.node.app.spi.workflows.HandleContext;
import com.hedera.node.app.spi.workflows.HandleException;
import com.hedera.node.app.spi.workflows.PreCheckException;
import com.hedera.node.app.spi.workflows.PreHandleContext;
import com.hedera.node.app.spi.workflows.TransactionHandler;
import edu.umd.cs.findbugs.annotations.NonNull;

import javax.inject.Singleton;

import static com.hedera.hapi.node.base.LambdaOwnerID.OwnerIdOneOfType.ACCOUNT_ID;
import static com.hedera.hapi.node.base.ResponseCodeEnum.INVALID_LAMBDA_ID;
import static com.hedera.hapi.node.base.ResponseCodeEnum.LAMBDA_STORAGE_KEY_TOO_LONG;
import static com.hedera.hapi.node.base.ResponseCodeEnum.LAMBDA_STORAGE_VALUE_TOO_LONG;
import static com.hedera.node.app.spi.workflows.PreCheckException.validateTruePreCheck;
import static java.util.Objects.requireNonNull;

@Singleton
public class LambdaSStoreHandler implements TransactionHandler {
    private static final long MAX_KV_LEN = 32L;

    @Override
    public void pureChecks(@NonNull final TransactionBody txn) throws PreCheckException {
        requireNonNull(txn);
        final var op = txn.lambdaSstoreOrThrow();
        validateTruePreCheck(op.hasLambdaId(), INVALID_LAMBDA_ID);
        final var lambdaId = op.lambdaIdOrThrow();
        validateTruePreCheck(lambdaId.hasOwnerId(), INVALID_LAMBDA_ID);
        final var ownerType = lambdaId.ownerIdOrThrow().ownerId().kind();
        validateTruePreCheck(ownerType == ACCOUNT_ID, INVALID_LAMBDA_ID);
        // Lambda indexes start at 1
        validateTruePreCheck(lambdaId.index() > 0, INVALID_LAMBDA_ID);
        for (final var slot : op.storageSlots()) {
            validateTruePreCheck(slot.key().length() <= MAX_KV_LEN, LAMBDA_STORAGE_KEY_TOO_LONG);
            validateTruePreCheck(slot.value().length() <= MAX_KV_LEN, LAMBDA_STORAGE_VALUE_TOO_LONG);
        }
    }

    @Override
    public void preHandle(@NonNull final PreHandleContext context) throws PreCheckException {
        requireNonNull(context);
        final var op = context.body().lambdaSstoreOrThrow();
        context.requireKeyOrThrow(op.lambdaIdOrThrow().ownerIdOrThrow().accountIdOrThrow(), INVALID_LAMBDA_ID);
    }

    @Override
    public void handle(@NonNull final HandleContext context) throws HandleException {
        requireNonNull(context);

    }
}
