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

import static com.hedera.services.exceptions.ValidationUtils.validateTrue;
import static com.hedera.services.ledger.properties.AccountProperty.AUTO_RENEW_ACCOUNT_ID;
import static com.hedera.services.state.submerkle.EntityId.MISSING_ENTITY_ID;
import static com.hedera.services.store.contracts.precompile.codec.KeyValueWrapper.KeyValueType.INVALID_KEY;
import static com.hedera.services.store.contracts.precompile.codec.TokenCreateWrapper.FixedFeeWrapper.FixedFeePayment.INVALID_PAYMENT;
import static com.hedera.services.utils.EntityIdUtils.asTypedEvmAddress;
import static com.hederahashgraph.api.proto.java.HederaFunctionality.ContractCall;
import static com.hederahashgraph.api.proto.java.ResponseCodeEnum.*;

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
import com.hedera.services.store.contracts.precompile.codec.TokenKeyWrapper;
import com.hedera.services.store.contracts.precompile.utils.KeyActivationUtils;
import com.hedera.services.store.contracts.precompile.utils.PrecompilePricingUtils;
import com.hedera.services.store.models.Id;
import com.hedera.services.txns.token.validators.CreateChecks;
import com.hedera.services.utils.EntityIdUtils;
import com.hederahashgraph.api.proto.java.*;
import java.math.BigInteger;
import java.time.Instant;
import java.util.Objects;
import java.util.function.Predicate;
import java.util.function.UnaryOperator;
import javax.inject.Provider;
import org.apache.commons.codec.DecoderException;
import org.apache.tuweni.bytes.Bytes;
import org.hyperledger.besu.datatypes.Address;
import org.hyperledger.besu.datatypes.Wei;
import org.hyperledger.besu.evm.frame.MessageFrame;

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
            final DecodingFacade decoder,
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
        super(
                ledgers,
                decoder,
                sideEffects,
                syntheticTxnFactory,
                infrastructureFactory,
                pricingUtils);
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
                    case AbiConstants.ABI_ID_CREATE_FUNGIBLE_TOKEN -> decoder.decodeFungibleCreate(
                            input, aliasResolver);
                    case AbiConstants.ABI_ID_CREATE_FUNGIBLE_TOKEN_WITH_FEES -> decoder
                            .decodeFungibleCreateWithFees(input, aliasResolver);
                    case AbiConstants.ABI_ID_CREATE_NON_FUNGIBLE_TOKEN -> decoder
                            .decodeNonFungibleCreate(input, aliasResolver);
                    case AbiConstants.ABI_ID_CREATE_NON_FUNGIBLE_TOKEN_WITH_FEES -> decoder
                            .decodeNonFungibleCreateWithFees(input, aliasResolver);
                    case AbiConstants.ABI_ID_CREATE_FUNGIBLE_TOKEN_V2 -> decoder
                            .decodeFungibleCreateV2(input, aliasResolver);
                    case AbiConstants.ABI_ID_CREATE_FUNGIBLE_TOKEN_WITH_FEES_V2 -> decoder
                            .decodeFungibleCreateWithFeesV2(input, aliasResolver);
                    case AbiConstants.ABI_ID_CREATE_NON_FUNGIBLE_TOKEN_V2 -> decoder
                            .decodeNonFungibleCreateV2(input, aliasResolver);
                    case AbiConstants.ABI_ID_CREATE_NON_FUNGIBLE_TOKEN_WITH_FEES_V2 -> decoder
                            .decodeNonFungibleCreateWithFeesV2(input, aliasResolver);
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
}
