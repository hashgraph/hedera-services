/*
 * Copyright (C) 2022-2024 Hedera Hashgraph, LLC
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

package com.hedera.node.app.service.mono.store.contracts.precompile.impl;

import static com.hedera.node.app.hapi.utils.contracts.ParsingConstants.INT;
import static com.hedera.node.app.service.evm.exceptions.InvalidTransactionException.*;
import static com.hedera.node.app.service.evm.utils.ValidationUtils.validateTrue;
import static com.hedera.node.app.service.evm.utils.ValidationUtils.validateTrueOrRevert;
import static com.hedera.node.app.service.mono.contracts.ContractsModule.SYSTEM_ACCOUNT_BOUNDARY;
import static com.hedera.node.app.service.mono.grpc.marshalling.ImpliedTransfers.NO_ALIASES;
import static com.hedera.node.app.service.mono.store.contracts.precompile.codec.DecodingFacade.NO_FUNGIBLE_TRANSFERS;
import static com.hedera.node.app.service.mono.store.contracts.precompile.codec.DecodingFacade.NO_NFT_EXCHANGES;
import static com.hedera.node.app.service.mono.store.contracts.precompile.codec.DecodingFacade.bindFungibleTransfersFrom;
import static com.hedera.node.app.service.mono.store.contracts.precompile.codec.DecodingFacade.bindHBarTransfersFrom;
import static com.hedera.node.app.service.mono.store.contracts.precompile.codec.DecodingFacade.bindNftExchangesFrom;
import static com.hedera.node.app.service.mono.store.contracts.precompile.codec.DecodingFacade.convertAddressBytesToTokenID;
import static com.hedera.node.app.service.mono.store.contracts.precompile.codec.DecodingFacade.convertLeftPaddedAddressToAccountId;
import static com.hedera.node.app.service.mono.store.contracts.precompile.codec.DecodingFacade.decodeAccountIds;
import static com.hedera.node.app.service.mono.store.contracts.precompile.codec.DecodingFacade.decodeFunctionCall;
import static com.hedera.node.app.service.mono.store.contracts.precompile.codec.DecodingFacade.generateAccountIDWithAliasCalculatedFrom;
import static com.hedera.node.app.service.mono.txns.span.SpanMapManager.reCalculateXferMeta;
import static com.hedera.node.app.service.mono.utils.EntityIdUtils.asTypedEvmAddress;
import static com.hederahashgraph.api.proto.java.HederaFunctionality.CryptoTransfer;
import static com.hederahashgraph.api.proto.java.ResponseCodeEnum.INVALID_ALIAS_KEY;
import static com.hederahashgraph.api.proto.java.ResponseCodeEnum.INVALID_FULL_PREFIX_SIGNATURE_FOR_PRECOMPILE;
import static com.hederahashgraph.api.proto.java.ResponseCodeEnum.INVALID_RECEIVING_NODE_ACCOUNT;
import static com.hederahashgraph.api.proto.java.ResponseCodeEnum.NOT_SUPPORTED;
import static com.hederahashgraph.api.proto.java.ResponseCodeEnum.OK;

import com.esaulpaugh.headlong.abi.ABIType;
import com.esaulpaugh.headlong.abi.Function;
import com.esaulpaugh.headlong.abi.Tuple;
import com.esaulpaugh.headlong.abi.TypeFactory;
import com.google.common.annotations.VisibleForTesting;
import com.google.protobuf.ByteString;
import com.hedera.node.app.service.evm.exceptions.InvalidTransactionException;
import com.hedera.node.app.service.mono.context.SideEffectsTracker;
import com.hedera.node.app.service.mono.context.properties.CustomFeeType;
import com.hedera.node.app.service.mono.contracts.sources.EvmSigsVerifier;
import com.hedera.node.app.service.mono.grpc.marshalling.ImpliedTransfers;
import com.hedera.node.app.service.mono.grpc.marshalling.ImpliedTransfersMarshal;
import com.hedera.node.app.service.mono.ledger.BalanceChange;
import com.hedera.node.app.service.mono.state.submerkle.FcAssessedCustomFee;
import com.hedera.node.app.service.mono.store.contracts.HederaStackedWorldStateUpdater;
import com.hedera.node.app.service.mono.store.contracts.WorldLedgers;
import com.hedera.node.app.service.mono.store.contracts.precompile.AbiConstants;
import com.hedera.node.app.service.mono.store.contracts.precompile.InfrastructureFactory;
import com.hedera.node.app.service.mono.store.contracts.precompile.SyntheticTxnFactory;
import com.hedera.node.app.service.mono.store.contracts.precompile.codec.CryptoTransferWrapper;
import com.hedera.node.app.service.mono.store.contracts.precompile.codec.DecodingFacade;
import com.hedera.node.app.service.mono.store.contracts.precompile.codec.TokenTransferWrapper;
import com.hedera.node.app.service.mono.store.contracts.precompile.codec.TransferWrapper;
import com.hedera.node.app.service.mono.store.contracts.precompile.utils.KeyActivationUtils;
import com.hedera.node.app.service.mono.store.contracts.precompile.utils.PrecompilePricingUtils;
import com.hedera.node.app.service.mono.store.contracts.precompile.utils.PrecompilePricingUtils.GasCostType;
import com.hedera.node.app.service.mono.store.models.Id;
import com.hedera.node.app.service.mono.store.tokens.HederaTokenStore;
import com.hedera.node.app.service.mono.txns.crypto.AbstractAutoCreationLogic;
import com.hedera.node.app.service.mono.utils.EntityIdUtils;
import com.hedera.node.app.service.mono.utils.EntityNum;
import com.hedera.node.app.service.mono.utils.accessors.TxnAccessor;
import com.hederahashgraph.api.proto.java.AccountAmount;
import com.hederahashgraph.api.proto.java.AccountID;
import com.hederahashgraph.api.proto.java.CryptoTransferTransactionBody;
import com.hederahashgraph.api.proto.java.ResponseCodeEnum;
import com.hederahashgraph.api.proto.java.Timestamp;
import com.hederahashgraph.api.proto.java.TokenID;
import com.hederahashgraph.api.proto.java.TokenTransferList;
import com.hederahashgraph.api.proto.java.TransactionBody;
import edu.umd.cs.findbugs.annotations.NonNull;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.function.Predicate;
import java.util.function.UnaryOperator;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.apache.tuweni.bytes.Bytes;
import org.hyperledger.besu.datatypes.Address;
import org.hyperledger.besu.evm.frame.MessageFrame;

public class TransferPrecompile extends AbstractWritePrecompile {
    private static final Logger log = LogManager.getLogger(TransferPrecompile.class);
    private static final Function CRYPTO_TRANSFER_FUNCTION =
            new Function("cryptoTransfer((address,(address,int64)[],(address,address,int64)[])[])", INT);
    private static final Function CRYPTO_TRANSFER_FUNCTION_V2 = new Function(
            "cryptoTransfer(((address,int64,bool)[]),(address,(address,int64,bool)[],(address,address,int64,"
                    + "bool)[])[])",
            INT);
    private static final Bytes CRYPTO_TRANSFER_SELECTOR = Bytes.wrap(CRYPTO_TRANSFER_FUNCTION.selector());
    private static final Bytes CRYPTO_TRANSFER_SELECTOR_V2 = Bytes.wrap(CRYPTO_TRANSFER_FUNCTION_V2.selector());
    private static final ABIType<Tuple> CRYPTO_TRANSFER_DECODER =
            TypeFactory.create("((bytes32,(bytes32,int64)[],(bytes32,bytes32,int64)[])[])");
    private static final ABIType<Tuple> CRYPTO_TRANSFER_DECODER_V2 = TypeFactory.create(
            "(((bytes32,int64,bool)[]),(bytes32,(bytes32,int64,bool)[],(bytes32,bytes32,int64,bool)[])[])");
    private static final Function TRANSFER_TOKENS_FUNCTION =
            new Function("transferTokens(address,address[],int64[])", INT);
    private static final Bytes TRANSFER_TOKENS_SELECTOR = Bytes.wrap(TRANSFER_TOKENS_FUNCTION.selector());
    private static final ABIType<Tuple> TRANSFER_TOKENS_DECODER = TypeFactory.create("(bytes32,bytes32[],int64[])");
    private static final Function TRANSFER_TOKEN_FUNCTION =
            new Function("transferToken(address,address,address,int64)", INT);
    private static final Bytes TRANSFER_TOKEN_SELECTOR = Bytes.wrap(TRANSFER_TOKEN_FUNCTION.selector());
    private static final ABIType<Tuple> TRANSFER_TOKEN_DECODER = TypeFactory.create("(bytes32,bytes32,bytes32,int64)");
    private static final Function TRANSFER_NFTS_FUNCTION =
            new Function("transferNFTs(address,address[],address[],int64[])", INT);
    private static final Bytes TRANSFER_NFTS_SELECTOR = Bytes.wrap(TRANSFER_NFTS_FUNCTION.selector());
    private static final ABIType<Tuple> TRANSFER_NFTS_DECODER =
            TypeFactory.create("(bytes32,bytes32[],bytes32[],int64[])");
    private static final Function TRANSFER_NFT_FUNCTION =
            new Function("transferNFT(address,address,address,int64)", INT);
    private static final Bytes TRANSFER_NFT_SELECTOR = Bytes.wrap(TRANSFER_NFT_FUNCTION.selector());
    private static final ABIType<Tuple> TRANSFER_NFT_DECODER = TypeFactory.create("(bytes32,bytes32,bytes32,int64)");
    private static final String TRANSFER = String.format(FAILURE_MESSAGE, "transfer");
    private static final String CHANGE_SWITCHED_TO_APPROVAL_WITHOUT_MATCHING_ADJUSTMENT_IN =
            "Change {} switched to approval without matching adjustment in {}";
    private static final String CHANGE_SWITCHED_TO_APPROVAL_MATCHED_CREDIT_IN =
            "Change {} switched to approval but matched credit in {}";
    private final HederaStackedWorldStateUpdater updater;
    private final EvmSigsVerifier sigsVerifier;
    private final int functionId;
    private final Address senderAddress;
    private final ImpliedTransfersMarshal impliedTransfersMarshal;
    private final boolean isLazyCreationEnabled;
    private final boolean topLevelSigsAreEnabled;
    private ResponseCodeEnum impliedValidity;
    private ImpliedTransfers impliedTransfers;
    private HederaTokenStore hederaTokenStore;
    protected CryptoTransferWrapper transferOp;
    private AbstractAutoCreationLogic autoCreationLogic;
    private int numLazyCreates;
    private Set<CustomFeeType> htsUnsupportedCustomReceiverDebits;

    // Non-final for testing purposes
    private boolean canFallbackToApprovals;

    public TransferPrecompile(
            final WorldLedgers ledgers,
            final HederaStackedWorldStateUpdater updater,
            final EvmSigsVerifier sigsVerifier,
            final SideEffectsTracker sideEffects,
            final SyntheticTxnFactory syntheticTxnFactory,
            final InfrastructureFactory infrastructureFactory,
            final PrecompilePricingUtils pricingUtils,
            final int functionId,
            final Address senderAddress,
            final Set<CustomFeeType> htsUnsupportedCustomFeeReceiverDebits,
            final boolean isLazyCreationEnabled,
            final boolean topLevelSigsAreEnabled,
            final boolean canFallbackToApprovals) {
        super(ledgers, sideEffects, syntheticTxnFactory, infrastructureFactory, pricingUtils);
        this.updater = updater;
        this.sigsVerifier = sigsVerifier;
        this.functionId = functionId;
        this.senderAddress = senderAddress;
        this.impliedTransfersMarshal = infrastructureFactory.newImpliedTransfersMarshal(ledgers.customFeeSchedules());
        this.htsUnsupportedCustomReceiverDebits = htsUnsupportedCustomFeeReceiverDebits;
        this.isLazyCreationEnabled = isLazyCreationEnabled;
        this.topLevelSigsAreEnabled = topLevelSigsAreEnabled;
        this.canFallbackToApprovals = canFallbackToApprovals;
    }

    protected void initializeHederaTokenStore() {
        hederaTokenStore = infrastructureFactory.newHederaTokenStore(
                sideEffects, ledgers.tokens(), ledgers.nfts(), ledgers.tokenRels());
    }

    @Override
    public void customizeTrackingLedgers(final WorldLedgers worldLedgers) {
        worldLedgers.customizeForAutoAssociatingOp(sideEffects);
    }

    @Override
    public TransactionBody.Builder body(final Bytes input, final UnaryOperator<byte[]> aliasResolver) {

        transferOp = switch (functionId) {
            case AbiConstants.ABI_ID_CRYPTO_TRANSFER -> decodeCryptoTransfer(
                    input, aliasResolver, ledgers.accounts()::contains);
            case AbiConstants.ABI_ID_CRYPTO_TRANSFER_V2 -> decodeCryptoTransferV2(
                    input, aliasResolver, ledgers.accounts()::contains);
            case AbiConstants.ABI_ID_TRANSFER_TOKENS -> decodeTransferTokens(
                    input, aliasResolver, ledgers.accounts()::contains);
            case AbiConstants.ABI_ID_TRANSFER_TOKEN -> decodeTransferToken(
                    input, aliasResolver, ledgers.accounts()::contains);
            case AbiConstants.ABI_ID_TRANSFER_NFTS -> decodeTransferNFTs(
                    input, aliasResolver, ledgers.accounts()::contains);
            case AbiConstants.ABI_ID_TRANSFER_NFT -> decodeTransferNFT(
                    input, aliasResolver, ledgers.accounts()::contains);
            default -> null;};
        Objects.requireNonNull(transferOp, "Unable to decode function input");

        transactionBody = syntheticTxnFactory.createCryptoTransfer(transferOp.tokenTransferWrappers());
        if (!transferOp.transferWrapper().hbarTransfers().isEmpty()) {
            transactionBody.mergeFrom(syntheticTxnFactory.createCryptoTransferForHbar(transferOp.transferWrapper()));
        }

        extrapolateDetailsFromSyntheticTxn();

        initializeHederaTokenStore();
        return transactionBody;
    }

    @Override
    public void addImplicitCostsIn(final TxnAccessor accessor) {
        if (impliedTransfers != null) {
            reCalculateXferMeta(accessor, impliedTransfers);
        }
    }

    @Override
    public void run(final MessageFrame frame) {
        if (impliedValidity == null) {
            extrapolateDetailsFromSyntheticTxn();
        }
        if (impliedValidity != ResponseCodeEnum.OK) {
            throw new InvalidTransactionException(impliedValidity);
        }

        final var assessmentStatus = impliedTransfers.getMeta().code();
        validateTrue(assessmentStatus == OK, assessmentStatus);
        final var changes = impliedTransfers.getAllBalanceChanges();

        hederaTokenStore.setAccountsLedger(ledgers.accounts());

        final boolean allowFixedCustomFeeTransfers =
                !htsUnsupportedCustomReceiverDebits.contains(CustomFeeType.FIXED_FEE);
        final boolean allowRoyaltyFallbackCustomFeeTransfers =
                !htsUnsupportedCustomReceiverDebits.contains(CustomFeeType.ROYALTY_FALLBACK_FEE);

        final var transferLogic = infrastructureFactory.newTransferLogic(
                hederaTokenStore, sideEffects, ledgers.nfts(), ledgers.accounts(), ledgers.tokenRels());

        final Map<ByteString, EntityNum> completedLazyCreates = new HashMap<>();
        for (int i = 0, n = changes.size(); i < n; i++) {
            final var change = changes.get(i);

            final var units = change.getAggregatedUnits();
            final var isDebit = units < 0;
            final var isCredit = units > 0;

            if (change.hasAlias()) {
                replaceAliasWithId(change, changes, completedLazyCreates);
            }

            // Checks whether the balance modification targets the receiver account (i.e. credit operation).
            if (isCredit && !change.isForCustomFee()) {
                revertIfReceiverIsSystemAccount(change);
            }

            if (isDebit && change.isForCustomFee()) {
                if (change.includesFallbackFee())
                    validateTrue(allowRoyaltyFallbackCustomFeeTransfers, NOT_SUPPORTED, "royalty fee");
                else validateTrue(allowFixedCustomFeeTransfers, NOT_SUPPORTED, "fixed fee");
            }

            if (change.isForNft() || isDebit) {
                // The receiver signature is enforced for a transfer of NFT with a royalty fallback
                // fee
                final var isForNonFallbackRoyaltyFee = change.isForCustomFee() && !change.includesFallbackFee();
                if (change.isApprovedAllowance() || isForNonFallbackRoyaltyFee) {
                    // Signing requirements are skipped for changes to be authorized via an
                    // allowance
                    continue;
                }
                final var senderHasSignedOrIsMsgSenderInFrame = KeyActivationUtils.validateKey(
                        frame,
                        change.getAccount().asEvmAddress(),
                        sigsVerifier::hasActiveKey,
                        ledgers,
                        updater.aliases(),
                        CryptoTransfer);
                // If the transfer doesn't have explicit sender authorization via msg.sender,
                // AND cannot use top-level signatures, then even if it did not claim to have
                // an approval, let it try to succeed via an approval IF this precompile is
                // one of the HAPI-style transfer precompiles
                if (!senderHasSignedOrIsMsgSenderInFrame && !topLevelSigsAreEnabled && canFallbackToApprovals) {
                    change.switchToApproved();
                    updateSynthOpForAutoApprovalOf(transactionBody.getCryptoTransferBuilder(), change);
                    continue;
                } else {
                    validateTrue(
                            senderHasSignedOrIsMsgSenderInFrame,
                            INVALID_FULL_PREFIX_SIGNATURE_FOR_PRECOMPILE,
                            TRANSFER);
                }
            }
            if (!change.isForCustomFee()) {
                /* Only process receiver sig requirements for that are not custom fee payments (custom fees are never
                NFT exchanges) */
                var hasReceiverSigIfReq = true;
                if (change.isForNft()) {
                    final var counterPartyAddress = asTypedEvmAddress(change.counterPartyAccountId());
                    hasReceiverSigIfReq = KeyActivationUtils.validateKey(
                            frame,
                            counterPartyAddress,
                            sigsVerifier::hasActiveKeyOrNoReceiverSigReq,
                            ledgers,
                            updater.aliases(),
                            CryptoTransfer);
                } else if (isCredit) {
                    hasReceiverSigIfReq = KeyActivationUtils.validateKey(
                            frame,
                            change.getAccount().asEvmAddress(),
                            sigsVerifier::hasActiveKeyOrNoReceiverSigReq,
                            ledgers,
                            updater.aliases(),
                            CryptoTransfer);
                }
                validateTrue(hasReceiverSigIfReq, INVALID_FULL_PREFIX_SIGNATURE_FOR_PRECOMPILE, TRANSFER);
            }
        }

        // track auto-creation child records if needed
        if (autoCreationLogic != null) {
            autoCreationLogic.submitRecords(infrastructureFactory.newRecordSubmissionsScopedTo(updater));
        }

        transferLogic.doZeroSum(changes);
    }

    private void replaceAliasWithId(
            final BalanceChange change,
            final List<BalanceChange> changes,
            final Map<ByteString, EntityNum> completedLazyCreates) {
        final var receiverAlias = change.getNonEmptyAliasIfPresent();
        final var isMirrorAddress = updater.aliases().isMirror(Address.wrap(Bytes.of(receiverAlias.toByteArray())));
        if (isMirrorAddress) {
            final var receiverAddress =
                    updater.aliases().resolveForEvm(Address.wrap(Bytes.of(receiverAlias.toByteArray())));
            final var addrToPass = receiverAddress == null ? Address.ZERO : receiverAddress;
            final var receiverAddressNum = EntityNum.fromEvmAddress(addrToPass).intValue();
            validateTrueOrRevert(receiverAddressNum >= SYSTEM_ACCOUNT_BOUNDARY, INVALID_RECEIVING_NODE_ACCOUNT);
        }
        validateTrueOrRevert(!isMirrorAddress, INVALID_ALIAS_KEY);
        if (completedLazyCreates.containsKey(receiverAlias)) {
            change.replaceNonEmptyAliasWith(completedLazyCreates.get(receiverAlias));
        } else {
            if (autoCreationLogic == null) {
                autoCreationLogic = infrastructureFactory.newAutoCreationLogicScopedTo(updater);
            }
            final var lazyCreateResult = autoCreationLogic.create(change, ledgers.accounts(), changes);
            validateTrue(lazyCreateResult.getLeft() == OK, lazyCreateResult.getLeft());
            completedLazyCreates.put(
                    receiverAlias,
                    EntityNum.fromAccountId(
                            change.counterPartyAccountId() == null
                                    ? change.accountId()
                                    : change.counterPartyAccountId()));
        }
    }

    @Override
    public List<FcAssessedCustomFee> getCustomFees() {
        return impliedTransfers.getUnaliasedAssessedCustomFees();
    }

    protected void extrapolateDetailsFromSyntheticTxn() {
        Objects.requireNonNull(
                transferOp, "`body` method should be called before `extrapolateDetailsFromSyntheticTxn`");

        final var op = transactionBody.getCryptoTransfer();
        impliedValidity = impliedTransfersMarshal.validityWithCurrentProps(op);
        if (impliedValidity != ResponseCodeEnum.OK) {
            return;
        }
        final var explicitChanges = constructBalanceChanges();
        if (numLazyCreates > 0 && !isLazyCreationEnabled) {
            impliedValidity = NOT_SUPPORTED;
            return;
        }
        final var hbarOnly = transferOp.transferWrapper().hbarTransfers().size();
        impliedTransfers = impliedTransfersMarshal.assessCustomFeesAndValidate(
                hbarOnly, 0, numLazyCreates, explicitChanges, NO_ALIASES, impliedTransfersMarshal.currentProps());
    }

    @Override
    public long getMinimumFeeInTinybars(final Timestamp consensusTime) {
        Objects.requireNonNull(transferOp, "`body` method should be called before `getMinimumFeeInTinybars`");
        long accumulatedCost = 0;
        final boolean customFees = impliedTransfers != null && impliedTransfers.hasAssessedCustomFees();
        // For fungible there are always at least two operations, so only charge half for each
        // operation
        final long ftTxCost = pricingUtils.getMinimumPriceInTinybars(
                        customFees
                                ? PrecompilePricingUtils.GasCostType.TRANSFER_FUNGIBLE_CUSTOM_FEES
                                : PrecompilePricingUtils.GasCostType.TRANSFER_FUNGIBLE,
                        consensusTime)
                / 2;
        // NFTs are atomic, one line can do it.
        final long nonFungibleTxCost = pricingUtils.getMinimumPriceInTinybars(
                customFees
                        ? PrecompilePricingUtils.GasCostType.TRANSFER_NFT_CUSTOM_FEES
                        : PrecompilePricingUtils.GasCostType.TRANSFER_NFT,
                consensusTime);
        for (final var transfer : transferOp.tokenTransferWrappers()) {
            accumulatedCost += transfer.fungibleTransfers().size() * ftTxCost;
            accumulatedCost += transfer.nftExchanges().size() * nonFungibleTxCost;
        }

        // add the cost for transferring hbars
        // Hbar transfer is similar to fungible tokens so only charge half for each operation
        final long hbarTxCost =
                pricingUtils.getMinimumPriceInTinybars(PrecompilePricingUtils.GasCostType.TRANSFER_HBAR, consensusTime)
                        / 2;
        accumulatedCost += transferOp.transferWrapper().hbarTransfers().size() * hbarTxCost;
        if (isLazyCreationEnabled && numLazyCreates > 0) {
            final var lazyCreationFee = pricingUtils.getMinimumPriceInTinybars(GasCostType.CRYPTO_CREATE, consensusTime)
                    + pricingUtils.getMinimumPriceInTinybars(GasCostType.CRYPTO_UPDATE, consensusTime);
            accumulatedCost += numLazyCreates * lazyCreationFee;
        }
        return accumulatedCost;
    }

    /**
     * Decodes the given bytes of the cryptoTransfer function parameters
     *
     * <p><b>Important: </b>This is an old version of this method and is superseded by
     * decodeCryptoTransferV2(). The selector for this function is derived from:
     * cryptoTransfer((address,(address,int64)[],(address,address,int64)[])[])
     *
     * @param input encoded bytes containing selector and input parameters
     * @param aliasResolver function used to resolve aliases
     * @return CryptoTransferWrapper codec
     */
    public static CryptoTransferWrapper decodeCryptoTransfer(
            final Bytes input, final UnaryOperator<byte[]> aliasResolver, final Predicate<AccountID> exists) {
        final List<SyntheticTxnFactory.HbarTransfer> hbarTransfers = Collections.emptyList();
        final Tuple decodedTuples = decodeFunctionCall(input, CRYPTO_TRANSFER_SELECTOR, CRYPTO_TRANSFER_DECODER);

        final List<TokenTransferWrapper> tokenTransferWrappers = new ArrayList<>();

        for (final var tuple : decodedTuples) {
            decodeTokenTransfer(aliasResolver, exists, tokenTransferWrappers, (Tuple[]) tuple);
        }

        return new CryptoTransferWrapper(new TransferWrapper(hbarTransfers), tokenTransferWrappers);
    }

    /**
     * Decodes the given bytes of the cryptoTransfer function parameters
     *
     * <p><b>Important: </b>This is the latest version and supersedes public static
     * CryptoTransferWrapper decodeCryptoTransfer(). The selector for this function is derived from:
     * cryptoTransfer(((address,int64,bool)[]),(address,(address,int64,bool)[],(address,address,int64,bool)[])[])
     * The first parameter describes hbar transfers and the second describes token transfers
     *
     * @param input encoded bytes containing selector and input parameters
     * @param aliasResolver function used to resolve aliases
     * @return CryptoTransferWrapper codec
     */
    public static CryptoTransferWrapper decodeCryptoTransferV2(
            final Bytes input, final UnaryOperator<byte[]> aliasResolver, final Predicate<AccountID> exists) {
        final Tuple decodedTuples = decodeFunctionCall(input, CRYPTO_TRANSFER_SELECTOR_V2, CRYPTO_TRANSFER_DECODER_V2);
        List<SyntheticTxnFactory.HbarTransfer> hbarTransfers = new ArrayList<>();
        final List<TokenTransferWrapper> tokenTransferWrappers = new ArrayList<>();

        final Tuple[] hbarTransferTuples = ((Tuple) decodedTuples.get(0)).get(0);
        final var tokenTransferTuples = decodedTuples.get(1);

        hbarTransfers = decodeHbarTransfers(aliasResolver, hbarTransfers, hbarTransferTuples, exists);

        decodeTokenTransfer(aliasResolver, exists, tokenTransferWrappers, (Tuple[]) tokenTransferTuples);

        return new CryptoTransferWrapper(new TransferWrapper(hbarTransfers), tokenTransferWrappers);
    }

    public static List<SyntheticTxnFactory.HbarTransfer> decodeHbarTransfers(
            final UnaryOperator<byte[]> aliasResolver,
            List<SyntheticTxnFactory.HbarTransfer> hbarTransfers,
            final Tuple[] hbarTransferTuples,
            final Predicate<AccountID> exists) {
        if (hbarTransferTuples.length > 0) {
            hbarTransfers = bindHBarTransfersFrom(hbarTransferTuples, aliasResolver, exists);
        }
        return hbarTransfers;
    }

    public static void decodeTokenTransfer(
            final UnaryOperator<byte[]> aliasResolver,
            final Predicate<AccountID> exists,
            final List<TokenTransferWrapper> tokenTransferWrappers,
            final Tuple[] tokenTransferTuples) {
        for (final var tupleNested : tokenTransferTuples) {
            final var tokenType = convertAddressBytesToTokenID(tupleNested.get(0));

            var nftExchanges = NO_NFT_EXCHANGES;
            var fungibleTransfers = NO_FUNGIBLE_TRANSFERS;

            final var abiAdjustments = (Tuple[]) tupleNested.get(1);
            if (abiAdjustments.length > 0) {
                fungibleTransfers = bindFungibleTransfersFrom(tokenType, abiAdjustments, aliasResolver, exists);
            }
            final var abiNftExchanges = (Tuple[]) tupleNested.get(2);
            if (abiNftExchanges.length > 0) {
                nftExchanges = bindNftExchangesFrom(tokenType, abiNftExchanges, aliasResolver, exists);
            }

            tokenTransferWrappers.add(new TokenTransferWrapper(nftExchanges, fungibleTransfers));
        }
    }

    public static CryptoTransferWrapper decodeTransferTokens(
            final Bytes input, final UnaryOperator<byte[]> aliasResolver, final Predicate<AccountID> exists) {
        final List<SyntheticTxnFactory.HbarTransfer> hbarTransfers = Collections.emptyList();
        final Tuple decodedArguments = decodeFunctionCall(input, TRANSFER_TOKENS_SELECTOR, TRANSFER_TOKENS_DECODER);

        final var tokenType = convertAddressBytesToTokenID(decodedArguments.get(0));
        final var accountIDs = decodeAccountIds(decodedArguments.get(1), aliasResolver);
        final var amounts = (long[]) decodedArguments.get(2);

        final List<SyntheticTxnFactory.FungibleTokenTransfer> fungibleTransfers = new ArrayList<>();
        addSignedAdjustments(fungibleTransfers, accountIDs, exists, tokenType, amounts);

        final var tokenTransferWrappers =
                Collections.singletonList(new TokenTransferWrapper(NO_NFT_EXCHANGES, fungibleTransfers));

        return new CryptoTransferWrapper(new TransferWrapper(hbarTransfers), tokenTransferWrappers);
    }

    public static void addSignedAdjustments(
            final List<SyntheticTxnFactory.FungibleTokenTransfer> fungibleTransfers,
            final List<AccountID> accountIDs,
            final Predicate<AccountID> exists,
            final TokenID tokenType,
            final long[] amounts) {
        for (int i = 0; i < accountIDs.size(); i++) {
            final var amount = amounts[i];

            var accountID = accountIDs.get(i);
            if (amount > 0 && !exists.test(accountID) && !accountID.hasAlias()) {
                accountID = generateAccountIDWithAliasCalculatedFrom(accountID);
            }

            DecodingFacade.addSignedAdjustment(fungibleTransfers, tokenType, accountID, amount, false, false);
        }
    }

    public static CryptoTransferWrapper decodeTransferToken(
            final Bytes input, final UnaryOperator<byte[]> aliasResolver, final Predicate<AccountID> exists) {
        final List<SyntheticTxnFactory.HbarTransfer> hbarTransfers = Collections.emptyList();
        final Tuple decodedArguments = decodeFunctionCall(input, TRANSFER_TOKEN_SELECTOR, TRANSFER_TOKEN_DECODER);

        final var tokenID = convertAddressBytesToTokenID(decodedArguments.get(0));
        final var sender = convertLeftPaddedAddressToAccountId(decodedArguments.get(1), aliasResolver);
        final var receiver = convertLeftPaddedAddressToAccountId(decodedArguments.get(2), aliasResolver, exists);
        final var amount = (long) decodedArguments.get(3);

        if (amount < 0) {
            throw new IllegalArgumentException("Amount must be non-negative");
        }

        final var tokenTransferWrappers = Collections.singletonList(new TokenTransferWrapper(
                NO_NFT_EXCHANGES,
                List.of(new SyntheticTxnFactory.FungibleTokenTransfer(amount, false, tokenID, sender, receiver))));
        return new CryptoTransferWrapper(new TransferWrapper(hbarTransfers), tokenTransferWrappers);
    }

    public static CryptoTransferWrapper decodeTransferNFTs(
            final Bytes input, final UnaryOperator<byte[]> aliasResolver, final Predicate<AccountID> exists) {
        final List<SyntheticTxnFactory.HbarTransfer> hbarTransfers = Collections.emptyList();
        final Tuple decodedArguments = decodeFunctionCall(input, TRANSFER_NFTS_SELECTOR, TRANSFER_NFTS_DECODER);

        final var tokenID = convertAddressBytesToTokenID(decodedArguments.get(0));
        final var senders = decodeAccountIds(decodedArguments.get(1), aliasResolver);
        final var receivers = decodeAccountIds(decodedArguments.get(2), aliasResolver);
        final var serialNumbers = ((long[]) decodedArguments.get(3));

        final List<SyntheticTxnFactory.NftExchange> nftExchanges = new ArrayList<>();
        addNftExchanges(nftExchanges, senders, receivers, serialNumbers, tokenID, exists);

        final var tokenTransferWrappers =
                Collections.singletonList(new TokenTransferWrapper(nftExchanges, NO_FUNGIBLE_TRANSFERS));
        return new CryptoTransferWrapper(new TransferWrapper(hbarTransfers), tokenTransferWrappers);
    }

    public static void addNftExchanges(
            final List<SyntheticTxnFactory.NftExchange> nftExchanges,
            final List<AccountID> senders,
            final List<AccountID> receivers,
            final long[] serialNumbers,
            final TokenID tokenID,
            final Predicate<AccountID> exists) {
        for (var i = 0; i < senders.size(); i++) {
            var receiver = receivers.get(i);
            if (!exists.test(receiver) && !receiver.hasAlias()) {
                receiver = generateAccountIDWithAliasCalculatedFrom(receiver);
            }
            final var nftExchange =
                    new SyntheticTxnFactory.NftExchange(serialNumbers[i], tokenID, senders.get(i), receiver);
            nftExchanges.add(nftExchange);
        }
    }

    public static CryptoTransferWrapper decodeTransferNFT(
            final Bytes input, final UnaryOperator<byte[]> aliasResolver, final Predicate<AccountID> exists) {
        final List<SyntheticTxnFactory.HbarTransfer> hbarTransfers = Collections.emptyList();
        final Tuple decodedArguments = decodeFunctionCall(input, TRANSFER_NFT_SELECTOR, TRANSFER_NFT_DECODER);

        final var tokenID = convertAddressBytesToTokenID(decodedArguments.get(0));
        final var sender = convertLeftPaddedAddressToAccountId(decodedArguments.get(1), aliasResolver);
        final var receiver = convertLeftPaddedAddressToAccountId(decodedArguments.get(2), aliasResolver, exists);
        final var serialNumber = (long) decodedArguments.get(3);

        final var tokenTransferWrappers = Collections.singletonList(new TokenTransferWrapper(
                List.of(new SyntheticTxnFactory.NftExchange(serialNumber, tokenID, sender, receiver)),
                NO_FUNGIBLE_TRANSFERS));
        return new CryptoTransferWrapper(new TransferWrapper(hbarTransfers), tokenTransferWrappers);
    }

    private List<BalanceChange> constructBalanceChanges() {
        Objects.requireNonNull(transferOp, "`body` method should be called before `getMinimumFeeInTinybars`");
        final List<BalanceChange> allChanges = new ArrayList<>();
        final var accountId = EntityIdUtils.accountIdFromEvmAddress(senderAddress);
        Set<AccountID> requestedLazyCreates = new HashSet<>();
        for (final TokenTransferWrapper tokenTransferWrapper : transferOp.tokenTransferWrappers()) {
            for (final var fungibleTransfer : tokenTransferWrapper.fungibleTransfers()) {
                final var receiver = fungibleTransfer.receiver();
                if (fungibleTransfer.sender() != null && receiver != null) {
                    allChanges.addAll(List.of(
                            BalanceChange.changingFtUnits(
                                    Id.fromGrpcToken(fungibleTransfer.getDenomination()),
                                    fungibleTransfer.getDenomination(),
                                    aaWith(receiver, fungibleTransfer.amount(), fungibleTransfer.isApproval()),
                                    accountId),
                            BalanceChange.changingFtUnits(
                                    Id.fromGrpcToken(fungibleTransfer.getDenomination()),
                                    fungibleTransfer.getDenomination(),
                                    aaWith(
                                            fungibleTransfer.sender(),
                                            -fungibleTransfer.amount(),
                                            fungibleTransfer.isApproval()),
                                    accountId)));
                    if (!receiver.getAlias().isEmpty()) {
                        requestedLazyCreates.add(receiver);
                    }
                } else if (fungibleTransfer.sender() == null) {
                    allChanges.add(BalanceChange.changingFtUnits(
                            Id.fromGrpcToken(fungibleTransfer.getDenomination()),
                            fungibleTransfer.getDenomination(),
                            aaWith(receiver, fungibleTransfer.amount(), fungibleTransfer.isApproval()),
                            accountId));
                    if (!receiver.getAlias().isEmpty()) {
                        requestedLazyCreates.add(receiver);
                    }
                } else {
                    allChanges.add(BalanceChange.changingFtUnits(
                            Id.fromGrpcToken(fungibleTransfer.getDenomination()),
                            fungibleTransfer.getDenomination(),
                            aaWith(
                                    fungibleTransfer.sender(),
                                    -fungibleTransfer.amount(),
                                    fungibleTransfer.isApproval()),
                            accountId));
                }
            }
            for (final var nftExchange : tokenTransferWrapper.nftExchanges()) {
                final var asGrpc = nftExchange.asGrpc();
                final var receiverAccountID = asGrpc.getReceiverAccountID();
                if (!receiverAccountID.getAlias().isEmpty()) {
                    requestedLazyCreates.add(receiverAccountID);
                }
                allChanges.add(BalanceChange.changingNftOwnership(
                        Id.fromGrpcToken(nftExchange.getTokenType()), nftExchange.getTokenType(), asGrpc, accountId));
            }
        }

        for (final var hbarTransfer : transferOp.transferWrapper().hbarTransfers()) {
            if (hbarTransfer.sender() != null) {
                allChanges.add(BalanceChange.changingHbar(
                        aaWith(hbarTransfer.sender(), -hbarTransfer.amount(), hbarTransfer.isApproval()), accountId));
            } else if (hbarTransfer.receiver() != null) {
                final var receiver = hbarTransfer.receiver();
                if (!receiver.getAlias().isEmpty()) {
                    requestedLazyCreates.add(receiver);
                }
                allChanges.add(BalanceChange.changingHbar(
                        aaWith(receiver, hbarTransfer.amount(), hbarTransfer.isApproval()), accountId));
            }
        }
        numLazyCreates = requestedLazyCreates.size();
        return allChanges;
    }

    private AccountAmount aaWith(final AccountID account, final long amount, final boolean isApproval) {
        return AccountAmount.newBuilder()
                .setAccountID(account)
                .setAmount(amount)
                .setIsApproval(isApproval)
                .build();
    }

    public void setCanFallbackToApprovals(final boolean canFallbackToApprovals) {
        this.canFallbackToApprovals = canFallbackToApprovals;
    }

    /**
     * Given a {@link BalanceChange} that was switched from key-based authorization to approval-based authorization,
     * tries to update the matching adjustment in the transfer list of the in-progress synthetic {@code CryptoTransfer}
     * op to reflect the switch.
     *
     * <p><b>Important:</b> this method acts via side effects, mutating the given {@code opBuilder} in-place.
     *
     * @param opBuilder the builder for the in-progress synthetic {@code CryptoTransfer} op
     * @param switchedChange the {@code BalanceChange} switched to approval-based authorization
     */
    @VisibleForTesting
    static void updateSynthOpForAutoApprovalOf(
            @NonNull final CryptoTransferTransactionBody.Builder opBuilder,
            @NonNull final BalanceChange switchedChange) {
        if (switchedChange.isForCustomFee()) {
            // The synthetic CryptoTransfer op won't have any transfer list entry for a custom fee adjustment,
            // so we can't externalize any more info in this case (given the current record creation logic)
            return;
        }
        if (switchedChange.isForHbar()) {
            updateSynthOpForAutoApprovalOfHbar(opBuilder, switchedChange);
        } else {
            updateSynthOpForAutoApprovalOfToken(opBuilder, switchedChange);
        }
    }

    /**
     * Given an hbar {@link BalanceChange} that was switched from key-based authorization to approval-based
     * authorization, tries to update the matching adjustment in the hbar transfer list of the in-progress
     * synthetic {@code CryptoTransfer} op to reflect the switch.
     *
     * @param opBuilder the builder for the in-progress synthetic {@code CryptoTransfer} op
     * @param switchedChange the hbar {@code BalanceChange} switched to approval-based authorization
     */
    private static void updateSynthOpForAutoApprovalOfHbar(
            @NonNull final CryptoTransferTransactionBody.Builder opBuilder,
            @NonNull final BalanceChange switchedChange) {
        final var transfersBuilder = opBuilder.getTransfersBuilder();
        for (int i = 0, n = transfersBuilder.getAccountAmountsCount(); i < n; i++) {
            final var hbarAdjust = transfersBuilder.getAccountAmountsBuilder(i);
            if (hbarAdjust.getAccountID().equals(switchedChange.accountId())) {
                if (hbarAdjust.getAmount() < 0) {
                    hbarAdjust.setIsApproval(true);
                } else {
                    log.error(CHANGE_SWITCHED_TO_APPROVAL_MATCHED_CREDIT_IN, switchedChange, opBuilder);
                }
                return;
            }
        }
        // This doesn't make sense, as it implies the TransferPrecompile has switched a non-custom-fee hbar
        // adjustment to approval-based authorization, but the synthetic CryptoTransfer op doesn't have a
        // matching adjustment in its transfer list
        log.error(CHANGE_SWITCHED_TO_APPROVAL_WITHOUT_MATCHING_ADJUSTMENT_IN, switchedChange, opBuilder);
    }

    /**
     * Given a token {@link BalanceChange} that was switched from key-based authorization to approval-based
     * authorization, tries to update the matching adjustment in the token transfer list of the in-progress
     * synthetic {@code CryptoTransfer} op to reflect the switch.
     *
     * @param opBuilder the builder for the in-progress synthetic {@code CryptoTransfer} op
     * @param switchedChange the token {@code BalanceChange} switched to approval-based authorization
     */
    private static void updateSynthOpForAutoApprovalOfToken(
            final CryptoTransferTransactionBody.Builder opBuilder, final BalanceChange switchedChange) {
        for (int i = 0, n = opBuilder.getTokenTransfersCount(); i < n; i++) {
            final var tokenTransfers = opBuilder.getTokenTransfers(i);
            if (tokenTransfers.getToken().equals(switchedChange.tokenId())) {
                if (switchedChange.isForNft()) {
                    updateBodyForAutoApprovalOfNonFungible(opBuilder, tokenTransfers, switchedChange, i);
                } else {
                    updateBodyForAutoApprovalOfFungible(opBuilder, tokenTransfers, switchedChange, i);
                }
                // Token ids cannot be repeated, so if there was an adjustment matching this change, it was in this list
                return;
            }
        }
    }

    /**
     * Given a fungible {@link BalanceChange} that was switched from key-based authorization to approval-based
     * authorization, tries to update the matching adjustment in the token transfer list of the in-progress
     * synthetic {@code CryptoTransfer} op to reflect the switch.
     *
     * @param opBuilder the builder for the in-progress synthetic {@code CryptoTransfer} op
     * @param switchedChange the fungible {@code BalanceChange} switched to approval-based authorization
     */
    private static void updateBodyForAutoApprovalOfFungible(
            final CryptoTransferTransactionBody.Builder opBuilder,
            final TokenTransferList tokenTransfers,
            final BalanceChange switchedChange,
            final int tokenTransfersIndex) {
        for (int j = 0, m = tokenTransfers.getTransfersCount(); j < m; j++) {
            final var adjust = tokenTransfers.getTransfers(j);
            if (adjust.getAccountID().equals(switchedChange.accountId())) {
                if (adjust.getAmount() < 0) {
                    opBuilder
                            .getTokenTransfersBuilder(tokenTransfersIndex)
                            .getTransfersBuilder(j)
                            .setIsApproval(true);
                } else {
                    log.error(CHANGE_SWITCHED_TO_APPROVAL_MATCHED_CREDIT_IN, switchedChange, opBuilder);
                }
                return;
            }
        }
        // This doesn't make sense, as it implies the TransferPrecompile has switched a non-custom-fee fungible
        // adjustment to approval-based authorization, but the synthetic CryptoTransfer op doesn't have a
        // matching adjustment in its token transfer list
        log.error(CHANGE_SWITCHED_TO_APPROVAL_WITHOUT_MATCHING_ADJUSTMENT_IN, switchedChange, opBuilder);
    }

    /**
     * Given a non-fungible {@link BalanceChange} that was switched from key-based authorization to approval-based
     * authorization, tries to update the matching adjustment in the token transfer list of the in-progress
     * synthetic {@code CryptoTransfer} op to reflect the switch.
     *
     * @param opBuilder the builder for the in-progress synthetic {@code CryptoTransfer} op
     * @param switchedChange the non-fungible {@code BalanceChange} switched to approval-based authorization
     */
    private static void updateBodyForAutoApprovalOfNonFungible(
            final CryptoTransferTransactionBody.Builder opBuilder,
            final TokenTransferList tokenTransfers,
            final BalanceChange switchedChange,
            final int tokenTransfersIndex) {
        for (int j = 0, m = tokenTransfers.getNftTransfersCount(); j < m; j++) {
            final var change = tokenTransfers.getNftTransfers(j);
            if (change.getSenderAccountID().equals(switchedChange.accountId())
                    && change.getSerialNumber() == switchedChange.serialNo()) {
                opBuilder
                        .getTokenTransfersBuilder(tokenTransfersIndex)
                        .getNftTransfersBuilder(j)
                        .setIsApproval(true);
                return;
            }
        }
        // This doesn't make sense, as it implies the TransferPrecompile has switched a non-custom-fee NFT
        // ownership change to approval-based authorization, but the synthetic CryptoTransfer op doesn't
        // have a matching ownership change in its token transfer list
        log.error(CHANGE_SWITCHED_TO_APPROVAL_WITHOUT_MATCHING_ADJUSTMENT_IN, switchedChange, opBuilder);
    }

    private void revertIfReceiverIsSystemAccount(final BalanceChange change) {
        final var accountNum = change.counterPartyAccountId() != null
                ? change.counterPartyAccountId().getAccountNum()
                : change.getAccount().num();

        validateTrueOrRevert(accountNum > SYSTEM_ACCOUNT_BOUNDARY, INVALID_RECEIVING_NODE_ACCOUNT);
    }
}
