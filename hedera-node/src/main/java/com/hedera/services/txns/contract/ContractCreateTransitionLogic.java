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
package com.hedera.services.txns.contract;

import static com.hedera.services.exceptions.ValidationUtils.validateFalse;
import static com.hedera.services.exceptions.ValidationUtils.validateTrue;
import static com.hedera.services.ledger.accounts.ContractCustomizer.fromHapiCreation;
import static com.hedera.services.ledger.accounts.HederaAccountCustomizer.hasStakedId;
import static com.hedera.services.state.EntityCreator.EMPTY_MEMO;
import static com.hedera.services.state.EntityCreator.NO_CUSTOM_FEES;
import static com.hedera.services.utils.EntityIdUtils.contractIdFromEvmAddress;
import static com.hederahashgraph.api.proto.java.ContractCreateTransactionBody.InitcodeSourceCase.INITCODE;
import static com.hederahashgraph.api.proto.java.ResponseCodeEnum.AUTORENEW_DURATION_NOT_IN_RANGE;
import static com.hederahashgraph.api.proto.java.ResponseCodeEnum.CONTRACT_FILE_EMPTY;
import static com.hederahashgraph.api.proto.java.ResponseCodeEnum.CONTRACT_NEGATIVE_GAS;
import static com.hederahashgraph.api.proto.java.ResponseCodeEnum.CONTRACT_NEGATIVE_VALUE;
import static com.hederahashgraph.api.proto.java.ResponseCodeEnum.INVALID_AUTORENEW_ACCOUNT;
import static com.hederahashgraph.api.proto.java.ResponseCodeEnum.INVALID_FILE_ID;
import static com.hederahashgraph.api.proto.java.ResponseCodeEnum.INVALID_RENEWAL_PERIOD;
import static com.hederahashgraph.api.proto.java.ResponseCodeEnum.INVALID_STAKING_ID;
import static com.hederahashgraph.api.proto.java.ResponseCodeEnum.MAX_GAS_LIMIT_EXCEEDED;
import static com.hederahashgraph.api.proto.java.ResponseCodeEnum.PROXY_ACCOUNT_ID_FIELD_IS_DEPRECATED;
import static com.hederahashgraph.api.proto.java.ResponseCodeEnum.REQUESTED_NUM_AUTOMATIC_ASSOCIATIONS_EXCEEDS_ASSOCIATION_LIMIT;
import static com.hederahashgraph.api.proto.java.ResponseCodeEnum.SERIALIZATION_FAILED;
import static com.hederahashgraph.api.proto.java.ResponseCodeEnum.STAKING_NOT_ENABLED;

import com.hedera.services.context.NodeInfo;
import com.hedera.services.context.SideEffectsTracker;
import com.hedera.services.context.TransactionContext;
import com.hedera.services.context.properties.GlobalDynamicProperties;
import com.hedera.services.contracts.execution.CreateEvmTxProcessor;
import com.hedera.services.contracts.execution.TransactionProcessingResult;
import com.hedera.services.exceptions.InvalidTransactionException;
import com.hedera.services.files.HederaFs;
import com.hedera.services.ledger.SigImpactHistorian;
import com.hedera.services.ledger.accounts.ContractCustomizer;
import com.hedera.services.legacy.core.jproto.JContractIDKey;
import com.hedera.services.legacy.proto.utils.ByteStringUtils;
import com.hedera.services.records.RecordsHistorian;
import com.hedera.services.records.TransactionRecordService;
import com.hedera.services.state.EntityCreator;
import com.hedera.services.state.merkle.MerkleAccount;
import com.hedera.services.store.AccountStore;
import com.hedera.services.store.contracts.HederaMutableWorldState;
import com.hedera.services.store.contracts.HederaWorldState;
import com.hedera.services.store.contracts.precompile.SyntheticTxnFactory;
import com.hedera.services.store.models.Id;
import com.hedera.services.stream.proto.SidecarType;
import com.hedera.services.stream.proto.TransactionSidecarRecord;
import com.hedera.services.txns.TransitionLogic;
import com.hedera.services.txns.validation.OptionValidator;
import com.hedera.services.utils.EntityNum;
import com.hedera.services.utils.SidecarUtils;
import com.hederahashgraph.api.proto.java.AccountID;
import com.hederahashgraph.api.proto.java.ContractCreateTransactionBody;
import com.hederahashgraph.api.proto.java.ContractID;
import com.hederahashgraph.api.proto.java.ResponseCodeEnum;
import com.hederahashgraph.api.proto.java.TransactionBody;
import com.swirlds.common.utility.CommonUtils;
import com.swirlds.merkle.map.MerkleMap;
import java.math.BigInteger;
import java.util.Collections;
import java.util.List;
import java.util.function.Function;
import java.util.function.Predicate;
import java.util.function.Supplier;
import javax.inject.Inject;
import javax.inject.Singleton;
import org.apache.tuweni.bytes.Bytes;
import org.hyperledger.besu.datatypes.Address;

@Singleton
public class ContractCreateTransitionLogic implements TransitionLogic {
    public static final JContractIDKey STANDIN_CONTRACT_ID_KEY = new JContractIDKey(0, 0, 0);

    private final HederaFs hfs;
    private final AccountStore accountStore;
    private final OptionValidator validator;
    private final TransactionContext txnCtx;
    private final EntityCreator entityCreator;
    private final RecordsHistorian recordsHistorian;
    private final HederaMutableWorldState worldState;
    private final TransactionRecordService recordService;
    private final CreateEvmTxProcessor evmTxProcessor;
    private final GlobalDynamicProperties properties;
    private final SigImpactHistorian sigImpactHistorian;
    private final Supplier<MerkleMap<EntityNum, MerkleAccount>> accounts;
    private final NodeInfo nodeInfo;
    private final SyntheticTxnFactory syntheticTxnFactory;

    @Inject
    public ContractCreateTransitionLogic(
            final HederaFs hfs,
            final TransactionContext txnCtx,
            final AccountStore accountStore,
            final OptionValidator validator,
            final HederaWorldState worldState,
            final EntityCreator entityCreator,
            final RecordsHistorian recordsHistorian,
            final TransactionRecordService recordService,
            final CreateEvmTxProcessor evmTxProcessor,
            final GlobalDynamicProperties properties,
            final SigImpactHistorian sigImpactHistorian,
            final SyntheticTxnFactory syntheticTxnFactory,
            final Supplier<MerkleMap<EntityNum, MerkleAccount>> accounts,
            final NodeInfo nodeInfo) {
        this.hfs = hfs;
        this.txnCtx = txnCtx;
        this.validator = validator;
        this.worldState = worldState;
        this.accountStore = accountStore;
        this.entityCreator = entityCreator;
        this.recordsHistorian = recordsHistorian;
        this.recordService = recordService;
        this.sigImpactHistorian = sigImpactHistorian;
        this.syntheticTxnFactory = syntheticTxnFactory;
        this.evmTxProcessor = evmTxProcessor;
        this.properties = properties;
        this.accounts = accounts;
        this.nodeInfo = nodeInfo;
    }

    @Override
    public void doStateTransition() {
        // --- Translate from gRPC types ---
        var contractCreateTxn = txnCtx.accessor().getTxn();
        final var senderId = Id.fromGrpcAccount(txnCtx.activePayer());
        doStateTransitionOperation(contractCreateTxn, senderId, false, null, 0, null);
    }

    public void doStateTransitionOperation(
            final TransactionBody contractCreateTxn,
            final Id senderId,
            final boolean createSyntheticRecord,
            final Id relayerId,
            final long maxGasAllowance,
            final BigInteger userOfferedGasPrice) {
        // --- Translate from gRPC types ---
        var op = contractCreateTxn.getContractCreateInstance();
        var key =
                op.hasAdminKey()
                        ? validator.attemptToDecodeOrThrow(op.getAdminKey(), SERIALIZATION_FAILED)
                        : STANDIN_CONTRACT_ID_KEY;
        // Standardize immutable contract key format; c.f.
        // https://github.com/hashgraph/hedera-services/issues/3037
        if (key.isEmpty()) {
            key = STANDIN_CONTRACT_ID_KEY;
        }

        if (op.hasAutoRenewAccountId()) {
            final var autoRenewAccountId = Id.fromGrpcAccount(op.getAutoRenewAccountId());
            final var autoRenewAccount =
                    accountStore.loadAccountOrFailWith(
                            autoRenewAccountId, INVALID_AUTORENEW_ACCOUNT);
            validateFalse(autoRenewAccount.isSmartContract(), INVALID_AUTORENEW_ACCOUNT);
        }

        // --- Load the model objects ---
        final var sender = accountStore.loadAccount(senderId);
        final var consensusTime = txnCtx.consensusTime();
        final var codeWithConstructorArgs = prepareCodeWithConstructorArguments(op);
        final var expiry = consensusTime.getEpochSecond() + op.getAutoRenewPeriod().getSeconds();
        final var newContractAddress = worldState.newContractAddress(sender.getId().asEvmAddress());

        // --- Do the business logic ---
        ContractCustomizer hapiSenderCustomizer = fromHapiCreation(key, consensusTime, op);
        if (!properties.areContractAutoAssociationsEnabled()) {
            hapiSenderCustomizer.accountCustomizer().maxAutomaticAssociations(0);
        }
        worldState.setHapiSenderCustomizer(hapiSenderCustomizer);
        TransactionProcessingResult result;
        try {
            if (relayerId == null) {
                result =
                        evmTxProcessor.execute(
                                sender,
                                newContractAddress,
                                op.getGas(),
                                op.getInitialBalance(),
                                codeWithConstructorArgs,
                                consensusTime,
                                expiry);
            } else {
                sender.incrementEthereumNonce();
                accountStore.commitAccount(sender);

                result =
                        evmTxProcessor.executeEth(
                                sender,
                                newContractAddress,
                                op.getGas(),
                                op.getInitialBalance(),
                                codeWithConstructorArgs,
                                consensusTime,
                                expiry,
                                accountStore.loadAccount(relayerId),
                                userOfferedGasPrice,
                                maxGasAllowance);
            }
        } finally {
            worldState.resetHapiSenderCustomizer();
        }

        // --- Persist changes into state ---
        final var createdContracts = worldState.getCreatedContractIds();
        result.setCreatedContracts(createdContracts);

        if (!result.isSuccessful()) {
            worldState.reclaimContractId();
        }

        // --- Externalise changes
        for (final var createdContract : createdContracts) {
            sigImpactHistorian.markEntityChanged(createdContract.getContractNum());
        }
        if (result.isSuccessful()) {
            final var newEvmAddress = newContractAddress.toArrayUnsafe();
            final var newContractId = contractIdFromEvmAddress(newEvmAddress);
            final var contractBytecodeSidecar =
                    op.getInitcodeSourceCase() != INITCODE
                            ? SidecarUtils.createContractBytecodeSidecarFrom(
                                    newContractId,
                                    codeWithConstructorArgs.toArrayUnsafe(),
                                    result.getOutput().toArrayUnsafe())
                            : SidecarUtils.createContractBytecodeSidecarFrom(
                                    newContractId, result.getOutput().toArrayUnsafe());
            if (createSyntheticRecord) {
                recordSyntheticOperation(
                        newContractId,
                        newEvmAddress,
                        hapiSenderCustomizer,
                        contractBytecodeSidecar);
                // bytecode sidecar is already externalized if needed in {@link
                // #recordSyntheticOperation}
                // so call {@link #externalizeSuccessfulEvmCreate} without contract bytecode sidecar
                // argument
                recordService.externalizeSuccessfulEvmCreate(result, newEvmAddress);
            } else {
                if (properties.enabledSidecars().contains(SidecarType.CONTRACT_BYTECODE)) {
                    recordService.externalizeSuccessfulEvmCreate(
                            result, newEvmAddress, contractBytecodeSidecar);
                } else {
                    recordService.externalizeSuccessfulEvmCreate(result, newEvmAddress);
                }
            }
            txnCtx.setTargetedContract(newContractId);
            sigImpactHistorian.markEntityChanged(newContractId.getContractNum());
        } else {
            recordService.externalizeUnsuccessfulEvmCreate(result);
        }
    }

    @Override
    public Predicate<TransactionBody> applicability() {
        return TransactionBody::hasContractCreateInstance;
    }

    @Override
    public Function<TransactionBody, ResponseCodeEnum> semanticCheck() {
        return this::validate;
    }

    public ResponseCodeEnum validate(TransactionBody contractCreateTxn) {
        var op = contractCreateTxn.getContractCreateInstance();

        if (!op.hasAutoRenewPeriod() || op.getAutoRenewPeriod().getSeconds() < 1) {
            return INVALID_RENEWAL_PERIOD;
        }
        if (!validator.isValidAutoRenewPeriod(op.getAutoRenewPeriod())) {
            return AUTORENEW_DURATION_NOT_IN_RANGE;
        }
        if (op.getGas() < 0) {
            return CONTRACT_NEGATIVE_GAS;
        }
        if (op.getInitialBalance() < 0) {
            return CONTRACT_NEGATIVE_VALUE;
        }
        if (op.getGas() > properties.maxGasPerSec()) {
            return MAX_GAS_LIMIT_EXCEEDED;
        }
        if (properties.areTokenAssociationsLimited()
                && op.getMaxAutomaticTokenAssociations() > properties.maxTokensPerAccount()) {
            return REQUESTED_NUM_AUTOMATIC_ASSOCIATIONS_EXCEEDS_ASSOCIATION_LIMIT;
        }
        if (op.hasProxyAccountID()
                && !op.getProxyAccountID().equals(AccountID.getDefaultInstance())) {
            return PROXY_ACCOUNT_ID_FIELD_IS_DEPRECATED;
        }
        final var stakedIdCase = op.getStakedIdCase().name();
        final var electsStakingId = hasStakedId(stakedIdCase);
        if (!properties.isStakingEnabled() && (electsStakingId || op.getDeclineReward())) {
            return STAKING_NOT_ENABLED;
        }
        if (electsStakingId
                && !validator.isValidStakedId(
                        stakedIdCase,
                        op.getStakedAccountId(),
                        op.getStakedNodeId(),
                        accounts.get(),
                        nodeInfo)) {
            return INVALID_STAKING_ID;
        }
        return validator.memoCheck(op.getMemo());
    }

    Bytes prepareCodeWithConstructorArguments(ContractCreateTransactionBody op) {
        if (op.getInitcodeSourceCase() == INITCODE) {
            return Bytes.wrap(ByteStringUtils.unwrapUnsafelyIfPossible(op.getInitcode()));
        } else {
            var bytecodeSrc = op.getFileID();
            validateTrue(hfs.exists(bytecodeSrc), INVALID_FILE_ID);
            byte[] bytecode = hfs.cat(bytecodeSrc);
            validateFalse(bytecode.length == 0, CONTRACT_FILE_EMPTY);

            var contractByteCodeString = new String(bytecode);
            if (!op.getConstructorParameters().isEmpty()) {
                final var constructorParamsHexString =
                        CommonUtils.hex(op.getConstructorParameters().toByteArray());
                contractByteCodeString += constructorParamsHexString;
            }
            try {
                return Bytes.fromHexString(contractByteCodeString);
            } catch (IllegalArgumentException e) {
                throw new InvalidTransactionException(ResponseCodeEnum.ERROR_DECODING_BYTESTRING);
            }
        }
    }

    private void recordSyntheticOperation(
            final ContractID newContractId,
            final byte[] newContractAddress,
            final ContractCustomizer opCustomizer,
            final TransactionSidecarRecord.Builder contractBytecodeSidecar) {
        var childRecordId = recordsHistorian.nextChildRecordSourceId();

        final var syntheticOp = syntheticTxnFactory.contractCreation(opCustomizer);

        final var sideEffects = new SideEffectsTracker();
        sideEffects.trackNewContract(newContractId, Address.wrap(Bytes.wrap(newContractAddress)));
        final var childRecord =
                entityCreator.createSuccessfulSyntheticRecord(
                        NO_CUSTOM_FEES, sideEffects, EMPTY_MEMO);

        recordsHistorian.trackFollowingChildRecord(
                childRecordId,
                syntheticOp,
                childRecord,
                properties.enabledSidecars().contains(SidecarType.CONTRACT_BYTECODE)
                        ? List.of(contractBytecodeSidecar)
                        : Collections.emptyList());
    }
}
