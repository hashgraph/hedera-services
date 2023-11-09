/*
 * Copyright (C) 2023 Hedera Hashgraph, LLC
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
import static com.hedera.hapi.node.base.ResponseCodeEnum.SUCCESS;
import static com.hedera.node.app.service.contract.impl.exec.processors.ProcessorModule.INITIAL_CONTRACT_NONCE;
import static com.hedera.node.app.service.contract.impl.utils.SynthTxnUtils.synthHollowAccountCreation;
import static java.util.Objects.requireNonNull;

import com.hedera.hapi.node.base.AccountID;
import com.hedera.hapi.node.base.ResponseCodeEnum;
import com.hedera.hapi.node.token.CryptoTransferTransactionBody;
import com.hedera.hapi.node.transaction.TransactionBody;
import com.hedera.node.app.service.contract.impl.annotations.TransactionScope;
import com.hedera.node.app.service.token.ReadableAccountStore;
import com.hedera.node.app.service.token.ReadableNftStore;
import com.hedera.node.app.service.token.ReadableTokenRelationStore;
import com.hedera.node.app.service.token.ReadableTokenStore;
import com.hedera.node.app.service.token.api.TokenServiceApi;
import com.hedera.node.app.service.token.records.CryptoCreateRecordBuilder;
import com.hedera.node.app.spi.workflows.HandleContext;
import com.hedera.pbj.runtime.io.buffer.Bytes;
import edu.umd.cs.findbugs.annotations.NonNull;
import javax.inject.Inject;

/**
 * A fully-mutable {@link HederaNativeOperations} implemented with a {@link HandleContext}.
 */
@TransactionScope
public class HandleHederaNativeOperations implements HederaNativeOperations {
    private final HandleContext context;

    @Inject
    public HandleHederaNativeOperations(@NonNull final HandleContext context) {
        this.context = requireNonNull(context);
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public @NonNull ReadableNftStore readableNftStore() {
        return context.readableStore(ReadableNftStore.class);
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public @NonNull ReadableTokenRelationStore readableTokenRelationStore() {
        return context.readableStore(ReadableTokenRelationStore.class);
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public @NonNull ReadableTokenStore readableTokenStore() {
        return context.readableStore(ReadableTokenStore.class);
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public @NonNull ReadableAccountStore readableAccountStore() {
        return context.readableStore(ReadableAccountStore.class);
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public void setNonce(final long contractNumber, final long nonce) {
        final var tokenServiceApi = context.serviceApi(TokenServiceApi.class);
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
        // There are no non-payer keys that will need to sign this transaction; therefore, activate no keys
        final var childRecordBuilder = context.dispatchChildTransaction(
                synthTxn, CryptoCreateRecordBuilder.class, key -> false, context.payer());
        // FUTURE - switch OK to SUCCESS once some status-setting responsibilities are clarified
        if (childRecordBuilder.status() != OK && childRecordBuilder.status() != SUCCESS) {
            throw new AssertionError("Not implemented");
        }
        return OK;
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public void finalizeHollowAccountAsContract(@NonNull final Bytes evmAddress) {
        requireNonNull(evmAddress);
        final var accountStore = context.readableStore(ReadableAccountStore.class);
        final var hollowAccountId = requireNonNull(accountStore.getAccountIDByAlias(evmAddress));
        final var tokenServiceApi = context.serviceApi(TokenServiceApi.class);
        tokenServiceApi.finalizeHollowAccountAsContract(hollowAccountId, INITIAL_CONTRACT_NONCE);
        // FUTURE: For temporary backward-compatibility with mono-service, consume an entity id
        context.newEntityNum();
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public @NonNull ResponseCodeEnum transferWithReceiverSigCheck(
            final long amount,
            final long fromEntityNumber,
            final long toEntityNumber,
            @NonNull final VerificationStrategy strategy) {
        final var to = requireNonNull(getAccount(toEntityNumber));
        final var signatureTest = strategy.asSignatureTestIn(context);
        if (to.receiverSigRequired() && !signatureTest.test(to.keyOrThrow())) {
            return INVALID_SIGNATURE;
        }
        final var tokenServiceApi = context.serviceApi(TokenServiceApi.class);
        tokenServiceApi.transferFromTo(
                AccountID.newBuilder().accountNum(fromEntityNumber).build(),
                AccountID.newBuilder().accountNum(toEntityNumber).build(),
                amount);
        return OK;
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public void trackDeletion(final long deletedNumber, final long beneficiaryNumber) {
        // TODO - implement after merging upstream
    }

    @Override
    public boolean checkForCustomFees(@NonNull final CryptoTransferTransactionBody op) {
        final var tokenServiceApi = context.serviceApi(TokenServiceApi.class);
        return tokenServiceApi.checkForCustomFees(op);
    }
}
