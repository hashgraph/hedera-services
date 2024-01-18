/*
 * Copyright (C) 2020-2024 Hedera Hashgraph, LLC
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

import static com.hedera.node.app.service.evm.accounts.HederaEvmContractAliases.isMirror;
import static com.hedera.node.app.service.evm.utils.ValidationUtils.validateTrue;
import static com.hedera.node.app.service.mono.contracts.ContractsV_0_30Module.EVM_VERSION_0_30;
import static com.hedera.node.app.service.mono.contracts.ContractsV_0_34Module.EVM_VERSION_0_34;
import static com.hedera.node.app.service.mono.contracts.ContractsV_0_38Module.EVM_VERSION_0_38;
import static com.hedera.node.app.service.mono.utils.EntityIdUtils.isAlias;
import static com.hedera.node.app.service.mono.utils.EntityIdUtils.isOfEvmAddressSize;
import static com.hederahashgraph.api.proto.java.HederaFunctionality.ContractCall;
import static com.hederahashgraph.api.proto.java.ResponseCodeEnum.CONTRACT_NEGATIVE_VALUE;
import static com.hederahashgraph.api.proto.java.ResponseCodeEnum.INSUFFICIENT_GAS;
import static com.hederahashgraph.api.proto.java.ResponseCodeEnum.INVALID_CONTRACT_ID;
import static com.hederahashgraph.api.proto.java.ResponseCodeEnum.INVALID_FEE_SUBMITTED;
import static com.hederahashgraph.api.proto.java.ResponseCodeEnum.INVALID_SIGNATURE;
import static com.hederahashgraph.api.proto.java.ResponseCodeEnum.INVALID_SOLIDITY_ADDRESS;
import static com.hederahashgraph.api.proto.java.ResponseCodeEnum.MAX_GAS_LIMIT_EXCEEDED;
import static com.hederahashgraph.api.proto.java.ResponseCodeEnum.OK;

import com.hedera.node.app.service.evm.exceptions.InvalidTransactionException;
import com.hedera.node.app.service.mono.config.EntityNumbers;
import com.hedera.node.app.service.mono.context.TransactionContext;
import com.hedera.node.app.service.mono.context.properties.GlobalDynamicProperties;
import com.hedera.node.app.service.mono.contracts.execution.CallEvmTxProcessor;
import com.hedera.node.app.service.mono.contracts.execution.TransactionProcessingResult;
import com.hedera.node.app.service.mono.contracts.sources.EvmSigsVerifier;
import com.hedera.node.app.service.mono.ledger.SigImpactHistorian;
import com.hedera.node.app.service.mono.ledger.accounts.AliasManager;
import com.hedera.node.app.service.mono.records.TransactionRecordService;
import com.hedera.node.app.service.mono.store.AccountStore;
import com.hedera.node.app.service.mono.store.contracts.CodeCache;
import com.hedera.node.app.service.mono.store.contracts.EntityAccess;
import com.hedera.node.app.service.mono.store.contracts.HederaMutableWorldState;
import com.hedera.node.app.service.mono.store.contracts.HederaWorldState;
import com.hedera.node.app.service.mono.store.contracts.WorldLedgers;
import com.hedera.node.app.service.mono.store.models.Account;
import com.hedera.node.app.service.mono.store.models.Id;
import com.hedera.node.app.service.mono.txns.PreFetchableTransition;
import com.hedera.node.app.service.mono.utils.EntityIdUtils;
import com.hedera.node.app.service.mono.utils.EntityNum;
import com.hedera.node.app.service.mono.utils.accessors.TxnAccessor;
import com.hederahashgraph.api.proto.java.AccountID;
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
import org.hyperledger.besu.datatypes.Address;
import org.hyperledger.besu.evm.gascalculator.GasCalculator;

@Singleton
public class ContractCallTransitionLogic implements PreFetchableTransition {
    private static final Logger log = LogManager.getLogger(ContractCallTransitionLogic.class);
    private static final Address NEVER_ACTIVE_CONTRACT_ADDRESS = Address.ZERO;
    private static final long INTRINSIC_GAS_LOWER_BOUND = 21_000L;

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
    private final EvmSigsVerifier sigsVerifier;
    private final WorldLedgers worldLedgers;
    private final GasCalculator gasCalculator;
    private final EntityNumbers entityNumbers;

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
            final EntityAccess entityAccess,
            final EvmSigsVerifier sigsVerifier,
            final WorldLedgers worldLedgers,
            final GasCalculator gasCalculator,
            final EntityNumbers entityNumbers) {
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
        this.sigsVerifier = sigsVerifier;
        this.worldLedgers = worldLedgers;
        this.gasCalculator = gasCalculator;
        this.entityNumbers = entityNumbers;
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
        worldState.clearProvisionalContractCreations();
        worldState.clearContractNonces();

        var op = contractCallTxn.getContractCall();

        // --- Load the model objects ---
        final var sender = accountStore.loadAccount(senderId);
        final var target = targetOf(op);

        Account receiver = extractAndValidateReceiver(op, target, relayerId != null, true);

        final var callData = !op.getFunctionParameters().isEmpty()
                ? Bytes.wrap(op.getFunctionParameters().toByteArray())
                : Bytes.EMPTY;

        // --- Do the business logic ---
        TransactionProcessingResult result;
        if (relayerId == null) {
            result = evmTxProcessor.execute(
                    sender, receiver.canonicalAddress(), op.getGas(), op.getAmount(), callData, txnCtx.consensusTime());
        } else {
            sender.incrementEthereumNonce();
            accountStore.commitAccount(sender);

            result = evmTxProcessor.executeEth(
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

            if (properties.isContractsNoncesExternalizationEnabled()) {
                final var createdNonces = worldState.getContractNonces();
                result.setContractNonces(createdNonces);
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

    public Function<TransactionBody, ResponseCodeEnum> semanticEthCheck() {
        return this::validateEthSemantics;
    }

    private ResponseCodeEnum validateSemantics(final TransactionBody txBody) {
        final var op = txBody.getContractCall();

        final var commonSemanticsResult = validateSemanticsCommon(txBody);
        if (commonSemanticsResult != OK) {
            return commonSemanticsResult;
        }

        try {
            extractAndValidateReceiver(op, targetOf(op), false, false);
        } catch (InvalidTransactionException e) {
            return e.getResponseCode();
        }

        return OK;
    }

    private ResponseCodeEnum validateEthSemantics(final TransactionBody txBody) {
        final var op = txBody.getContractCall();

        final var commonSemanticsResult = validateSemanticsCommon(txBody);
        if (commonSemanticsResult != OK) {
            return commonSemanticsResult;
        }

        try {
            extractAndValidateReceiver(op, targetOf(op), true, false);
        } catch (InvalidTransactionException e) {
            return e.getResponseCode();
        }

        return OK;
    }

    private ResponseCodeEnum validateSemanticsCommon(final TransactionBody transactionBody) {
        var op = transactionBody.getContractCall();

        if (op.getGas()
                < Math.max(gasCalculator.transactionIntrinsicGasCost(Bytes.EMPTY, false), INTRINSIC_GAS_LOWER_BOUND)) {
            return INSUFFICIENT_GAS;
        }
        if (op.getAmount() < 0) {
            return CONTRACT_NEGATIVE_VALUE;
        }
        if (op.getGas() > properties.maxGasPerSec()) {
            return MAX_GAS_LIMIT_EXCEEDED;
        }
        final var target = targetOf(op);
        if (possiblySanityCheckOp(op, target)) {
            try {
                final var receiver = accountStore.loadContract(target.toId());
                validateTrue(receiver != null && receiver.isSmartContract(), INVALID_CONTRACT_ID);
            } catch (InvalidTransactionException e) {
                return e.getResponseCode();
            }
        }
        return OK;
    }

    // Determine if the operation should be sanity checked.
    private boolean possiblySanityCheckOp(final ContractCallTransactionBody op, final EntityNum target) {
        if (properties.callsToNonExistingEntitiesEnabled(target.toId().asEvmAddress())) {
            return false;
        }

        // Tokens are valid targets
        if (entityAccess.isTokenAccount(target.toId().asEvmAddress())) {
            return false;
        }

        // Check for possible lazy create
        if (((target.equals(EntityNum.MISSING_NUM)
                        && isOfEvmAddressSize(op.getContractID().getEvmAddress()))
                || op.getAmount() > 0)) {
            return false;
        }

        return true;
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

    private Account extractAndValidateReceiver(
            ContractCallTransactionBody op,
            final EntityNum unaliasedTargetNum,
            final boolean isEthTx,
            final boolean isHandle) {

        final var unaliasedTargetId = unaliasedTargetNum.toId();
        final var targetAliasIsMissing = unaliasedTargetNum.equals(EntityNum.MISSING_NUM);
        final var targetEVMAddress = op.getContractID().getEvmAddress();
        final var hasContractIdAsEvmAddress = targetEVMAddress != null;
        final var isLongZeroAddress =
                !isAlias(op.getContractID()) || (hasContractIdAsEvmAddress && isMirror(targetEVMAddress.toByteArray()));
        final var isSystemAccount = entityNumbers.isSystemAccount(
                AccountID.newBuilder().setAccountNum(unaliasedTargetId.num()).build());
        final var isTokenAccount = entityAccess.isTokenAccount(unaliasedTargetId.asEvmAddress());
        final var isUsableContract = accountStore.isContractUsable(unaliasedTargetId);

        // the receiver account exists in the ledger or is a long-zero address
        if (!targetAliasIsMissing) {

            if (op.getAmount() > 0) {
                if (isHandle) {
                    // Since contracts cannot have receiverSigRequired=true, this can only
                    // restrict us from sending value to an EOA
                    final var sigReqIsMet = sigsVerifier.hasActiveKeyOrNoReceiverSigReq(
                            false,
                            unaliasedTargetNum.toEvmAddress(),
                            NEVER_ACTIVE_CONTRACT_ADDRESS,
                            worldLedgers,
                            ContractCall);
                    validateTrue(sigReqIsMet, INVALID_SIGNATURE);
                }
                validateTrue(!isSystemAccount, INVALID_FEE_SUBMITTED);
                validateTrue(
                        entityAccess.isExtant(unaliasedTargetNum.toEvmAddress())
                                || !isLongZeroAddress
                                || isTokenAccount,
                        INVALID_CONTRACT_ID);
            } else {
                if (!OK.equals(isUsableContract)) {
                    // call to non-existing contract flow
                    validateTrue(!EVM_VERSION_0_30.equals(properties.evmVersion()), isUsableContract);
                    validateTrue(!EVM_VERSION_0_34.equals(properties.evmVersion()), isUsableContract);
                    validateTrue(!EVM_VERSION_0_38.equals(properties.evmVersion()), isUsableContract);
                    validateTrue(properties.allowCallsToNonContractAccounts(), isUsableContract);
                }
            }

            if (OK.equals(isUsableContract)) {
                return accountStore.loadContract(unaliasedTargetId);
            } else {
                return new Account(unaliasedTargetId);
            }
        } else {
            // target address may be missing if:
            // 1. this is a lazy-create through an ethereum transaction
            // 2. this is a call to a non-existing contract

            // validate contract address size
            validateTrue(isOfEvmAddressSize(targetEVMAddress), INVALID_CONTRACT_ID);

            // lazy create flow
            if (op.getAmount() > 0) {
                // do not permit lazy creation from non-ethereum transaction
                validateTrue(isEthTx, INVALID_FEE_SUBMITTED);
                // do not permit lazy creation in EVM version v030
                validateTrue(!properties.evmVersion().equals(EVM_VERSION_0_30), INVALID_SOLIDITY_ADDRESS);
                // do not permit lazy creation of mirror address
                validateTrue(
                        !aliasManager.isMirror(Address.wrap(Bytes.of(targetEVMAddress.toByteArray()))),
                        INVALID_SOLIDITY_ADDRESS);
                // do not permit lazy creation of system accounts
                validateTrue(!isSystemAccount, INVALID_FEE_SUBMITTED);
                // do not permit lazy creation if flags are disabled
                validateTrue(
                        properties.isAutoCreationEnabled() && properties.isLazyCreationEnabled(),
                        INVALID_FEE_SUBMITTED);

            } else {
                // call to non-existing contract flow
                validateTrue(!EVM_VERSION_0_30.equals(properties.evmVersion()), INVALID_CONTRACT_ID);
                validateTrue(!EVM_VERSION_0_34.equals(properties.evmVersion()), INVALID_CONTRACT_ID);
                validateTrue(!EVM_VERSION_0_38.equals(properties.evmVersion()), INVALID_CONTRACT_ID);
                validateTrue(properties.allowCallsToNonContractAccounts(), INVALID_CONTRACT_ID);
            }
            return new Account(targetEVMAddress);
        }
    }
}
