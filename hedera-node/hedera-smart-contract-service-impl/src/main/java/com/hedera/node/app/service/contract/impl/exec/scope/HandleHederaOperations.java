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

import static com.hedera.hapi.node.base.ResponseCodeEnum.OK;
import static com.hedera.hapi.node.base.ResponseCodeEnum.SUCCESS;
import static com.hedera.node.app.service.contract.impl.utils.SynthTxnUtils.synthAccountCreationFromHapi;
import static com.hedera.node.app.service.contract.impl.utils.SynthTxnUtils.synthContractCreationFromParent;
import static java.util.Objects.requireNonNull;

import com.hedera.hapi.node.base.AccountID;
import com.hedera.hapi.node.base.ContractID;
import com.hedera.hapi.node.contract.ContractCreateTransactionBody;
import com.hedera.hapi.node.contract.ContractNonceInfo;
import com.hedera.hapi.node.token.CryptoCreateTransactionBody;
import com.hedera.hapi.node.transaction.TransactionBody;
import com.hedera.node.app.service.contract.impl.annotations.TransactionScope;
import com.hedera.node.app.service.contract.impl.state.ContractStateStore;
import com.hedera.node.app.service.contract.impl.state.WritableContractStateStore;
import com.hedera.node.app.service.token.ReadableAccountStore;
import com.hedera.node.app.service.token.api.TokenServiceApi;
import com.hedera.node.app.service.token.records.CryptoCreateRecordBuilder;
import com.hedera.node.app.spi.workflows.HandleContext;
import com.hedera.node.config.data.LedgerConfig;
import com.hedera.pbj.runtime.io.buffer.Bytes;
import edu.umd.cs.findbugs.annotations.NonNull;
import edu.umd.cs.findbugs.annotations.Nullable;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;
import java.util.Optional;
import javax.inject.Inject;

/**
 * A fully mutable {@link HederaOperations} implementation based on a {@link HandleContext}.
 */
@TransactionScope
public class HandleHederaOperations implements HederaOperations {
    private static final Comparator<ContractID> CONTRACT_ID_NUM_COMPARATOR =
            Comparator.comparingLong(ContractID::contractNumOrThrow);
    private static final Comparator<ContractNonceInfo> NONCE_INFO_CONTRACT_ID_COMPARATOR =
            Comparator.comparing(ContractNonceInfo::contractIdOrThrow, CONTRACT_ID_NUM_COMPARATOR);
    public static final Bytes ZERO_ENTROPY = Bytes.fromHex(
            "000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000");

    private final LedgerConfig ledgerConfig;
    private final HandleContext context;

    @Inject
    public HandleHederaOperations(@NonNull final LedgerConfig ledgerConfig, @NonNull final HandleContext context) {
        this.ledgerConfig = requireNonNull(ledgerConfig);
        this.context = requireNonNull(context);
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public @NonNull HandleHederaOperations begin() {
        context.savepointStack().createSavepoint();
        return this;
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public void commit() {
        // Currently the savepoint stack only supports reverting savepoints; then commits all remaining at the end
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public void revert() {
        context.savepointStack().rollback();
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public ContractStateStore getStore() {
        return context.writableStore(WritableContractStateStore.class);
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public long peekNextEntityNumber() {
        return context.peekAtNewEntityNum();
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public long useNextEntityNumber() {
        return context.newEntityNum();
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public @NonNull Bytes entropy() {
        return Optional.ofNullable(context.blockRecordInfo().getNMinus3RunningHash())
                .orElse(ZERO_ENTROPY);
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public long lazyCreationCostInGas() {
        // TODO - implement correctly
        return 1L;
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public long gasPriceInTinybars() {
        // TODO - implement correctly
        return 1L;
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public long valueInTinybars(final long tinycents) {
        // TODO - implement correctly
        return tinycents;
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public void collectFee(@NonNull final AccountID payerId, final long amount) {
        requireNonNull(payerId);
        final var tokenServiceApi = context.serviceApi(TokenServiceApi.class);
        final var coinbaseId =
                AccountID.newBuilder().accountNum(ledgerConfig.fundingAccount()).build();
        tokenServiceApi.transferFromTo(payerId, coinbaseId, amount);
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public void refundFee(@NonNull final AccountID payerId, final long amount) {
        requireNonNull(payerId);
        final var tokenServiceApi = context.serviceApi(TokenServiceApi.class);
        final var coinbaseId =
                AccountID.newBuilder().accountNum(ledgerConfig.fundingAccount()).build();
        tokenServiceApi.transferFromTo(coinbaseId, payerId, amount);
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public void chargeStorageRent(final long contractNumber, final long amount, final boolean itemizeStoragePayments) {
        // TODO - implement before enabling contract expiry
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public void updateStorageMetadata(
            final long contractNumber, @NonNull final Bytes firstKey, final int netChangeInSlotsUsed) {
        final var tokenServiceApi = context.serviceApi(TokenServiceApi.class);
        final var accountId = AccountID.newBuilder().accountNum(contractNumber).build();
        tokenServiceApi.updateStorageMetadata(accountId, firstKey, netChangeInSlotsUsed);
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public void createContract(final long number, final long parentNumber, @Nullable final Bytes evmAddress) {
        final var accountStore = context.readableStore(ReadableAccountStore.class);
        final var parent = accountStore.getAccountById(
                AccountID.newBuilder().accountNum(parentNumber).build());
        final var impliedContractCreation = synthContractCreationFromParent(
                ContractID.newBuilder().contractNum(number).build(), requireNonNull(parent));
        dispatchAndMarkCreation(
                number,
                synthAccountCreationFromHapi(
                        ContractID.newBuilder().contractNum(number).build(), evmAddress, impliedContractCreation));
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public void createContract(
            final long number, @NonNull final ContractCreateTransactionBody body, @Nullable final Bytes evmAddress) {
        requireNonNull(body);
        dispatchAndMarkCreation(
                number,
                synthAccountCreationFromHapi(
                        ContractID.newBuilder().contractNum(number).build(), evmAddress, body));
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public void deleteAliasedContract(@NonNull final Bytes evmAddress) {
        requireNonNull(evmAddress);
        final var tokenServiceApi = context.serviceApi(TokenServiceApi.class);
        tokenServiceApi.deleteAndMaybeUnaliasContract(
                ContractID.newBuilder().evmAddress(evmAddress).build());
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public void deleteUnaliasedContract(final long number) {
        final var tokenServiceApi = context.serviceApi(TokenServiceApi.class);
        tokenServiceApi.deleteAndMaybeUnaliasContract(
                ContractID.newBuilder().contractNum(number).build());
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public List<Long> getModifiedAccountNumbers() {
        // TODO - remove this method, isn't needed
        return Collections.emptyList();
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public List<ContractID> createdContractIds() {
        final var tokenServiceApi = context.serviceApi(TokenServiceApi.class);
        // TODO - add a newContractIds() method to TokenServiceApi instead
        return tokenServiceApi.modifiedAccountIds().stream()
                .map(accountId -> ContractID.newBuilder()
                        .contractNum(accountId.accountNumOrThrow())
                        .build())
                .sorted(CONTRACT_ID_NUM_COMPARATOR)
                .toList();
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public List<ContractNonceInfo> updatedContractNonces() {
        final var tokenServiceApi = context.serviceApi(TokenServiceApi.class);
        final var updatedNonces = new ArrayList<>(tokenServiceApi.updatedContractNonces());
        updatedNonces.sort(NONCE_INFO_CONTRACT_ID_COMPARATOR);
        return updatedNonces;
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public int getOriginalSlotsUsed(final long contractNumber) {
        // TODO - extend API and use getOriginalValue() from writable store
        return 0;
    }

    private void dispatchAndMarkCreation(final long number, @NonNull final CryptoCreateTransactionBody body) {
        // Create the contract account by dispatching a synthetic HAPI transaction
        // TODO - implement proper signature VerificationAssistant
        final var recordBuilder = context.dispatchChildTransaction(
                TransactionBody.newBuilder().cryptoCreateAccount(body).build(),
                CryptoCreateRecordBuilder.class,
                key -> false);
        // TODO - switch OK to SUCCESS once some status-setting responsibilities are clarified
        if (recordBuilder.status() != OK && recordBuilder.status() != SUCCESS) {
            throw new AssertionError("Not implemented");
        }
        // Then use the TokenService API to mark the created account as a contract
        final var tokenServiceApi = context.serviceApi(TokenServiceApi.class);
        final var accountId = AccountID.newBuilder().accountNum(number).build();
        tokenServiceApi.markAsContract(accountId);
    }
}
