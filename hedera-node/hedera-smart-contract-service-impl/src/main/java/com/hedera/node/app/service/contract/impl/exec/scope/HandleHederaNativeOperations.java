/*
 * Copyright (C) 2023-2024 Hedera Hashgraph, LLC
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.hedera.node.app.service.contract.impl.exec.scope;

import static com.hedera.hapi.node.base.ResponseCodeEnum.INVALID_SIGNATURE;
import static com.hedera.hapi.node.base.ResponseCodeEnum.OK;
import static com.hedera.node.app.service.contract.impl.exec.utils.FrameUtils.selfDestructBeneficiariesFor;
import static com.hedera.node.app.service.contract.impl.utils.SynthTxnUtils.synthHollowAccountCreation;
import static java.util.Objects.requireNonNull;

import com.hedera.hapi.node.base.AccountID;
import com.hedera.hapi.node.base.Key;
import com.hedera.hapi.node.base.ResponseCodeEnum;
import com.hedera.hapi.node.token.CryptoTransferTransactionBody;
import com.hedera.hapi.node.transaction.TransactionBody;
import com.hedera.node.app.service.contract.impl.annotations.TransactionScope;
import com.hedera.node.app.service.token.ReadableAccountStore;
import com.hedera.node.app.service.token.ReadableNftStore;
import com.hedera.node.app.service.token.ReadableTokenRelationStore;
import com.hedera.node.app.service.token.ReadableTokenStore;
import com.hedera.node.app.service.token.api.TokenServiceApi;
import com.hedera.node.app.service.token.records.CryptoCreateStreamBuilder;
import com.hedera.node.app.spi.workflows.HandleContext;
import com.hedera.node.app.spi.workflows.HandleException;
import com.hedera.pbj.runtime.io.buffer.Bytes;
import edu.umd.cs.findbugs.annotations.NonNull;
import edu.umd.cs.findbugs.annotations.Nullable;
import javax.inject.Inject;
import org.hyperledger.besu.evm.frame.MessageFrame;

/**
 * A fully-mutable {@link HederaNativeOperations} implemented with a {@link HandleContext}.
 */
@TransactionScope
public class HandleHederaNativeOperations implements HederaNativeOperations {
    private final HandleContext context;

    @Nullable
    private final Key maybeEthSenderKey;

    @Inject
    public HandleHederaNativeOperations(@NonNull final HandleContext context, @Nullable final Key maybeEthSenderKey) {
        this.context = requireNonNull(context);
        this.maybeEthSenderKey = maybeEthSenderKey;
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public @NonNull ReadableNftStore readableNftStore() {
        return context.storeFactory().readableStore(ReadableNftStore.class);
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public @NonNull ReadableTokenRelationStore readableTokenRelationStore() {
        return context.storeFactory().readableStore(ReadableTokenRelationStore.class);
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public @NonNull ReadableTokenStore readableTokenStore() {
        return context.storeFactory().readableStore(ReadableTokenStore.class);
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public @NonNull ReadableAccountStore readableAccountStore() {
        return context.storeFactory().readableStore(ReadableAccountStore.class);
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public void setNonce(final long contractNumber, final long nonce) {
        final var tokenServiceApi = context.storeFactory().serviceApi(TokenServiceApi.class);
        tokenServiceApi.setNonce(
                AccountID.newBuilder().accountNum(contractNumber).build(), nonce);
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public @NonNull ResponseCodeEnum createHollowAccount(@NonNull final Bytes evmAddress) {
        final var synthTxn = TransactionBody.newBuilder()
                .cryptoCreateAccount(synthHollowAccountCreation(evmAddress))
                .build();

        // Note the use of the null "verification assistant" callback; we don't want any
        // signing requirements enforced for this synthetic transaction
        try {
            final var childRecordBuilder = context.dispatchRemovablePrecedingTransaction(
                    synthTxn,
                    CryptoCreateStreamBuilder.class,
                    null,
                    context.payer(),
                    HandleContext.ConsensusThrottling.ON);
            return childRecordBuilder.status();
        } catch (final HandleException e) {
            // It is critically important we don't let HandleExceptions propagate to the workflow because
            // it doesn't rollback for contract operations so we can commit gas charges; that is, the
            // EVM transaction should always either run to completion or (if it must) throw an internal
            // failure like an IllegalArgumentException---but not a HandleException!
            return e.getStatus();
        }
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public void finalizeHollowAccountAsContract(@NonNull final Bytes evmAddress) {
        requireNonNull(evmAddress);
        final var accountStore = context.storeFactory().readableStore(ReadableAccountStore.class);
        final var hollowAccountId = requireNonNull(accountStore.getAccountIDByAlias(evmAddress));
        final var tokenServiceApi = context.storeFactory().serviceApi(TokenServiceApi.class);
        tokenServiceApi.finalizeHollowAccountAsContract(hollowAccountId);
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public @NonNull ResponseCodeEnum transferWithReceiverSigCheck(
            final long amount,
            final AccountID fromEntityId,
            final AccountID toEntityId,
            @NonNull final VerificationStrategy strategy) {
        final var to = requireNonNull(getAccount(toEntityId));
        final var signatureTest = strategy.asSignatureTestIn(context, maybeEthSenderKey);
        if (to.receiverSigRequired() && !signatureTest.test(to.keyOrThrow())) {
            return INVALID_SIGNATURE;
        }
        final var tokenServiceApi = context.storeFactory().serviceApi(TokenServiceApi.class);
        tokenServiceApi.transferFromTo(fromEntityId, toEntityId, amount);
        return OK;
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public void trackSelfDestructBeneficiary(
            final AccountID deletedId, final AccountID beneficiaryId, @NonNull final MessageFrame frame) {
        requireNonNull(frame);
        selfDestructBeneficiariesFor(frame).addBeneficiaryForDeletedAccount(deletedId, beneficiaryId);
    }

    @Override
    public boolean checkForCustomFees(@NonNull final CryptoTransferTransactionBody op) {
        final var tokenServiceApi = context.storeFactory().serviceApi(TokenServiceApi.class);
        return tokenServiceApi.checkForCustomFees(op);
    }
}
