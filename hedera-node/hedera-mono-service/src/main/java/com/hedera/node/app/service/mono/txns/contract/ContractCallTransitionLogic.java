/*
 * Copyright (C) 2020-2022 Hedera Hashgraph, LLC
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
package com.hedera.node.app.service.mono.txns.contract;

import static com.hedera.node.app.service.mono.contracts.ContractsV_0_30Module.EVM_VERSION_0_30;
import static com.hedera.node.app.service.evm.utils.ValidationUtils.validateTrue;
import static com.hedera.node.app.service.mono.utils.EntityIdUtils.EVM_ADDRESS_SIZE;
import static com.hederahashgraph.api.proto.java.ResponseCodeEnum.CONTRACT_NEGATIVE_GAS;
import static com.hederahashgraph.api.proto.java.ResponseCodeEnum.CONTRACT_NEGATIVE_VALUE;
import static com.hederahashgraph.api.proto.java.ResponseCodeEnum.INVALID_ACCOUNT_ID;
import static com.hederahashgraph.api.proto.java.ResponseCodeEnum.INVALID_CONTRACT_ID;
import static com.hederahashgraph.api.proto.java.ResponseCodeEnum.MAX_GAS_LIMIT_EXCEEDED;
import static com.hederahashgraph.api.proto.java.ResponseCodeEnum.OK;

import com.hedera.node.app.service.mono.context.TransactionContext;
import com.hedera.node.app.service.mono.context.properties.GlobalDynamicProperties;
import com.hedera.node.app.service.mono.contracts.execution.CallEvmTxProcessor;
import com.hedera.node.app.service.mono.contracts.execution.TransactionProcessingResult;
import com.hedera.node.app.service.mono.ledger.SigImpactHistorian;
import com.hedera.node.app.service.mono.ledger.accounts.AliasManager;
import com.hedera.node.app.service.mono.records.TransactionRecordService;
import com.hedera.node.app.service.mono.store.AccountStore;
import com.hedera.node.app.service.mono.store.contracts.CodeCache;
import com.hedera.node.app.service.mono.store.contracts.EntityAccess;
import com.hedera.node.app.service.mono.store.contracts.HederaMutableWorldState;
import com.hedera.node.app.service.mono.store.contracts.HederaWorldState;
import com.hedera.node.app.service.mono.store.models.Account;
import com.hedera.node.app.service.mono.store.models.Id;
import com.hedera.node.app.service.mono.txns.PreFetchableTransition;
import com.hedera.node.app.service.mono.utils.EntityIdUtils;
import com.hedera.node.app.service.mono.utils.EntityNum;
import com.hedera.node.app.service.mono.utils.accessors.TxnAccessor;
import com.hederahashgraph.api.proto.java.ContractCallTransactionBody;
import com.hederahashgraph.api.proto.java.ResponseCodeEnum;
import com.hederahashgraph.api.proto.java.TransactionBody;
import java.math.BigInteger;
import java.util.function.Function;
import java.util.function.Predicate;
import javax.inject.Inject;
import javax.inject.Singleton;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.apache.tuweni.bytes.Bytes;

@Singleton
public class ContractCallTransitionLogic implements PreFetchableTransition {
    private static final Logger log = LogManager.getLogger(ContractCallTransitionLogic.class);

    private final AccountStore accountStore;
    private final TransactionContext txnCtx;
    private final HederaMutableWorldState worldState;
    private final TransactionRecordService recordService;
    private final CallEvmTxProcessor evmTxProcessor;
    private final GlobalDynamicProperties properties;
    private final CodeCache codeCache;
    private final AliasManager aliasManager;
    private final SigImpactHistorian sigImpactHistorian;
    private final EntityAccess entityAccess;

    @Inject
    public ContractCallTransitionLogic(
            final TransactionContext txnCtx,
            final AccountStore accountStore,
            final HederaWorldState worldState,
            final TransactionRecordService recordService,
            final CallEvmTxProcessor evmTxProcessor,
            final GlobalDynamicProperties properties,
            final CodeCache codeCache,
            final SigImpactHistorian sigImpactHistorian,
            final AliasManager aliasManager,
            final EntityAccess entityAccess) {
        this.txnCtx = txnCtx;
        this.aliasManager = aliasManager;
        this.worldState = worldState;
        this.accountStore = accountStore;
        this.recordService = recordService;
        this.evmTxProcessor = evmTxProcessor;
        this.properties = properties;
        this.codeCache = codeCache;
        this.sigImpactHistorian = sigImpactHistorian;
        this.entityAccess = entityAccess;
    }

    @Override
    public void doStateTransition() {
        // --- Translate from gRPC types ---
        var contractCallTxn = txnCtx.accessor().getTxn();
        final var senderId = Id.fromGrpcAccount(txnCtx.activePayer());
        doStateTransitionOperation(contractCallTxn, senderId, null, 0, null);
    }

    public void doStateTransitionOperation(
            final TransactionBody contractCallTxn,
            final Id senderId,
            final Id relayerId,
            final long maxGasAllowanceInTinybars,
            final BigInteger offeredGasPrice) {
        var op = contractCallTxn.getContractCall();

        // --- Load the model objects ---
        final var sender = accountStore.loadAccount(senderId);

        final var target = targetOf(op);
        final var targetId = target.toId();
        Account receiver;
        if (target.equals(EntityNum.MISSING_NUM)
                && relayerId != null
                && !properties.evmVersion().equals(EVM_VERSION_0_30)
                && properties.isAutoCreationEnabled()
                && properties.isLazyCreationEnabled()) {
            // allow Ethereum transactions to lazy create a hollow account
            // if `to` is non-existent and `value` is non-zero
            validateTrue(op.getAmount() > 0, INVALID_ACCOUNT_ID);
            final var evmAddress = op.getContractID().getEvmAddress();
            validateTrue(evmAddress.size() == EVM_ADDRESS_SIZE, INVALID_CONTRACT_ID);
            receiver = new Account(evmAddress);
        } else {
            receiver =
                    entityAccess.isTokenAccount(targetId.asEvmAddress())
                            ? new Account(targetId)
                            : accountStore.loadContract(targetId);
        }

        final var callData =
                !op.getFunctionParameters().isEmpty()
                        ? Bytes.wrap(op.getFunctionParameters().toByteArray())
                        : Bytes.EMPTY;

        // --- Do the business logic ---
        TransactionProcessingResult result;
        if (relayerId == null) {
            result =
                    evmTxProcessor.execute(
                            sender,
                            receiver.canonicalAddress(),
                            op.getGas(),
                            op.getAmount(),
                            callData,
                            txnCtx.consensusTime());
        } else {
            sender.incrementEthereumNonce();
            accountStore.commitAccount(sender);

            result =
                    evmTxProcessor.executeEth(
                            sender,
                            receiver.canonicalAddress(),
                            op.getGas(),
                            op.getAmount(),
                            callData,
                            txnCtx.consensusTime(),
                            offeredGasPrice,
                            accountStore.loadAccount(relayerId),
                            maxGasAllowanceInTinybars);
        }

        /* --- Externalise result --- */
        if (target.equals(EntityNum.MISSING_NUM)) {
            // for failed lazy creates there is no ID we can set in the receipt
            // so only do this if result was successful; SigImpactHistorian has
            // already been updated in AutoCreationLogic, so no need to do duplicate work here
            if (result.isSuccessful()) {
                final var hollowAccountNum =
                        aliasManager.lookupIdBy(op.getContractID().getEvmAddress());
                txnCtx.setTargetedContract(hollowAccountNum.toGrpcContractID());
            }
        } else {
            // --- Persist changes into state ---
            final var createdContracts = worldState.getCreatedContractIds();
            result.setCreatedContracts(createdContracts);

            txnCtx.setTargetedContract(target.toGrpcContractID());

            for (final var createdContract : createdContracts) {
                sigImpactHistorian.markEntityChanged(createdContract.getContractNum());
            }
        }
        recordService.externaliseEvmCallTransaction(result);
    }

    @Override
    public Predicate<TransactionBody> applicability() {
        return TransactionBody::hasContractCall;
    }

    @Override
    public Function<TransactionBody, ResponseCodeEnum> semanticCheck() {
        return this::validateSemantics;
    }

    private ResponseCodeEnum validateSemantics(final TransactionBody transactionBody) {
        var op = transactionBody.getContractCall();

        if (op.getGas() < 0) {
            return CONTRACT_NEGATIVE_GAS;
        }
        if (op.getAmount() < 0) {
            return CONTRACT_NEGATIVE_VALUE;
        }
        if (op.getGas() > properties.maxGasPerSec()) {
            return MAX_GAS_LIMIT_EXCEEDED;
        }
        return OK;
    }

    @Override
    public void preFetch(final TxnAccessor accessor) {
        final var op = accessor.getTxn().getContractCall();
        preFetchOperation(op);
    }

    public void preFetchOperation(final ContractCallTransactionBody op) {
        final var id = targetOf(op);
        final var address = id.toEvmAddress();

        try {
            codeCache.getIfPresent(address);
        } catch (Exception e) {
            log.warn("Exception while attempting to pre-fetch code for {}", address, e);
        }
    }

    private EntityNum targetOf(final ContractCallTransactionBody op) {
        final var idOrAlias = op.getContractID();
        return EntityIdUtils.unaliased(idOrAlias, aliasManager);
    }
}
