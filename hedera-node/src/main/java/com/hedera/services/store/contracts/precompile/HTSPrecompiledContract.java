/*
 * Copyright (C) 2021-2022 Hedera Hashgraph, LLC
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
package com.hedera.services.store.contracts.precompile;

import static com.hedera.services.exceptions.ValidationUtils.validateTrue;
import static com.hedera.services.state.EntityCreator.EMPTY_MEMO;
import static com.hedera.services.store.contracts.precompile.utils.DescriptorUtils.isTokenProxyRedirect;
import static com.hedera.services.store.contracts.precompile.utils.DescriptorUtils.isViewFunction;
import static com.hedera.services.utils.EntityIdUtils.contractIdFromEvmAddress;
import static com.hederahashgraph.api.proto.java.ResponseCodeEnum.FAIL_INVALID;
import static com.hederahashgraph.api.proto.java.ResponseCodeEnum.INSUFFICIENT_GAS;
import static com.hederahashgraph.api.proto.java.ResponseCodeEnum.INVALID_TOKEN_ID;
import static com.hederahashgraph.api.proto.java.ResponseCodeEnum.NOT_SUPPORTED;

import com.google.common.annotations.VisibleForTesting;
import com.hedera.services.context.SideEffectsTracker;
import com.hedera.services.context.primitives.StateView;
import com.hedera.services.context.properties.GlobalDynamicProperties;
import com.hedera.services.contracts.sources.EvmSigsVerifier;
import com.hedera.services.contracts.sources.TxnAwareEvmSigsVerifier;
import com.hedera.services.exceptions.InvalidTransactionException;
import com.hedera.services.fees.FeeCalculator;
import com.hedera.services.grpc.marshalling.ImpliedTransfersMarshal;
import com.hedera.services.records.RecordsHistorian;
import com.hedera.services.state.EntityCreator;
import com.hedera.services.state.enums.TokenType;
import com.hedera.services.state.expiry.ExpiringCreations;
import com.hedera.services.state.submerkle.EntityId;
import com.hedera.services.state.submerkle.ExpirableTxnRecord;
import com.hedera.services.store.contracts.AbstractLedgerWorldUpdater;
import com.hedera.services.store.contracts.HederaStackedWorldStateUpdater;
import com.hedera.services.store.contracts.WorldLedgers;
import com.hedera.services.store.contracts.precompile.codec.EncodingFacade;
import com.hedera.services.store.contracts.precompile.impl.AllowancePrecompile;
import com.hedera.services.store.contracts.precompile.impl.ApprovePrecompile;
import com.hedera.services.store.contracts.precompile.impl.AssociatePrecompile;
import com.hedera.services.store.contracts.precompile.impl.BalanceOfPrecompile;
import com.hedera.services.store.contracts.precompile.impl.BurnPrecompile;
import com.hedera.services.store.contracts.precompile.impl.DecimalsPrecompile;
import com.hedera.services.store.contracts.precompile.impl.DeleteTokenPrecompile;
import com.hedera.services.store.contracts.precompile.impl.DissociatePrecompile;
import com.hedera.services.store.contracts.precompile.impl.ERCTransferPrecompile;
import com.hedera.services.store.contracts.precompile.impl.FreezeTokenPrecompile;
import com.hedera.services.store.contracts.precompile.impl.FungibleTokenInfoPrecompile;
import com.hedera.services.store.contracts.precompile.impl.GetApprovedPrecompile;
import com.hedera.services.store.contracts.precompile.impl.GetTokenDefaultFreezeStatus;
import com.hedera.services.store.contracts.precompile.impl.GetTokenDefaultKycStatus;
import com.hedera.services.store.contracts.precompile.impl.GetTokenExpiryInfoPrecompile;
import com.hedera.services.store.contracts.precompile.impl.GetTokenKeyPrecompile;
import com.hedera.services.store.contracts.precompile.impl.GetTokenTypePrecompile;
import com.hedera.services.store.contracts.precompile.impl.GrantKycPrecompile;
import com.hedera.services.store.contracts.precompile.impl.IsApprovedForAllPrecompile;
import com.hedera.services.store.contracts.precompile.impl.IsFrozenPrecompile;
import com.hedera.services.store.contracts.precompile.impl.IsKycPrecompile;
import com.hedera.services.store.contracts.precompile.impl.IsTokenPrecompile;
import com.hedera.services.store.contracts.precompile.impl.MintPrecompile;
import com.hedera.services.store.contracts.precompile.impl.MultiAssociatePrecompile;
import com.hedera.services.store.contracts.precompile.impl.MultiDissociatePrecompile;
import com.hedera.services.store.contracts.precompile.impl.NamePrecompile;
import com.hedera.services.store.contracts.precompile.impl.NonFungibleTokenInfoPrecompile;
import com.hedera.services.store.contracts.precompile.impl.OwnerOfPrecompile;
import com.hedera.services.store.contracts.precompile.impl.PausePrecompile;
import com.hedera.services.store.contracts.precompile.impl.RevokeKycPrecompile;
import com.hedera.services.store.contracts.precompile.impl.SetApprovalForAllPrecompile;
import com.hedera.services.store.contracts.precompile.impl.SymbolPrecompile;
import com.hedera.services.store.contracts.precompile.impl.TokenCreatePrecompile;
import com.hedera.services.store.contracts.precompile.impl.TokenGetCustomFeesPrecompile;
import com.hedera.services.store.contracts.precompile.impl.TokenInfoPrecompile;
import com.hedera.services.store.contracts.precompile.impl.TokenURIPrecompile;
import com.hedera.services.store.contracts.precompile.impl.TokenUpdateKeysPrecompile;
import com.hedera.services.store.contracts.precompile.impl.TokenUpdatePrecompile;
import com.hedera.services.store.contracts.precompile.impl.TotalSupplyPrecompile;
import com.hedera.services.store.contracts.precompile.impl.TransferPrecompile;
import com.hedera.services.store.contracts.precompile.impl.UnfreezeTokenPrecompile;
import com.hedera.services.store.contracts.precompile.impl.UnpausePrecompile;
import com.hedera.services.store.contracts.precompile.impl.UpdateTokenExpiryInfoPrecompile;
import com.hedera.services.store.contracts.precompile.impl.WipeFungiblePrecompile;
import com.hedera.services.store.contracts.precompile.impl.WipeNonFungiblePrecompile;
import com.hedera.services.store.contracts.precompile.utils.DescriptorUtils;
import com.hedera.services.store.contracts.precompile.utils.PrecompilePricingUtils;
import com.hedera.services.store.contracts.precompile.utils.PrecompileUtils;
import com.hederahashgraph.api.proto.java.ContractID;
import com.hederahashgraph.api.proto.java.ResponseCodeEnum;
import com.hederahashgraph.api.proto.java.TransactionBody;
import java.util.Optional;
import java.util.function.Supplier;
import java.util.function.UnaryOperator;
import javax.inject.Inject;
import javax.inject.Provider;
import javax.inject.Singleton;
import org.apache.commons.lang3.tuple.Pair;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.apache.tuweni.bytes.Bytes;
import org.hyperledger.besu.datatypes.Address;
import org.hyperledger.besu.evm.frame.ExceptionalHaltReason;
import org.hyperledger.besu.evm.frame.MessageFrame;
import org.hyperledger.besu.evm.gascalculator.GasCalculator;
import org.hyperledger.besu.evm.precompile.AbstractPrecompiledContract;
import org.hyperledger.besu.evm.precompile.PrecompiledContract;
import org.jetbrains.annotations.NotNull;

@Singleton
public class HTSPrecompiledContract extends AbstractPrecompiledContract {
    private static final Logger log = LogManager.getLogger(HTSPrecompiledContract.class);

    public static final String HTS_PRECOMPILED_CONTRACT_ADDRESS = "0x167";
    public static final ContractID HTS_PRECOMPILE_MIRROR_ID =
            contractIdFromEvmAddress(
                    Address.fromHexString(HTS_PRECOMPILED_CONTRACT_ADDRESS).toArrayUnsafe());
    public static final EntityId HTS_PRECOMPILE_MIRROR_ENTITY_ID =
            EntityId.fromGrpcContractId(HTS_PRECOMPILE_MIRROR_ID);

    private static final PrecompileContractResult NO_RESULT =
            new PrecompileContractResult(
                    null, true, MessageFrame.State.COMPLETED_FAILED, Optional.empty());

    private static final Bytes STATIC_CALL_REVERT_REASON =
            Bytes.of("HTS precompiles are not static".getBytes());
    private static final String NOT_SUPPORTED_FUNGIBLE_OPERATION_REASON =
            "Invalid operation for ERC-20 token!";
    private static final String NOT_SUPPORTED_NON_FUNGIBLE_OPERATION_REASON =
            "Invalid operation for ERC-721 token!";
    private static final Bytes ERROR_DECODING_INPUT_REVERT_REASON =
            Bytes.of("Error decoding precompile input".getBytes());
    public static final String URI_QUERY_NON_EXISTING_TOKEN_ERROR =
            "ERC721Metadata: URI query for nonexistent token";

    private final EntityCreator creator;
    private final EncodingFacade encoder;
    private final GlobalDynamicProperties dynamicProperties;
    private final EvmSigsVerifier sigsVerifier;
    private final RecordsHistorian recordsHistorian;
    private final SyntheticTxnFactory syntheticTxnFactory;
    private final InfrastructureFactory infrastructureFactory;
    private final ImpliedTransfersMarshal impliedTransfersMarshal;

    private Precompile precompile;
    private TransactionBody.Builder transactionBody;
    private final Provider<FeeCalculator> feeCalculator;
    private long gasRequirement = 0;
    private final StateView currentView;
    private SideEffectsTracker sideEffectsTracker;
    private final PrecompilePricingUtils precompilePricingUtils;
    private WorldLedgers ledgers;
    private Address senderAddress;
    private HederaStackedWorldStateUpdater updater;

    @Inject
    public HTSPrecompiledContract(
            final GlobalDynamicProperties dynamicProperties,
            final GasCalculator gasCalculator,
            final RecordsHistorian recordsHistorian,
            final TxnAwareEvmSigsVerifier sigsVerifier,
            final EncodingFacade encoder,
            final SyntheticTxnFactory syntheticTxnFactory,
            final ExpiringCreations creator,
            final ImpliedTransfersMarshal impliedTransfersMarshal,
            final Provider<FeeCalculator> feeCalculator,
            final StateView currentView,
            final PrecompilePricingUtils precompilePricingUtils,
            final InfrastructureFactory infrastructureFactory) {
        super("HTS", gasCalculator);
        this.encoder = encoder;
        this.sigsVerifier = sigsVerifier;
        this.recordsHistorian = recordsHistorian;
        this.syntheticTxnFactory = syntheticTxnFactory;
        this.creator = creator;
        this.dynamicProperties = dynamicProperties;
        this.impliedTransfersMarshal = impliedTransfersMarshal;
        this.feeCalculator = feeCalculator;
        this.currentView = currentView;
        this.precompilePricingUtils = precompilePricingUtils;
        this.infrastructureFactory = infrastructureFactory;
    }

    public Pair<Long, Bytes> computeCosted(final Bytes input, final MessageFrame frame) {
        if (frame.isStatic()) {
            if (!isTokenProxyRedirect(input) && !isViewFunction(input)) {
                frame.setRevertReason(STATIC_CALL_REVERT_REASON);
                return Pair.of(defaultGas(), null);
            }

            final var proxyUpdater = (HederaStackedWorldStateUpdater) frame.getWorldUpdater();
            if (!proxyUpdater.isInTransaction()) {
                if (isTokenProxyRedirect(input)) {
                    final var executor =
                            infrastructureFactory.newRedirectExecutor(
                                    input, frame, precompilePricingUtils::computeViewFunctionGas);
                    return executor.computeCosted();
                } else if (isViewFunction(input)) {
                    final var executor =
                            infrastructureFactory.newViewExecutor(
                                    input,
                                    frame,
                                    precompilePricingUtils::computeViewFunctionGas,
                                    currentView,
                                    ledgers);
                    return executor.computeCosted();
                }
            }
        }
        final var result = computePrecompile(input, frame);
        return Pair.of(gasRequirement, result.getOutput());
    }

    @Override
    public long gasRequirement(final Bytes bytes) {
        return gasRequirement;
    }

    @NotNull
    @Override
    public PrecompileContractResult computePrecompile(
            final Bytes input, @NotNull final MessageFrame frame) {
        prepareFields(frame);
        prepareComputation(input, updater::unaliased);

        gasRequirement = defaultGas();
        if (this.precompile == null || this.transactionBody == null) {
            frame.setRevertReason(ERROR_DECODING_INPUT_REVERT_REASON);
            return NO_RESULT;
        }

        final var now = frame.getBlockValues().getTimestamp();
        gasRequirement = precompile.getGasRequirement(now);
        Bytes result = computeInternal(frame);

        return result == null
                ? PrecompiledContract.PrecompileContractResult.halt(
                        null, Optional.of(ExceptionalHaltReason.NONE))
                : PrecompiledContract.PrecompileContractResult.success(result);
    }

    void prepareFields(final MessageFrame frame) {
        this.updater = (HederaStackedWorldStateUpdater) frame.getWorldUpdater();
        this.sideEffectsTracker = infrastructureFactory.newSideEffects();
        this.ledgers = updater.wrappedTrackingLedgers(sideEffectsTracker);

        final var unaliasedSenderAddress =
                updater.permissivelyUnaliased(frame.getSenderAddress().toArray());
        this.senderAddress = Address.wrap(Bytes.of(unaliasedSenderAddress));
    }

    void prepareComputation(final Bytes input, final UnaryOperator<byte[]> aliasResolver) {
        this.precompile = null;
        this.transactionBody = null;

        int functionId = input.getInt(0);
        this.gasRequirement = 0L;

        this.precompile =
                switch (functionId) {
                    case AbiConstants.ABI_ID_CRYPTO_TRANSFER,
                            AbiConstants.ABI_ID_TRANSFER_TOKENS,
                            AbiConstants.ABI_ID_TRANSFER_TOKEN,
                            AbiConstants.ABI_ID_TRANSFER_NFTS,
                            AbiConstants.ABI_ID_TRANSFER_NFT -> new TransferPrecompile(
                            ledgers,
                            updater,
                            sigsVerifier,
                            sideEffectsTracker,
                            syntheticTxnFactory,
                            infrastructureFactory,
                            precompilePricingUtils,
                            functionId,
                            senderAddress,
                            impliedTransfersMarshal);
                    case AbiConstants.ABI_ID_MINT_TOKEN -> new MintPrecompile(
                            ledgers,
                            encoder,
                            updater.aliases(),
                            sigsVerifier,
                            recordsHistorian,
                            sideEffectsTracker,
                            syntheticTxnFactory,
                            infrastructureFactory,
                            precompilePricingUtils);
                    case AbiConstants.ABI_ID_BURN_TOKEN -> new BurnPrecompile(
                            ledgers,
                            encoder,
                            updater.aliases(),
                            sigsVerifier,
                            sideEffectsTracker,
                            syntheticTxnFactory,
                            infrastructureFactory,
                            precompilePricingUtils);
                    case AbiConstants.ABI_ID_ASSOCIATE_TOKENS -> new MultiAssociatePrecompile(
                            ledgers,
                            updater.aliases(),
                            sigsVerifier,
                            sideEffectsTracker,
                            syntheticTxnFactory,
                            infrastructureFactory,
                            precompilePricingUtils,
                            feeCalculator,
                            currentView);
                    case AbiConstants.ABI_ID_ASSOCIATE_TOKEN -> new AssociatePrecompile(
                            ledgers,
                            updater.aliases(),
                            sigsVerifier,
                            sideEffectsTracker,
                            syntheticTxnFactory,
                            infrastructureFactory,
                            precompilePricingUtils,
                            feeCalculator,
                            currentView);
                    case AbiConstants.ABI_ID_DISSOCIATE_TOKENS -> new MultiDissociatePrecompile(
                            ledgers,
                            updater.aliases(),
                            sigsVerifier,
                            sideEffectsTracker,
                            syntheticTxnFactory,
                            infrastructureFactory,
                            precompilePricingUtils,
                            feeCalculator,
                            currentView);
                    case AbiConstants.ABI_ID_DISSOCIATE_TOKEN -> new DissociatePrecompile(
                            ledgers,
                            updater.aliases(),
                            sigsVerifier,
                            sideEffectsTracker,
                            syntheticTxnFactory,
                            infrastructureFactory,
                            precompilePricingUtils,
                            feeCalculator,
                            currentView);
                    case AbiConstants.ABI_ID_PAUSE_TOKEN -> new PausePrecompile(
                            ledgers,
                            updater.aliases(),
                            sigsVerifier,
                            sideEffectsTracker,
                            syntheticTxnFactory,
                            infrastructureFactory,
                            precompilePricingUtils);
                    case AbiConstants.ABI_ID_UNPAUSE_TOKEN -> new UnpausePrecompile(
                            ledgers,
                            updater.aliases(),
                            sigsVerifier,
                            sideEffectsTracker,
                            syntheticTxnFactory,
                            infrastructureFactory,
                            precompilePricingUtils);
                    case AbiConstants.ABI_ID_ALLOWANCE -> checkFeatureFlag(
                            dynamicProperties.areAllowancesEnabled(),
                            () ->
                                    new AllowancePrecompile(
                                            syntheticTxnFactory,
                                            ledgers,
                                            encoder,
                                            precompilePricingUtils));
                    case AbiConstants.ABI_ID_APPROVE -> checkFeatureFlag(
                            dynamicProperties.areAllowancesEnabled(),
                            () ->
                                    new ApprovePrecompile(
                                            true,
                                            ledgers,
                                            encoder,
                                            currentView,
                                            sideEffectsTracker,
                                            syntheticTxnFactory,
                                            infrastructureFactory,
                                            precompilePricingUtils,
                                            senderAddress));
                    case AbiConstants.ABI_ID_APPROVE_NFT -> checkFeatureFlag(
                            dynamicProperties.areAllowancesEnabled(),
                            () ->
                                    new ApprovePrecompile(
                                            false,
                                            ledgers,
                                            encoder,
                                            currentView,
                                            sideEffectsTracker,
                                            syntheticTxnFactory,
                                            infrastructureFactory,
                                            precompilePricingUtils,
                                            senderAddress));
                    case AbiConstants.ABI_ID_SET_APPROVAL_FOR_ALL -> checkFeatureFlag(
                            dynamicProperties.areAllowancesEnabled(),
                            () ->
                                    new SetApprovalForAllPrecompile(
                                            ledgers,
                                            currentView,
                                            sideEffectsTracker,
                                            syntheticTxnFactory,
                                            infrastructureFactory,
                                            precompilePricingUtils,
                                            senderAddress));
                    case AbiConstants.ABI_ID_GET_APPROVED -> checkFeatureFlag(
                            dynamicProperties.areAllowancesEnabled(),
                            () ->
                                    new GetApprovedPrecompile(
                                            syntheticTxnFactory,
                                            ledgers,
                                            encoder,
                                            precompilePricingUtils));
                    case AbiConstants.ABI_ID_IS_APPROVED_FOR_ALL -> checkFeatureFlag(
                            dynamicProperties.areAllowancesEnabled(),
                            () ->
                                    new IsApprovedForAllPrecompile(
                                            syntheticTxnFactory,
                                            ledgers,
                                            encoder,
                                            precompilePricingUtils));
                    case AbiConstants
                            .ABI_ID_GET_TOKEN_DEFAULT_FREEZE_STATUS -> new GetTokenDefaultFreezeStatus(
                            syntheticTxnFactory, ledgers, encoder, precompilePricingUtils);
                    case AbiConstants
                            .ABI_ID_GET_TOKEN_DEFAULT_KYC_STATUS -> new GetTokenDefaultKycStatus(
                            syntheticTxnFactory, ledgers, encoder, precompilePricingUtils);
                    case AbiConstants.ABI_ID_IS_KYC -> new IsKycPrecompile(
                            null, syntheticTxnFactory, ledgers, encoder, precompilePricingUtils);
                    case AbiConstants.ABI_ID_GRANT_TOKEN_KYC -> new GrantKycPrecompile(
                            ledgers,
                            updater.aliases(),
                            sigsVerifier,
                            sideEffectsTracker,
                            syntheticTxnFactory,
                            infrastructureFactory,
                            precompilePricingUtils);
                    case AbiConstants.ABI_ID_REVOKE_TOKEN_KYC -> new RevokeKycPrecompile(
                            ledgers,
                            updater.aliases(),
                            sigsVerifier,
                            sideEffectsTracker,
                            syntheticTxnFactory,
                            infrastructureFactory,
                            precompilePricingUtils);
                    case AbiConstants.ABI_WIPE_TOKEN_ACCOUNT_FUNGIBLE -> new WipeFungiblePrecompile(
                            ledgers,
                            updater.aliases(),
                            sigsVerifier,
                            sideEffectsTracker,
                            syntheticTxnFactory,
                            infrastructureFactory,
                            precompilePricingUtils);
                    case AbiConstants.ABI_WIPE_TOKEN_ACCOUNT_NFT -> new WipeNonFungiblePrecompile(
                            ledgers,
                            updater.aliases(),
                            sigsVerifier,
                            sideEffectsTracker,
                            syntheticTxnFactory,
                            infrastructureFactory,
                            precompilePricingUtils);
                    case AbiConstants.ABI_ID_IS_FROZEN -> new IsFrozenPrecompile(
                            null, syntheticTxnFactory, ledgers, encoder, precompilePricingUtils);
                    case AbiConstants.ABI_ID_FREEZE -> new FreezeTokenPrecompile(
                            ledgers,
                            updater.aliases(),
                            sigsVerifier,
                            sideEffectsTracker,
                            syntheticTxnFactory,
                            infrastructureFactory,
                            precompilePricingUtils,
                            true);
                    case AbiConstants.ABI_ID_UNFREEZE -> new UnfreezeTokenPrecompile(
                            ledgers,
                            updater.aliases(),
                            sigsVerifier,
                            sideEffectsTracker,
                            syntheticTxnFactory,
                            infrastructureFactory,
                            precompilePricingUtils,
                            false);
                    case AbiConstants.ABI_ID_DELETE_TOKEN -> new DeleteTokenPrecompile(
                            ledgers,
                            updater.aliases(),
                            sigsVerifier,
                            sideEffectsTracker,
                            syntheticTxnFactory,
                            infrastructureFactory,
                            precompilePricingUtils);
                    case AbiConstants.ABI_ID_UPDATE_TOKEN_INFO,
                            AbiConstants.ABI_ID_UPDATE_TOKEN_INFO_V2 -> new TokenUpdatePrecompile(
                            ledgers,
                            updater.aliases(),
                            sigsVerifier,
                            sideEffectsTracker,
                            syntheticTxnFactory,
                            infrastructureFactory,
                            precompilePricingUtils,
                            functionId);
                    case AbiConstants.ABI_ID_UPDATE_TOKEN_KEYS -> new TokenUpdateKeysPrecompile(
                            ledgers,
                            updater.aliases(),
                            sigsVerifier,
                            sideEffectsTracker,
                            syntheticTxnFactory,
                            infrastructureFactory,
                            precompilePricingUtils);
                    case AbiConstants.ABI_ID_GET_TOKEN_KEY -> new GetTokenKeyPrecompile(
                            null, syntheticTxnFactory, ledgers, encoder, precompilePricingUtils);
                    case AbiConstants.ABI_ID_REDIRECT_FOR_TOKEN -> {
                        final var target = DescriptorUtils.getRedirectTarget(input);
                        final var tokenId = target.tokenId();
                        final var isFungibleToken =
                                TokenType.FUNGIBLE_COMMON.equals(ledgers.typeOf(tokenId));
                        final var nestedFunctionSelector = target.descriptor();
                        yield switch (nestedFunctionSelector) {
                            case AbiConstants.ABI_ID_ERC_NAME -> new NamePrecompile(
                                    tokenId,
                                    syntheticTxnFactory,
                                    ledgers,
                                    encoder,
                                    precompilePricingUtils);
                            case AbiConstants.ABI_ID_ERC_SYMBOL -> new SymbolPrecompile(
                                    tokenId,
                                    syntheticTxnFactory,
                                    ledgers,
                                    encoder,
                                    precompilePricingUtils);
                            case AbiConstants.ABI_ID_ERC_DECIMALS -> checkFungible(
                                    isFungibleToken,
                                    () ->
                                            new DecimalsPrecompile(
                                                    tokenId,
                                                    syntheticTxnFactory,
                                                    ledgers,
                                                    encoder,
                                                    precompilePricingUtils));
                            case AbiConstants
                                    .ABI_ID_ERC_TOTAL_SUPPLY_TOKEN -> new TotalSupplyPrecompile(
                                    tokenId,
                                    syntheticTxnFactory,
                                    ledgers,
                                    encoder,
                                    precompilePricingUtils);
                            case AbiConstants
                                    .ABI_ID_ERC_BALANCE_OF_TOKEN -> new BalanceOfPrecompile(
                                    tokenId,
                                    syntheticTxnFactory,
                                    ledgers,
                                    encoder,
                                    precompilePricingUtils);
                            case AbiConstants.ABI_ID_ERC_OWNER_OF_NFT -> checkNFT(
                                    isFungibleToken,
                                    () ->
                                            new OwnerOfPrecompile(
                                                    tokenId,
                                                    syntheticTxnFactory,
                                                    ledgers,
                                                    encoder,
                                                    precompilePricingUtils));
                            case AbiConstants.ABI_ID_ERC_TOKEN_URI_NFT -> checkNFT(
                                    isFungibleToken,
                                    () ->
                                            new TokenURIPrecompile(
                                                    tokenId,
                                                    syntheticTxnFactory,
                                                    ledgers,
                                                    encoder,
                                                    precompilePricingUtils));
                            case AbiConstants.ABI_ID_ERC_TRANSFER -> checkFungible(
                                    isFungibleToken,
                                    () ->
                                            new ERCTransferPrecompile(
                                                    tokenId,
                                                    senderAddress,
                                                    isFungibleToken,
                                                    ledgers,
                                                    encoder,
                                                    updater,
                                                    sigsVerifier,
                                                    sideEffectsTracker,
                                                    syntheticTxnFactory,
                                                    infrastructureFactory,
                                                    precompilePricingUtils,
                                                    functionId,
                                                    impliedTransfersMarshal));
                            case AbiConstants.ABI_ID_ERC_TRANSFER_FROM -> checkFeatureFlag(
                                    dynamicProperties.areAllowancesEnabled(),
                                    () ->
                                            new ERCTransferPrecompile(
                                                    tokenId,
                                                    senderAddress,
                                                    isFungibleToken,
                                                    ledgers,
                                                    encoder,
                                                    updater,
                                                    sigsVerifier,
                                                    sideEffectsTracker,
                                                    syntheticTxnFactory,
                                                    infrastructureFactory,
                                                    precompilePricingUtils,
                                                    functionId,
                                                    impliedTransfersMarshal));
                            case AbiConstants.ABI_ID_ERC_ALLOWANCE -> checkFeatureFlag(
                                    dynamicProperties.areAllowancesEnabled(),
                                    () ->
                                            new AllowancePrecompile(
                                                    tokenId,
                                                    syntheticTxnFactory,
                                                    ledgers,
                                                    encoder,
                                                    precompilePricingUtils));
                            case AbiConstants.ABI_ID_ERC_APPROVE -> checkFeatureFlag(
                                    dynamicProperties.areAllowancesEnabled(),
                                    () ->
                                            new ApprovePrecompile(
                                                    tokenId,
                                                    isFungibleToken,
                                                    ledgers,
                                                    encoder,
                                                    currentView,
                                                    sideEffectsTracker,
                                                    syntheticTxnFactory,
                                                    infrastructureFactory,
                                                    precompilePricingUtils,
                                                    senderAddress));
                            case AbiConstants.ABI_ID_ERC_SET_APPROVAL_FOR_ALL -> checkFeatureFlag(
                                    dynamicProperties.areAllowancesEnabled(),
                                    () ->
                                            new SetApprovalForAllPrecompile(
                                                    tokenId,
                                                    ledgers,
                                                    currentView,
                                                    sideEffectsTracker,
                                                    syntheticTxnFactory,
                                                    infrastructureFactory,
                                                    precompilePricingUtils,
                                                    senderAddress));
                            case AbiConstants.ABI_ID_ERC_GET_APPROVED -> checkFeatureFlag(
                                    dynamicProperties.areAllowancesEnabled(),
                                    () ->
                                            new GetApprovedPrecompile(
                                                    tokenId,
                                                    syntheticTxnFactory,
                                                    ledgers,
                                                    encoder,
                                                    precompilePricingUtils));
                            case AbiConstants.ABI_ID_ERC_IS_APPROVED_FOR_ALL -> checkFeatureFlag(
                                    dynamicProperties.areAllowancesEnabled(),
                                    () ->
                                            new IsApprovedForAllPrecompile(
                                                    tokenId,
                                                    syntheticTxnFactory,
                                                    ledgers,
                                                    encoder,
                                                    precompilePricingUtils));
                            default -> null;
                        };
                    }
                    case AbiConstants.ABI_ID_CREATE_FUNGIBLE_TOKEN,
                            AbiConstants.ABI_ID_CREATE_FUNGIBLE_TOKEN_WITH_FEES,
                            AbiConstants.ABI_ID_CREATE_NON_FUNGIBLE_TOKEN,
                            AbiConstants.ABI_ID_CREATE_NON_FUNGIBLE_TOKEN_WITH_FEES,
                            AbiConstants.ABI_ID_CREATE_FUNGIBLE_TOKEN_V2,
                            AbiConstants.ABI_ID_CREATE_FUNGIBLE_TOKEN_WITH_FEES_V2,
                            AbiConstants.ABI_ID_CREATE_NON_FUNGIBLE_TOKEN_V2,
                            AbiConstants
                                    .ABI_ID_CREATE_NON_FUNGIBLE_TOKEN_WITH_FEES_V2 -> (dynamicProperties
                                    .isHTSPrecompileCreateEnabled())
                            ? new TokenCreatePrecompile(
                                    ledgers,
                                    encoder,
                                    updater,
                                    sigsVerifier,
                                    recordsHistorian,
                                    sideEffectsTracker,
                                    syntheticTxnFactory,
                                    infrastructureFactory,
                                    functionId,
                                    senderAddress,
                                    dynamicProperties.fundingAccount(),
                                    feeCalculator,
                                    precompilePricingUtils)
                            : null;
                    case AbiConstants.ABI_ID_GET_TOKEN_INFO -> new TokenInfoPrecompile(
                            null,
                            syntheticTxnFactory,
                            ledgers,
                            encoder,
                            precompilePricingUtils,
                            currentView);
                    case AbiConstants
                            .ABI_ID_GET_FUNGIBLE_TOKEN_INFO -> new FungibleTokenInfoPrecompile(
                            null,
                            syntheticTxnFactory,
                            ledgers,
                            encoder,
                            precompilePricingUtils,
                            currentView);
                    case AbiConstants
                            .ABI_ID_GET_NON_FUNGIBLE_TOKEN_INFO -> new NonFungibleTokenInfoPrecompile(
                            null,
                            syntheticTxnFactory,
                            ledgers,
                            encoder,
                            precompilePricingUtils,
                            currentView);
                    case AbiConstants.ABI_ID_IS_TOKEN -> new IsTokenPrecompile(
                            null,
                            syntheticTxnFactory,
                            ledgers,
                            encoder,
                            precompilePricingUtils,
                            currentView);
                    case AbiConstants.ABI_ID_GET_TOKEN_TYPE -> new GetTokenTypePrecompile(
                            null,
                            syntheticTxnFactory,
                            ledgers,
                            encoder,
                            precompilePricingUtils,
                            currentView);
                    case AbiConstants
                            .ABI_ID_GET_TOKEN_CUSTOM_FEES -> new TokenGetCustomFeesPrecompile(
                            null,
                            syntheticTxnFactory,
                            ledgers,
                            encoder,
                            precompilePricingUtils,
                            currentView);
                    case AbiConstants
                            .ABI_ID_GET_TOKEN_EXPIRY_INFO -> new GetTokenExpiryInfoPrecompile(
                            null,
                            syntheticTxnFactory,
                            ledgers,
                            encoder,
                            precompilePricingUtils,
                            currentView);
                    case AbiConstants
                            .ABI_ID_UPDATE_TOKEN_EXPIRY_INFO -> new UpdateTokenExpiryInfoPrecompile(
                            ledgers,
                            updater.aliases(),
                            sigsVerifier,
                            sideEffectsTracker,
                            syntheticTxnFactory,
                            infrastructureFactory,
                            precompilePricingUtils);
                    case AbiConstants.ABI_ID_TRANSFER_FROM -> checkFeatureFlag(
                            dynamicProperties.areAllowancesEnabled(),
                            () ->
                                    new ERCTransferPrecompile(
                                            senderAddress,
                                            true,
                                            ledgers,
                                            encoder,
                                            updater,
                                            sigsVerifier,
                                            sideEffectsTracker,
                                            syntheticTxnFactory,
                                            infrastructureFactory,
                                            precompilePricingUtils,
                                            functionId,
                                            impliedTransfersMarshal));
                    case AbiConstants.ABI_ID_TRANSFER_FROM_NFT -> checkFeatureFlag(
                            dynamicProperties.areAllowancesEnabled(),
                            () ->
                                    new ERCTransferPrecompile(
                                            senderAddress,
                                            false,
                                            ledgers,
                                            encoder,
                                            updater,
                                            sigsVerifier,
                                            sideEffectsTracker,
                                            syntheticTxnFactory,
                                            infrastructureFactory,
                                            precompilePricingUtils,
                                            functionId,
                                            impliedTransfersMarshal));
                    default -> null;
                };
        if (precompile != null) {
            decodeInput(input, aliasResolver);
        }
    }

    /* --- Helpers --- */
    void decodeInput(final Bytes input, final UnaryOperator<byte[]> aliasResolver) {
        this.transactionBody = TransactionBody.newBuilder();
        try {
            this.transactionBody = this.precompile.body(input, aliasResolver);
        } catch (Exception e) {
            log.warn("Internal precompile failure", e);
            transactionBody = null;
        }
    }

    private Precompile checkNFT(boolean isFungible, Supplier<Precompile> precompileSupplier) {
        if (isFungible) {
            throw new InvalidTransactionException(
                    NOT_SUPPORTED_FUNGIBLE_OPERATION_REASON, INVALID_TOKEN_ID);
        } else {
            return precompileSupplier.get();
        }
    }

    private Precompile checkFungible(boolean isFungible, Supplier<Precompile> precompileSupplier) {
        if (!isFungible) {
            throw new InvalidTransactionException(
                    NOT_SUPPORTED_NON_FUNGIBLE_OPERATION_REASON, INVALID_TOKEN_ID);
        } else {
            return precompileSupplier.get();
        }
    }

    private Precompile checkFeatureFlag(
            boolean featureFlag, Supplier<Precompile> precompileSupplier) {
        if (!featureFlag) {
            throw new InvalidTransactionException(NOT_SUPPORTED);
        } else {
            return precompileSupplier.get();
        }
    }

    @SuppressWarnings("rawtypes")
    protected Bytes computeInternal(final MessageFrame frame) {
        Bytes result;
        ExpirableTxnRecord.Builder childRecord;
        try {
            validateTrue(frame.getRemainingGas() >= gasRequirement, INSUFFICIENT_GAS);

            precompile.handleSentHbars(frame);
            precompile.customizeTrackingLedgers(ledgers);
            precompile.run(frame);

            // As in HederaLedger.commit(), we must first commit the ledgers before creating our
            // synthetic record, as the ledger interceptors will populate the sideEffectsTracker
            ledgers.commit();

            childRecord =
                    creator.createSuccessfulSyntheticRecord(
                            precompile.getCustomFees(), sideEffectsTracker, EMPTY_MEMO);
            result = precompile.getSuccessResultFor(childRecord);
            addContractCallResultToRecord(childRecord, result, Optional.empty(), frame);
        } catch (final InvalidTransactionException e) {
            final var status = e.getResponseCode();
            childRecord = creator.createUnsuccessfulSyntheticRecord(status);
            result = precompile.getFailureResultFor(status);
            addContractCallResultToRecord(childRecord, result, Optional.of(status), frame);
            if (e.isReverting()) {
                frame.setState(MessageFrame.State.REVERT);
                frame.setRevertReason(e.getRevertReason());
            }
        } catch (final Exception e) {
            log.warn("Internal precompile failure", e);
            childRecord = creator.createUnsuccessfulSyntheticRecord(FAIL_INVALID);
            result = precompile.getFailureResultFor(FAIL_INVALID);
            addContractCallResultToRecord(childRecord, result, Optional.of(FAIL_INVALID), frame);
        }

        // This should always have a parent stacked updater
        final var parentUpdater = updater.parentUpdater();
        if (parentUpdater.isPresent()) {
            final var parent = (AbstractLedgerWorldUpdater) parentUpdater.get();
            parent.manageInProgressRecord(recordsHistorian, childRecord, this.transactionBody);
        } else {
            throw new InvalidTransactionException(
                    "HTS precompile frame had no parent updater", FAIL_INVALID);
        }

        return result;
    }

    private void addContractCallResultToRecord(
            final ExpirableTxnRecord.Builder childRecord,
            final Bytes result,
            final Optional<ResponseCodeEnum> errorStatus,
            final MessageFrame messageFrame) {
        if (dynamicProperties.shouldExportPrecompileResults()) {
            PrecompileUtils.addContractCallResultToRecord(
                    this.gasRequirement,
                    childRecord,
                    result,
                    errorStatus,
                    messageFrame,
                    dynamicProperties.shouldExportPrecompileResults(),
                    precompile.shouldAddTraceabilityFieldsToRecord(),
                    senderAddress);
        }
    }

    private long defaultGas() {
        return dynamicProperties.htsDefaultGasCost();
    }

    @VisibleForTesting
    public Precompile getPrecompile() {
        return precompile;
    }
}
