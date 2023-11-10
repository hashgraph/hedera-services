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
import static com.hedera.node.app.service.contract.impl.utils.ConversionUtils.tuweniToPbjBytes;
import static com.hedera.node.app.service.contract.impl.utils.SynthTxnUtils.*;
import static java.util.Objects.requireNonNull;

import com.hedera.hapi.node.base.*;
import com.hedera.hapi.node.contract.ContractCreateTransactionBody;
import com.hedera.hapi.node.contract.ContractFunctionResult;
import com.hedera.hapi.node.contract.EthereumTransactionBody;
import com.hedera.hapi.node.token.CryptoCreateTransactionBody;
import com.hedera.hapi.node.transaction.TransactionBody;
import com.hedera.hapi.streams.*;
import com.hedera.node.app.service.contract.impl.annotations.TransactionScope;
import com.hedera.node.app.service.contract.impl.exec.EvmActionTracer;
import com.hedera.node.app.service.contract.impl.exec.gas.TinybarValues;
import com.hedera.node.app.service.contract.impl.exec.utils.ActionWrapper;
import com.hedera.node.app.service.contract.impl.hevm.ActionSidecarContentTracer;
import com.hedera.node.app.service.contract.impl.records.ContractCallRecordBuilder;
import com.hedera.node.app.service.contract.impl.records.ContractCreateRecordBuilder;
import com.hedera.node.app.service.contract.impl.state.ContractStateStore;
import com.hedera.node.app.service.contract.impl.state.WritableContractStateStore;
import com.hedera.node.app.service.token.ReadableAccountStore;
import com.hedera.node.app.service.token.api.ContractChangeSummary;
import com.hedera.node.app.service.token.api.TokenServiceApi;
import com.hedera.node.app.spi.workflows.HandleContext;
import com.hedera.node.config.data.ContractsConfig;
import com.hedera.node.config.data.LedgerConfig;
import com.hedera.pbj.runtime.io.buffer.Bytes;
import edu.umd.cs.findbugs.annotations.NonNull;
import edu.umd.cs.findbugs.annotations.Nullable;
import java.util.AbstractMap;
import java.util.Collections;
import java.util.List;
import java.util.Optional;
import javax.inject.Inject;
import org.hyperledger.besu.evm.account.MutableAccount;
import org.hyperledger.besu.evm.frame.MessageFrame;

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
            final long contractNumber, @Nullable final Bytes firstKey, final int netChangeInSlotsUsed) {
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

    /**
     * {@inheritDoc}
     */
    @Override
    public long getOriginalSlotsUsed(final long contractNumber) {
        final var tokenServiceApi = context.serviceApi(TokenServiceApi.class);
        return tokenServiceApi.originalKvUsageFor(
                AccountID.newBuilder().accountNum(contractNumber).build());
    }

    private void dispatchAndMarkCreation(
            final long number,
            @NonNull final CryptoCreateTransactionBody body,
            @Nullable final AccountID autoRenewAccountId,
            @Nullable final Bytes evmAddress) {
        // create should have conditional child record
        final var recordBuilder = context.dispatchRemovableChildTransaction(
                TransactionBody.newBuilder().cryptoCreateAccount(body).build(),
                ContractCreateRecordBuilder.class,
                key -> true,
                context.payer());

        final var contractId = ContractID.newBuilder().contractNum(number).build();
        //save a reference to a child record builder, in order to add bytecode sidecar after frame is executed
        context.registerCreationChildRecordBuilder(contractId.contractNum());
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

    public void externalizeHollowAccountMerge(
            @NonNull ContractID contractId, @Nullable Bytes evmAddress, @Nullable ContractBytecode bytecode) {
        var recordBuilder = context.addRemovableChildRecordBuilder(ContractCreateRecordBuilder.class);
        recordBuilder
                .contractID(contractId)
                .transaction(Transaction.DEFAULT)
                // todo add null check or make it nonNull
                .addContractBytecode(bytecode, false)
                .contractCreateResult(ContractFunctionResult.newBuilder()
                        .contractID(contractId)
                        .evmAddress(evmAddress)
                        .build());
        context.registerCreationChildRecordBuilder(contractId.contractNum());
    }

    public void addActionAndStateChangesSidecars(ActionSidecarContentTracer tracer, ContractStateChanges stateChanges) {
        var enabledSidecars =
                context.configuration().getConfigData(ContractsConfig.class).sidecars();
        if (enabledSidecars.contains(SidecarType.CONTRACT_ACTION)) {
            context.recordBuilder(ContractCallRecordBuilder.class)
                    .contractActions(List.of(new AbstractMap.SimpleEntry<>(
                            ContractActions.newBuilder()
                                    .contractActions(((EvmActionTracer) tracer)
                                            .actionStack().allActions().stream()
                                                    .map(ActionWrapper::get)
                                                    .toList())
                                    .build(),
                            false)));
        }

        if (enabledSidecars.contains(SidecarType.CONTRACT_STATE_CHANGE)) {
            if (stateChanges.contractStateChanges().size() > 0) {
                context.recordBuilder(ContractCallRecordBuilder.class).addContractStateChanges(stateChanges, false);
            }
        }
    }

    public void addBytecodeSidecar(MessageFrame frame, ContractID recipientId,
                                   MutableAccount recipientAccount) {
        var enabledSidecars =
                context.configuration().getConfigData(ContractsConfig.class).sidecars();

        if (enabledSidecars.contains(SidecarType.CONTRACT_BYTECODE)) {
            var body = context.body().data().value();
            //toplevel sidecars
            if(frame.getType().equals(MessageFrame.Type.CONTRACT_CREATION)) {
                var bytecodeBuilder = ContractBytecode.newBuilder();
                //add contract id and runtime if frame is not reverted
                if (recipientAccount != null && !frame.getState().equals(MessageFrame.State.REVERT)) {
                    bytecodeBuilder.contractId(recipientId);
                    bytecodeBuilder.runtimeBytecode(tuweniToPbjBytes(recipientAccount.getCode()));
                }

                var recordBuilder = context.recordBuilder(ContractCreateRecordBuilder.class);
                if(body instanceof ContractCreateTransactionBody) {
                    if(!((ContractCreateTransactionBody) body).hasInitcode()) {
                        bytecodeBuilder.initcode(tuweniToPbjBytes(frame.getCode().getBytes()));
                    }
                } else if (body instanceof EthereumTransactionBody) {
                    // try to find creation child record builder by contract number
                    var childRecordBuilder = (ContractCreateRecordBuilder) context.getCreationChildRecordBuilder(recipientId.contractNum());
                    recordBuilder = childRecordBuilder == null ? recordBuilder : childRecordBuilder;
                } else {
                    //contract call create
                    var childRecordBuilder = (ContractCreateRecordBuilder) context.getCreationChildRecordBuilder(recipientId.contractNum());
                    recordBuilder = childRecordBuilder == null ? recordBuilder : childRecordBuilder;
                    bytecodeBuilder.initcode(tuweniToPbjBytes(frame.getCode().getBytes()));
                }
                recordBuilder.addContractBytecode(bytecodeBuilder.build(), false);
            }
        }
    }
}
