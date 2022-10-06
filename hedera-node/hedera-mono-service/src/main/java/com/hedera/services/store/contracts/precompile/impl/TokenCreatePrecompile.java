/*
 * Copyright (C) 2022 Hedera Hashgraph, LLC
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
package com.hedera.services.store.contracts.precompile.impl;

import static com.hedera.services.contracts.ParsingConstants.ARRAY_BRACKETS;
import static com.hedera.services.contracts.ParsingConstants.FIXED_FEE;
import static com.hedera.services.contracts.ParsingConstants.FRACTIONAL_FEE;
import static com.hedera.services.contracts.ParsingConstants.ROYALTY_FEE;
import static com.hedera.services.exceptions.ValidationUtils.validateTrue;
import static com.hedera.services.ledger.properties.AccountProperty.AUTO_RENEW_ACCOUNT_ID;
import static com.hedera.services.state.submerkle.EntityId.MISSING_ENTITY_ID;
import static com.hedera.services.store.contracts.precompile.codec.DecodingFacade.FIXED_FEE_DECODER;
import static com.hedera.services.store.contracts.precompile.codec.DecodingFacade.FRACTIONAL_FEE_DECODER;
import static com.hedera.services.store.contracts.precompile.codec.DecodingFacade.HEDERA_TOKEN_STRUCT;
import static com.hedera.services.store.contracts.precompile.codec.DecodingFacade.HEDERA_TOKEN_STRUCT_DECODER;
import static com.hedera.services.store.contracts.precompile.codec.DecodingFacade.HEDERA_TOKEN_STRUCT_V2;
import static com.hedera.services.store.contracts.precompile.codec.DecodingFacade.ROYALTY_FEE_DECODER;
import static com.hedera.services.store.contracts.precompile.codec.DecodingFacade.convertAddressBytesToTokenID;
import static com.hedera.services.store.contracts.precompile.codec.DecodingFacade.convertLeftPaddedAddressToAccountId;
import static com.hedera.services.store.contracts.precompile.codec.DecodingFacade.decodeFunctionCall;
import static com.hedera.services.store.contracts.precompile.codec.DecodingFacade.decodeTokenExpiry;
import static com.hedera.services.store.contracts.precompile.codec.DecodingFacade.decodeTokenKeys;
import static com.hedera.services.store.contracts.precompile.codec.KeyValueWrapper.KeyValueType.INVALID_KEY;
import static com.hedera.services.store.contracts.precompile.codec.TokenCreateWrapper.FixedFeeWrapper.FixedFeePayment.INVALID_PAYMENT;
import static com.hedera.services.utils.EntityIdUtils.asTypedEvmAddress;
import static com.hederahashgraph.api.proto.java.HederaFunctionality.ContractCall;
import static com.hederahashgraph.api.proto.java.ResponseCodeEnum.FAIL_INVALID;
import static com.hederahashgraph.api.proto.java.ResponseCodeEnum.INSUFFICIENT_TX_FEE;
import static com.hederahashgraph.api.proto.java.ResponseCodeEnum.INVALID_FULL_PREFIX_SIGNATURE_FOR_PRECOMPILE;
import static com.hederahashgraph.api.proto.java.ResponseCodeEnum.INVALID_TRANSACTION_BODY;
import static com.hederahashgraph.api.proto.java.ResponseCodeEnum.OK;

import com.esaulpaugh.headlong.abi.ABIType;
import com.esaulpaugh.headlong.abi.Function;
import com.esaulpaugh.headlong.abi.Tuple;
import com.esaulpaugh.headlong.abi.TypeFactory;
import com.hedera.services.context.SideEffectsTracker;
import com.hedera.services.contracts.sources.EvmSigsVerifier;
import com.hedera.services.exceptions.InvalidTransactionException;
import com.hedera.services.fees.FeeCalculator;
import com.hedera.services.ledger.properties.AccountProperty;
import com.hedera.services.legacy.core.jproto.JECDSASecp256k1Key;
import com.hedera.services.legacy.core.jproto.JEd25519Key;
import com.hedera.services.legacy.core.jproto.JKey;
import com.hedera.services.records.RecordsHistorian;
import com.hedera.services.state.submerkle.EntityId;
import com.hedera.services.state.submerkle.ExpirableTxnRecord;
import com.hedera.services.store.contracts.HederaStackedWorldStateUpdater;
import com.hedera.services.store.contracts.WorldLedgers;
import com.hedera.services.store.contracts.precompile.AbiConstants;
import com.hedera.services.store.contracts.precompile.HTSPrecompiledContract;
import com.hedera.services.store.contracts.precompile.InfrastructureFactory;
import com.hedera.services.store.contracts.precompile.SyntheticTxnFactory;
import com.hedera.services.store.contracts.precompile.codec.DecodingFacade;
import com.hedera.services.store.contracts.precompile.codec.EncodingFacade;
import com.hedera.services.store.contracts.precompile.codec.TokenCreateWrapper;
import com.hedera.services.store.contracts.precompile.codec.TokenCreateWrapper.FixedFeeWrapper;
import com.hedera.services.store.contracts.precompile.codec.TokenCreateWrapper.FractionalFeeWrapper;
import com.hedera.services.store.contracts.precompile.codec.TokenCreateWrapper.RoyaltyFeeWrapper;
import com.hedera.services.store.contracts.precompile.codec.TokenKeyWrapper;
import com.hedera.services.store.contracts.precompile.utils.KeyActivationUtils;
import com.hedera.services.store.contracts.precompile.utils.PrecompilePricingUtils;
import com.hedera.services.store.models.Id;
import com.hedera.services.txns.token.validators.CreateChecks;
import com.hedera.services.utils.EntityIdUtils;
import com.hederahashgraph.api.proto.java.AccountID;
import com.hederahashgraph.api.proto.java.ResponseCodeEnum;
import com.hederahashgraph.api.proto.java.Timestamp;
import com.hederahashgraph.api.proto.java.TransactionBody;
import com.hederahashgraph.api.proto.java.TransactionID;
import java.math.BigInteger;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.function.Predicate;
import java.util.function.UnaryOperator;
import javax.inject.Provider;
import org.apache.commons.codec.DecoderException;
import org.apache.tuweni.bytes.Bytes;
import org.hyperledger.besu.datatypes.Address;
import org.hyperledger.besu.datatypes.Wei;
import org.hyperledger.besu.evm.frame.MessageFrame;
import org.jetbrains.annotations.NotNull;

/**
 * Executes the logic of creating a token from {@link HTSPrecompiledContract}.
 *
 * <p>When a token create call is received, the execution can follow one of the following 4
 * scenarios:
 *
 * <ol>
 *   <li>The calling smart contract has sent a corrupt input to the {@link HTSPrecompiledContract}
 *       (e.g. passing random bytes through the {@code encode()} Solidity method), which cannot be
 *       decoded to a valid {@link TokenCreateWrapper}.
 *       <ul>
 *         <li><b>result</b> - the {@link DecodingFacade} throws an exception and null is returned
 *             from the {@link HTSPrecompiledContract}, setting the message frame's revert reason to
 *             the {@code ERROR_DECODING_INPUT_REVERT_REASON} constant
 *         <li><b>gas cost</b> - the current value returned from {@code
 *             dynamicProperties.htsDefaultGasCost()}
 *         <li><b>hbar cost</b> - all sent HBars are refunded to the frame sender
 *       </ul>
 *   <li>The decoding succeeds, we create a valid {@link TokenCreateWrapper}, but we cannot
 *       translate it to a valid token create {@link TransactionBody}. This comes from <b>difference
 *       in the design of the Solidity function interface and the HAPI (protobufs)</b>
 *       <ul>
 *         <li><b>result</b> - {@link MessageFrame}'s revertReason is set to the {@code
 *             ERROR_DECODING_INPUT_REVERT_REASON} constant and null is returned from the {@link
 *             HTSPrecompiledContract}
 *         <li><b>gas cost</b> - the current value returned from {@code
 *             dynamicProperties.htsDefaultGasCost()}
 *         <li><b>hbar cost</b> - all sent HBars are refunded to the frame sender
 *       </ul>
 *   <li>The decoding succeeds, we create a valid {@link TokenCreateWrapper}, we successfully
 *       translate it to a valid token create {@link TransactionBody}. However, the {@link
 *       CreateChecks} validations find an input error.
 *       <ul>
 *         <li><b>result</b> - a child {@link ExpirableTxnRecord} is created, containing the error
 *             response code. (from the point of view of the EVM this is a successful precompile
 *             call, however, from a Hedera's perspective there has been a problem during the
 *             execution)
 *         <li><b>gas cost</b> - 100 000 gas
 *         <li><b>hbar cost</b> - the HBars needed for the token creation are charged from the frame
 *             sender address (any excess HBars are refunded)
 *       </ul>
 *   <li>The decoding succeeds, we create a valid {@link TokenCreateWrapper}, we successfully
 *       translate it to a valid token create {@link TransactionBody}, the {@link CreateChecks}
 *       token create validations pass and the whole execution flow succeeds.
 *       <ul>
 *         <li><b>result</b> - a child {@link ExpirableTxnRecord} is created, containing the
 *             successful response code and the ID of the newly created token.
 *         <li><b>gas cost</b> - 100 000 gas
 *         <li><b>hbar cost</b> - the HBars needed for the token creation are charged from the frame
 *             sender address (any excess HBars are refunded)
 *       </ul>
 * </ol>
 */
public class TokenCreatePrecompile extends AbstractWritePrecompile {
    private static final String TOKEN_CREATE = String.format(FAILURE_MESSAGE, "token create");
    private static final Function TOKEN_CREATE_FUNGIBLE_FUNCTION =
            new Function("createFungibleToken(" + HEDERA_TOKEN_STRUCT + ",uint256,uint256)");
    public static final Bytes TOKEN_CREATE_FUNGIBLE_SELECTOR =
            Bytes.wrap(TOKEN_CREATE_FUNGIBLE_FUNCTION.selector());
    public static final ABIType<Tuple> TOKEN_CREATE_FUNGIBLE_DECODER =
            TypeFactory.create("(" + HEDERA_TOKEN_STRUCT_DECODER + ",uint256,uint256)");
    private static final Function TOKEN_CREATE_FUNGIBLE_FUNCTION_V2 =
            new Function("createFungibleToken(" + HEDERA_TOKEN_STRUCT_V2 + ",uint64,uint32)");
    public static final Bytes TOKEN_CREATE_FUNGIBLE_SELECTOR_V2 =
            Bytes.wrap(TOKEN_CREATE_FUNGIBLE_FUNCTION_V2.selector());
    private static final Function TOKEN_CREATE_FUNGIBLE_WITH_FEES_FUNCTION =
            new Function(
                    "createFungibleTokenWithCustomFees("
                            + HEDERA_TOKEN_STRUCT
                            + ",uint256,uint256,"
                            + FIXED_FEE
                            + ARRAY_BRACKETS
                            + ","
                            + FRACTIONAL_FEE
                            + ARRAY_BRACKETS
                            + ")");
    public static final Bytes TOKEN_CREATE_FUNGIBLE_WITH_FEES_SELECTOR =
            Bytes.wrap(TOKEN_CREATE_FUNGIBLE_WITH_FEES_FUNCTION.selector());
    private static final Function TOKEN_CREATE_FUNGIBLE_WITH_FEES_FUNCTION_V2 =
            new Function(
                    "createFungibleTokenWithCustomFees("
                            + HEDERA_TOKEN_STRUCT_V2
                            + ",uint64,uint32,"
                            + FIXED_FEE
                            + ARRAY_BRACKETS
                            + ","
                            + FRACTIONAL_FEE
                            + ARRAY_BRACKETS
                            + ")");
    public static final Bytes TOKEN_CREATE_FUNGIBLE_WITH_FEES_SELECTOR_V2 =
            Bytes.wrap(TOKEN_CREATE_FUNGIBLE_WITH_FEES_FUNCTION_V2.selector());
    public static final ABIType<Tuple> TOKEN_CREATE_FUNGIBLE_WITH_FEES_DECODER =
            TypeFactory.create(
                    "("
                            + HEDERA_TOKEN_STRUCT_DECODER
                            + ",uint256,uint256,"
                            + FIXED_FEE_DECODER
                            + ARRAY_BRACKETS
                            + ","
                            + FRACTIONAL_FEE_DECODER
                            + ARRAY_BRACKETS
                            + ")");
    private static final Function TOKEN_CREATE_NON_FUNGIBLE_FUNCTION =
            new Function("createNonFungibleToken(" + HEDERA_TOKEN_STRUCT + ")");
    public static final Bytes TOKEN_CREATE_NON_FUNGIBLE_SELECTOR =
            Bytes.wrap(TOKEN_CREATE_NON_FUNGIBLE_FUNCTION.selector());
    private static final Function TOKEN_CREATE_NON_FUNGIBLE_FUNCTION_V2 =
            new Function("createNonFungibleToken(" + HEDERA_TOKEN_STRUCT_V2 + ")");
    public static final Bytes TOKEN_CREATE_NON_FUNGIBLE_SELECTOR_V2 =
            Bytes.wrap(TOKEN_CREATE_NON_FUNGIBLE_FUNCTION_V2.selector());
    public static final ABIType<Tuple> TOKEN_CREATE_NON_FUNGIBLE_DECODER =
            TypeFactory.create("(" + HEDERA_TOKEN_STRUCT_DECODER + ")");
    private static final Function TOKEN_CREATE_NON_FUNGIBLE_WITH_FEES_FUNCTION =
            new Function(
                    "createNonFungibleTokenWithCustomFees("
                            + HEDERA_TOKEN_STRUCT
                            + ","
                            + FIXED_FEE
                            + ARRAY_BRACKETS
                            + ","
                            + ROYALTY_FEE
                            + ARRAY_BRACKETS
                            + ")");
    public static final Bytes TOKEN_CREATE_NON_FUNGIBLE_WITH_FEES_SELECTOR =
            Bytes.wrap(TOKEN_CREATE_NON_FUNGIBLE_WITH_FEES_FUNCTION.selector());
    private static final Function TOKEN_CREATE_NON_FUNGIBLE_WITH_FEES_FUNCTION_V2 =
            new Function(
                    "createNonFungibleTokenWithCustomFees("
                            + HEDERA_TOKEN_STRUCT_V2
                            + ","
                            + FIXED_FEE
                            + ARRAY_BRACKETS
                            + ","
                            + ROYALTY_FEE
                            + ARRAY_BRACKETS
                            + ")");
    public static final Bytes TOKEN_CREATE_NON_FUNGIBLE_WITH_FEES_SELECTOR_V2 =
            Bytes.wrap(TOKEN_CREATE_NON_FUNGIBLE_WITH_FEES_FUNCTION_V2.selector());
    public static final ABIType<Tuple> TOKEN_CREATE_NON_FUNGIBLE_WITH_FEES_DECODER =
            TypeFactory.create(
                    "("
                            + HEDERA_TOKEN_STRUCT_DECODER
                            + ","
                            + FIXED_FEE_DECODER
                            + ARRAY_BRACKETS
                            + ","
                            + ROYALTY_FEE_DECODER
                            + ARRAY_BRACKETS
                            + ")");
    private final EncodingFacade encoder;
    private final HederaStackedWorldStateUpdater updater;
    private final EvmSigsVerifier sigsVerifier;
    private final RecordsHistorian recordsHistorian;
    private final int functionId;
    private final Address senderAddress;
    private final AccountID fundingAccount;
    private final Provider<FeeCalculator> feeCalculator;
    private TokenCreateWrapper tokenCreateOp;

    public TokenCreatePrecompile(
            final WorldLedgers ledgers,
            final EncodingFacade encoder,
            final HederaStackedWorldStateUpdater updater,
            final EvmSigsVerifier sigsVerifier,
            final RecordsHistorian recordsHistorian,
            final SideEffectsTracker sideEffects,
            final SyntheticTxnFactory syntheticTxnFactory,
            final InfrastructureFactory infrastructureFactory,
            final int functionId,
            final Address senderAddress,
            final AccountID fundingAccount,
            final Provider<FeeCalculator> feeCalculator,
            final PrecompilePricingUtils pricingUtils) {
        super(ledgers, sideEffects, syntheticTxnFactory, infrastructureFactory, pricingUtils);
        this.encoder = encoder;
        this.updater = updater;
        this.sigsVerifier = sigsVerifier;
        this.recordsHistorian = recordsHistorian;
        this.functionId = functionId;
        this.senderAddress = senderAddress;
        this.fundingAccount = fundingAccount;
        this.feeCalculator = feeCalculator;
    }

    @Override
    public TransactionBody.Builder body(
            final Bytes input, final UnaryOperator<byte[]> aliasResolver) {
        tokenCreateOp =
                switch (functionId) {
                    case AbiConstants.ABI_ID_CREATE_FUNGIBLE_TOKEN -> decodeFungibleCreate(
                            input, aliasResolver);
                    case AbiConstants
                            .ABI_ID_CREATE_FUNGIBLE_TOKEN_WITH_FEES -> decodeFungibleCreateWithFees(
                            input, aliasResolver);
                    case AbiConstants.ABI_ID_CREATE_NON_FUNGIBLE_TOKEN -> decodeNonFungibleCreate(
                            input, aliasResolver);
                    case AbiConstants
                            .ABI_ID_CREATE_NON_FUNGIBLE_TOKEN_WITH_FEES -> decodeNonFungibleCreateWithFees(
                            input, aliasResolver);
                    case AbiConstants.ABI_ID_CREATE_FUNGIBLE_TOKEN_V2 -> decodeFungibleCreateV2(
                            input, aliasResolver);
                    case AbiConstants
                            .ABI_ID_CREATE_FUNGIBLE_TOKEN_WITH_FEES_V2 -> decodeFungibleCreateWithFeesV2(
                            input, aliasResolver);
                    case AbiConstants
                            .ABI_ID_CREATE_NON_FUNGIBLE_TOKEN_V2 -> decodeNonFungibleCreateV2(
                            input, aliasResolver);
                    case AbiConstants
                            .ABI_ID_CREATE_NON_FUNGIBLE_TOKEN_WITH_FEES_V2 -> decodeNonFungibleCreateWithFeesV2(
                            input, aliasResolver);
                    default -> null;
                };

        /* --- Validate Solidity input and massage it to be able to transform it to tokenCreateTxnBody --- */
        verifySolidityInput();
        try {
            replaceInheritedProperties();
        } catch (DecoderException e) {
            throw new InvalidTransactionException(FAIL_INVALID);
        }
        transactionBody = syntheticTxnFactory.createTokenCreate(tokenCreateOp);

        return transactionBody;
    }

    @Override
    public void run(final MessageFrame frame) {
        Objects.requireNonNull(tokenCreateOp, "`body` method should be called before `run`");

        /* --- Validate the synthetic create txn body before proceeding with the rest of the execution --- */
        final var creationTime = recordsHistorian.nextFollowingChildConsensusTime();
        final var tokenCreateChecks = infrastructureFactory.newCreateChecks();
        final var result =
                tokenCreateChecks.validatorForConsTime(creationTime).apply(transactionBody.build());
        validateTrue(result == OK, result);

        /* --- Check required signatures --- */
        final var treasuryId = Id.fromGrpcAccount(tokenCreateOp.getTreasury());
        final var treasuryHasSigned =
                KeyActivationUtils.validateKey(
                        frame,
                        treasuryId.asEvmAddress(),
                        sigsVerifier::hasActiveKey,
                        ledgers,
                        updater.aliases());
        validateTrue(treasuryHasSigned, INVALID_FULL_PREFIX_SIGNATURE_FOR_PRECOMPILE, TOKEN_CREATE);
        tokenCreateOp
                .getAdminKey()
                .ifPresent(
                        key ->
                                validateTrue(
                                        validateAdminKey(frame, key),
                                        INVALID_FULL_PREFIX_SIGNATURE_FOR_PRECOMPILE,
                                        TOKEN_CREATE));

        /* --- Build the necessary infrastructure to execute the transaction --- */
        final var accountStore = infrastructureFactory.newAccountStore(ledgers.accounts());
        final var tokenStore =
                infrastructureFactory.newTokenStore(
                        accountStore,
                        sideEffects,
                        ledgers.tokens(),
                        ledgers.nfts(),
                        ledgers.tokenRels());
        final var tokenCreateLogic =
                infrastructureFactory.newTokenCreateLogic(accountStore, tokenStore);

        /* --- Execute the transaction and capture its results --- */
        tokenCreateLogic.create(
                creationTime.getEpochSecond(),
                EntityIdUtils.accountIdFromEvmAddress(senderAddress),
                transactionBody.getTokenCreation());
    }

    @Override
    public long getGasRequirement(long blockTimestamp) {
        return getMinimumFeeInTinybars(Timestamp.newBuilder().setSeconds(blockTimestamp).build());
    }

    @Override
    public void customizeTrackingLedgers(final WorldLedgers worldLedgers) {
        worldLedgers.customizeForAutoAssociatingOp(sideEffects);
    }

    @Override
    public void handleSentHbars(final MessageFrame frame) {
        final var timestampSeconds = frame.getBlockValues().getTimestamp();
        final var timestamp = Timestamp.newBuilder().setSeconds(timestampSeconds).build();
        final var gasPriceInTinybars =
                feeCalculator.get().estimatedGasPriceInTinybars(ContractCall, timestamp);
        final var calculatedFeeInTinybars =
                pricingUtils.gasFeeInTinybars(
                        transactionBody.setTransactionID(
                                TransactionID.newBuilder()
                                        .setTransactionValidStart(timestamp)
                                        .build()),
                        Instant.ofEpochSecond(timestampSeconds),
                        this);

        final var tinybarsRequirement =
                calculatedFeeInTinybars
                        + (calculatedFeeInTinybars / 5)
                        - getMinimumFeeInTinybars(timestamp) * gasPriceInTinybars;

        validateTrue(
                frame.getValue().greaterOrEqualThan(Wei.of(tinybarsRequirement)),
                INSUFFICIENT_TX_FEE);

        updater.getAccount(senderAddress)
                .getMutable()
                .decrementBalance(Wei.of(tinybarsRequirement));
        updater.getAccount(Id.fromGrpcAccount(fundingAccount).asEvmAddress())
                .getMutable()
                .incrementBalance(Wei.of(tinybarsRequirement));
    }

    /* --- Due to differences in Solidity and protobuf interfaces, perform custom checks on the input  --- */
    private void verifySolidityInput() {
        /*
         * Verify initial supply and decimals fall withing the allowed ranges of the types
         * they convert to (long and int, respectively), since in the Solidity interface
         * they are specified as uint256s and illegal values may be passed as input.
         */
        if (tokenCreateOp.isFungible()) {
            validateTrue(
                    tokenCreateOp.getInitSupply().compareTo(BigInteger.valueOf(Long.MAX_VALUE)) < 1,
                    INVALID_TRANSACTION_BODY);
            validateTrue(
                    tokenCreateOp.getDecimals().compareTo(BigInteger.valueOf(Integer.MAX_VALUE))
                            < 1,
                    INVALID_TRANSACTION_BODY);
        }

        /*
         * Check keys validity. The `TokenKey` struct in `IHederaTokenService.sol`
         * defines a `keyType` bit field, which smart contract developers will use to
         * set the type of key the `KeyValue` field will be used for. For example, if the
         * `keyType` field is set to `00000001`, then the key value will be used for adminKey.
         * If it is set to `00000011` the key value will be used for both adminKey and kycKey.
         * Since an array of `TokenKey` structs is passed to the precompile, we have to
         * check if each one specifies the type of key it applies to (that the bit field
         * is not `00000000` and no bit bigger than 6 is set) and also that there are not multiple
         * keys values for the same key type (e.g. multiple `TokenKey` instances have the adminKey bit set)
         */
        final var tokenKeys = tokenCreateOp.getTokenKeys();
        if (!tokenKeys.isEmpty()) {
            for (int i = 0, tokenKeysSize = tokenKeys.size(); i < tokenKeysSize; i++) {
                final var tokenKey = tokenKeys.get(i);
                validateTrue(
                        tokenKey.key().getKeyValueType() != INVALID_KEY, INVALID_TRANSACTION_BODY);
                final var tokenKeyBitField = tokenKey.keyType();
                validateTrue(
                        tokenKeyBitField != 0 && tokenKeyBitField < 128, INVALID_TRANSACTION_BODY);
                for (int j = i + 1; j < tokenKeysSize; j++) {
                    validateTrue(
                            (tokenKeyBitField & tokenKeys.get(j).keyType()) == 0,
                            INVALID_TRANSACTION_BODY);
                }
            }
        }

        /*
         * The denomination of a fixed fee depends on the values of tokenId, useHbarsForPayment
         * useCurrentTokenForPayment. Exactly one of the values of the struct should be set.
         */
        if (!tokenCreateOp.getFixedFees().isEmpty()) {
            for (final var fixedFee : tokenCreateOp.getFixedFees()) {
                validateTrue(
                        fixedFee.getFixedFeePayment() != INVALID_PAYMENT, INVALID_TRANSACTION_BODY);
            }
        }

        /*
         * When a royalty fee with fallback fee is specified, we need to check that
         * the fallback fixed fee is valid.
         */
        if (!tokenCreateOp.getRoyaltyFees().isEmpty()) {
            for (final var royaltyFee : tokenCreateOp.getRoyaltyFees()) {
                if (royaltyFee.fallbackFixedFee() != null) {
                    validateTrue(
                            royaltyFee.fallbackFixedFee().getFixedFeePayment() != INVALID_PAYMENT,
                            INVALID_TRANSACTION_BODY);
                }
            }
        }
    }

    private void replaceInheritedKeysWithSenderKey(AccountID parentId) throws DecoderException {
        tokenCreateOp.setAllInheritedKeysTo(
                (JKey) ledgers.accounts().get(parentId, AccountProperty.KEY));
    }

    private void replaceInheritedProperties() throws DecoderException {
        final var parentId = EntityIdUtils.accountIdFromEvmAddress(senderAddress);
        final var parentAutoRenewId =
                (EntityId) ledgers.accounts().get(parentId, AUTO_RENEW_ACCOUNT_ID);
        if (!MISSING_ENTITY_ID.equals(parentAutoRenewId) && !tokenCreateOp.hasAutoRenewAccount()) {
            tokenCreateOp.inheritAutoRenewAccount(parentAutoRenewId);
        }
        replaceInheritedKeysWithSenderKey(parentId);
    }

    private boolean validateAdminKey(
            final MessageFrame frame, final TokenKeyWrapper tokenKeyWrapper) {
        final var key = tokenKeyWrapper.key();
        return switch (key.getKeyValueType()) {
            case INHERIT_ACCOUNT_KEY -> KeyActivationUtils.validateKey(
                    frame, senderAddress, sigsVerifier::hasActiveKey, ledgers, updater.aliases());
            case CONTRACT_ID -> KeyActivationUtils.validateKey(
                    frame,
                    asTypedEvmAddress(key.getContractID()),
                    sigsVerifier::hasActiveKey,
                    ledgers,
                    updater.aliases());
            case DELEGATABLE_CONTRACT_ID -> KeyActivationUtils.validateKey(
                    frame,
                    asTypedEvmAddress(key.getDelegatableContractID()),
                    sigsVerifier::hasActiveKey,
                    ledgers,
                    updater.aliases());
            case ED25519 -> validateCryptoKey(
                    new JEd25519Key(key.getEd25519Key()), sigsVerifier::cryptoKeyIsActive);
            case ECDSA_SECPK256K1 -> validateCryptoKey(
                    new JECDSASecp256k1Key(key.getEcdsaSecp256k1()),
                    sigsVerifier::cryptoKeyIsActive);
            default -> false;
        };
    }

    private boolean validateCryptoKey(final JKey key, final Predicate<JKey> keyActiveTest) {
        return keyActiveTest.test(key);
    }

    @Override
    public long getMinimumFeeInTinybars(final Timestamp consensusTime) {
        return 100_000L;
    }

    @Override
    public Bytes getSuccessResultFor(final ExpirableTxnRecord.Builder childRecord) {
        final var receiptBuilder = childRecord.getReceiptBuilder();
        validateTrue(receiptBuilder != null, FAIL_INVALID);
        return encoder.encodeCreateSuccess(
                asTypedEvmAddress(childRecord.getReceiptBuilder().getTokenId().toGrpcTokenId()));
    }

    @Override
    public Bytes getFailureResultFor(final ResponseCodeEnum status) {
        return encoder.encodeCreateFailure(status);
    }

    /**
     * Decodes the given bytes of the fungible token.
     *
     * <p><b>Important: </b>This is an old version of this method and is superseded by
     * decodeFungibleCreateV2(). The selector for this function is derived from:
     * createFungibleToken((string,string,address,string,bool,uint32,bool,(uint256,(bool,address,bytes,bytes,address))[],
     * (uint32,address,uint32)),uint256,uint256)
     *
     * @param input encoded bytes containing selector and input parameters
     * @param aliasResolver function used to resolve aliases
     * @return TokenCreateWrapper codec
     */
    public static TokenCreateWrapper decodeFungibleCreate(
            final Bytes input, final UnaryOperator<byte[]> aliasResolver) {
        final Tuple decodedArguments =
                decodeFunctionCall(
                        input, TOKEN_CREATE_FUNGIBLE_SELECTOR, TOKEN_CREATE_FUNGIBLE_DECODER);

        return decodeTokenCreateWithoutFees(
                decodedArguments.get(0),
                true,
                decodedArguments.get(1),
                decodedArguments.get(2),
                aliasResolver);
    }

    public static TokenCreateWrapper decodeTokenCreateWithoutFees(
            @NotNull final Tuple tokenCreateStruct,
            final boolean isFungible,
            final BigInteger initSupply,
            final BigInteger decimals,
            final UnaryOperator<byte[]> aliasResolver) {
        final var tokenName = (String) tokenCreateStruct.get(0);
        final var tokenSymbol = (String) tokenCreateStruct.get(1);
        final var tokenTreasury =
                convertLeftPaddedAddressToAccountId(tokenCreateStruct.get(2), aliasResolver);
        final var memo = (String) tokenCreateStruct.get(3);
        final var isSupplyTypeFinite = (Boolean) tokenCreateStruct.get(4);
        final var maxSupply = (long) tokenCreateStruct.get(5);
        final var isFreezeDefault = (Boolean) tokenCreateStruct.get(6);
        final var tokenKeys = decodeTokenKeys(tokenCreateStruct.get(7), aliasResolver);
        final var tokenExpiry = decodeTokenExpiry(tokenCreateStruct.get(8), aliasResolver);

        return new TokenCreateWrapper(
                isFungible,
                tokenName,
                tokenSymbol,
                tokenTreasury.getAccountNum() != 0 ? tokenTreasury : null,
                memo,
                isSupplyTypeFinite,
                initSupply,
                decimals,
                maxSupply,
                isFreezeDefault,
                tokenKeys,
                tokenExpiry);
    }

    /**
     * Decodes the given bytes of the fungible token.
     *
     * <p><b>Important: </b>This is an old version of this method and is superseded by
     * decodeFungibleCreateWithFeesV2(). The selector for this function is derived from:
     * createFungibleTokenWithCustomFees((string,string,address,string,bool,uint32,bool,(uint256,(bool,address,bytes,
     * bytes,address))[],(uint32,address,uint32)),uint256,uint256,(uint32,address,bool,bool,address)[],
     * (uint32,uint32,uint32,uint32,bool,address)[])
     *
     * @param input encoded bytes containing selector and input parameters
     * @param aliasResolver function used to resolve aliases
     * @return TokenCreateWrapper codec
     */
    public static TokenCreateWrapper decodeFungibleCreateWithFees(
            final Bytes input, final UnaryOperator<byte[]> aliasResolver) {
        final Tuple decodedArguments =
                decodeFunctionCall(
                        input,
                        TOKEN_CREATE_FUNGIBLE_WITH_FEES_SELECTOR,
                        TOKEN_CREATE_FUNGIBLE_WITH_FEES_DECODER);

        final var tokenCreateWrapper =
                decodeTokenCreateWithoutFees(
                        decodedArguments.get(0),
                        true,
                        decodedArguments.get(1),
                        decodedArguments.get(2),
                        aliasResolver);
        final var fixedFees = decodeFixedFees(decodedArguments.get(3), aliasResolver);
        final var fractionalFees = decodeFractionalFees(decodedArguments.get(4), aliasResolver);
        tokenCreateWrapper.setFixedFees(fixedFees);
        tokenCreateWrapper.setFractionalFees(fractionalFees);

        return tokenCreateWrapper;
    }

    public static List<FixedFeeWrapper> decodeFixedFees(
            @NotNull final Tuple[] fixedFeesTuples, final UnaryOperator<byte[]> aliasResolver) {
        final List<FixedFeeWrapper> fixedFees = new ArrayList<>(fixedFeesTuples.length);
        for (final var fixedFeeTuple : fixedFeesTuples) {
            final var amount = (long) fixedFeeTuple.get(0);
            final var tokenId = convertAddressBytesToTokenID(fixedFeeTuple.get(1));
            final var useHbarsForPayment = (Boolean) fixedFeeTuple.get(2);
            final var useCurrentTokenForPayment = (Boolean) fixedFeeTuple.get(3);
            final var feeCollector =
                    convertLeftPaddedAddressToAccountId(fixedFeeTuple.get(4), aliasResolver);
            fixedFees.add(
                    new FixedFeeWrapper(
                            amount,
                            tokenId.getTokenNum() != 0 ? tokenId : null,
                            useHbarsForPayment,
                            useCurrentTokenForPayment,
                            feeCollector.getAccountNum() != 0 ? feeCollector : null));
        }
        return fixedFees;
    }

    public static List<FractionalFeeWrapper> decodeFractionalFees(
            @NotNull final Tuple[] fractionalFeesTuples,
            final UnaryOperator<byte[]> aliasResolver) {
        final List<FractionalFeeWrapper> fractionalFees =
                new ArrayList<>(fractionalFeesTuples.length);
        for (final var fractionalFeeTuple : fractionalFeesTuples) {
            final var numerator = (long) fractionalFeeTuple.get(0);
            final var denominator = (long) fractionalFeeTuple.get(1);
            final var minimumAmount = (long) fractionalFeeTuple.get(2);
            final var maximumAmount = (long) fractionalFeeTuple.get(3);
            final var netOfTransfers = (Boolean) fractionalFeeTuple.get(4);
            final var feeCollector =
                    convertLeftPaddedAddressToAccountId(fractionalFeeTuple.get(5), aliasResolver);
            fractionalFees.add(
                    new FractionalFeeWrapper(
                            numerator,
                            denominator,
                            minimumAmount,
                            maximumAmount,
                            netOfTransfers,
                            feeCollector.getAccountNum() != 0 ? feeCollector : null));
        }
        return fractionalFees;
    }

    public static TokenCreateWrapper decodeNonFungibleCreate(
            final Bytes input, final UnaryOperator<byte[]> aliasResolver) {
        final Tuple decodedArguments =
                decodeFunctionCall(
                        input,
                        TOKEN_CREATE_NON_FUNGIBLE_SELECTOR,
                        TOKEN_CREATE_NON_FUNGIBLE_DECODER);

        return decodeTokenCreateWithoutFees(
                decodedArguments.get(0), false, BigInteger.ZERO, BigInteger.ZERO, aliasResolver);
    }

    public static TokenCreateWrapper decodeNonFungibleCreateWithFees(
            final Bytes input, final UnaryOperator<byte[]> aliasResolver) {
        final Tuple decodedArguments =
                decodeFunctionCall(
                        input,
                        TOKEN_CREATE_NON_FUNGIBLE_WITH_FEES_SELECTOR,
                        TOKEN_CREATE_NON_FUNGIBLE_WITH_FEES_DECODER);

        final var tokenCreateWrapper =
                decodeTokenCreateWithoutFees(
                        decodedArguments.get(0),
                        false,
                        BigInteger.ZERO,
                        BigInteger.ZERO,
                        aliasResolver);
        final var fixedFees = decodeFixedFees(decodedArguments.get(1), aliasResolver);
        final var royaltyFees = decodeRoyaltyFees(decodedArguments.get(2), aliasResolver);
        tokenCreateWrapper.setFixedFees(fixedFees);
        tokenCreateWrapper.setRoyaltyFees(royaltyFees);

        return tokenCreateWrapper;
    }

    public static List<RoyaltyFeeWrapper> decodeRoyaltyFees(
            @NotNull final Tuple[] royaltyFeesTuples, final UnaryOperator<byte[]> aliasResolver) {
        final List<RoyaltyFeeWrapper> decodedRoyaltyFees =
                new ArrayList<>(royaltyFeesTuples.length);
        for (final var royaltyFeeTuple : royaltyFeesTuples) {
            final var numerator = (long) royaltyFeeTuple.get(0);
            final var denominator = (long) royaltyFeeTuple.get(1);

            // When at least 1 of the following 3 values is different from its default value,
            // we treat it as though the user has tried to specify a fallbackFixedFee
            final var fixedFeeAmount = (long) royaltyFeeTuple.get(2);
            final var fixedFeeTokenId = convertAddressBytesToTokenID(royaltyFeeTuple.get(3));
            final var fixedFeeUseHbars = (Boolean) royaltyFeeTuple.get(4);
            FixedFeeWrapper fixedFee = null;
            if (fixedFeeAmount != 0
                    || fixedFeeTokenId.getTokenNum() != 0
                    || Boolean.TRUE.equals(fixedFeeUseHbars)) {
                fixedFee =
                        new FixedFeeWrapper(
                                fixedFeeAmount,
                                fixedFeeTokenId.getTokenNum() != 0 ? fixedFeeTokenId : null,
                                fixedFeeUseHbars,
                                false,
                                null);
            }

            final var feeCollector =
                    convertLeftPaddedAddressToAccountId(royaltyFeeTuple.get(5), aliasResolver);
            decodedRoyaltyFees.add(
                    new RoyaltyFeeWrapper(
                            numerator,
                            denominator,
                            fixedFee,
                            feeCollector.getAccountNum() != 0 ? feeCollector : null));
        }
        return decodedRoyaltyFees;
    }

    /**
     * Decodes the given bytes of the fungible token.
     *
     * <p><b>Important: </b>This is the latest version and supersedes decodeFungibleCreate(). The
     * selector for this function is derived from:
     * createFungibleToken((string,string,address,string,bool,int64,bool,(uint256,(bool,address,bytes,bytes,address))[],
     * (uint32,address,uint32)),uint64,uint32)
     *
     * @param input encoded bytes containing selector and input parameters
     * @param aliasResolver function used to resolve aliases
     * @return TokenCreateWrapper codec
     */
    public static TokenCreateWrapper decodeFungibleCreateV2(
            final Bytes input, final UnaryOperator<byte[]> aliasResolver) {
        final Tuple decodedArguments =
                decodeFunctionCall(
                        input, TOKEN_CREATE_FUNGIBLE_SELECTOR_V2, TOKEN_CREATE_FUNGIBLE_DECODER);

        return decodeTokenCreateWithoutFees(
                decodedArguments.get(0),
                true,
                decodedArguments.get(1),
                decodedArguments.get(2),
                aliasResolver);
    }

    /**
     * Decodes the given bytes of the fungible token.
     *
     * <p><b>Important: </b>This is the latest version and supersedes
     * decodeFungibleCreateWithFees(). The selector for this function is derived from:
     * createFungibleTokenWithCustomFees((string,string,address,string,bool,int64,bool,(uint256,(bool,address,bytes,
     * bytes,address))[],(uint32,address,uint32)),uint64,uint32,(uint32,address,bool,bool,address)[],
     * (uint32,uint32,uint32,uint32,bool,address)[])
     *
     * @param input encoded bytes containing selector and input parameters
     * @param aliasResolver function used to resolve aliases
     * @return TokenCreateWrapper codec
     */
    public static TokenCreateWrapper decodeFungibleCreateWithFeesV2(
            final Bytes input, final UnaryOperator<byte[]> aliasResolver) {
        final Tuple decodedArguments =
                decodeFunctionCall(
                        input,
                        TOKEN_CREATE_FUNGIBLE_WITH_FEES_SELECTOR_V2,
                        TOKEN_CREATE_FUNGIBLE_WITH_FEES_DECODER);

        final var tokenCreateWrapper =
                decodeTokenCreateWithoutFees(
                        decodedArguments.get(0),
                        true,
                        decodedArguments.get(1),
                        decodedArguments.get(2),
                        aliasResolver);
        final var fixedFees = decodeFixedFees(decodedArguments.get(3), aliasResolver);
        final var fractionalFees = decodeFractionalFees(decodedArguments.get(4), aliasResolver);
        tokenCreateWrapper.setFixedFees(fixedFees);
        tokenCreateWrapper.setFractionalFees(fractionalFees);

        return tokenCreateWrapper;
    }

    /**
     * Decodes the given bytes of the non-fungible token.
     *
     * <p><b>Important: </b>This is the latest version and supersedes decodeNonFungibleCreateV2().
     * The selector for this function is derived from:
     * createNonFungibleToken((string,string,address,string,bool,int64,bool,(uint256,(bool,address,bytes,bytes,address))[],
     * (uint32,address,uint32)))
     *
     * @param input encoded bytes containing selector and input parameters
     * @param aliasResolver function used to resolve aliases
     * @return TokenCreateWrapper codec
     */
    public static TokenCreateWrapper decodeNonFungibleCreateV2(
            final Bytes input, final UnaryOperator<byte[]> aliasResolver) {
        final Tuple decodedArguments =
                decodeFunctionCall(
                        input,
                        TOKEN_CREATE_NON_FUNGIBLE_SELECTOR_V2,
                        TOKEN_CREATE_NON_FUNGIBLE_DECODER);

        return decodeTokenCreateWithoutFees(
                decodedArguments.get(0), false, BigInteger.ZERO, BigInteger.ZERO, aliasResolver);
    }

    /**
     * Decodes the given bytes of the non-fungible token.
     *
     * <p><b>Important: </b>This is the latest version and supersedes
     * decodeNonFungibleCreateWithFees(). The selector for this function is derived from:
     * createNonFungibleTokenWithCustomFees((string,string,address,string,bool,int64,bool,(uint256,(bool,address,bytes,
     * bytes,address))[],(uint32,address,uint32)),(uint32,address,bool,bool,address)[],
     * (uint32,uint32,uint32,address,bool,address)[])
     *
     * @param input encoded bytes containing selector and input parameters
     * @param aliasResolver function used to resolve aliases
     * @return TokenCreateWrapper codec
     */
    public static TokenCreateWrapper decodeNonFungibleCreateWithFeesV2(
            final Bytes input, final UnaryOperator<byte[]> aliasResolver) {
        final Tuple decodedArguments =
                decodeFunctionCall(
                        input,
                        TOKEN_CREATE_NON_FUNGIBLE_WITH_FEES_SELECTOR_V2,
                        TOKEN_CREATE_NON_FUNGIBLE_WITH_FEES_DECODER);

        final var tokenCreateWrapper =
                decodeTokenCreateWithoutFees(
                        decodedArguments.get(0),
                        false,
                        BigInteger.ZERO,
                        BigInteger.ZERO,
                        aliasResolver);
        final var fixedFees = decodeFixedFees(decodedArguments.get(1), aliasResolver);
        final var royaltyFees = decodeRoyaltyFees(decodedArguments.get(2), aliasResolver);
        tokenCreateWrapper.setFixedFees(fixedFees);
        tokenCreateWrapper.setRoyaltyFees(royaltyFees);

        return tokenCreateWrapper;
    }
}
