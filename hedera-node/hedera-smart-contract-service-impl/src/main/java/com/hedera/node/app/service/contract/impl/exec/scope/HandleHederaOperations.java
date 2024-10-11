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

import static com.hedera.hapi.node.base.ResponseCodeEnum.SUCCESS;
import static com.hedera.node.app.service.contract.impl.ContractServiceImpl.LAZY_MEMO;
import static com.hedera.node.app.service.contract.impl.utils.ConversionUtils.FEE_SCHEDULE_UNITS_PER_TINYCENT;
import static com.hedera.node.app.service.contract.impl.utils.ConversionUtils.tuweniToPbjBytes;
import static com.hedera.node.app.service.contract.impl.utils.SynthTxnUtils.*;
import static com.hedera.node.app.spi.key.KeyUtils.IMMUTABILITY_SENTINEL_KEY;
import static com.hedera.node.app.spi.workflows.record.ExternalizedRecordCustomizer.SUPPRESSING_EXTERNALIZED_RECORD_CUSTOMIZER;
import static com.hedera.node.app.spi.workflows.record.StreamBuilder.transactionWith;
import static java.util.Objects.requireNonNull;

import com.hedera.hapi.node.base.*;
import com.hedera.hapi.node.contract.ContractCreateTransactionBody;
import com.hedera.hapi.node.contract.ContractFunctionResult;
import com.hedera.hapi.node.token.CryptoCreateTransactionBody;
import com.hedera.hapi.node.transaction.SignedTransaction;
import com.hedera.hapi.node.transaction.TransactionBody;
import com.hedera.node.app.service.contract.impl.annotations.TransactionScope;
import com.hedera.node.app.service.contract.impl.exec.gas.SystemContractGasCalculator;
import com.hedera.node.app.service.contract.impl.exec.gas.TinybarValues;
import com.hedera.node.app.service.contract.impl.exec.utils.PendingCreationMetadata;
import com.hedera.node.app.service.contract.impl.exec.utils.PendingCreationMetadataRef;
import com.hedera.node.app.service.contract.impl.records.ContractCreateStreamBuilder;
import com.hedera.node.app.service.contract.impl.records.ContractOperationStreamBuilder;
import com.hedera.node.app.service.contract.impl.state.ContractStateStore;
import com.hedera.node.app.service.contract.impl.state.WritableContractStateStore;
import com.hedera.node.app.service.token.ReadableAccountStore;
import com.hedera.node.app.service.token.api.ContractChangeSummary;
import com.hedera.node.app.service.token.api.TokenServiceApi;
import com.hedera.node.app.spi.workflows.HandleContext;
import com.hedera.node.app.spi.workflows.HandleException;
import com.hedera.node.app.spi.workflows.ResourceExhaustedException;
import com.hedera.node.app.spi.workflows.record.ExternalizedRecordCustomizer;
import com.hedera.node.config.data.AccountsConfig;
import com.hedera.node.config.data.ContractsConfig;
import com.hedera.node.config.data.HederaConfig;
import com.hedera.node.config.data.LedgerConfig;
import com.hedera.pbj.runtime.ParseException;
import com.hedera.pbj.runtime.UncheckedParseException;
import com.hedera.pbj.runtime.io.buffer.Bytes;
import edu.umd.cs.findbugs.annotations.NonNull;
import edu.umd.cs.findbugs.annotations.Nullable;
import java.util.Collections;
import java.util.List;
import javax.inject.Inject;
import org.hyperledger.besu.datatypes.Address;

/**
 * A fully mutable {@link HederaOperations} implementation based on a {@link HandleContext}.
 */
@TransactionScope
public class HandleHederaOperations implements HederaOperations {
    public static final Bytes ZERO_ENTROPY = Bytes.fromHex(
            "000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000");
    private static final CryptoCreateTransactionBody.Builder CREATE_TXN_BODY_BUILDER =
            CryptoCreateTransactionBody.newBuilder()
                    .initialBalance(0)
                    .maxAutomaticTokenAssociations(0)
                    .autoRenewPeriod(Duration.newBuilder().seconds(THREE_MONTHS_IN_SECONDS))
                    .key(IMMUTABILITY_SENTINEL_KEY)
                    .memo(LAZY_MEMO);

    private final TinybarValues tinybarValues;
    private final LedgerConfig ledgerConfig;
    private final ContractsConfig contractsConfig;
    private final SystemContractGasCalculator gasCalculator;
    private final HederaConfig hederaConfig;
    private final HandleContext context;
    private final HederaFunctionality functionality;
    private final PendingCreationMetadataRef pendingCreationMetadataRef;
    private final AccountsConfig accountsConfig;

    @Inject
    public HandleHederaOperations(
            @NonNull final LedgerConfig ledgerConfig,
            @NonNull final ContractsConfig contractsConfig,
            @NonNull final HandleContext context,
            @NonNull final TinybarValues tinybarValues,
            @NonNull final SystemContractGasCalculator gasCalculator,
            @NonNull final HederaConfig hederaConfig,
            @NonNull final HederaFunctionality functionality,
            @NonNull final PendingCreationMetadataRef pendingCreationMetadataRef,
            @NonNull final AccountsConfig accountsConfig) {
        this.ledgerConfig = requireNonNull(ledgerConfig);
        this.contractsConfig = requireNonNull(contractsConfig);
        this.context = requireNonNull(context);
        this.tinybarValues = requireNonNull(tinybarValues);
        this.gasCalculator = requireNonNull(gasCalculator);
        this.hederaConfig = requireNonNull(hederaConfig);
        this.functionality = requireNonNull(functionality);
        this.pendingCreationMetadataRef = requireNonNull(pendingCreationMetadataRef);
        this.accountsConfig = requireNonNull(accountsConfig);
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
    public ContractStateStore getStore() {
        return context.storeFactory().writableStore(WritableContractStateStore.class);
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public long peekNextEntityNumber() {
        return context.entityNumGenerator().peekAtNewEntityNum();
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public long useNextEntityNumber() {
        return context.entityNumGenerator().newEntityNum();
    }

    @Override
    public long contractCreationLimit() {
        return contractsConfig.maxNumber();
    }

    @Override
    public long accountCreationLimit() {
        return accountsConfig.maxNumber();
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public @NonNull Bytes entropy() {
        final var entropy = context.blockRecordInfo().prngSeed();
        return (entropy == null || entropy.equals(Bytes.EMPTY)) ? ZERO_ENTROPY : entropy;
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public long lazyCreationCostInGas(@NonNull final Address recipient) {
        final var payerId = context.payer();
        // Calculate gas for a CryptoCreateTransactionBody with an alias address
        final var synthCreation = TransactionBody.newBuilder()
                .cryptoCreateAccount(CREATE_TXN_BODY_BUILDER.alias(tuweniToPbjBytes(recipient)))
                .build();
        final var createFee = gasCalculator.feeCalculatorPriceInTinyBars(synthCreation, payerId);

        // isGasPrecisionLossFixEnabled is a temporary feature flag that will be removed in the future.
        if (!contractsConfig.isGasPrecisionLossFixEnabled()) {
            return (createFee) / gasCalculator.topLevelGasPrice();
        }
        return (createFee) * FEE_SCHEDULE_UNITS_PER_TINYCENT / gasCalculator.topLevelGasPriceInTinyBars();
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
        final var tokenServiceApi = context.storeFactory().serviceApi(TokenServiceApi.class);
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
        final var tokenServiceApi = context.storeFactory().serviceApi(TokenServiceApi.class);
        final var coinbaseId =
                AccountID.newBuilder().accountNum(ledgerConfig.fundingAccount()).build();
        tokenServiceApi.transferFromTo(coinbaseId, payerId, amount);
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public void chargeStorageRent(
            final ContractID contractID, final long amount, final boolean itemizeStoragePayments) {
        // (FUTURE) Needed before enabling contract expiry
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public void updateStorageMetadata(
            final ContractID contractID, @NonNull final Bytes firstKey, final int netChangeInSlotsUsed) {
        requireNonNull(firstKey);
        final var tokenServiceApi = context.storeFactory().serviceApi(TokenServiceApi.class);
        tokenServiceApi.updateStorageMetadata(contractID, firstKey, netChangeInSlotsUsed);
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public void createContract(final long number, final long parentNumber, @Nullable final Bytes evmAddress) {
        final var accountStore = context.storeFactory().readableStore(ReadableAccountStore.class);
        final var parent = accountStore.getAccountById(
                AccountID.newBuilder().accountNum(parentNumber).build());
        final var impliedContractCreation = synthContractCreationFromParent(
                ContractID.newBuilder().contractNum(number).build(), requireNonNull(parent));
        try {
            dispatchAndMarkCreation(
                    number,
                    synthAccountCreationFromHapi(
                            ContractID.newBuilder().contractNum(number).build(), evmAddress, impliedContractCreation),
                    impliedContractCreation,
                    parent.autoRenewAccountId(),
                    evmAddress,
                    ExternalizeInitcodeOnSuccess.YES);
        } catch (final HandleException e) {
            throw new ResourceExhaustedException(e.getStatus());
        }
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public void createContract(
            final long number, @NonNull final ContractCreateTransactionBody body, @Nullable final Bytes evmAddress) {
        requireNonNull(body);
        // Note that a EthereumTransaction with a top-level creation still needs to externalize its
        // implied ContractCreateTransactionBody (unlike ContractCreate, which evidently already does so)
        dispatchAndMarkCreation(
                number,
                synthAccountCreationFromHapi(
                        ContractID.newBuilder().contractNum(number).build(), evmAddress, body),
                functionality == HederaFunctionality.ETHEREUM_TRANSACTION ? body : null,
                body.autoRenewAccountId(),
                evmAddress,
                body.hasInitcode() ? ExternalizeInitcodeOnSuccess.NO : ExternalizeInitcodeOnSuccess.YES);
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public void deleteAliasedContract(@NonNull final Bytes evmAddress) {
        requireNonNull(evmAddress);
        final var tokenServiceApi = context.storeFactory().serviceApi(TokenServiceApi.class);
        tokenServiceApi.deleteContract(
                ContractID.newBuilder().evmAddress(evmAddress).build());
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public void deleteUnaliasedContract(final long number) {
        final var tokenServiceApi = context.storeFactory().serviceApi(TokenServiceApi.class);
        tokenServiceApi.deleteContract(
                ContractID.newBuilder().contractNum(number).build());
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public List<Long> getModifiedAccountNumbers() {
        return Collections.emptyList();
    }

    @Override
    public ContractChangeSummary summarizeContractChanges() {
        final var tokenServiceApi = context.storeFactory().serviceApi(TokenServiceApi.class);
        return tokenServiceApi.summarizeContractChanges();
    }

    @Override
    public long getOriginalSlotsUsed(final ContractID contractID) {
        final var tokenServiceApi = context.storeFactory().serviceApi(TokenServiceApi.class);
        return tokenServiceApi.originalKvUsageFor(contractID);
    }

    @Override
    public void externalizeHollowAccountMerge(@NonNull ContractID contractId, @Nullable Bytes evmAddress) {
        final var recordBuilder = context.savepointStack()
                .addRemovableChildRecordBuilder(ContractCreateStreamBuilder.class)
                .contractID(contractId)
                .status(SUCCESS)
                .transaction(transactionWith(TransactionBody.newBuilder()
                        .contractCreateInstance(synthContractCreationForExternalization(contractId))
                        .build()))
                .contractCreateResult(ContractFunctionResult.newBuilder()
                        .contractID(contractId)
                        .evmAddress(evmAddress)
                        .build());
        final var pendingCreationMetadata = new PendingCreationMetadata(recordBuilder, true);
        pendingCreationMetadataRef.set(contractId, pendingCreationMetadata);
    }

    @Override
    public ContractID shardAndRealmValidated(@NonNull final ContractID contractId) {
        return configValidated(contractId, hederaConfig);
    }

    private enum ExternalizeInitcodeOnSuccess {
        YES,
        NO
    }

    private void dispatchAndMarkCreation(
            final long number,
            @NonNull final CryptoCreateTransactionBody bodyToDispatch,
            @Nullable final ContractCreateTransactionBody bodyToExternalize,
            @Nullable final AccountID autoRenewAccountId,
            @Nullable final Bytes evmAddress,
            @NonNull final ExternalizeInitcodeOnSuccess externalizeInitcodeOnSuccess) {
        // Create should have conditional child record, but we only externalize this child if it's not already
        // externalized by the top-level HAPI transaction; and we "finish" the synthetic transaction by swapping
        // in the contract creation body for the dispatched crypto create body. This child transaction will not
        // be throttled at consensus.
        final var isTopLevelCreation = bodyToExternalize == null;
        final var recordBuilder = context.dispatchRemovableChildTransaction(
                TransactionBody.newBuilder().cryptoCreateAccount(bodyToDispatch).build(),
                ContractCreateStreamBuilder.class,
                null,
                context.payer(),
                isTopLevelCreation
                        ? SUPPRESSING_EXTERNALIZED_RECORD_CUSTOMIZER
                        : contractBodyCustomizerFor(number, bodyToExternalize),
                HandleContext.ConsensusThrottling.OFF);
        if (recordBuilder.status() != SUCCESS) {
            // The only plausible failure mode (MAX_ENTITIES_IN_PRICE_REGIME_HAVE_BEEN_CREATED) should
            // have been pre-validated in ProxyWorldUpdater.createAccount() so this is an invariant failure
            throw new IllegalStateException("Unexpected failure creating new contract - " + recordBuilder.status());
        }
        // If this creation runs to a successful completion, its ContractBytecode sidecar
        // goes in the top-level record or the just-created child record depending on whether
        // we are doing this on behalf of a HAPI ContractCreate call; we only include the
        // initcode in the bytecode sidecar if it's not already externalized via a body
        final var pendingCreationMetadata = new PendingCreationMetadata(
                isTopLevelCreation
                        ? context.savepointStack().getBaseBuilder(ContractOperationStreamBuilder.class)
                        : recordBuilder,
                externalizeInitcodeOnSuccess == ExternalizeInitcodeOnSuccess.YES);
        final var contractId = ContractID.newBuilder().contractNum(number).build();
        pendingCreationMetadataRef.set(contractId, pendingCreationMetadata);
        recordBuilder
                .contractID(contractId)
                .contractCreateResult(ContractFunctionResult.newBuilder()
                        .contractID(contractId)
                        .evmAddress(evmAddress)
                        .build());
        // Mark the created account as a contract with the given auto-renew account id
        final var tokenServiceApi = context.storeFactory().serviceApi(TokenServiceApi.class);
        final var accountId = AccountID.newBuilder().accountNum(number).build();
        tokenServiceApi.markAsContract(accountId, autoRenewAccountId);
    }

    private ExternalizedRecordCustomizer contractBodyCustomizerFor(
            final long createdNumber, @NonNull final ContractCreateTransactionBody op) {
        return transaction -> {
            try {
                final var dispatchedTransaction = SignedTransaction.PROTOBUF.parseStrict(
                        transaction.signedTransactionBytes().toReadableSequentialData());
                final var dispatchedBody = TransactionBody.PROTOBUF.parseStrict(
                        dispatchedTransaction.bodyBytes().toReadableSequentialData());
                if (!dispatchedBody.hasCryptoCreateAccount()) {
                    throw new IllegalArgumentException(
                            "Dispatched transaction body was not a crypto create" + dispatchedBody);
                }
                final var standardizedOp = standardized(createdNumber, op);
                return transactionWith(dispatchedBody
                        .copyBuilder()
                        .contractCreateInstance(standardizedOp)
                        .build());
            } catch (ParseException e) {
                // Should be impossible
                throw new UncheckedParseException(e);
            }
        };
    }

    private ContractCreateTransactionBody standardized(
            final long createdNumber, @NonNull final ContractCreateTransactionBody op) {
        var standardAdminKey = op.adminKey();
        if (op.hasAdminKey()) {
            final var adminNum =
                    op.adminKeyOrThrow().contractIDOrElse(ContractID.DEFAULT).contractNumOrElse(0L);
            // For mono-service fidelity, don't set an explicit admin key for a self-managed contract
            if (createdNumber == adminNum) {
                standardAdminKey = null;
            }
        }
        if (needsStandardization(op, standardAdminKey)) {
            // Initial balance, gas, and initcode are only set on top-level HAPI transactions
            return new ContractCreateTransactionBody(
                    com.hedera.hapi.node.contract.codec.ContractCreateTransactionBodyProtoCodec.INITCODE_SOURCE_UNSET,
                    standardAdminKey,
                    0L,
                    0L,
                    op.proxyAccountID(),
                    op.autoRenewPeriod(),
                    op.constructorParameters(),
                    op.shardID(),
                    op.realmID(),
                    op.newRealmAdminKey(),
                    op.memo(),
                    op.maxAutomaticTokenAssociations(),
                    op.autoRenewAccountId(),
                    op.stakedId(),
                    op.declineReward());
        } else {
            return op;
        }
    }

    private boolean needsStandardization(
            @NonNull final ContractCreateTransactionBody op, @Nullable final Key standardAdminKey) {
        return op.hasInitcode() || op.gas() > 0L || op.initialBalance() > 0L || standardAdminKey != op.adminKey();
    }
}
