/*
 * Copyright (C) 2022-2023 Hedera Hashgraph, LLC
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
import static com.hedera.node.app.service.mono.store.contracts.precompile.AbiConstants.ABI_ID_HRC_ASSOCIATE;
import static com.hedera.node.app.service.mono.store.contracts.precompile.AbiConstants.ABI_ID_HRC_DISSOCIATE;
import static com.hedera.node.app.service.mono.store.contracts.precompile.AbiConstants.ABI_ID_REDIRECT_FOR_TOKEN;
import static com.hedera.node.app.service.mono.store.contracts.precompile.HTSPrecompiledContract.HTS_PRECOMPILED_CONTRACT_ADDRESS;
import static com.hedera.node.app.service.mono.store.contracts.precompile.HTSTestsUtil.accountMerkleId;
import static com.hedera.node.app.service.mono.store.contracts.precompile.HTSTestsUtil.contractAddress;
import static com.hedera.node.app.service.mono.store.contracts.precompile.HTSTestsUtil.fungibleTokenAddr;
import static com.hedera.node.app.service.mono.store.contracts.precompile.HTSTestsUtil.precompiledContract;
import static com.hedera.node.app.service.mono.store.contracts.precompile.HTSTestsUtil.receiver;
import static com.hedera.node.app.service.mono.store.contracts.precompile.HTSTestsUtil.recipientAddress;
import static com.hedera.node.app.service.mono.store.contracts.precompile.HTSTestsUtil.sender;
import static com.hedera.node.app.service.mono.store.contracts.precompile.HTSTestsUtil.senderAddress;
import static com.hedera.node.app.service.mono.store.contracts.precompile.HTSTestsUtil.token;
import static com.hedera.node.app.service.mono.store.contracts.precompile.HTSTestsUtil.tokenAddress;
import static com.hederahashgraph.api.proto.java.HederaFunctionality.TokenAssociateToAccount;
import static com.hederahashgraph.api.proto.java.HederaFunctionality.TokenDissociateFromAccount;
import static com.hederahashgraph.api.proto.java.ResponseCodeEnum.INVALID_ACCOUNT_ID;
import static com.hederahashgraph.api.proto.java.ResponseCodeEnum.OK;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

import com.esaulpaugh.headlong.util.Integers;
import com.hedera.node.app.hapi.fees.pricing.AssetsLoader;
import com.hedera.node.app.hapi.utils.fee.FeeObject;
import com.hedera.node.app.service.evm.exceptions.InvalidTransactionException;
import com.hedera.node.app.service.evm.store.contracts.precompile.EvmHTSPrecompiledContract;
import com.hedera.node.app.service.evm.store.contracts.precompile.codec.EvmEncodingFacade;
import com.hedera.node.app.service.evm.store.contracts.precompile.codec.TokenAllowanceWrapper;
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
import com.hedera.node.app.service.mono.store.contracts.precompile.codec.Association;
import com.hedera.node.app.service.mono.store.contracts.precompile.codec.Dissociation;
import com.hedera.node.app.service.mono.store.contracts.precompile.codec.EncodingFacade;
import com.hedera.node.app.service.mono.store.contracts.precompile.utils.PrecompilePricingUtils;
import com.hedera.node.app.service.mono.store.models.Id;
import com.hedera.node.app.service.mono.store.models.NftId;
import com.hedera.node.app.service.mono.txns.token.AssociateLogic;
import com.hedera.node.app.service.mono.txns.token.DissociateLogic;
import com.hedera.node.app.service.mono.utils.EntityIdUtils;
import com.hedera.node.app.service.mono.utils.accessors.AccessorFactory;
import com.hederahashgraph.api.proto.java.AccountID;
import com.hederahashgraph.api.proto.java.ExchangeRate;
import com.hederahashgraph.api.proto.java.HederaFunctionality;
import com.hederahashgraph.api.proto.java.SubType;
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
import org.hyperledger.besu.datatypes.Address;
import org.hyperledger.besu.datatypes.Wei;
import org.hyperledger.besu.evm.frame.MessageFrame;
import org.hyperledger.besu.evm.gascalculator.GasCalculator;
import org.hyperledger.besu.evm.worldstate.WorldUpdater;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.MockedStatic;
import org.mockito.Mockito;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class HRCPrecompilesTest {
    @Mock
    private GlobalDynamicProperties dynamicProperties;

    @Mock
    private GasCalculator gasCalculator;

    @Mock
    private MessageFrame frame;

    @Mock
    private TxnAwareEvmSigsVerifier sigsVerifier;

    @Mock
    private RecordsHistorian recordsHistorian;

    @Mock
    private EncodingFacade encoder;

    @Mock
    private EvmEncodingFacade evmEncoder;

    @Mock
    private SideEffectsTracker sideEffects;

    @Mock
    private TransactionBody.Builder mockSynthBodyBuilder;

    @Mock
    private ExpirableTxnRecord.Builder mockRecordBuilder;

    @Mock
    private SyntheticTxnFactory syntheticTxnFactory;

    @Mock
    private HederaStackedWorldStateUpdater worldUpdater;

    @Mock
    private WorldLedgers wrappedLedgers;

    @Mock
    private TransactionalLedger<NftId, NftProperty, UniqueTokenAdapter> nfts;

    @Mock
    private TransactionalLedger<Pair<AccountID, TokenID>, TokenRelProperty, HederaTokenRel> tokenRels;

    @Mock
    private TransactionalLedger<AccountID, AccountProperty, HederaAccount> accounts;

    @Mock
    private TransactionalLedger<TokenID, TokenProperty, MerkleToken> tokens;

    @Mock
    private ExpiringCreations creator;

    @Mock
    private FeeCalculator feeCalculator;

    @Mock
    private StateView stateView;

    @Mock
    private ContractAliases aliases;

    @Mock
    private FeeObject mockFeeObject;

    @Mock
    private UsagePricesProvider resourceCosts;

    @Mock
    private InfrastructureFactory infrastructureFactory;

    @Mock
    private AccountStore accountStore;

    @Mock
    private TypedTokenStore tokenStore;

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

    @Mock
    private Deque<MessageFrame> frameDeque;

    @Mock
    private Iterator<MessageFrame> dequeIterator;

    @Mock
    private AssociateLogic associateLogic;

    @Mock
    private DissociateLogic dissociateLogic;

    private static final int CENTS_RATE = 12;
    private static final int HBAR_RATE = 1;

    private HTSPrecompiledContract subject;
    private MockedStatic<EntityIdUtils> entityIdUtils;
    private MockedStatic<Association> association;
    private MockedStatic<Dissociation> dissociation;

    @BeforeEach
    void setUp() throws IOException {
        final Map<HederaFunctionality, Map<SubType, BigDecimal>> canonicalPrices = new HashMap<>();
        final Map<SubType, BigDecimal> type = new HashMap<>();
        type.put(SubType.TOKEN_FUNGIBLE_COMMON, BigDecimal.valueOf(0));
        type.put(SubType.TOKEN_NON_FUNGIBLE_UNIQUE, BigDecimal.valueOf(0));
        canonicalPrices.put(HederaFunctionality.CryptoTransfer, type);
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
        given(infrastructureFactory.newSideEffects()).willReturn(sideEffects);
        entityIdUtils = Mockito.mockStatic(EntityIdUtils.class);
        entityIdUtils
                .when(() -> EntityIdUtils.accountIdFromEvmAddress(senderAddress))
                .thenReturn(sender);
        entityIdUtils
                .when(() -> EntityIdUtils.contractIdFromEvmAddress(
                        Address.fromHexString(HTS_PRECOMPILED_CONTRACT_ADDRESS).toArray()))
                .thenReturn(precompiledContract);
        entityIdUtils.when(() -> EntityIdUtils.asTypedEvmAddress(sender)).thenReturn(senderAddress);
        entityIdUtils.when(() -> EntityIdUtils.asTypedEvmAddress(token)).thenReturn(tokenAddress);
        entityIdUtils.when(() -> EntityIdUtils.asTypedEvmAddress(receiver)).thenReturn(recipientAddress);
        entityIdUtils
                .when(() -> EntityIdUtils.tokenIdFromEvmAddress(fungibleTokenAddr.toArray()))
                .thenReturn(token);
        entityIdUtils
                .when(() -> EntityIdUtils.tokenIdFromEvmAddress(fungibleTokenAddr))
                .thenReturn(token);
        given(worldUpdater.permissivelyUnaliased(any()))
                .willAnswer(invocationOnMock -> invocationOnMock.getArgument(0));
        association = Mockito.mockStatic(Association.class);
        dissociation = Mockito.mockStatic(Dissociation.class);
    }

    @AfterEach
    void closeMocks() {
        entityIdUtils.close();
        association.close();
        dissociation.close();
    }

    @Test
    @DisplayName("Associate token via HRC facade")
    void hrcAssociate() {
        final Bytes nestedPretendArguments = Bytes.of(Integers.toBytes(ABI_ID_HRC_ASSOCIATE));
        final Bytes pretendArguments = givenFrameContext(nestedPretendArguments);

        givenLedgers();
        givenPricingUtilsContext();

        given(wrappedLedgers.tokens().exists(any())).willReturn(true);
        given(infrastructureFactory.newSideEffects()).willReturn(sideEffects);
        given(worldUpdater.permissivelyUnaliased(any()))
                .willAnswer(invocationOnMock -> invocationOnMock.getArgument(0));
        given(Association.singleAssociation(any(), any())).willReturn(HTSTestsUtil.associateOp);
        given(syntheticTxnFactory.createAssociate(HTSTestsUtil.associateOp)).willReturn(mockSynthBodyBuilder);
        given(infrastructureFactory.newAccountStore(accounts)).willReturn(accountStore);
        given(infrastructureFactory.newTokenStore(accountStore, sideEffects, tokens, nfts, tokenRels))
                .willReturn(tokenStore);
        given(infrastructureFactory.newAssociateLogic(accountStore, tokenStore)).willReturn(associateLogic);
        given(associateLogic.validateSyntax(any())).willReturn(OK);
        given(sigsVerifier.hasActiveKey(
                        false,
                        Id.fromGrpcAccount(accountMerkleId).asEvmAddress(),
                        HTSTestsUtil.senderAddress,
                        wrappedLedgers,
                        TokenAssociateToAccount))
                .willReturn(true);
        given(feeCalculator.estimatedGasPriceInTinybars(HederaFunctionality.ContractCall, HTSTestsUtil.timestamp))
                .willReturn(1L);
        given(mockSynthBodyBuilder.build())
                .willReturn(TransactionBody.newBuilder().build());
        given(mockSynthBodyBuilder.setTransactionID(any(TransactionID.class))).willReturn(mockSynthBodyBuilder);
        given(feeCalculator.computeFee(any(), any(), any(), any())).willReturn(mockFeeObject);
        given(mockFeeObject.serviceFee()).willReturn(1L);
        given(worldUpdater.aliases()).willReturn(aliases);
        given(aliases.resolveForEvm(any())).willAnswer(invocationOnMock -> invocationOnMock.getArgument(0));
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
                .associate(Id.fromGrpcAccount(accountMerkleId), Collections.singletonList(HTSTestsUtil.tokenMerkleId));
        verify(wrappedLedgers).commit();
        verify(worldUpdater).manageInProgressRecord(recordsHistorian, mockRecordBuilder, mockSynthBodyBuilder);
    }

    @Test
    @DisplayName("Associate token via HRC facade fails when tokenId is invalid")
    void hrcAssociateBadTokenId() {
        final Bytes nestedPretendArguments = Bytes.of(Integers.toBytes(ABI_ID_HRC_ASSOCIATE));
        final Bytes pretendArguments = givenMinimalFrameContext(nestedPretendArguments);

        // this line defines the tokenId as invalid
        given(wrappedLedgers.tokens()).willReturn(tokens);
        given(wrappedLedgers.tokens().exists(any())).willReturn(false);
        given(infrastructureFactory.newSideEffects()).willReturn(sideEffects);
        given(worldUpdater.permissivelyUnaliased(any()))
                .willAnswer(invocationOnMock -> invocationOnMock.getArgument(0));
        given(Association.singleAssociation(any(), any())).willReturn(HTSTestsUtil.associateOp);

        // when:
        subject.prepareFields(frame);
        assertThrows(InvalidTransactionException.class, () -> subject.prepareComputation(pretendArguments, a -> a));

        verify(wrappedLedgers, never()).commit();
    }

    @Test
    @DisplayName("Associate token via HRC facade fails when account is invalid")
    void hrcAssociateBadAccountId() {
        final Bytes nestedPretendArguments = Bytes.of(Integers.toBytes(ABI_ID_HRC_ASSOCIATE));
        final Bytes pretendArguments = givenFrameContext(nestedPretendArguments);

        givenLedgers();
        givenPricingUtilsContext();

        given(wrappedLedgers.tokens().exists(any())).willReturn(true);
        given(infrastructureFactory.newSideEffects()).willReturn(sideEffects);
        given(worldUpdater.permissivelyUnaliased(any()))
                .willAnswer(invocationOnMock -> invocationOnMock.getArgument(0));
        given(Association.singleAssociation(any(), any())).willReturn(HTSTestsUtil.associateOp);
        given(syntheticTxnFactory.createAssociate(HTSTestsUtil.associateOp)).willReturn(mockSynthBodyBuilder);
        given(infrastructureFactory.newAccountStore(accounts)).willReturn(accountStore);
        given(infrastructureFactory.newTokenStore(accountStore, sideEffects, tokens, nfts, tokenRels))
                .willReturn(tokenStore);
        given(infrastructureFactory.newAssociateLogic(accountStore, tokenStore)).willReturn(associateLogic);
        given(associateLogic.validateSyntax(any())).willReturn(OK);
        given(sigsVerifier.hasActiveKey(
                        false,
                        Id.fromGrpcAccount(accountMerkleId).asEvmAddress(),
                        HTSTestsUtil.senderAddress,
                        wrappedLedgers,
                        TokenAssociateToAccount))
                .willReturn(true);
        given(feeCalculator.estimatedGasPriceInTinybars(HederaFunctionality.ContractCall, HTSTestsUtil.timestamp))
                .willReturn(1L);
        given(mockSynthBodyBuilder.build())
                .willReturn(TransactionBody.newBuilder().build());
        given(mockSynthBodyBuilder.setTransactionID(any(TransactionID.class))).willReturn(mockSynthBodyBuilder);
        given(feeCalculator.computeFee(any(), any(), any(), any())).willReturn(mockFeeObject);
        given(mockFeeObject.serviceFee()).willReturn(1L);
        given(worldUpdater.aliases()).willReturn(aliases);
        given(aliases.resolveForEvm(any())).willAnswer(invocationOnMock -> invocationOnMock.getArgument(0));
        // Throw InvalidTransactionException with INVALID_ACCOUNT_ID
        doThrow(new InvalidTransactionException(INVALID_ACCOUNT_ID))
                .when(associateLogic)
                .associate(any(), any());

        // when:
        subject.prepareFields(frame);
        subject.prepareComputation(pretendArguments, a -> a);
        subject.getPrecompile().getGasRequirement(HTSTestsUtil.TEST_CONSENSUS_TIME);
        final var result = subject.computeInternal(frame);

        // then:
        Assertions.assertEquals(HTSTestsUtil.invalidAccountId, result);
        verify(wrappedLedgers, never()).commit();
    }

    @Test
    @DisplayName("Dissociate token via HRC facade")
    void hrcDissociate() {
        final Bytes nestedPretendArguments = Bytes.of(Integers.toBytes(ABI_ID_HRC_DISSOCIATE));
        final Bytes pretendArguments = givenFrameContext(nestedPretendArguments);

        givenLedgers();
        givenPricingUtilsContext();

        given(wrappedLedgers.tokens().exists(any())).willReturn(true);
        given(infrastructureFactory.newSideEffects()).willReturn(sideEffects);
        given(worldUpdater.permissivelyUnaliased(any()))
                .willAnswer(invocationOnMock -> invocationOnMock.getArgument(0));
        given(Dissociation.singleDissociation(any(), any())).willReturn(HTSTestsUtil.dissociateOp);
        given(syntheticTxnFactory.createDissociate(HTSTestsUtil.dissociateOp)).willReturn(mockSynthBodyBuilder);
        given(infrastructureFactory.newAccountStore(accounts)).willReturn(accountStore);
        given(infrastructureFactory.newTokenStore(accountStore, sideEffects, tokens, nfts, tokenRels))
                .willReturn(tokenStore);
        given(infrastructureFactory.newDissociateLogic(accountStore, tokenStore))
                .willReturn(dissociateLogic);
        given(dissociateLogic.validateSyntax(any())).willReturn(OK);
        given(sigsVerifier.hasActiveKey(
                        false,
                        Id.fromGrpcAccount(accountMerkleId).asEvmAddress(),
                        HTSTestsUtil.senderAddress,
                        wrappedLedgers,
                        TokenDissociateFromAccount))
                .willReturn(true);
        given(feeCalculator.estimatedGasPriceInTinybars(HederaFunctionality.ContractCall, HTSTestsUtil.timestamp))
                .willReturn(1L);
        given(mockSynthBodyBuilder.build())
                .willReturn(TransactionBody.newBuilder().build());
        given(mockSynthBodyBuilder.setTransactionID(any(TransactionID.class))).willReturn(mockSynthBodyBuilder);
        given(feeCalculator.computeFee(any(), any(), any(), any())).willReturn(mockFeeObject);
        given(mockFeeObject.serviceFee()).willReturn(1L);
        given(worldUpdater.aliases()).willReturn(aliases);
        given(aliases.resolveForEvm(any())).willAnswer(invocationOnMock -> invocationOnMock.getArgument(0));
        given(creator.createSuccessfulSyntheticRecord(Collections.emptyList(), sideEffects, EMPTY_MEMO))
                .willReturn(mockRecordBuilder);

        // when:
        subject.prepareFields(frame);
        subject.prepareComputation(pretendArguments, a -> a);
        subject.getPrecompile().getGasRequirement(HTSTestsUtil.TEST_CONSENSUS_TIME);
        final var result = subject.computeInternal(frame);

        // then:
        Assertions.assertEquals(HTSTestsUtil.successResult, result);
        verify(dissociateLogic)
                .dissociate(Id.fromGrpcAccount(accountMerkleId), Collections.singletonList(HTSTestsUtil.tokenMerkleId));
        verify(wrappedLedgers).commit();
        verify(worldUpdater).manageInProgressRecord(recordsHistorian, mockRecordBuilder, mockSynthBodyBuilder);
    }

    @Test
    @DisplayName("Dissociate token via HRC facade fails when tokenId is invalid")
    void hrcDissociateBadTokenId() {
        final Bytes nestedPretendArguments = Bytes.of(Integers.toBytes(ABI_ID_HRC_DISSOCIATE));
        final Bytes pretendArguments = givenMinimalFrameContext(nestedPretendArguments);

        // this line defines the tokenId as invalid
        given(wrappedLedgers.tokens()).willReturn(tokens);
        given(wrappedLedgers.tokens().exists(any())).willReturn(false);
        given(infrastructureFactory.newSideEffects()).willReturn(sideEffects);
        given(worldUpdater.permissivelyUnaliased(any()))
                .willAnswer(invocationOnMock -> invocationOnMock.getArgument(0));
        given(Dissociation.singleDissociation(any(), any())).willReturn(HTSTestsUtil.dissociateOp);

        // when:
        subject.prepareFields(frame);
        assertThrows(InvalidTransactionException.class, () -> subject.prepareComputation(pretendArguments, a -> a));
        verify(wrappedLedgers, never()).commit();
    }

    @Test
    @DisplayName("Dissociate token via HRC facade fails when account is invalid")
    void hrcDissociateBadAccountId() {
        final Bytes nestedPretendArguments = Bytes.of(Integers.toBytes(ABI_ID_HRC_DISSOCIATE));
        final Bytes pretendArguments = givenFrameContext(nestedPretendArguments);

        givenLedgers();
        givenPricingUtilsContext();

        given(wrappedLedgers.tokens().exists(any())).willReturn(true);
        given(infrastructureFactory.newSideEffects()).willReturn(sideEffects);
        given(worldUpdater.permissivelyUnaliased(any()))
                .willAnswer(invocationOnMock -> invocationOnMock.getArgument(0));
        given(Dissociation.singleDissociation(any(), any())).willReturn(HTSTestsUtil.dissociateOp);
        given(syntheticTxnFactory.createDissociate(HTSTestsUtil.dissociateOp)).willReturn(mockSynthBodyBuilder);
        given(infrastructureFactory.newAccountStore(accounts)).willReturn(accountStore);
        given(infrastructureFactory.newTokenStore(accountStore, sideEffects, tokens, nfts, tokenRels))
                .willReturn(tokenStore);
        given(infrastructureFactory.newDissociateLogic(accountStore, tokenStore))
                .willReturn(dissociateLogic);
        given(dissociateLogic.validateSyntax(any())).willReturn(OK);
        given(sigsVerifier.hasActiveKey(
                        false,
                        Id.fromGrpcAccount(accountMerkleId).asEvmAddress(),
                        HTSTestsUtil.senderAddress,
                        wrappedLedgers,
                        TokenDissociateFromAccount))
                .willReturn(true);
        given(feeCalculator.estimatedGasPriceInTinybars(HederaFunctionality.ContractCall, HTSTestsUtil.timestamp))
                .willReturn(1L);
        given(mockSynthBodyBuilder.build())
                .willReturn(TransactionBody.newBuilder().build());
        given(mockSynthBodyBuilder.setTransactionID(any(TransactionID.class))).willReturn(mockSynthBodyBuilder);
        given(feeCalculator.computeFee(any(), any(), any(), any())).willReturn(mockFeeObject);
        given(worldUpdater.aliases()).willReturn(aliases);
        given(aliases.resolveForEvm(any())).willAnswer(invocationOnMock -> invocationOnMock.getArgument(0));
        given(mockFeeObject.serviceFee()).willReturn(1L);
        // Throw InvalidTransactionException with INVALID_ACCOUNT_ID
        doThrow(new InvalidTransactionException(INVALID_ACCOUNT_ID))
                .when(dissociateLogic)
                .dissociate(any(), any());

        // when:
        subject.prepareFields(frame);
        subject.prepareComputation(pretendArguments, a -> a);
        subject.getPrecompile().getGasRequirement(HTSTestsUtil.TEST_CONSENSUS_TIME);
        final var result = subject.computeInternal(frame);

        // then:
        Assertions.assertEquals(HTSTestsUtil.invalidAccountId, result);
        verify(wrappedLedgers, never()).commit();
    }

    private Bytes givenFrameContext(final Bytes nestedArg) {
        given(frame.getSenderAddress()).willReturn(senderAddress);
        given(frame.getRecipientAddress()).willReturn(contractAddress);
        given(frame.getContractAddress()).willReturn(contractAddress);
        given(frame.getWorldUpdater()).willReturn(worldUpdater);
        given(frame.getRemainingGas()).willReturn(300L);
        given(frame.getValue()).willReturn(Wei.ZERO);
        final Optional<WorldUpdater> parent = Optional.of(worldUpdater);
        given(worldUpdater.parentUpdater()).willReturn(parent);
        given(worldUpdater.wrappedTrackingLedgers(any())).willReturn(wrappedLedgers);
        given(frame.getMessageFrameStack()).willReturn(frameDeque);
        given(frame.getMessageFrameStack().iterator()).willReturn(dequeIterator);
        return Bytes.concatenate(Bytes.of(Integers.toBytes(ABI_ID_REDIRECT_FOR_TOKEN)), fungibleTokenAddr, nestedArg);
    }

    private Bytes givenMinimalFrameContext(final Bytes nestedArg) {
        given(frame.getSenderAddress()).willReturn(senderAddress);
        given(frame.getWorldUpdater()).willReturn(worldUpdater);
        given(worldUpdater.wrappedTrackingLedgers(any())).willReturn(wrappedLedgers);
        return Bytes.concatenate(Bytes.of(Integers.toBytes(ABI_ID_REDIRECT_FOR_TOKEN)), fungibleTokenAddr, nestedArg);
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

    public static final TokenAllowanceWrapper<TokenID, AccountID, AccountID> ALLOWANCE_WRAPPER =
            new TokenAllowanceWrapper<>(token, sender, receiver);
}
