/*
 * Copyright (C) 2021-2023 Hedera Hashgraph, LLC
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

package com.hedera.node.app.service.mono.store.contracts.precompile;

import static com.hedera.node.app.service.mono.state.EntityCreator.EMPTY_MEMO;
import static com.hedera.node.app.service.mono.store.contracts.precompile.AbiConstants.ABI_ID_ASSOCIATE_TOKEN;
import static com.hedera.node.app.service.mono.store.contracts.precompile.AbiConstants.ABI_ID_ASSOCIATE_TOKENS;
import static com.hedera.node.app.service.mono.store.contracts.precompile.impl.AssociatePrecompile.decodeAssociation;
import static com.hedera.node.app.service.mono.store.contracts.precompile.impl.MultiAssociatePrecompile.decodeMultipleAssociations;
import static com.hederahashgraph.api.proto.java.HederaFunctionality.TokenAssociateToAccount;
import static com.hederahashgraph.api.proto.java.ResponseCodeEnum.FAIL_INVALID;
import static com.hederahashgraph.api.proto.java.ResponseCodeEnum.INVALID_FULL_PREFIX_SIGNATURE_FOR_PRECOMPILE;
import static com.hederahashgraph.api.proto.java.ResponseCodeEnum.OK;
import static com.hederahashgraph.api.proto.java.ResponseCodeEnum.TOKEN_ID_REPEATED_IN_TOKEN_LIST;
import static java.util.function.UnaryOperator.identity;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

import com.esaulpaugh.headlong.util.Integers;
import com.hedera.node.app.hapi.fees.pricing.AssetsLoader;
import com.hedera.node.app.hapi.utils.fee.FeeObject;
import com.hedera.node.app.service.evm.store.contracts.precompile.EvmHTSPrecompiledContract;
import com.hedera.node.app.service.evm.store.contracts.precompile.codec.EvmEncodingFacade;
import com.hedera.node.app.service.mono.context.SideEffectsTracker;
import com.hedera.node.app.service.mono.context.primitives.StateView;
import com.hedera.node.app.service.mono.context.properties.GlobalDynamicProperties;
import com.hedera.node.app.service.mono.contracts.sources.TxnAwareEvmSigsVerifier;
import com.hedera.node.app.service.mono.fees.FeeCalculator;
import com.hedera.node.app.service.mono.fees.HbarCentExchange;
import com.hedera.node.app.service.mono.fees.calculation.UsagePricesProvider;
import com.hedera.node.app.service.mono.ledger.TransactionalLedger;
import com.hedera.node.app.service.mono.ledger.accounts.ContractAliases;
import com.hedera.node.app.service.mono.ledger.properties.AccountProperty;
import com.hedera.node.app.service.mono.ledger.properties.NftProperty;
import com.hedera.node.app.service.mono.ledger.properties.TokenProperty;
import com.hedera.node.app.service.mono.ledger.properties.TokenRelProperty;
import com.hedera.node.app.service.mono.records.RecordsHistorian;
import com.hedera.node.app.service.mono.state.expiry.ExpiringCreations;
import com.hedera.node.app.service.mono.state.merkle.MerkleToken;
import com.hedera.node.app.service.mono.state.migration.HederaAccount;
import com.hedera.node.app.service.mono.state.migration.HederaTokenRel;
import com.hedera.node.app.service.mono.state.migration.UniqueTokenAdapter;
import com.hedera.node.app.service.mono.state.submerkle.ExpirableTxnRecord;
import com.hedera.node.app.service.mono.store.AccountStore;
import com.hedera.node.app.service.mono.store.TypedTokenStore;
import com.hedera.node.app.service.mono.store.contracts.HederaStackedWorldStateUpdater;
import com.hedera.node.app.service.mono.store.contracts.WorldLedgers;
import com.hedera.node.app.service.mono.store.contracts.precompile.codec.EncodingFacade;
import com.hedera.node.app.service.mono.store.contracts.precompile.impl.AssociatePrecompile;
import com.hedera.node.app.service.mono.store.contracts.precompile.impl.MultiAssociatePrecompile;
import com.hedera.node.app.service.mono.store.contracts.precompile.utils.PrecompilePricingUtils;
import com.hedera.node.app.service.mono.store.models.Id;
import com.hedera.node.app.service.mono.store.models.NftId;
import com.hedera.node.app.service.mono.txns.token.AssociateLogic;
import com.hedera.node.app.service.mono.utils.accessors.AccessorFactory;
import com.hederahashgraph.api.proto.java.AccountID;
import com.hederahashgraph.api.proto.java.ExchangeRate;
import com.hederahashgraph.api.proto.java.HederaFunctionality;
import com.hederahashgraph.api.proto.java.SubType;
import com.hederahashgraph.api.proto.java.TokenAssociateTransactionBody;
import com.hederahashgraph.api.proto.java.TokenID;
import com.hederahashgraph.api.proto.java.TransactionBody;
import com.hederahashgraph.api.proto.java.TransactionID;
import java.io.IOException;
import java.math.BigDecimal;
import java.util.Collections;
import java.util.Deque;
import java.util.HashMap;
import java.util.Iterator;
import java.util.Map;
import java.util.Optional;
import org.apache.commons.lang3.tuple.Pair;
import org.apache.tuweni.bytes.Bytes;
import org.hyperledger.besu.datatypes.Wei;
import org.hyperledger.besu.evm.frame.MessageFrame;
import org.hyperledger.besu.evm.gascalculator.GasCalculator;
import org.hyperledger.besu.evm.worldstate.WorldUpdater;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.MockedStatic;
import org.mockito.Mockito;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
@SuppressWarnings("rawtypes")
class AssociatePrecompileTest {
    @Mock
    private GlobalDynamicProperties dynamicProperties;

    @Mock
    private GasCalculator gasCalculator;

    @Mock
    private RecordsHistorian recordsHistorian;

    @Mock
    private TxnAwareEvmSigsVerifier sigsVerifier;

    @Mock
    private EncodingFacade encoder;

    @Mock
    private EvmEncodingFacade evmEncoder;

    @Mock
    private SyntheticTxnFactory syntheticTxnFactory;

    @Mock
    private ExpiringCreations creator;

    @Mock
    private AccountStore accountStore;

    @Mock
    private TypedTokenStore tokenStore;

    @Mock
    private AssociateLogic associateLogic;

    @Mock
    private SideEffectsTracker sideEffects;

    @Mock
    private MessageFrame frame;

    @Mock
    private MessageFrame parentFrame;

    @Mock
    private Deque<MessageFrame> frameDeque;

    @Mock
    private Iterator<MessageFrame> dequeIterator;

    @Mock
    private HederaStackedWorldStateUpdater worldUpdater;

    @Mock
    private WorldLedgers wrappedLedgers;

    @Mock
    private TransactionalLedger<AccountID, AccountProperty, HederaAccount> accounts;

    @Mock
    private TransactionalLedger<Pair<AccountID, TokenID>, TokenRelProperty, HederaTokenRel> tokenRels;

    @Mock
    private TransactionalLedger<NftId, NftProperty, UniqueTokenAdapter> nfts;

    @Mock
    private TransactionalLedger<TokenID, TokenProperty, MerkleToken> tokens;

    @Mock
    private TransactionBody.Builder mockSynthBodyBuilder;

    @Mock
    private ExpirableTxnRecord.Builder mockRecordBuilder;

    @Mock
    private FeeCalculator feeCalculator;

    @Mock
    private FeeObject mockFeeObject;

    @Mock
    private StateView stateView;

    @Mock
    private ContractAliases aliases;

    @Mock
    private UsagePricesProvider resourceCosts;

    @Mock
    private InfrastructureFactory infrastructureFactory;

    @Mock
    private AssetsLoader assetLoader;

    @Mock
    private HbarCentExchange exchange;

    @Mock
    private ExchangeRate exchangeRate;

    @Mock
    private AccessorFactory accessorFactory;

    @Mock
    private EvmHTSPrecompiledContract evmHTSPrecompiledContract;

    private static final long TEST_SERVICE_FEE = 5_000_000;
    private static final long TEST_NETWORK_FEE = 400_000;
    private static final long TEST_NODE_FEE = 300_000;
    private static final int CENTS_RATE = 12;
    private static final int HBAR_RATE = 1;
    private static final long EXPECTED_GAS_PRICE =
            (TEST_SERVICE_FEE + TEST_NETWORK_FEE + TEST_NODE_FEE) / HTSTestsUtil.DEFAULT_GAS_PRICE * 6 / 5;
    private static final Bytes ASSOCIATE_INPUT = Bytes.fromHexString(
            "0x49146bde00000000000000000000000000000000000000000000000000000000000004820000000000000000000000000000000000000000000000000000000000000480");
    private static final Bytes MULTIPLE_ASSOCIATE_INPUT = Bytes.fromHexString(
            "0x2e63879b00000000000000000000000000000000000000000000000000000000000004880000000000000000000000000000000000000000000000000000000000000040000000000000000000000000000000000000000000000000000000000000000200000000000000000000000000000000000000000000000000000000000004860000000000000000000000000000000000000000000000000000000000000486");

    private HTSPrecompiledContract subject;
    private MockedStatic<AssociatePrecompile> associatePrecompile;
    private MockedStatic<MultiAssociatePrecompile> multiAssociatePrecompile;

    @BeforeEach
    void setUp() throws IOException {
        final Map<HederaFunctionality, Map<SubType, BigDecimal>> canonicalPrices = new HashMap<>();
        canonicalPrices.put(TokenAssociateToAccount, Map.of(SubType.DEFAULT, BigDecimal.valueOf(0)));
        given(assetLoader.loadCanonicalPrices()).willReturn(canonicalPrices);
        final PrecompilePricingUtils precompilePricingUtils = new PrecompilePricingUtils(
                assetLoader, exchange, () -> feeCalculator, resourceCosts, stateView, accessorFactory);
        subject = new HTSPrecompiledContract(
                dynamicProperties,
                gasCalculator,
                recordsHistorian,
                sigsVerifier,
                encoder,
                evmEncoder,
                syntheticTxnFactory,
                creator,
                () -> feeCalculator,
                stateView,
                precompilePricingUtils,
                infrastructureFactory,
                evmHTSPrecompiledContract);

        associatePrecompile = Mockito.mockStatic(AssociatePrecompile.class);
        multiAssociatePrecompile = Mockito.mockStatic(MultiAssociatePrecompile.class);
    }

    @AfterEach
    void closeMocks() {
        associatePrecompile.close();
        multiAssociatePrecompile.close();
    }

    @Test
    void computeAssociateTokenFailurePathWorks() {
        // given:
        givenCommonFrameContext();
        givenPricingUtilsContext();
        final Bytes pretendArguments = Bytes.ofUnsignedInt(ABI_ID_ASSOCIATE_TOKEN);
        given(infrastructureFactory.newSideEffects()).willReturn(sideEffects);
        given(worldUpdater.permissivelyUnaliased(any()))
                .willAnswer(invocationOnMock -> invocationOnMock.getArgument(0));
        associatePrecompile
                .when(() -> decodeAssociation(eq(pretendArguments), any()))
                .thenReturn(HTSTestsUtil.associateOp);
        given(syntheticTxnFactory.createAssociate(HTSTestsUtil.associateOp)).willReturn(mockSynthBodyBuilder);
        given(sigsVerifier.hasActiveKey(
                        false,
                        Id.fromGrpcAccount(HTSTestsUtil.accountMerkleId).asEvmAddress(),
                        HTSTestsUtil.senderAddress,
                        wrappedLedgers,
                        TokenAssociateToAccount))
                .willReturn(false);
        given(feeCalculator.estimatedGasPriceInTinybars(HederaFunctionality.ContractCall, HTSTestsUtil.timestamp))
                .willReturn(1L);
        given(mockSynthBodyBuilder.build())
                .willReturn(TransactionBody.newBuilder().build());
        given(mockSynthBodyBuilder.setTransactionID(any(TransactionID.class))).willReturn(mockSynthBodyBuilder);
        given(feeCalculator.computeFee(any(), any(), any(), any())).willReturn(mockFeeObject);
        given(mockFeeObject.serviceFee()).willReturn(1L);
        given(creator.createUnsuccessfulSyntheticRecord(INVALID_FULL_PREFIX_SIGNATURE_FOR_PRECOMPILE))
                .willReturn(mockRecordBuilder);

        // when:
        subject.prepareFields(frame);
        subject.prepareComputation(pretendArguments, a -> a);
        subject.getPrecompile().getGasRequirement(HTSTestsUtil.TEST_CONSENSUS_TIME);
        final var result = subject.computeInternal(frame);

        // then:
        Assertions.assertEquals(HTSTestsUtil.invalidFullPrefix, result);
        verify(worldUpdater).manageInProgressRecord(recordsHistorian, mockRecordBuilder, mockSynthBodyBuilder);
    }

    @Test
    void computeAssociateTokenFailurePathWorksWithNullLedgers() {
        // given:
        givenFrameContextWithDelegateCallFromParent();
        givenPricingUtilsContext();
        final Bytes pretendArguments = Bytes.ofUnsignedInt(ABI_ID_ASSOCIATE_TOKEN);
        given(infrastructureFactory.newSideEffects()).willReturn(sideEffects);
        given(worldUpdater.permissivelyUnaliased(any()))
                .willAnswer(invocationOnMock -> invocationOnMock.getArgument(0));
        associatePrecompile
                .when(() -> decodeAssociation(eq(pretendArguments), any()))
                .thenReturn(HTSTestsUtil.associateOp);
        given(syntheticTxnFactory.createAssociate(HTSTestsUtil.associateOp)).willReturn(mockSynthBodyBuilder);
        given(sigsVerifier.hasActiveKey(
                        false,
                        Id.fromGrpcAccount(HTSTestsUtil.accountMerkleId).asEvmAddress(),
                        HTSTestsUtil.senderAddress,
                        null,
                        TokenAssociateToAccount))
                .willThrow(new NullPointerException());
        given(feeCalculator.estimatedGasPriceInTinybars(HederaFunctionality.ContractCall, HTSTestsUtil.timestamp))
                .willReturn(1L);
        given(mockSynthBodyBuilder.build())
                .willReturn(TransactionBody.newBuilder().build());
        given(mockSynthBodyBuilder.setTransactionID(any(TransactionID.class))).willReturn(mockSynthBodyBuilder);
        given(feeCalculator.computeFee(any(), any(), any(), any())).willReturn(mockFeeObject);
        given(mockFeeObject.serviceFee()).willReturn(1L);
        given(creator.createUnsuccessfulSyntheticRecord(FAIL_INVALID)).willReturn(mockRecordBuilder);

        // when:
        subject.prepareFields(frame);
        subject.prepareComputation(pretendArguments, a -> a);
        subject.getPrecompile().getGasRequirement(HTSTestsUtil.TEST_CONSENSUS_TIME);
        final var result = subject.computeInternal(frame);

        // then:
        Assertions.assertEquals(HTSTestsUtil.failInvalidResult, result);
        verify(worldUpdater).manageInProgressRecord(recordsHistorian, mockRecordBuilder, mockSynthBodyBuilder);
    }

    @Test
    void computeAssociateTokenHappyPathWorksWithDelegateCall() {
        givenPricingUtilsContext();
        given(infrastructureFactory.newSideEffects()).willReturn(sideEffects);
        given(worldUpdater.permissivelyUnaliased(any()))
                .willAnswer(invocationOnMock -> invocationOnMock.getArgument(0));
        given(frame.getContractAddress()).willReturn(HTSTestsUtil.contractAddress);
        given(frame.getRecipientAddress()).willReturn(HTSTestsUtil.contractAddress);
        given(frame.getSenderAddress()).willReturn(HTSTestsUtil.senderAddress);
        given(frame.getWorldUpdater()).willReturn(worldUpdater);
        given(frame.getRemainingGas()).willReturn(300L);
        given(frame.getValue()).willReturn(Wei.ZERO);
        final Optional<WorldUpdater> parent = Optional.of(worldUpdater);
        given(worldUpdater.parentUpdater()).willReturn(parent);
        given(worldUpdater.wrappedTrackingLedgers(any())).willReturn(wrappedLedgers);
        given(frame.getRecipientAddress()).willReturn(HTSTestsUtil.recipientAddress);
        givenLedgers();
        final Bytes pretendArguments = Bytes.ofUnsignedInt(ABI_ID_ASSOCIATE_TOKEN);
        associatePrecompile
                .when(() -> decodeAssociation(eq(pretendArguments), any()))
                .thenReturn(HTSTestsUtil.associateOp);
        given(syntheticTxnFactory.createAssociate(HTSTestsUtil.associateOp)).willReturn(mockSynthBodyBuilder);
        given(sigsVerifier.hasActiveKey(
                        true,
                        Id.fromGrpcAccount(HTSTestsUtil.accountMerkleId).asEvmAddress(),
                        HTSTestsUtil.recipientAddress,
                        wrappedLedgers,
                        TokenAssociateToAccount))
                .willReturn(true);
        given(infrastructureFactory.newAccountStore(accounts)).willReturn(accountStore);
        given(infrastructureFactory.newTokenStore(accountStore, sideEffects, tokens, nfts, tokenRels))
                .willReturn(tokenStore);
        given(infrastructureFactory.newAssociateLogic(accountStore, tokenStore)).willReturn(associateLogic);
        given(feeCalculator.estimatedGasPriceInTinybars(HederaFunctionality.ContractCall, HTSTestsUtil.timestamp))
                .willReturn(1L);
        given(mockSynthBodyBuilder.build())
                .willReturn(TransactionBody.newBuilder().build());
        given(mockSynthBodyBuilder.setTransactionID(any(TransactionID.class))).willReturn(mockSynthBodyBuilder);
        given(feeCalculator.computeFee(any(), any(), any(), any())).willReturn(mockFeeObject);
        given(mockFeeObject.serviceFee()).willReturn(1L);
        given(creator.createSuccessfulSyntheticRecord(Collections.emptyList(), sideEffects, EMPTY_MEMO))
                .willReturn(mockRecordBuilder);
        given(worldUpdater.aliases()).willReturn(aliases);
        given(aliases.resolveForEvm(any())).willAnswer(invocationOnMock -> invocationOnMock.getArgument(0));
        given(associateLogic.validateSyntax(any())).willReturn(OK);

        // when:
        subject.prepareFields(frame);
        subject.prepareComputation(pretendArguments, a -> a);
        subject.getPrecompile().getGasRequirement(HTSTestsUtil.TEST_CONSENSUS_TIME);
        final var result = subject.computeInternal(frame);

        // then:
        Assertions.assertEquals(HTSTestsUtil.successResult, result);
        verify(associateLogic)
                .associate(
                        Id.fromGrpcAccount(HTSTestsUtil.accountMerkleId),
                        Collections.singletonList(HTSTestsUtil.tokenMerkleId));
        verify(wrappedLedgers).commit();
        verify(worldUpdater).manageInProgressRecord(recordsHistorian, mockRecordBuilder, mockSynthBodyBuilder);
    }

    @Test
    void computeAssociateTokenBadSyntax() {
        givenPricingUtilsContext();
        given(infrastructureFactory.newSideEffects()).willReturn(sideEffects);
        given(worldUpdater.permissivelyUnaliased(any()))
                .willAnswer(invocationOnMock -> invocationOnMock.getArgument(0));
        given(frame.getContractAddress()).willReturn(HTSTestsUtil.contractAddress);
        given(frame.getRecipientAddress()).willReturn(HTSTestsUtil.contractAddress);
        given(frame.getSenderAddress()).willReturn(HTSTestsUtil.senderAddress);
        given(frame.getWorldUpdater()).willReturn(worldUpdater);
        given(frame.getRemainingGas()).willReturn(300L);
        given(frame.getValue()).willReturn(Wei.ZERO);
        final Optional<WorldUpdater> parent = Optional.of(worldUpdater);
        given(worldUpdater.parentUpdater()).willReturn(parent);
        given(worldUpdater.wrappedTrackingLedgers(any())).willReturn(wrappedLedgers);
        given(frame.getRecipientAddress()).willReturn(HTSTestsUtil.recipientAddress);
        givenLedgers();
        final Bytes pretendArguments = Bytes.ofUnsignedInt(ABI_ID_ASSOCIATE_TOKEN);
        associatePrecompile
                .when(() -> decodeAssociation(eq(pretendArguments), any()))
                .thenReturn(HTSTestsUtil.associateOp);
        given(syntheticTxnFactory.createAssociate(HTSTestsUtil.associateOp)).willReturn(mockSynthBodyBuilder);
        given(sigsVerifier.hasActiveKey(
                        true,
                        Id.fromGrpcAccount(HTSTestsUtil.accountMerkleId).asEvmAddress(),
                        HTSTestsUtil.recipientAddress,
                        wrappedLedgers,
                        TokenAssociateToAccount))
                .willReturn(true);
        given(infrastructureFactory.newAccountStore(accounts)).willReturn(accountStore);
        given(infrastructureFactory.newTokenStore(accountStore, sideEffects, tokens, nfts, tokenRels))
                .willReturn(tokenStore);
        given(infrastructureFactory.newAssociateLogic(accountStore, tokenStore)).willReturn(associateLogic);
        given(feeCalculator.estimatedGasPriceInTinybars(HederaFunctionality.ContractCall, HTSTestsUtil.timestamp))
                .willReturn(1L);
        given(mockSynthBodyBuilder.build())
                .willReturn(TransactionBody.newBuilder().build());
        given(mockSynthBodyBuilder.setTransactionID(any(TransactionID.class))).willReturn(mockSynthBodyBuilder);
        given(feeCalculator.computeFee(any(), any(), any(), any())).willReturn(mockFeeObject);
        given(mockFeeObject.serviceFee()).willReturn(1L);
        given(worldUpdater.aliases()).willReturn(aliases);
        given(aliases.resolveForEvm(any())).willAnswer(invocationOnMock -> invocationOnMock.getArgument(0));
        given(associateLogic.validateSyntax(any())).willReturn(TOKEN_ID_REPEATED_IN_TOKEN_LIST);
        given(creator.createUnsuccessfulSyntheticRecord(TOKEN_ID_REPEATED_IN_TOKEN_LIST))
                .willReturn(mockRecordBuilder);

        // when:
        subject.prepareFields(frame);
        subject.prepareComputation(pretendArguments, a -> a);
        subject.getPrecompile().getGasRequirement(HTSTestsUtil.TEST_CONSENSUS_TIME);
        subject.computeInternal(frame);

        verify(wrappedLedgers, never()).commit();
    }

    @Test
    void computeAssociateTokenHappyPathWorksWithDelegateCallFromParentFrame() {
        givenFrameContextWithDelegateCallFromParent();
        givenLedgers();
        givenPricingUtilsContext();
        final Bytes pretendArguments = Bytes.ofUnsignedInt(ABI_ID_ASSOCIATE_TOKEN);
        given(infrastructureFactory.newSideEffects()).willReturn(sideEffects);
        given(worldUpdater.permissivelyUnaliased(any()))
                .willAnswer(invocationOnMock -> invocationOnMock.getArgument(0));
        associatePrecompile
                .when(() -> decodeAssociation(eq(pretendArguments), any()))
                .thenReturn(HTSTestsUtil.associateOp);
        given(syntheticTxnFactory.createAssociate(HTSTestsUtil.associateOp)).willReturn(mockSynthBodyBuilder);
        given(sigsVerifier.hasActiveKey(
                        true,
                        Id.fromGrpcAccount(HTSTestsUtil.accountMerkleId).asEvmAddress(),
                        HTSTestsUtil.senderAddress,
                        wrappedLedgers,
                        TokenAssociateToAccount))
                .willReturn(true);
        given(infrastructureFactory.newAccountStore(accounts)).willReturn(accountStore);
        given(infrastructureFactory.newTokenStore(accountStore, sideEffects, tokens, nfts, tokenRels))
                .willReturn(tokenStore);
        given(infrastructureFactory.newAssociateLogic(accountStore, tokenStore)).willReturn(associateLogic);
        given(associateLogic.validateSyntax(any())).willReturn(OK);
        given(feeCalculator.estimatedGasPriceInTinybars(HederaFunctionality.ContractCall, HTSTestsUtil.timestamp))
                .willReturn(1L);
        given(mockSynthBodyBuilder.build())
                .willReturn(TransactionBody.newBuilder().build());
        given(mockSynthBodyBuilder.setTransactionID(any(TransactionID.class))).willReturn(mockSynthBodyBuilder);
        given(feeCalculator.computeFee(any(), any(), any(), any())).willReturn(mockFeeObject);
        given(mockFeeObject.serviceFee()).willReturn(1L);
        given(creator.createSuccessfulSyntheticRecord(Collections.emptyList(), sideEffects, EMPTY_MEMO))
                .willReturn(mockRecordBuilder);

        // when:
        subject.prepareFields(frame);
        subject.prepareComputation(pretendArguments, a -> a);
        subject.getPrecompile().getGasRequirement(HTSTestsUtil.TEST_CONSENSUS_TIME);
        final var result = subject.computeInternal(frame);

        // then:
        Assertions.assertEquals(HTSTestsUtil.successResult, result);
        verify(associateLogic)
                .associate(
                        Id.fromGrpcAccount(HTSTestsUtil.accountMerkleId),
                        Collections.singletonList(HTSTestsUtil.tokenMerkleId));
        verify(wrappedLedgers).commit();
        verify(worldUpdater).manageInProgressRecord(recordsHistorian, mockRecordBuilder, mockSynthBodyBuilder);
    }

    @Test
    void computeAssociateTokenHappyPathWorksWithEmptyMessageFrameStack() {
        givenFrameContextWithEmptyMessageFrameStack();
        givenLedgers();
        givenPricingUtilsContext();
        final Bytes pretendArguments = Bytes.ofUnsignedInt(ABI_ID_ASSOCIATE_TOKEN);
        given(infrastructureFactory.newSideEffects()).willReturn(sideEffects);
        given(worldUpdater.permissivelyUnaliased(any()))
                .willAnswer(invocationOnMock -> invocationOnMock.getArgument(0));
        associatePrecompile
                .when(() -> decodeAssociation(eq(pretendArguments), any()))
                .thenReturn(HTSTestsUtil.associateOp);
        given(syntheticTxnFactory.createAssociate(HTSTestsUtil.associateOp)).willReturn(mockSynthBodyBuilder);
        given(sigsVerifier.hasActiveKey(
                        false,
                        Id.fromGrpcAccount(HTSTestsUtil.accountMerkleId).asEvmAddress(),
                        HTSTestsUtil.senderAddress,
                        wrappedLedgers,
                        TokenAssociateToAccount))
                .willReturn(true);
        given(infrastructureFactory.newAccountStore(accounts)).willReturn(accountStore);
        given(infrastructureFactory.newTokenStore(accountStore, sideEffects, tokens, nfts, tokenRels))
                .willReturn(tokenStore);
        given(infrastructureFactory.newAssociateLogic(accountStore, tokenStore)).willReturn(associateLogic);
        given(associateLogic.validateSyntax(any())).willReturn(OK);
        given(feeCalculator.estimatedGasPriceInTinybars(HederaFunctionality.ContractCall, HTSTestsUtil.timestamp))
                .willReturn(1L);
        given(mockSynthBodyBuilder.build())
                .willReturn(TransactionBody.newBuilder().build());
        given(mockSynthBodyBuilder.setTransactionID(any(TransactionID.class))).willReturn(mockSynthBodyBuilder);
        given(feeCalculator.computeFee(any(), any(), any(), any())).willReturn(mockFeeObject);
        given(mockFeeObject.serviceFee()).willReturn(1L);
        given(creator.createSuccessfulSyntheticRecord(Collections.emptyList(), sideEffects, EMPTY_MEMO))
                .willReturn(mockRecordBuilder);

        // when:
        subject.prepareFields(frame);
        subject.prepareComputation(pretendArguments, a -> a);
        subject.getPrecompile().getGasRequirement(HTSTestsUtil.TEST_CONSENSUS_TIME);
        final var result = subject.computeInternal(frame);

        // then:
        Assertions.assertEquals(HTSTestsUtil.successResult, result);
        verify(associateLogic)
                .associate(
                        Id.fromGrpcAccount(HTSTestsUtil.accountMerkleId),
                        Collections.singletonList(HTSTestsUtil.tokenMerkleId));
        verify(wrappedLedgers).commit();
        verify(worldUpdater).manageInProgressRecord(recordsHistorian, mockRecordBuilder, mockSynthBodyBuilder);
    }

    @Test
    void computeAssociateTokenHappyPathWorksWithoutParentFrame() {
        givenFrameContextWithoutParentFrame();
        givenLedgers();
        givenPricingUtilsContext();
        final Bytes pretendArguments = Bytes.ofUnsignedInt(ABI_ID_ASSOCIATE_TOKEN);
        given(infrastructureFactory.newSideEffects()).willReturn(sideEffects);
        given(worldUpdater.permissivelyUnaliased(any()))
                .willAnswer(invocationOnMock -> invocationOnMock.getArgument(0));
        associatePrecompile
                .when(() -> decodeAssociation(eq(pretendArguments), any()))
                .thenReturn(HTSTestsUtil.associateOp);
        given(syntheticTxnFactory.createAssociate(HTSTestsUtil.associateOp)).willReturn(mockSynthBodyBuilder);
        given(sigsVerifier.hasActiveKey(
                        false,
                        Id.fromGrpcAccount(HTSTestsUtil.accountMerkleId).asEvmAddress(),
                        HTSTestsUtil.senderAddress,
                        wrappedLedgers,
                        TokenAssociateToAccount))
                .willReturn(true);
        given(infrastructureFactory.newAccountStore(accounts)).willReturn(accountStore);
        given(infrastructureFactory.newTokenStore(accountStore, sideEffects, tokens, nfts, tokenRels))
                .willReturn(tokenStore);
        given(infrastructureFactory.newAssociateLogic(accountStore, tokenStore)).willReturn(associateLogic);
        given(associateLogic.validateSyntax(any())).willReturn(OK);
        given(feeCalculator.estimatedGasPriceInTinybars(HederaFunctionality.ContractCall, HTSTestsUtil.timestamp))
                .willReturn(1L);
        given(mockSynthBodyBuilder.build())
                .willReturn(TransactionBody.newBuilder().build());
        given(mockSynthBodyBuilder.setTransactionID(any(TransactionID.class))).willReturn(mockSynthBodyBuilder);
        given(feeCalculator.computeFee(any(), any(), any(), any())).willReturn(mockFeeObject);
        given(mockFeeObject.serviceFee()).willReturn(1L);
        given(creator.createSuccessfulSyntheticRecord(Collections.emptyList(), sideEffects, EMPTY_MEMO))
                .willReturn(mockRecordBuilder);

        // when:
        subject.prepareFields(frame);
        subject.prepareComputation(pretendArguments, a -> a);
        subject.getPrecompile().getGasRequirement(HTSTestsUtil.TEST_CONSENSUS_TIME);
        final var result = subject.computeInternal(frame);

        // then:
        Assertions.assertEquals(HTSTestsUtil.successResult, result);
        verify(associateLogic)
                .associate(
                        Id.fromGrpcAccount(HTSTestsUtil.accountMerkleId),
                        Collections.singletonList(HTSTestsUtil.tokenMerkleId));
        verify(wrappedLedgers).commit();
        verify(worldUpdater).manageInProgressRecord(recordsHistorian, mockRecordBuilder, mockSynthBodyBuilder);
    }

    @Test
    void computeMultiAssociateTokenHappyPathWorks() {
        givenCommonFrameContext();
        givenLedgers();
        givenPricingUtilsContext();
        final Bytes pretendArguments = Bytes.ofUnsignedInt(ABI_ID_ASSOCIATE_TOKENS);
        given(infrastructureFactory.newSideEffects()).willReturn(sideEffects);
        given(worldUpdater.permissivelyUnaliased(any()))
                .willAnswer(invocationOnMock -> invocationOnMock.getArgument(0));
        multiAssociatePrecompile
                .when(() -> decodeMultipleAssociations(eq(pretendArguments), any()))
                .thenReturn(HTSTestsUtil.multiAssociateOp);
        given(syntheticTxnFactory.createAssociate(HTSTestsUtil.multiAssociateOp))
                .willReturn(mockSynthBodyBuilder);
        given(sigsVerifier.hasActiveKey(
                        false,
                        Id.fromGrpcAccount(HTSTestsUtil.accountMerkleId).asEvmAddress(),
                        HTSTestsUtil.senderAddress,
                        wrappedLedgers,
                        TokenAssociateToAccount))
                .willReturn(true);
        given(infrastructureFactory.newAccountStore(accounts)).willReturn(accountStore);
        given(infrastructureFactory.newTokenStore(accountStore, sideEffects, tokens, nfts, tokenRels))
                .willReturn(tokenStore);
        given(infrastructureFactory.newAssociateLogic(accountStore, tokenStore)).willReturn(associateLogic);
        given(associateLogic.validateSyntax(any())).willReturn(OK);
        given(feeCalculator.estimatedGasPriceInTinybars(HederaFunctionality.ContractCall, HTSTestsUtil.timestamp))
                .willReturn(1L);
        given(mockSynthBodyBuilder.build())
                .willReturn(TransactionBody.newBuilder().build());
        given(mockSynthBodyBuilder.setTransactionID(any(TransactionID.class))).willReturn(mockSynthBodyBuilder);
        given(feeCalculator.computeFee(any(), any(), any(), any())).willReturn(mockFeeObject);
        given(mockFeeObject.serviceFee()).willReturn(1L);
        given(creator.createSuccessfulSyntheticRecord(Collections.emptyList(), sideEffects, EMPTY_MEMO))
                .willReturn(mockRecordBuilder);

        // when:
        subject.prepareFields(frame);
        subject.prepareComputation(pretendArguments, a -> a);
        subject.getPrecompile().getGasRequirement(HTSTestsUtil.TEST_CONSENSUS_TIME);
        final var result = subject.computeInternal(frame);

        // then:
        Assertions.assertEquals(HTSTestsUtil.successResult, result);
        verify(associateLogic)
                .associate(Id.fromGrpcAccount(HTSTestsUtil.accountMerkleId), HTSTestsUtil.multiAssociateOp.tokenIds());
        verify(wrappedLedgers).commit();
        verify(worldUpdater).manageInProgressRecord(recordsHistorian, mockRecordBuilder, mockSynthBodyBuilder);
    }

    @Test
    void gasRequirementReturnsCorrectValueForAssociateTokens() {
        // given
        givenFrameContext();
        givenPricingUtilsContext();
        final Bytes input = Bytes.of(Integers.toBytes(ABI_ID_ASSOCIATE_TOKENS));
        given(infrastructureFactory.newSideEffects()).willReturn(sideEffects);
        given(worldUpdater.permissivelyUnaliased(any()))
                .willAnswer(invocationOnMock -> invocationOnMock.getArgument(0));
        multiAssociatePrecompile
                .when(() -> decodeMultipleAssociations(any(), any()))
                .thenReturn(HTSTestsUtil.multiAssociateOp);
        final var builder = TokenAssociateTransactionBody.newBuilder();
        builder.setAccount(HTSTestsUtil.multiDissociateOp.accountId());
        builder.addAllTokens(HTSTestsUtil.multiDissociateOp.tokenIds());
        given(syntheticTxnFactory.createAssociate(any()))
                .willReturn(TransactionBody.newBuilder().setTokenAssociate(builder));
        given(feeCalculator.computeFee(any(), any(), any(), any()))
                .willReturn(new FeeObject(TEST_NODE_FEE, TEST_NETWORK_FEE, TEST_SERVICE_FEE));
        given(feeCalculator.estimatedGasPriceInTinybars(any(), any())).willReturn(HTSTestsUtil.DEFAULT_GAS_PRICE);
        given(worldUpdater.permissivelyUnaliased(any()))
                .willAnswer(invocationOnMock -> invocationOnMock.getArgument(0));

        subject.prepareFields(frame);
        subject.prepareComputation(input, a -> a);
        final long result = subject.getPrecompile().getGasRequirement(HTSTestsUtil.TEST_CONSENSUS_TIME);

        // then
        assertEquals(EXPECTED_GAS_PRICE, result);
    }

    @Test
    void gasRequirementReturnsCorrectValueForAssociateToken() {
        // given
        givenFrameContext();
        givenPricingUtilsContext();
        final Bytes input = Bytes.of(Integers.toBytes(ABI_ID_ASSOCIATE_TOKEN));
        given(infrastructureFactory.newSideEffects()).willReturn(sideEffects);
        given(worldUpdater.permissivelyUnaliased(any()))
                .willAnswer(invocationOnMock -> invocationOnMock.getArgument(0));
        final var builder = TokenAssociateTransactionBody.newBuilder();
        builder.setAccount(HTSTestsUtil.associateOp.accountId());
        builder.addAllTokens(HTSTestsUtil.associateOp.tokenIds());
        given(syntheticTxnFactory.createAssociate(any()))
                .willReturn(TransactionBody.newBuilder().setTokenAssociate(builder));
        given(feeCalculator.computeFee(any(), any(), any(), any()))
                .willReturn(new FeeObject(TEST_NODE_FEE, TEST_NETWORK_FEE, TEST_SERVICE_FEE));
        given(feeCalculator.estimatedGasPriceInTinybars(any(), any())).willReturn(HTSTestsUtil.DEFAULT_GAS_PRICE);
        given(worldUpdater.permissivelyUnaliased(any()))
                .willAnswer(invocationOnMock -> invocationOnMock.getArgument(0));

        subject.prepareFields(frame);
        subject.prepareComputation(input, a -> a);
        final long result = subject.getPrecompile().getGasRequirement(HTSTestsUtil.TEST_CONSENSUS_TIME);

        // then
        assertEquals(EXPECTED_GAS_PRICE, result);
    }

    @Test
    void decodeAssociateToken() {
        associatePrecompile
                .when(() -> decodeAssociation(ASSOCIATE_INPUT, identity()))
                .thenCallRealMethod();
        final var decodedInput = decodeAssociation(ASSOCIATE_INPUT, identity());

        assertTrue(decodedInput.accountId().getAccountNum() > 0);
        assertTrue(decodedInput.tokenIds().get(0).getTokenNum() > 0);
    }

    @Test
    void decodeMultipleAssociateToken() {
        multiAssociatePrecompile
                .when(() -> decodeMultipleAssociations(MULTIPLE_ASSOCIATE_INPUT, identity()))
                .thenCallRealMethod();
        final var decodedInput = decodeMultipleAssociations(MULTIPLE_ASSOCIATE_INPUT, identity());

        assertTrue(decodedInput.accountId().getAccountNum() > 0);
        assertEquals(2, decodedInput.tokenIds().size());
        assertTrue(decodedInput.tokenIds().get(0).getTokenNum() > 0);
        assertTrue(decodedInput.tokenIds().get(1).getTokenNum() > 0);
    }

    private void givenFrameContextWithDelegateCallFromParent() {
        given(parentFrame.getContractAddress()).willReturn(HTSTestsUtil.parentContractAddress);
        given(parentFrame.getRecipientAddress()).willReturn(HTSTestsUtil.parentRecipientAddress);
        givenCommonFrameContext();
        given(frame.getMessageFrameStack().iterator().hasNext()).willReturn(true);
        given(frame.getMessageFrameStack().iterator().next()).willReturn(parentFrame);
    }

    private void givenFrameContextWithEmptyMessageFrameStack() {
        givenCommonFrameContext();
        given(frame.getMessageFrameStack().iterator().hasNext()).willReturn(false);
    }

    private void givenFrameContextWithoutParentFrame() {
        givenCommonFrameContext();
        given(frame.getMessageFrameStack().iterator().hasNext()).willReturn(true, false);
    }

    private void givenCommonFrameContext() {
        given(frame.getContractAddress()).willReturn(HTSTestsUtil.contractAddress);
        given(frame.getRecipientAddress()).willReturn(HTSTestsUtil.contractAddress);
        given(frame.getSenderAddress()).willReturn(HTSTestsUtil.senderAddress);
        given(frame.getMessageFrameStack()).willReturn(frameDeque);
        given(frame.getMessageFrameStack().iterator()).willReturn(dequeIterator);
        given(frame.getWorldUpdater()).willReturn(worldUpdater);
        given(frame.getRemainingGas()).willReturn(300L);
        given(frame.getValue()).willReturn(Wei.ZERO);
        given(worldUpdater.aliases()).willReturn(aliases);
        given(aliases.resolveForEvm(any())).willAnswer(invocationOnMock -> invocationOnMock.getArgument(0));
        given(worldUpdater.aliases()).willReturn(aliases);
        final Optional<WorldUpdater> parent = Optional.of(worldUpdater);
        given(worldUpdater.parentUpdater()).willReturn(parent);
        given(worldUpdater.wrappedTrackingLedgers(any())).willReturn(wrappedLedgers);
    }

    private void givenLedgers() {
        given(wrappedLedgers.accounts()).willReturn(accounts);
        given(wrappedLedgers.tokenRels()).willReturn(tokenRels);
        given(wrappedLedgers.nfts()).willReturn(nfts);
        given(wrappedLedgers.tokens()).willReturn(tokens);
    }

    private void givenPricingUtilsContext() {
        given(exchange.rate(any())).willReturn(exchangeRate);
        given(exchangeRate.getCentEquiv()).willReturn(CENTS_RATE);
        given(exchangeRate.getHbarEquiv()).willReturn(HBAR_RATE);
    }

    private void givenFrameContext() {
        given(frame.getSenderAddress()).willReturn(HTSTestsUtil.contractAddress);
        given(frame.getWorldUpdater()).willReturn(worldUpdater);
        given(worldUpdater.wrappedTrackingLedgers(any())).willReturn(wrappedLedgers);
    }
}
