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

import static com.hedera.node.app.service.evm.utils.ValidationUtils.validateFalse;
import static com.hedera.node.app.service.mono.config.HederaNumbers.FIRST_USER_ENTITY;
import static com.hedera.node.app.service.mono.ledger.accounts.ContractCustomizer.fromHapiCreation;
import static com.hedera.node.app.service.mono.ledger.accounts.HederaAccountCustomizer.hasStakedId;
import static com.hedera.node.app.service.mono.state.EntityCreator.EMPTY_MEMO;
import static com.hedera.node.app.service.mono.state.EntityCreator.NO_CUSTOM_FEES;
import static com.hedera.node.app.service.mono.utils.EntityIdUtils.contractIdFromEvmAddress;
import static com.hederahashgraph.api.proto.java.ContractCreateTransactionBody.InitcodeSourceCase.INITCODE;
import static com.hederahashgraph.api.proto.java.ResponseCodeEnum.AUTORENEW_DURATION_NOT_IN_RANGE;
import static com.hederahashgraph.api.proto.java.ResponseCodeEnum.CONTRACT_BYTECODE_EMPTY;
import static com.hederahashgraph.api.proto.java.ResponseCodeEnum.CONTRACT_FILE_EMPTY;
import static com.hederahashgraph.api.proto.java.ResponseCodeEnum.CONTRACT_NEGATIVE_GAS;
import static com.hederahashgraph.api.proto.java.ResponseCodeEnum.CONTRACT_NEGATIVE_VALUE;
import static com.hederahashgraph.api.proto.java.ResponseCodeEnum.INVALID_AUTORENEW_ACCOUNT;
import static com.hederahashgraph.api.proto.java.ResponseCodeEnum.INVALID_FILE_ID;
import static com.hederahashgraph.api.proto.java.ResponseCodeEnum.INVALID_RENEWAL_PERIOD;
import static com.hederahashgraph.api.proto.java.ResponseCodeEnum.INVALID_STAKING_ID;
import static com.hederahashgraph.api.proto.java.ResponseCodeEnum.MAX_GAS_LIMIT_EXCEEDED;
import static com.hederahashgraph.api.proto.java.ResponseCodeEnum.NOT_SUPPORTED;
import static com.hederahashgraph.api.proto.java.ResponseCodeEnum.PROXY_ACCOUNT_ID_FIELD_IS_DEPRECATED;
import static com.hederahashgraph.api.proto.java.ResponseCodeEnum.REQUESTED_NUM_AUTOMATIC_ASSOCIATIONS_EXCEEDS_ASSOCIATION_LIMIT;
import static com.hederahashgraph.api.proto.java.ResponseCodeEnum.SERIALIZATION_FAILED;
import static com.hederahashgraph.api.proto.java.ResponseCodeEnum.STAKING_NOT_ENABLED;

import com.hedera.node.app.hapi.utils.ByteStringUtils;
import com.hedera.node.app.service.evm.exceptions.InvalidTransactionException;
import com.hedera.node.app.service.mono.context.NodeInfo;
import com.hedera.node.app.service.mono.context.SideEffectsTracker;
import com.hedera.node.app.service.mono.context.TransactionContext;
import com.hedera.node.app.service.mono.context.properties.GlobalDynamicProperties;
import com.hedera.node.app.service.mono.contracts.execution.CreateEvmTxProcessor;
import com.hedera.node.app.service.mono.contracts.execution.TransactionProcessingResult;
import com.hedera.node.app.service.mono.files.HederaFs;
import com.hedera.node.app.service.mono.files.TieredHederaFs;
import com.hedera.node.app.service.mono.ledger.SigImpactHistorian;
import com.hedera.node.app.service.mono.ledger.accounts.AliasManager;
import com.hedera.node.app.service.mono.ledger.accounts.ContractCustomizer;
import com.hedera.node.app.service.mono.legacy.core.jproto.JContractIDKey;
import com.hedera.node.app.service.mono.records.RecordsHistorian;
import com.hedera.node.app.service.mono.records.TransactionRecordService;
import com.hedera.node.app.service.mono.state.EntityCreator;
import com.hedera.node.app.service.mono.state.migration.AccountStorageAdapter;
import com.hedera.node.app.service.mono.store.AccountStore;
import com.hedera.node.app.service.mono.store.contracts.HederaMutableWorldState;
import com.hedera.node.app.service.mono.store.contracts.HederaWorldState;
import com.hedera.node.app.service.mono.store.contracts.precompile.SyntheticTxnFactory;
import com.hedera.node.app.service.mono.store.models.Id;
import com.hedera.node.app.service.mono.txns.TransitionLogic;
import com.hedera.node.app.service.mono.txns.validation.OptionValidator;
import com.hedera.node.app.service.mono.utils.SidecarUtils;
import com.hedera.services.stream.proto.SidecarType;
import com.hedera.services.stream.proto.TransactionSidecarRecord;
import com.hederahashgraph.api.proto.java.AccountID;
import com.hederahashgraph.api.proto.java.ContractCreateTransactionBody;
import com.hederahashgraph.api.proto.java.ContractID;
import com.hederahashgraph.api.proto.java.ResponseCodeEnum;
import com.hederahashgraph.api.proto.java.TransactionBody;
import com.swirlds.common.utility.CommonUtils;
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
    private final Supplier<AccountStorageAdapter> accounts;
    private final NodeInfo nodeInfo;
    private final SyntheticTxnFactory syntheticTxnFactory;
    private final AliasManager aliasManager;

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
            final Supplier<AccountStorageAdapter> accounts,
            final NodeInfo nodeInfo,
            final AliasManager aliasManager) {
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
        this.aliasManager = aliasManager;
    }

    @Override
    public void doStateTransition() {
        // --- Translate from gRPC types ---
        final var contractCreateTxn = txnCtx.accessor().getTxn();
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
        worldState.clearProvisionalContractCreations();
        worldState.clearContractNonces();

        // --- Translate from gRPC types ---
        final var op = contractCreateTxn.getContractCreateInstance();

        var key = op.hasAdminKey()
                ? validator.attemptToDecodeOrThrow(op.getAdminKey(), SERIALIZATION_FAILED)
                : STANDIN_CONTRACT_ID_KEY;
        // Standardize immutable contract key format; c.f.
        // https://github.com/hashgraph/hedera-services/issues/3037
        if (key.isEmpty()) {
            key = STANDIN_CONTRACT_ID_KEY;
        }

        if (op.hasAutoRenewAccountId()) {
            final var autoRenewAccountId = Id.fromGrpcAccount(op.getAutoRenewAccountId());
            accountStore.loadAccountOrFailWith(autoRenewAccountId, INVALID_AUTORENEW_ACCOUNT);
        }

        // --- Load the model objects ---
        final var sender = accountStore.loadAccount(senderId);
        final var consensusTime = txnCtx.consensusTime();
        final var codeWithConstructorArgs = prepareCodeWithConstructorArguments(op);

        final Address newContractAddress;

        final var newContractMirrorAddress =
                worldState.newContractAddress(sender.getId().asEvmAddress());
        if (relayerId == null) {
            newContractAddress = newContractMirrorAddress;
        } else {
            // Since there is an Ethereum origin, set the contract address as the CREATE format
            // specified in the Yellow Paper
            final var create1ContractAddress =
                    Address.contractAddress(sender.canonicalAddress(), sender.getEthereumNonce());
            aliasManager.link(create1ContractAddress, newContractMirrorAddress);
            newContractAddress = create1ContractAddress;
        }

        // --- Do the business logic ---
        final ContractCustomizer hapiSenderCustomizer =
                fromHapiCreation(key, consensusTime, op, newContractMirrorAddress);
        worldState.setHapiSenderCustomizer(hapiSenderCustomizer);
        TransactionProcessingResult result;
        try {
            if (relayerId == null) {
                result = evmTxProcessor.execute(
                        sender,
                        newContractAddress,
                        op.getGas(),
                        op.getInitialBalance(),
                        codeWithConstructorArgs,
                        consensusTime);
            } else {
                result = evmTxProcessor.executeEth(
                        sender,
                        newContractAddress,
                        op.getGas(),
                        op.getInitialBalance(),
                        codeWithConstructorArgs,
                        consensusTime,
                        accountStore.loadAccount(relayerId),
                        userOfferedGasPrice,
                        maxGasAllowance);
                result.setSignerNonce(worldState.get(senderId.asEvmAddress()).getNonce());
            }
        } finally {
            worldState.resetHapiSenderCustomizer();
        }

        // --- Persist changes into state ---
        final var createdContracts = worldState.getCreatedContractIds();
        result.setCreatedContracts(createdContracts);

        if (properties.isContractsNoncesExternalizationEnabled()) {
            final var createdNonces = worldState.getContractNonces();
            result.setContractNonces(createdNonces);
        }

        if (!result.isSuccessful()) {
            worldState.reclaimContractId();

            if (aliasManager.isInUse(newContractAddress)) {
                aliasManager.unlink(newContractAddress);
            }
        }

        // --- Externalise changes
        for (final var createdContract : createdContracts) {
            sigImpactHistorian.markEntityChanged(createdContract.getContractNum());
        }
        if (result.isSuccessful()) {
            final var newEvmAddress = newContractAddress.toArrayUnsafe();
            // Note we _cannot_ rely on a call to aliasResolver.resolveForEvm() here,
            // because the new contract's EVM-address-to-id link might not exist
            // any more---the new contract's constructor can SELFDESTRUCT
            final var newContractId = contractIdFromEvmAddress(newContractMirrorAddress);
            final var contractBytecodeSidecar = op.getInitcodeSourceCase() != INITCODE
                    ? SidecarUtils.createContractBytecodeSidecarFrom(
                            newContractId,
                            codeWithConstructorArgs.toArrayUnsafe(),
                            result.getOutput().toArrayUnsafe())
                    : SidecarUtils.createContractBytecodeSidecarFrom(
                            newContractId, result.getOutput().toArrayUnsafe());
            if (createSyntheticRecord) {
                recordSyntheticOperation(newContractId, newEvmAddress, hapiSenderCustomizer, contractBytecodeSidecar);
                // bytecode sidecar is already externalized if needed in {@link
                // #recordSyntheticOperation}
                // so call {@link #externalizeSuccessfulEvmCreate} without contract bytecode sidecar
                // argument
                recordService.externalizeSuccessfulEvmCreate(result, newEvmAddress);
            } else {
                if (properties.enabledSidecars().contains(SidecarType.CONTRACT_BYTECODE)) {
                    recordService.externalizeSuccessfulEvmCreate(result, newEvmAddress, contractBytecodeSidecar);
                } else {
                    recordService.externalizeSuccessfulEvmCreate(result, newEvmAddress);
                }
            }
            txnCtx.setTargetedContract(newContractId);
            sigImpactHistorian.markEntityChanged(newContractId.getContractNum());
            if (relayerId != null) {
                sigImpactHistorian.markAliasChanged(ByteStringUtils.wrapUnsafely(newContractAddress.toArrayUnsafe()));
            }
        } else {
            if (properties.enabledSidecars().contains(SidecarType.CONTRACT_BYTECODE)
                    && op.getInitcodeSourceCase() != INITCODE) {
                final var bytecodeSidecar = SidecarUtils.createContractBytecodeSidecarForFailedCreate(
                        codeWithConstructorArgs.toArrayUnsafe());
                recordService.externalizeUnsuccessfulEvmCreate(result, bytecodeSidecar);
            } else {
                recordService.externalizeUnsuccessfulEvmCreate(result);
            }
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

    public ResponseCodeEnum validate(final TransactionBody contractCreateTxn) {
        final var op = contractCreateTxn.getContractCreateInstance();

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
        final var usesAutoAssociations = op.getMaxAutomaticTokenAssociations() > 0;
        if (usesAutoAssociations && !properties.areContractAutoAssociationsEnabled()) {
            return NOT_SUPPORTED;
        }
        if (op.getMaxAutomaticTokenAssociations() > properties.maxAllowedAutoAssociations()) {
            return REQUESTED_NUM_AUTOMATIC_ASSOCIATIONS_EXCEEDS_ASSOCIATION_LIMIT;
        }
        if (properties.areTokenAssociationsLimited()
                && op.getMaxAutomaticTokenAssociations() > properties.maxTokensPerAccount()) {
            return REQUESTED_NUM_AUTOMATIC_ASSOCIATIONS_EXCEEDS_ASSOCIATION_LIMIT;
        }
        if (op.hasProxyAccountID() && !op.getProxyAccountID().equals(AccountID.getDefaultInstance())) {
            return PROXY_ACCOUNT_ID_FIELD_IS_DEPRECATED;
        }
        final var stakedIdCase = op.getStakedIdCase().name();
        final var electsStakingId = hasStakedId(stakedIdCase);
        if (!properties.isStakingEnabled() && (electsStakingId || op.getDeclineReward())) {
            return STAKING_NOT_ENABLED;
        }
        if (electsStakingId
                && !validator.isValidStakedId(
                        stakedIdCase, op.getStakedAccountId(), op.getStakedNodeId(), accounts.get(), nodeInfo)) {
            return INVALID_STAKING_ID;
        }
        return validator.memoCheck(op.getMemo());
    }

    Bytes prepareCodeWithConstructorArguments(final ContractCreateTransactionBody op) {
        if (op.getInitcodeSourceCase() == INITCODE) {
            validateFalse(op.getInitcode().isEmpty(), CONTRACT_BYTECODE_EMPTY);
            return Bytes.wrap(ByteStringUtils.unwrapUnsafelyIfPossible(op.getInitcode()));
        } else {
            final var bytecodeSrc = op.getFileID();
            validateFalse(bytecodeSrc.getFileNum() < FIRST_USER_ENTITY, INVALID_FILE_ID);
            final byte[] bytecode;
            try {
                bytecode = hfs.cat(bytecodeSrc);
            } catch (final IllegalArgumentException e) {
                final var failureReason = TieredHederaFs.IllegalArgumentType.valueOf(e.getMessage())
                        .suggestedStatus();
                throw new InvalidTransactionException(failureReason);
            }
            validateFalse(bytecode.length == 0, CONTRACT_FILE_EMPTY);

            var contractByteCodeString = new String(bytecode);
            if (!op.getConstructorParameters().isEmpty()) {
                final var constructorParamsHexString =
                        CommonUtils.hex(op.getConstructorParameters().toByteArray());
                contractByteCodeString += constructorParamsHexString;
            }
            try {
                return Bytes.fromHexString(contractByteCodeString);
            } catch (final IllegalArgumentException e) {
                throw new InvalidTransactionException(ResponseCodeEnum.ERROR_DECODING_BYTESTRING);
            }
        }
    }

    private void recordSyntheticOperation(
            final ContractID newContractId,
            final byte[] newContractAddress,
            final ContractCustomizer opCustomizer,
            final TransactionSidecarRecord.Builder contractBytecodeSidecar) {
        final var childRecordId = recordsHistorian.nextChildRecordSourceId();

        final var syntheticOp = syntheticTxnFactory.contractCreation(opCustomizer);

        final var sideEffects = new SideEffectsTracker();
        sideEffects.trackNewContract(newContractId, Address.wrap(Bytes.wrap(newContractAddress)));
        final var childRecord = entityCreator.createSuccessfulSyntheticRecord(NO_CUSTOM_FEES, sideEffects, EMPTY_MEMO);

        recordsHistorian.trackFollowingChildRecord(
                childRecordId,
                syntheticOp,
                childRecord,
                properties.enabledSidecars().contains(SidecarType.CONTRACT_BYTECODE)
                        ? List.of(contractBytecodeSidecar)
                        : Collections.emptyList());
    }
}
