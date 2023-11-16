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
import static com.hedera.node.app.service.contract.impl.utils.SynthTxnUtils.*;
import static com.hedera.node.app.spi.workflows.record.ExternalizedRecordCustomizer.SUPPRESSING_EXTERNALIZED_RECORD_CUSTOMIZER;
import static java.util.Objects.requireNonNull;

import com.hedera.hapi.node.base.*;
import com.hedera.hapi.node.contract.ContractCreateTransactionBody;
import com.hedera.hapi.node.contract.ContractFunctionResult;
import com.hedera.hapi.node.token.CryptoCreateTransactionBody;
import com.hedera.hapi.node.transaction.SignedTransaction;
import com.hedera.hapi.node.transaction.TransactionBody;
import com.hedera.node.app.service.contract.impl.annotations.TransactionScope;
import com.hedera.node.app.service.contract.impl.exec.gas.TinybarValues;
import com.hedera.node.app.service.contract.impl.records.ContractCreateRecordBuilder;
import com.hedera.node.app.service.contract.impl.state.ContractStateStore;
import com.hedera.node.app.service.contract.impl.state.WritableContractStateStore;
import com.hedera.node.app.service.token.ReadableAccountStore;
import com.hedera.node.app.service.token.api.ContractChangeSummary;
import com.hedera.node.app.service.token.api.TokenServiceApi;
import com.hedera.node.app.spi.workflows.HandleContext;
import com.hedera.node.app.spi.workflows.record.ExternalizedRecordCustomizer;
import com.hedera.node.config.data.ContractsConfig;
import com.hedera.node.config.data.LedgerConfig;
import com.hedera.pbj.runtime.io.buffer.Bytes;
import edu.umd.cs.findbugs.annotations.NonNull;
import edu.umd.cs.findbugs.annotations.Nullable;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.util.Collections;
import java.util.List;
import java.util.Optional;
import javax.inject.Inject;

/**
 * A fully mutable {@link HederaOperations} implementation based on a {@link HandleContext}.
 */
@TransactionScope
public class HandleHederaOperations implements HederaOperations {
    public static final Bytes ZERO_ENTROPY = Bytes.fromHex(
            "000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000");

    private final TinybarValues tinybarValues;
    private final LedgerConfig ledgerConfig;
    private final ContractsConfig contractsConfig;
    private final HandleContext context;

    @Inject
    public HandleHederaOperations(
            @NonNull final LedgerConfig ledgerConfig,
            @NonNull final ContractsConfig contractsConfig,
            @NonNull final HandleContext context,
            @NonNull final TinybarValues tinybarValues) {
        this.ledgerConfig = requireNonNull(ledgerConfig);
        this.contractsConfig = requireNonNull(contractsConfig);
        this.context = requireNonNull(context);
        this.tinybarValues = requireNonNull(tinybarValues);
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
        context.savepointStack().commit();
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
    public void revertChildRecords() {
        context.revertChildRecords();
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

    @Override
    public long contractCreationLimit() {
        return contractsConfig.maxNumber();
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
        return tinybarValues.topLevelTinybarGasPrice();
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public long valueInTinybars(final long tinycents) {
        return tinybarValues.asTinybars(tinycents);
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
        requireNonNull(firstKey);
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
                        ContractID.newBuilder().contractNum(number).build(), evmAddress, impliedContractCreation),
                impliedContractCreation,
                parent.autoRenewAccountId(),
                evmAddress);
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
                        ContractID.newBuilder().contractNum(number).build(), evmAddress, body),
                null,
                body.autoRenewAccountId(),
                evmAddress);
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public void deleteAliasedContract(@NonNull final Bytes evmAddress) {
        requireNonNull(evmAddress);
        final var tokenServiceApi = context.serviceApi(TokenServiceApi.class);
        tokenServiceApi.deleteContract(
                ContractID.newBuilder().evmAddress(evmAddress).build());
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public void deleteUnaliasedContract(final long number) {
        final var tokenServiceApi = context.serviceApi(TokenServiceApi.class);
        tokenServiceApi.deleteContract(
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

    @Override
    public ContractChangeSummary summarizeContractChanges() {
        final var tokenServiceApi = context.serviceApi(TokenServiceApi.class);
        return tokenServiceApi.summarizeContractChanges();
    }

    @Override
    public long getOriginalSlotsUsed(final long contractNumber) {
        final var tokenServiceApi = context.serviceApi(TokenServiceApi.class);
        return tokenServiceApi.originalKvUsageFor(
                AccountID.newBuilder().accountNum(contractNumber).build());
    }

    @Override
    public void externalizeHollowAccountMerge(@NonNull ContractID contractId, @Nullable Bytes evmAddress) {
        var recordBuilder = context.addRemovableChildRecordBuilder(ContractCreateRecordBuilder.class);
        recordBuilder
                .contractID(contractId)
                // add dummy transaction, because SingleTransactionRecord require NonNull on build
                .transaction(Transaction.newBuilder()
                        .signedTransactionBytes(Bytes.EMPTY)
                        .build())
                .contractCreateResult(ContractFunctionResult.newBuilder()
                        .contractID(contractId)
                        .evmAddress(evmAddress)
                        .build());
    }

    private void dispatchAndMarkCreation(
            final long number,
            @NonNull final CryptoCreateTransactionBody bodyToDispatch,
            @Nullable final ContractCreateTransactionBody bodyToExternalize,
            @Nullable final AccountID autoRenewAccountId,
            @Nullable final Bytes evmAddress) {
        // Create should have conditional child record, but we only externalize this child if it's not already
        // externalized by the top-level HAPI transaction; and we "finish" the synthetic transaction by swapping
        // in the contract creation body for the dispatched crypto create body
        final var recordBuilder = context.dispatchRemovableChildTransaction(
                TransactionBody.newBuilder().cryptoCreateAccount(bodyToDispatch).build(),
                ContractCreateRecordBuilder.class,
                null,
                context.payer(),
                (bodyToExternalize == null)
                        ? SUPPRESSING_EXTERNALIZED_RECORD_CUSTOMIZER
                        : contractBodyCustomizerFor(bodyToExternalize));

        final var contractId = ContractID.newBuilder().contractNum(number).build();
        // add additional create record fields
        recordBuilder
                .contractID(contractId)
                .contractCreateResult(ContractFunctionResult.newBuilder()
                        .contractID(contractId)
                        .evmAddress(evmAddress)
                        .build());
        // TODO - switch OK to SUCCESS once some status-setting responsibilities are clarified
        if (recordBuilder.status() != OK && recordBuilder.status() != SUCCESS) {
            throw new AssertionError("Not implemented");
        }
        // Then use the TokenService API to mark the created account as a contract
        final var tokenServiceApi = context.serviceApi(TokenServiceApi.class);
        final var accountId = AccountID.newBuilder().accountNum(number).build();

        tokenServiceApi.markAsContract(accountId, autoRenewAccountId);
    }

    private ExternalizedRecordCustomizer contractBodyCustomizerFor(@NonNull final ContractCreateTransactionBody op) {
        return transaction -> {
            try {
                final var signedTransaction = SignedTransaction.PROTOBUF.parseStrict(
                        transaction.signedTransactionBytes().toReadableSequentialData());
                final var body = TransactionBody.PROTOBUF.parseStrict(
                        signedTransaction.bodyBytes().toReadableSequentialData());
                if (!body.hasCryptoCreateAccount()) {
                    throw new IllegalArgumentException("Dispatched transaction body was not a crypto create");
                }
                final var finishedBody =
                        body.copyBuilder().contractCreateInstance(op).build();
                final var finishedSignedTransaction = signedTransaction
                        .copyBuilder()
                        .bodyBytes(TransactionBody.PROTOBUF.toBytes(finishedBody))
                        .build();
                return transaction
                        .copyBuilder()
                        .signedTransactionBytes(SignedTransaction.PROTOBUF.toBytes(finishedSignedTransaction))
                        .build();
            } catch (IOException internal) {
                // This should never happen
                throw new UncheckedIOException(internal);
            }
        };
    }
}
