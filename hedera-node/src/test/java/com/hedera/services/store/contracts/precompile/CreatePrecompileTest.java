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
package com.hedera.services.store.contracts.precompile;

import static com.hedera.services.ledger.properties.AccountProperty.AUTO_RENEW_ACCOUNT_ID;
import static com.hedera.services.ledger.properties.AccountProperty.KEY;
import static com.hedera.services.state.EntityCreator.EMPTY_MEMO;
import static com.hedera.services.store.contracts.precompile.AbiConstants.ABI_ID_CREATE_FUNGIBLE_TOKEN;
import static com.hedera.services.store.contracts.precompile.AbiConstants.ABI_ID_CREATE_FUNGIBLE_TOKEN_WITH_FEES;
import static com.hedera.services.store.contracts.precompile.AbiConstants.ABI_ID_CREATE_NON_FUNGIBLE_TOKEN;
import static com.hedera.services.store.contracts.precompile.AbiConstants.ABI_ID_CREATE_NON_FUNGIBLE_TOKEN_WITH_FEES;
import static com.hedera.services.store.contracts.precompile.HTSTestsUtil.account;
import static com.hedera.services.store.contracts.precompile.HTSTestsUtil.contractAddr;
import static com.hedera.services.store.contracts.precompile.HTSTestsUtil.contractAddress;
import static com.hedera.services.store.contracts.precompile.HTSTestsUtil.createNonFungibleTokenCreateWrapperWithKeys;
import static com.hedera.services.store.contracts.precompile.HTSTestsUtil.createTokenCreateWrapperWithKeys;
import static com.hedera.services.store.contracts.precompile.HTSTestsUtil.feeCollector;
import static com.hedera.services.store.contracts.precompile.HTSTestsUtil.fixedFee;
import static com.hedera.services.store.contracts.precompile.HTSTestsUtil.invalidFullPrefix;
import static com.hedera.services.store.contracts.precompile.HTSTestsUtil.invalidSigResult;
import static com.hedera.services.store.contracts.precompile.HTSTestsUtil.pendingChildConsTime;
import static com.hedera.services.store.contracts.precompile.HTSTestsUtil.senderAddress;
import static com.hedera.services.store.contracts.precompile.HTSTestsUtil.successResult;
import static com.hedera.services.store.contracts.precompile.HTSTestsUtil.timestamp;
import static com.hederahashgraph.api.proto.java.ResponseCodeEnum.INVALID_FULL_PREFIX_SIGNATURE_FOR_PRECOMPILE;
import static com.hederahashgraph.api.proto.java.ResponseCodeEnum.INVALID_SIGNATURE;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import com.esaulpaugh.headlong.util.Integers;
import com.hedera.services.context.SideEffectsTracker;
import com.hedera.services.context.primitives.StateView;
import com.hedera.services.context.properties.GlobalDynamicProperties;
import com.hedera.services.contracts.sources.TxnAwareEvmSigsVerifier;
import com.hedera.services.exceptions.InvalidTransactionException;
import com.hedera.services.fees.FeeCalculator;
import com.hedera.services.fees.HbarCentExchange;
import com.hedera.services.fees.calculation.UsagePricesProvider;
import com.hedera.services.grpc.marshalling.ImpliedTransfersMarshal;
import com.hedera.services.ledger.TransactionalLedger;
import com.hedera.services.ledger.accounts.ContractAliases;
import com.hedera.services.ledger.properties.AccountProperty;
import com.hedera.services.ledger.properties.NftProperty;
import com.hedera.services.ledger.properties.TokenProperty;
import com.hedera.services.ledger.properties.TokenRelProperty;
import com.hedera.services.legacy.core.jproto.JContractIDKey;
import com.hedera.services.legacy.core.jproto.JECDSASecp256k1Key;
import com.hedera.services.legacy.core.jproto.JEd25519Key;
import com.hedera.services.legacy.core.jproto.JKey;
import com.hedera.services.legacy.core.jproto.TxnReceipt;
import com.hedera.services.pricing.AssetsLoader;
import com.hedera.services.records.RecordsHistorian;
import com.hedera.services.state.expiry.ExpiringCreations;
import com.hedera.services.state.merkle.MerkleAccount;
import com.hedera.services.state.merkle.MerkleToken;
import com.hedera.services.state.merkle.MerkleTokenRelStatus;
import com.hedera.services.state.merkle.MerkleUniqueToken;
import com.hedera.services.state.submerkle.EntityId;
import com.hedera.services.state.submerkle.ExpirableTxnRecord;
import com.hedera.services.store.AccountStore;
import com.hedera.services.store.TypedTokenStore;
import com.hedera.services.store.contracts.HederaStackedWorldStateUpdater;
import com.hedera.services.store.contracts.UpdateTrackingLedgerAccount;
import com.hedera.services.store.contracts.WorldLedgers;
import com.hedera.services.store.contracts.precompile.codec.DecodingFacade;
import com.hedera.services.store.contracts.precompile.codec.EncodingFacade;
import com.hedera.services.store.contracts.precompile.codec.KeyValueWrapper;
import com.hedera.services.store.contracts.precompile.codec.TokenCreateWrapper;
import com.hedera.services.store.contracts.precompile.codec.TokenKeyWrapper;
import com.hedera.services.store.contracts.precompile.utils.PrecompilePricingUtils;
import com.hedera.services.store.models.Id;
import com.hedera.services.store.models.NftId;
import com.hedera.services.txns.token.CreateLogic;
import com.hedera.services.txns.token.validators.CreateChecks;
import com.hedera.services.utils.EntityIdUtils;
import com.hedera.services.utils.accessors.AccessorFactory;
import com.hederahashgraph.api.proto.java.AccountID;
import com.hederahashgraph.api.proto.java.ResponseCodeEnum;
import com.hederahashgraph.api.proto.java.TokenCreateTransactionBody;
import com.hederahashgraph.api.proto.java.TokenID;
import com.hederahashgraph.api.proto.java.TransactionBody;
import com.hederahashgraph.api.proto.java.TransactionID;
import com.hederahashgraph.fee.FeeObject;
import java.math.BigInteger;
import java.util.Collections;
import java.util.List;
import java.util.Optional;
import java.util.function.Function;
import org.apache.commons.codec.DecoderException;
import org.apache.commons.lang3.tuple.Pair;
import org.apache.tuweni.bytes.Bytes;
import org.hyperledger.besu.datatypes.Wei;
import org.hyperledger.besu.evm.account.EvmAccount;
import org.hyperledger.besu.evm.frame.BlockValues;
import org.hyperledger.besu.evm.frame.MessageFrame;
import org.hyperledger.besu.evm.gascalculator.GasCalculator;
import org.hyperledger.besu.evm.worldstate.WorldUpdater;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.Mockito;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class CreatePrecompileTest {

    @Mock private GlobalDynamicProperties dynamicProperties;
    @Mock private GasCalculator gasCalculator;
    @Mock private MessageFrame frame;
    @Mock private TxnAwareEvmSigsVerifier sigsVerifier;
    @Mock private RecordsHistorian recordsHistorian;
    @Mock private DecodingFacade decoder;
    @Mock private EncodingFacade encoder;
    @Mock private SideEffectsTracker sideEffects;
    @Mock private TransactionBody.Builder mockSynthBodyBuilder;
    @Mock private TokenCreateTransactionBody tokenCreateTransactionBody;
    @Mock private ExpirableTxnRecord.Builder mockRecordBuilder;
    @Mock private SyntheticTxnFactory syntheticTxnFactory;
    @Mock private HederaStackedWorldStateUpdater worldUpdater;
    @Mock private WorldLedgers wrappedLedgers;
    @Mock private TransactionalLedger<NftId, NftProperty, MerkleUniqueToken> nfts;

    @Mock
    private TransactionalLedger<Pair<AccountID, TokenID>, TokenRelProperty, MerkleTokenRelStatus>
            tokenRels;

    @Mock private TransactionalLedger<AccountID, AccountProperty, MerkleAccount> accounts;
    @Mock private TransactionalLedger<TokenID, TokenProperty, MerkleToken> tokens;
    @Mock private ExpiringCreations creator;
    @Mock private ImpliedTransfersMarshal impliedTransfersMarshal;
    @Mock private FeeCalculator feeCalculator;
    @Mock private StateView stateView;
    @Mock private ContractAliases aliases;
    @Mock private UsagePricesProvider resourceCosts;
    @Mock private CreateChecks createChecks;
    @Mock private CreateLogic createLogic;
    @Mock private TypedTokenStore typedTokenStore;
    @Mock private AccountStore accountStore;
    @Mock private InfrastructureFactory infrastructureFactory;
    @Mock private AssetsLoader assetLoader;
    @Mock private HbarCentExchange exchange;
    @Mock private AccessorFactory accessorFactory;

    private HTSPrecompiledContract subject;
    private UpdateTrackingLedgerAccount senderMutableAccount;
    private UpdateTrackingLedgerAccount fundingMutableAccount;

    private static final long TEST_SERVICE_FEE = 100L;
    private static final long TEST_NODE_FEE = 100_000L;
    private static final long TEST_NETWORK_FEE = 100L;
    private static final long EXPECTED_TINYBARS_REQUIREMENT =
            (TEST_SERVICE_FEE + TEST_NETWORK_FEE + TEST_NODE_FEE)
                    + (TEST_SERVICE_FEE + TEST_NETWORK_FEE + TEST_NODE_FEE) / 5;
    private static final long SENDER_INITIAL_BALANCE = 1_000_000L;
    private static final long FUNDING_ACCOUNT_INITIAL_BALANCE = 123_123L;

    @BeforeEach
    void setUp() {
        PrecompilePricingUtils precompilePricingUtils =
                new PrecompilePricingUtils(
                        assetLoader,
                        exchange,
                        () -> feeCalculator,
                        resourceCosts,
                        stateView,
                        accessorFactory);
        subject =
                new HTSPrecompiledContract(
                        dynamicProperties,
                        gasCalculator,
                        recordsHistorian,
                        sigsVerifier,
                        decoder,
                        encoder,
                        syntheticTxnFactory,
                        creator,
                        impliedTransfersMarshal,
                        () -> feeCalculator,
                        stateView,
                        precompilePricingUtils,
                        infrastructureFactory);
        given(infrastructureFactory.newSideEffects()).willReturn(sideEffects);
        given(worldUpdater.permissivelyUnaliased(any()))
                .willAnswer(invocationOnMock -> invocationOnMock.getArgument(0));
    }

    @Test
    void gasAndValueRequirementCalculationWorksAsExpected() {
        // given
        given(dynamicProperties.isHTSPrecompileCreateEnabled()).willReturn(true);
        given(frame.getWorldUpdater()).willReturn(worldUpdater);
        given(frame.getRemainingGas()).willReturn(100_000L);
        Optional<WorldUpdater> parent = Optional.of(worldUpdater);
        given(worldUpdater.parentUpdater()).willReturn(parent);
        given(worldUpdater.wrappedTrackingLedgers(any())).willReturn(wrappedLedgers);
        givenValidGasCalculation();
        given(dynamicProperties.isHTSPrecompileCreateEnabled()).willReturn(true);
        given(frame.getWorldUpdater()).willReturn(worldUpdater);
        given(worldUpdater.wrappedTrackingLedgers(any())).willReturn(wrappedLedgers);
        given(wrappedLedgers.accounts()).willReturn(accounts);
        given(frame.getSenderAddress()).willReturn(senderAddress);
        Bytes pretendArguments = Bytes.of(Integers.toBytes(ABI_ID_CREATE_FUNGIBLE_TOKEN));
        final TokenCreateWrapper wrapper = createTokenCreateWrapperWithKeys(List.of());
        given(decoder.decodeFungibleCreate(any(), any())).willReturn(wrapper);
        given(mockSynthBodyBuilder.build())
                .willReturn(
                        TransactionBody.newBuilder()
                                .setTokenCreation(tokenCreateTransactionBody)
                                .build());
        given(mockSynthBodyBuilder.setTransactionID(any(TransactionID.class)))
                .willReturn(mockSynthBodyBuilder);
        given(syntheticTxnFactory.createTokenCreate(wrapper)).willReturn(mockSynthBodyBuilder);

        subject.compute(pretendArguments, frame);

        // then
        assertEquals(
                subject.getPrecompile().getMinimumFeeInTinybars(timestamp),
                subject.gasRequirement(pretendArguments));
        final var tinyBarsRequirement =
                EXPECTED_TINYBARS_REQUIREMENT
                        - subject.getPrecompile().getMinimumFeeInTinybars(timestamp);
        assertEquals(
                SENDER_INITIAL_BALANCE - tinyBarsRequirement,
                senderMutableAccount.getBalance().toLong());
        assertEquals(
                FUNDING_ACCOUNT_INITIAL_BALANCE + tinyBarsRequirement,
                fundingMutableAccount.getBalance().toLong());
    }

    @Test
    void gasAndValueRequirementThrowsWhenValueIsNotSufficient() {
        // given
        given(dynamicProperties.isHTSPrecompileCreateEnabled()).willReturn(true);
        given(frame.getWorldUpdater()).willReturn(worldUpdater);
        Optional<WorldUpdater> parent = Optional.of(worldUpdater);
        given(worldUpdater.wrappedTrackingLedgers(any())).willReturn(wrappedLedgers);
        given(wrappedLedgers.accounts()).willReturn(accounts);

        given(frame.getSenderAddress()).willReturn(senderAddress);
        Bytes pretendArguments = Bytes.of(Integers.toBytes(ABI_ID_CREATE_FUNGIBLE_TOKEN));
        final TokenCreateWrapper wrapper = createTokenCreateWrapperWithKeys(List.of());
        given(decoder.decodeFungibleCreate(any(), any())).willReturn(wrapper);
        given(mockSynthBodyBuilder.build())
                .willReturn(
                        TransactionBody.newBuilder()
                                .setTokenCreation(tokenCreateTransactionBody)
                                .build());
        given(mockSynthBodyBuilder.setTransactionID(any(TransactionID.class)))
                .willReturn(mockSynthBodyBuilder);
        given(syntheticTxnFactory.createTokenCreate(wrapper)).willReturn(mockSynthBodyBuilder);
        given(feeCalculator.computeFee(any(), any(), any(), any()))
                .willReturn(new FeeObject(100_000L, 100_000L, 100_000L));
        given(feeCalculator.estimatedGasPriceInTinybars(any(), any())).willReturn(1L);
        given(frame.getValue()).willReturn(Wei.of(1_000L));
        final var blockValuesMock = mock(BlockValues.class);
        given(frame.getBlockValues()).willReturn(blockValuesMock);
        given(blockValuesMock.getTimestamp()).willReturn(timestamp.getSeconds());

        subject.prepareFields(frame);
        subject.prepareComputation(pretendArguments, a -> a);

        final var precompile = subject.getPrecompile();
        assertThrows(InvalidTransactionException.class, () -> precompile.handleSentHbars(frame));

        // then
        Mockito.verifyNoMoreInteractions(syntheticTxnFactory);
    }

    @Test
    void createFungibleHappyPathWorks() {
        // test-specific preparations
        final var tokenCreateWrapper =
                createTokenCreateWrapperWithKeys(
                        List.of(
                                new TokenKeyWrapper(
                                        1,
                                        new KeyValueWrapper(
                                                false,
                                                EntityIdUtils.contractIdFromEvmAddress(
                                                        contractAddress),
                                                new byte[] {},
                                                new byte[] {},
                                                null)),
                                new TokenKeyWrapper(
                                        8,
                                        new KeyValueWrapper(
                                                false,
                                                null,
                                                new byte[] {},
                                                new byte[] {},
                                                EntityIdUtils.contractIdFromEvmAddress(
                                                        contractAddress)))));
        Bytes pretendArguments = Bytes.of(Integers.toBytes(ABI_ID_CREATE_FUNGIBLE_TOKEN));
        given(decoder.decodeFungibleCreate(eq(pretendArguments), any()))
                .willReturn(tokenCreateWrapper);

        prepareAndAssertCreateHappyPathSucceeds(tokenCreateWrapper, pretendArguments);
    }

    @Test
    void createNonFungibleHappyPathWorks() {
        // test-specific preparations
        final var tokenCreateWrapper =
                createNonFungibleTokenCreateWrapperWithKeys(
                        List.of(
                                new TokenKeyWrapper(
                                        1,
                                        new KeyValueWrapper(
                                                false,
                                                null,
                                                new byte[] {},
                                                new byte
                                                        [JECDSASecp256k1Key
                                                                .ECDSA_SECP256K1_COMPRESSED_KEY_LENGTH],
                                                null))));
        Bytes pretendArguments = Bytes.of(Integers.toBytes(ABI_ID_CREATE_NON_FUNGIBLE_TOKEN));
        given(decoder.decodeNonFungibleCreate(eq(pretendArguments), any()))
                .willReturn(tokenCreateWrapper);
        given(wrappedLedgers.accounts()).willReturn(accounts);
        given(accounts.get(any(), eq(AUTO_RENEW_ACCOUNT_ID)))
                .willReturn(EntityId.fromGrpcAccountId(account));
        given(sigsVerifier.cryptoKeyIsActive(any())).willReturn(true);

        prepareAndAssertCreateHappyPathSucceeds(tokenCreateWrapper, pretendArguments);
    }

    @Test
    void createFungibleWithFeesHappyPathWorks() {
        // test-specific preparations
        final var tokenCreateWrapper =
                createTokenCreateWrapperWithKeys(
                        List.of(
                                new TokenKeyWrapper(
                                        1,
                                        new KeyValueWrapper(
                                                false,
                                                null,
                                                new byte[] {},
                                                new byte[] {},
                                                EntityIdUtils.contractIdFromEvmAddress(
                                                        contractAddress)))));
        tokenCreateWrapper.setFixedFees(List.of(fixedFee));
        tokenCreateWrapper.setFractionalFees(List.of(HTSTestsUtil.fractionalFee));
        Bytes pretendArguments = Bytes.of(Integers.toBytes(ABI_ID_CREATE_FUNGIBLE_TOKEN_WITH_FEES));
        given(decoder.decodeFungibleCreateWithFees(eq(pretendArguments), any()))
                .willReturn(tokenCreateWrapper);

        prepareAndAssertCreateHappyPathSucceeds(tokenCreateWrapper, pretendArguments);
    }

    @Test
    void createNonFungibleWithFeesHappyPathWorks() {
        // test-specific preparations
        final var tokenCreateWrapper =
                createNonFungibleTokenCreateWrapperWithKeys(
                        List.of(
                                new TokenKeyWrapper(
                                        1,
                                        new KeyValueWrapper(
                                                true, null, new byte[] {}, new byte[] {}, null))));
        tokenCreateWrapper.setFixedFees(List.of(fixedFee));
        tokenCreateWrapper.setRoyaltyFees(List.of(HTSTestsUtil.royaltyFee));
        Bytes pretendArguments =
                Bytes.of(Integers.toBytes(ABI_ID_CREATE_NON_FUNGIBLE_TOKEN_WITH_FEES));
        given(wrappedLedgers.accounts()).willReturn(accounts);
        given(accounts.get(any(), eq(AUTO_RENEW_ACCOUNT_ID)))
                .willReturn(EntityId.fromGrpcAccountId(account));
        given(decoder.decodeNonFungibleCreateWithFees(eq(pretendArguments), any()))
                .willReturn(tokenCreateWrapper);
        given(accounts.get(any(), eq(KEY)))
                .willReturn(
                        new JContractIDKey(
                                EntityIdUtils.contractIdFromEvmAddress(contractAddress)));

        prepareAndAssertCreateHappyPathSucceeds(tokenCreateWrapper, pretendArguments);
    }

    @Test
    void createFailurePath() {
        givenMinimalFrameContext();
        givenValidGasCalculation();

        given(wrappedLedgers.accounts()).willReturn(accounts);
        given(aliases.resolveForEvm(any()))
                .willAnswer(invocationOnMock -> invocationOnMock.getArgument(0));
        given(worldUpdater.aliases()).willReturn(aliases);
        Bytes pretendArguments = Bytes.of(Integers.toBytes(ABI_ID_CREATE_FUNGIBLE_TOKEN));
        final var tokenCreateWrapper =
                createTokenCreateWrapperWithKeys(
                        List.of(
                                new TokenKeyWrapper(
                                        1,
                                        new KeyValueWrapper(
                                                false,
                                                null,
                                                new byte[JEd25519Key.ED25519_BYTE_LENGTH],
                                                new byte[] {},
                                                null))));
        given(decoder.decodeFungibleCreate(eq(pretendArguments), any()))
                .willReturn(tokenCreateWrapper);
        given(syntheticTxnFactory.createTokenCreate(tokenCreateWrapper))
                .willReturn(mockSynthBodyBuilder);
        given(mockSynthBodyBuilder.build())
                .willReturn(
                        TransactionBody.newBuilder()
                                .setTokenCreation(tokenCreateTransactionBody)
                                .build());
        given(mockSynthBodyBuilder.setTransactionID(any(TransactionID.class)))
                .willReturn(mockSynthBodyBuilder);
        given(frame.getSenderAddress()).willReturn(senderAddress);
        given(sigsVerifier.hasActiveKey(Mockito.anyBoolean(), any(), any(), any()))
                .willReturn(true);
        final var validator = Mockito.mock(Function.class);
        given(createChecks.validatorForConsTime(any())).willReturn(validator);
        given(validator.apply(any())).willReturn(ResponseCodeEnum.OK);
        given(recordsHistorian.nextFollowingChildConsensusTime()).willReturn(pendingChildConsTime);
        given(sigsVerifier.cryptoKeyIsActive(any(JKey.class)))
                .willThrow(new InvalidTransactionException(INVALID_SIGNATURE));
        given(creator.createUnsuccessfulSyntheticRecord(INVALID_SIGNATURE))
                .willReturn(mockRecordBuilder);
        given(encoder.encodeCreateFailure(INVALID_SIGNATURE)).willReturn(invalidSigResult);
        given(infrastructureFactory.newCreateChecks()).willReturn(createChecks);

        // when:
        final var result = subject.compute(pretendArguments, frame);

        // then:
        assertEquals(invalidSigResult, result);

        verify(creator).createUnsuccessfulSyntheticRecord(INVALID_SIGNATURE);
        verify(createLogic, never())
                .create(
                        pendingChildConsTime.getEpochSecond(),
                        EntityIdUtils.accountIdFromEvmAddress(senderAddress),
                        tokenCreateTransactionBody);
        verify(wrappedLedgers, never()).commit();
        verify(worldUpdater)
                .manageInProgressRecord(recordsHistorian, mockRecordBuilder, mockSynthBodyBuilder);
    }

    @Test
    void createFailsWhenCreateChecksAreNotSuccessful() {
        givenValidGasCalculation();
        given(frame.getRemainingGas()).willReturn(100_000L);
        given(dynamicProperties.isHTSPrecompileCreateEnabled()).willReturn(true);
        given(frame.getWorldUpdater()).willReturn(worldUpdater);
        Optional<WorldUpdater> parent = Optional.of(worldUpdater);
        given(worldUpdater.parentUpdater()).willReturn(parent);
        given(worldUpdater.wrappedTrackingLedgers(any())).willReturn(wrappedLedgers);
        given(wrappedLedgers.accounts()).willReturn(accounts);
        Bytes pretendArguments = Bytes.of(Integers.toBytes(ABI_ID_CREATE_FUNGIBLE_TOKEN));
        final var tokenCreateWrapper =
                createTokenCreateWrapperWithKeys(
                        List.of(
                                new TokenKeyWrapper(
                                        1,
                                        new KeyValueWrapper(
                                                false,
                                                null,
                                                new byte[JEd25519Key.ED25519_BYTE_LENGTH],
                                                new byte[] {},
                                                null))));
        given(decoder.decodeFungibleCreate(eq(pretendArguments), any()))
                .willReturn(tokenCreateWrapper);
        given(mockSynthBodyBuilder.build())
                .willReturn(
                        TransactionBody.newBuilder()
                                .setTokenCreation(tokenCreateTransactionBody)
                                .build());
        given(mockSynthBodyBuilder.setTransactionID(any(TransactionID.class)))
                .willReturn(mockSynthBodyBuilder);
        given(syntheticTxnFactory.createTokenCreate(tokenCreateWrapper))
                .willReturn(mockSynthBodyBuilder);
        given(frame.getSenderAddress()).willReturn(senderAddress);
        final var tokenCreateValidator = Mockito.mock(Function.class);
        given(createChecks.validatorForConsTime(any())).willReturn(tokenCreateValidator);
        given(tokenCreateValidator.apply(any())).willReturn(INVALID_SIGNATURE);
        given(creator.createUnsuccessfulSyntheticRecord(INVALID_SIGNATURE))
                .willReturn(mockRecordBuilder);
        given(encoder.encodeCreateFailure(INVALID_SIGNATURE)).willReturn(invalidSigResult);
        given(infrastructureFactory.newCreateChecks()).willReturn(createChecks);

        // when:
        final var result = subject.compute(pretendArguments, frame);

        // then:
        assertEquals(invalidSigResult, result);

        verify(creator).createUnsuccessfulSyntheticRecord(INVALID_SIGNATURE);
        verify(createLogic, never())
                .create(
                        pendingChildConsTime.getEpochSecond(),
                        EntityIdUtils.accountIdFromEvmAddress(senderAddress),
                        tokenCreateTransactionBody);
        verify(wrappedLedgers, never()).commit();
        verify(worldUpdater)
                .manageInProgressRecord(recordsHistorian, mockRecordBuilder, mockSynthBodyBuilder);
    }

    @Test
    void validateAdminKeySignatureFailsIfKeyIsInvalid() {
        givenMinimalFrameContext();
        givenValidGasCalculation();
        given(wrappedLedgers.accounts()).willReturn(accounts);
        given(frame.getRemainingGas()).willReturn(100_000L);
        given(dynamicProperties.isHTSPrecompileCreateEnabled()).willReturn(true);
        given(frame.getWorldUpdater()).willReturn(worldUpdater);
        Optional<WorldUpdater> parent = Optional.of(worldUpdater);
        given(worldUpdater.parentUpdater()).willReturn(parent);
        given(worldUpdater.wrappedTrackingLedgers(any())).willReturn(wrappedLedgers);
        given(wrappedLedgers.accounts()).willReturn(accounts);
        Bytes pretendArguments = Bytes.of(Integers.toBytes(ABI_ID_CREATE_FUNGIBLE_TOKEN));
        final var keyValueMock = Mockito.mock(KeyValueWrapper.class);
        when(keyValueMock.getKeyValueType())
                .thenReturn(KeyValueWrapper.KeyValueType.CONTRACT_ID)
                .thenReturn(KeyValueWrapper.KeyValueType.INVALID_KEY);
        final var tokenCreateWrapper =
                createTokenCreateWrapperWithKeys(List.of(new TokenKeyWrapper(1, keyValueMock)));
        given(decoder.decodeFungibleCreate(eq(pretendArguments), any()))
                .willReturn(tokenCreateWrapper);
        given(mockSynthBodyBuilder.build())
                .willReturn(
                        TransactionBody.newBuilder()
                                .setTokenCreation(tokenCreateTransactionBody)
                                .build());
        given(mockSynthBodyBuilder.setTransactionID(any(TransactionID.class)))
                .willReturn(mockSynthBodyBuilder);
        given(syntheticTxnFactory.createTokenCreate(tokenCreateWrapper))
                .willReturn(mockSynthBodyBuilder);
        given(frame.getSenderAddress()).willReturn(senderAddress);
        given(sigsVerifier.hasActiveKey(Mockito.anyBoolean(), any(), any(), any()))
                .willReturn(true);
        final var tokenCreateValidator = Mockito.mock(Function.class);
        given(createChecks.validatorForConsTime(any())).willReturn(tokenCreateValidator);
        given(tokenCreateValidator.apply(any())).willReturn(ResponseCodeEnum.OK);
        given(aliases.resolveForEvm(any()))
                .willAnswer(invocationOnMock -> invocationOnMock.getArgument(0));
        given(worldUpdater.aliases()).willReturn(aliases);
        given(
                        creator.createUnsuccessfulSyntheticRecord(
                                INVALID_FULL_PREFIX_SIGNATURE_FOR_PRECOMPILE))
                .willReturn(mockRecordBuilder);
        given(encoder.encodeCreateFailure(INVALID_FULL_PREFIX_SIGNATURE_FOR_PRECOMPILE))
                .willReturn(invalidFullPrefix);
        given(infrastructureFactory.newCreateChecks()).willReturn(createChecks);

        // when:
        final var result = subject.compute(pretendArguments, frame);

        // then:
        assertEquals(invalidFullPrefix, result);

        verify(creator)
                .createUnsuccessfulSyntheticRecord(INVALID_FULL_PREFIX_SIGNATURE_FOR_PRECOMPILE);
        verify(createLogic, never())
                .create(
                        pendingChildConsTime.getEpochSecond(),
                        EntityIdUtils.accountIdFromEvmAddress(senderAddress),
                        tokenCreateTransactionBody);
        verify(wrappedLedgers, never()).commit();
        verify(worldUpdater)
                .manageInProgressRecord(recordsHistorian, mockRecordBuilder, mockSynthBodyBuilder);
    }

    @Test
    void createReturnsNullAndSetsRevertReasonWhenKeyWithMultipleKeyTypesIsPresent() {
        // test-specific preparations
        final var tokenCreateWrapper =
                createTokenCreateWrapperWithKeys(
                        List.of(
                                new TokenKeyWrapper(
                                        1,
                                        new KeyValueWrapper(
                                                false,
                                                EntityIdUtils.contractIdFromEvmAddress(
                                                        contractAddress),
                                                new byte[JEd25519Key.ED25519_BYTE_LENGTH],
                                                new byte[] {},
                                                null))));

        prepareAndAssertRevertReasonIsSetAndNullIsReturned(tokenCreateWrapper);
    }

    @Test
    void createReturnsNullAndSetsRevertReasonsWhenKeyWithNoKeyTypeToApplyToPresent() {
        // test-specific preparations
        final var tokenCreateWrapper =
                createTokenCreateWrapperWithKeys(
                        List.of(
                                new TokenKeyWrapper(
                                        0,
                                        new KeyValueWrapper(
                                                false,
                                                EntityIdUtils.contractIdFromEvmAddress(
                                                        contractAddress),
                                                new byte[] {},
                                                new byte[] {},
                                                null)),
                                new TokenKeyWrapper(
                                        1,
                                        new KeyValueWrapper(
                                                false,
                                                null,
                                                new byte[JEd25519Key.ED25519_BYTE_LENGTH],
                                                new byte[] {},
                                                null))));

        prepareAndAssertRevertReasonIsSetAndNullIsReturned(tokenCreateWrapper);
    }

    @Test
    void createReturnsNullAndSetsRevertReasonWhenMultipleKeysForSameKeyTypePresent() {
        // test-specific preparations
        final var tokenCreateWrapper =
                createTokenCreateWrapperWithKeys(
                        List.of(
                                new TokenKeyWrapper(
                                        1,
                                        new KeyValueWrapper(
                                                false,
                                                EntityIdUtils.contractIdFromEvmAddress(
                                                        contractAddress),
                                                new byte[] {},
                                                new byte[] {},
                                                null)),
                                new TokenKeyWrapper(
                                        1,
                                        new KeyValueWrapper(
                                                false,
                                                null,
                                                new byte[] {},
                                                new byte[] {},
                                                EntityIdUtils.contractIdFromEvmAddress(
                                                        contractAddress)))));

        prepareAndAssertRevertReasonIsSetAndNullIsReturned(tokenCreateWrapper);
    }

    @Test
    void createReturnsNullAndSetsRevertReasonWhenKeyWithBitBiggerThan6IsSet() {
        // test-specific preparations
        final var tokenCreateWrapper =
                createTokenCreateWrapperWithKeys(
                        List.of(
                                new TokenKeyWrapper(
                                        128,
                                        new KeyValueWrapper(
                                                false,
                                                null,
                                                new byte[] {},
                                                new byte[] {},
                                                EntityIdUtils.contractIdFromEvmAddress(
                                                        contractAddress)))));

        prepareAndAssertRevertReasonIsSetAndNullIsReturned(tokenCreateWrapper);
    }

    @Test
    void createReturnsNullAndSetsRevertReasonWhenSenderKeyCannotBeDecoded()
            throws DecoderException {
        // test-specific preparations
        final var tokenCreateWrapper = Mockito.mock(TokenCreateWrapper.class);
        given(wrappedLedgers.accounts()).willReturn(accounts);
        final var keyMock = Mockito.mock(JKey.class);
        given(accounts.get(any(), any())).willReturn(keyMock);

        prepareAndAssertRevertReasonIsSetAndNullIsReturned(tokenCreateWrapper);
    }

    @Test
    void createReturnsNullAndSetsRevertReasonWhenInitSupplyIsBiggerThanMaxLong() {
        // test-specific preparations
        final var invalidTokenCreate =
                new TokenCreateWrapper(
                        true,
                        "",
                        "",
                        null,
                        "",
                        false,
                        new BigInteger(
                                "9223372036854775809"), // LONG_MAX (9,223,372,036,854,775,807) + 2
                        BigInteger.ZERO,
                        0L,
                        false,
                        Collections.emptyList(),
                        null);

        prepareAndAssertRevertReasonIsSetAndNullIsReturned(invalidTokenCreate);
    }

    @Test
    void createReturnsNullAndSetsRevertReasonWhenDecimalsIsBiggerThanMaxInt() {
        // test-specific preparations
        final var invalidTokenCreate =
                new TokenCreateWrapper(
                        true,
                        "",
                        "",
                        null,
                        "",
                        false,
                        BigInteger.ZERO,
                        BigInteger.valueOf(Long.MAX_VALUE),
                        0L,
                        false,
                        Collections.emptyList(),
                        null);

        prepareAndAssertRevertReasonIsSetAndNullIsReturned(invalidTokenCreate);
    }

    @Test
    void createReturnsNullAndSetsRevertReasonWhenInvalidFixedFeeIsPresent() {
        // test-specific preparations
        final var invalidTokenCreate = createTokenCreateWrapperWithKeys(Collections.emptyList());
        final var fixedFeeMock = mock(TokenCreateWrapper.FixedFeeWrapper.class);
        given(fixedFeeMock.getFixedFeePayment())
                .willReturn(TokenCreateWrapper.FixedFeeWrapper.FixedFeePayment.INVALID_PAYMENT);
        invalidTokenCreate.setFixedFees(List.of(fixedFeeMock));

        prepareAndAssertRevertReasonIsSetAndNullIsReturned(invalidTokenCreate);
    }

    @Test
    void createReturnsNullAndSetsRevertReasonWhenRoyaltyFeeWithInvalidFallbackFeeIsPresent() {
        // test-specific preparations
        final var invalidTokenCreate = createTokenCreateWrapperWithKeys(Collections.emptyList());
        final var fixedFeeMock = mock(TokenCreateWrapper.FixedFeeWrapper.class);
        given(fixedFeeMock.getFixedFeePayment())
                .willReturn(TokenCreateWrapper.FixedFeeWrapper.FixedFeePayment.INVALID_PAYMENT);
        invalidTokenCreate.setRoyaltyFees(
                List.of(
                        new TokenCreateWrapper.RoyaltyFeeWrapper(1, 2, null, feeCollector),
                        new TokenCreateWrapper.RoyaltyFeeWrapper(
                                1, 2, fixedFeeMock, feeCollector)));

        prepareAndAssertRevertReasonIsSetAndNullIsReturned(invalidTokenCreate);
    }

    private void prepareAndAssertCreateHappyPathSucceeds(
            TokenCreateWrapper tokenCreateWrapper, Bytes pretendArguments) {
        givenMinimalFrameContext();
        givenLedgers();
        givenValidGasCalculation();
        given(aliases.resolveForEvm(any()))
                .willAnswer(invocationOnMock -> invocationOnMock.getArgument(0));
        given(worldUpdater.aliases()).willReturn(aliases);
        given(mockSynthBodyBuilder.build())
                .willReturn(
                        TransactionBody.newBuilder()
                                .setTokenCreation(tokenCreateTransactionBody)
                                .build());
        given(mockSynthBodyBuilder.setTransactionID(any(TransactionID.class)))
                .willReturn(mockSynthBodyBuilder);
        given(syntheticTxnFactory.createTokenCreate(tokenCreateWrapper))
                .willReturn(mockSynthBodyBuilder);
        given(frame.getSenderAddress()).willReturn(senderAddress);
        given(mockSynthBodyBuilder.getTokenCreation()).willReturn(tokenCreateTransactionBody);
        given(sigsVerifier.hasActiveKey(Mockito.anyBoolean(), any(), any(), any()))
                .willReturn(true);
        final var tokenCreateValidator = Mockito.mock(Function.class);
        given(createChecks.validatorForConsTime(any())).willReturn(tokenCreateValidator);
        given(tokenCreateValidator.apply(any())).willReturn(ResponseCodeEnum.OK);
        given(infrastructureFactory.newAccountStore(accounts)).willReturn(accountStore);
        given(
                        infrastructureFactory.newTokenStore(
                                accountStore, sideEffects, tokens, nfts, tokenRels))
                .willReturn(typedTokenStore);
        given(infrastructureFactory.newTokenCreateLogic(accountStore, typedTokenStore))
                .willReturn(createLogic);
        given(infrastructureFactory.newCreateChecks()).willReturn(createChecks);
        given(recordsHistorian.nextFollowingChildConsensusTime()).willReturn(pendingChildConsTime);
        given(
                        creator.createSuccessfulSyntheticRecord(
                                Collections.emptyList(), sideEffects, EMPTY_MEMO))
                .willReturn(mockRecordBuilder);
        given(mockRecordBuilder.getReceiptBuilder())
                .willReturn(
                        TxnReceipt.newBuilder()
                                .setTokenId(
                                        EntityId.fromGrpcTokenId(
                                                TokenID.newBuilder().setTokenNum(1L).build())));
        given(encoder.encodeCreateSuccess(any())).willReturn(successResult);

        // when:
        final var result = subject.computePrecompile(pretendArguments, frame);

        // then:
        assertEquals(successResult, result.getOutput());
        // and:
        verify(createLogic)
                .create(
                        pendingChildConsTime.getEpochSecond(),
                        EntityIdUtils.accountIdFromEvmAddress(senderAddress),
                        tokenCreateTransactionBody);
        verify(wrappedLedgers).commit();
        verify(worldUpdater)
                .manageInProgressRecord(recordsHistorian, mockRecordBuilder, mockSynthBodyBuilder);
    }

    private void prepareAndAssertRevertReasonIsSetAndNullIsReturned(
            TokenCreateWrapper tokenCreateWrapper) {
        given(dynamicProperties.isHTSPrecompileCreateEnabled()).willReturn(true);
        given(frame.getWorldUpdater()).willReturn(worldUpdater);
        given(worldUpdater.wrappedTrackingLedgers(any())).willReturn(wrappedLedgers);
        Bytes pretendArguments = Bytes.of(Integers.toBytes(ABI_ID_CREATE_NON_FUNGIBLE_TOKEN));
        given(decoder.decodeNonFungibleCreate(eq(pretendArguments), any()))
                .willReturn(tokenCreateWrapper);
        given(frame.getSenderAddress()).willReturn(senderAddress);

        // when:
        final var result = subject.computePrecompile(pretendArguments, frame);

        // then:
        assertNull(result.getOutput());

        // and
        verify(frame).setRevertReason(any());
        verifyNoInteractions(createLogic);
        verify(wrappedLedgers, never()).commit();
        verify(worldUpdater, never())
                .manageInProgressRecord(recordsHistorian, mockRecordBuilder, mockSynthBodyBuilder);
    }

    private void givenMinimalFrameContext() {
        given(dynamicProperties.isHTSPrecompileCreateEnabled()).willReturn(true);
        given(frame.getContractAddress()).willReturn(contractAddr);
        given(frame.getWorldUpdater()).willReturn(worldUpdater);
        given(frame.getRemainingGas()).willReturn(100_000L);
        Optional<WorldUpdater> parent = Optional.of(worldUpdater);
        given(worldUpdater.parentUpdater()).willReturn(parent);
        given(worldUpdater.wrappedTrackingLedgers(any())).willReturn(wrappedLedgers);
    }

    private void givenLedgers() {
        given(wrappedLedgers.accounts()).willReturn(accounts);
        given(wrappedLedgers.tokenRels()).willReturn(tokenRels);
        given(wrappedLedgers.nfts()).willReturn(nfts);
        given(wrappedLedgers.tokens()).willReturn(tokens);
    }

    private void givenValidGasCalculation() {
        given(feeCalculator.computeFee(any(), any(), any(), any()))
                .willReturn(new FeeObject(TEST_NODE_FEE, TEST_NETWORK_FEE, TEST_SERVICE_FEE));
        given(feeCalculator.estimatedGasPriceInTinybars(any(), any())).willReturn(1L);
        given(frame.getValue()).willReturn(Wei.of(1_000_000L));

        final var blockValuesMock = mock(BlockValues.class);
        given(frame.getBlockValues()).willReturn(blockValuesMock);
        given(blockValuesMock.getTimestamp()).willReturn(timestamp.getSeconds());

        final var mockSenderEvmAccount = Mockito.mock(EvmAccount.class);
        given(worldUpdater.getAccount(senderAddress)).willReturn(mockSenderEvmAccount);
        senderMutableAccount = new UpdateTrackingLedgerAccount(senderAddress, null);
        given(mockSenderEvmAccount.getMutable()).willReturn(senderMutableAccount);
        senderMutableAccount.setBalance(Wei.of(SENDER_INITIAL_BALANCE));

        given(dynamicProperties.fundingAccount()).willReturn(account);
        final var mockFundingEvmAccount = mock(EvmAccount.class);
        given(
                        worldUpdater.getAccount(
                                Id.fromGrpcAccount(dynamicProperties.fundingAccount())
                                        .asEvmAddress()))
                .willReturn(mockFundingEvmAccount);
        fundingMutableAccount =
                new UpdateTrackingLedgerAccount(EntityIdUtils.asTypedEvmAddress(account), null);
        given(mockFundingEvmAccount.getMutable()).willReturn(fundingMutableAccount);
        fundingMutableAccount.setBalance(Wei.of(FUNDING_ACCOUNT_INITIAL_BALANCE));
    }
}
