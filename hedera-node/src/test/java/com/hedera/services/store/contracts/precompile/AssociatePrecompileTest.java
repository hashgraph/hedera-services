package com.hedera.services.store.contracts.precompile;

/*-
 * ‌
 * Hedera Services Node
 * ​
 * Copyright (C) 2018 - 2021 Hedera Hashgraph, LLC
 * ​
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
 * ‍
 */

import com.hedera.services.context.SideEffectsTracker;
import com.hedera.services.context.primitives.StateView;
import com.hedera.services.context.properties.GlobalDynamicProperties;
import com.hedera.services.contracts.sources.TxnAwareEvmSigsVerifier;
import com.hedera.services.fees.FeeCalculator;
import com.hedera.services.fees.calculation.UsagePricesProvider;
import com.hedera.services.grpc.marshalling.ImpliedTransfersMarshal;
import com.hedera.services.ledger.TransactionalLedger;
import com.hedera.services.ledger.accounts.ContractAliases;
import com.hedera.services.ledger.properties.AccountProperty;
import com.hedera.services.ledger.properties.NftProperty;
import com.hedera.services.ledger.properties.TokenProperty;
import com.hedera.services.ledger.properties.TokenRelProperty;
import com.hedera.services.records.RecordsHistorian;
import com.hedera.services.state.expiry.ExpiringCreations;
import com.hedera.services.state.merkle.MerkleAccount;
import com.hedera.services.state.merkle.MerkleToken;
import com.hedera.services.state.merkle.MerkleTokenRelStatus;
import com.hedera.services.state.merkle.MerkleUniqueToken;
import com.hedera.services.state.submerkle.ExpirableTxnRecord;
import com.hedera.services.store.AccountStore;
import com.hedera.services.store.TypedTokenStore;
import com.hedera.services.store.contracts.HederaStackedWorldStateUpdater;
import com.hedera.services.store.contracts.WorldLedgers;
import com.hedera.services.store.contracts.precompile.codec.DecodingFacade;
import com.hedera.services.store.contracts.precompile.codec.EncodingFacade;
import com.hedera.services.store.contracts.precompile.utils.PrecompilePricingUtils;
import com.hedera.services.store.models.Id;
import com.hedera.services.store.models.NftId;
import com.hedera.services.txns.crypto.validators.ApproveAllowanceChecks;
import com.hedera.services.txns.crypto.validators.DeleteAllowanceChecks;
import com.hedera.services.txns.token.AssociateLogic;
import com.hedera.services.txns.token.validators.CreateChecks;
import com.hederahashgraph.api.proto.java.AccountID;
import com.hederahashgraph.api.proto.java.HederaFunctionality;
import com.hederahashgraph.api.proto.java.TokenID;
import com.hederahashgraph.api.proto.java.TransactionBody;
import com.hederahashgraph.api.proto.java.TransactionID;
import com.hederahashgraph.fee.FeeObject;
import org.apache.commons.lang3.tuple.Pair;
import org.apache.tuweni.bytes.Bytes;
import org.hyperledger.besu.datatypes.Wei;
import org.hyperledger.besu.evm.frame.MessageFrame;
import org.hyperledger.besu.evm.gascalculator.GasCalculator;
import org.hyperledger.besu.evm.worldstate.WorldUpdater;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Collections;
import java.util.Deque;
import java.util.Iterator;
import java.util.Optional;

import static com.hedera.services.state.EntityCreator.EMPTY_MEMO;
import static com.hedera.services.store.contracts.precompile.AbiConstants.ABI_ID_ASSOCIATE_TOKEN;
import static com.hedera.services.store.contracts.precompile.AbiConstants.ABI_ID_ASSOCIATE_TOKENS;
import static com.hedera.services.store.contracts.precompile.HTSTestsUtil.TEST_CONSENSUS_TIME;
import static com.hedera.services.store.contracts.precompile.HTSTestsUtil.accountMerkleId;
import static com.hedera.services.store.contracts.precompile.HTSTestsUtil.associateOp;
import static com.hedera.services.store.contracts.precompile.HTSTestsUtil.contractAddress;
import static com.hedera.services.store.contracts.precompile.HTSTestsUtil.failInvalidResult;
import static com.hedera.services.store.contracts.precompile.HTSTestsUtil.invalidSigResult;
import static com.hedera.services.store.contracts.precompile.HTSTestsUtil.multiAssociateOp;
import static com.hedera.services.store.contracts.precompile.HTSTestsUtil.parentContractAddress;
import static com.hedera.services.store.contracts.precompile.HTSTestsUtil.parentRecipientAddress;
import static com.hedera.services.store.contracts.precompile.HTSTestsUtil.recipientAddress;
import static com.hedera.services.store.contracts.precompile.HTSTestsUtil.senderAddress;
import static com.hedera.services.store.contracts.precompile.HTSTestsUtil.successResult;
import static com.hedera.services.store.contracts.precompile.HTSTestsUtil.timestamp;
import static com.hedera.services.store.contracts.precompile.HTSTestsUtil.tokenMerkleId;
import static com.hederahashgraph.api.proto.java.ResponseCodeEnum.FAIL_INVALID;
import static com.hederahashgraph.api.proto.java.ResponseCodeEnum.INVALID_SIGNATURE;
import static com.hederahashgraph.api.proto.java.ResponseCodeEnum.OK;
import static com.hederahashgraph.api.proto.java.ResponseCodeEnum.TOKEN_ID_REPEATED_IN_TOKEN_LIST;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

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
	private DecodingFacade decoder;
	@Mock
	private EncodingFacade encoder;
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
	private TransactionalLedger<AccountID, AccountProperty, MerkleAccount> accounts;
	@Mock
	private TransactionalLedger<Pair<AccountID, TokenID>, TokenRelProperty, MerkleTokenRelStatus> tokenRels;
	@Mock
	private TransactionalLedger<NftId, NftProperty, MerkleUniqueToken> nfts;
	@Mock
	private TransactionalLedger<TokenID, TokenProperty, MerkleToken> tokens;
	@Mock
	private TransactionBody.Builder mockSynthBodyBuilder;
	@Mock
	private ExpirableTxnRecord.Builder mockRecordBuilder;
	@Mock
	private ImpliedTransfersMarshal impliedTransfersMarshal;
	@Mock
	private FeeCalculator feeCalculator;
	@Mock
	private FeeObject mockFeeObject;
	@Mock
	private StateView stateView;
	@Mock
	private PrecompilePricingUtils precompilePricingUtils;
	@Mock
	private ContractAliases aliases;
	@Mock
	private UsagePricesProvider resourceCosts;
	@Mock
	private CreateChecks createChecks;
	@Mock
	private InfrastructureFactory infrastructureFactory;
	@Mock
	private ApproveAllowanceChecks allowanceChecks;
	@Mock
	private DeleteAllowanceChecks deleteAllowanceChecks;

	private HTSPrecompiledContract subject;

	@BeforeEach
	void setUp() {
		subject = new HTSPrecompiledContract(
				dynamicProperties, gasCalculator,
				recordsHistorian, sigsVerifier, decoder, encoder,
				syntheticTxnFactory, creator, impliedTransfersMarshal,
				() -> feeCalculator, stateView, precompilePricingUtils, resourceCosts,
				infrastructureFactory);

		given(infrastructureFactory.newSideEffects()).willReturn(sideEffects);
		given(worldUpdater.permissivelyUnaliased(any())).willAnswer(invocationOnMock -> invocationOnMock.getArgument(0));
	}

	@Test
	void computeAssociateTokenFailurePathWorks() {
		// given:
		givenCommonFrameContext();
		Bytes pretendArguments = Bytes.ofUnsignedInt(ABI_ID_ASSOCIATE_TOKEN);
		given(decoder.decodeAssociation(eq(pretendArguments), any())).willReturn(associateOp);
		given(syntheticTxnFactory.createAssociate(associateOp)).willReturn(mockSynthBodyBuilder);
		given(sigsVerifier.hasActiveKey(false,
				Id.fromGrpcAccount(accountMerkleId).asEvmAddress(), senderAddress,
				wrappedLedgers))
				.willReturn(false);
		given(feeCalculator.estimatedGasPriceInTinybars(HederaFunctionality.ContractCall, timestamp))
				.willReturn(1L);
		given(mockSynthBodyBuilder.build()).
				willReturn(TransactionBody.newBuilder().build());
		given(mockSynthBodyBuilder.setTransactionID(any(TransactionID.class)))
				.willReturn(mockSynthBodyBuilder);
		given(feeCalculator.computeFee(any(), any(), any(), any()))
				.willReturn(mockFeeObject);
		given(mockFeeObject.getServiceFee())
				.willReturn(1L);
		given(creator.createUnsuccessfulSyntheticRecord(INVALID_SIGNATURE)).willReturn(mockRecordBuilder);

		// when:
		subject.prepareFields(frame);
		subject.prepareComputation(pretendArguments, a -> a);
		subject.computeGasRequirement(TEST_CONSENSUS_TIME);
		final var result = subject.computeInternal(frame);

		// then:
		assertEquals(invalidSigResult, result);
		verify(worldUpdater).manageInProgressRecord(recordsHistorian, mockRecordBuilder, mockSynthBodyBuilder);
	}

	@Test
	void computeAssociateTokenFailurePathWorksWithNullLedgers() {
		// given:
		givenCommonFrameContext();
		Bytes pretendArguments = Bytes.ofUnsignedInt(ABI_ID_ASSOCIATE_TOKEN);
		given(decoder.decodeAssociation(eq(pretendArguments), any())).willReturn(associateOp);
		given(syntheticTxnFactory.createAssociate(associateOp)).willReturn(mockSynthBodyBuilder);
		given(sigsVerifier.hasActiveKey(false,
				Id.fromGrpcAccount(accountMerkleId).asEvmAddress(), senderAddress,
				null))
				.willThrow(new NullPointerException());
		given(feeCalculator.estimatedGasPriceInTinybars(HederaFunctionality.ContractCall, timestamp))
				.willReturn(1L);
		given(mockSynthBodyBuilder.build()).
				willReturn(TransactionBody.newBuilder().build());
		given(mockSynthBodyBuilder.setTransactionID(any(TransactionID.class)))
				.willReturn(mockSynthBodyBuilder);
		given(feeCalculator.computeFee(any(), any(), any(), any()))
				.willReturn(mockFeeObject);
		given(mockFeeObject.getServiceFee())
				.willReturn(1L);
		given(creator.createUnsuccessfulSyntheticRecord(FAIL_INVALID)).willReturn(mockRecordBuilder);

		// when:
		subject.prepareFields(frame);
		subject.prepareComputation(pretendArguments, a -> a);
		subject.computeGasRequirement(TEST_CONSENSUS_TIME);
		final var result = subject.computeInternal(frame);

		// then:
		assertEquals(failInvalidResult, result);
		verify(worldUpdater).manageInProgressRecord(recordsHistorian, mockRecordBuilder, mockSynthBodyBuilder);
	}

	@Test
	void computeAssociateTokenHappyPathWorksWithDelegateCall() {
		given(frame.getContractAddress()).willReturn(contractAddress);
		given(frame.getRecipientAddress()).willReturn(contractAddress);
		given(frame.getSenderAddress()).willReturn(senderAddress);
		given(frame.getWorldUpdater()).willReturn(worldUpdater);
		given(frame.getRemainingGas()).willReturn(300L);
		given(frame.getValue()).willReturn(Wei.ZERO);
		Optional<WorldUpdater> parent = Optional.of(worldUpdater);
		given(worldUpdater.parentUpdater()).willReturn(parent);
		given(worldUpdater.wrappedTrackingLedgers(any())).willReturn(wrappedLedgers);
		given(frame.getRecipientAddress()).willReturn(recipientAddress);
		givenLedgers();
		Bytes pretendArguments = Bytes.ofUnsignedInt(ABI_ID_ASSOCIATE_TOKEN);
		given(decoder.decodeAssociation(eq(pretendArguments), any()))
				.willReturn(associateOp);
		given(syntheticTxnFactory.createAssociate(associateOp))
				.willReturn(mockSynthBodyBuilder);
		given(sigsVerifier.hasActiveKey(true,
				Id.fromGrpcAccount(accountMerkleId).asEvmAddress(), recipientAddress,
				wrappedLedgers))
				.willReturn(true);
		given(infrastructureFactory.newAccountStore(accounts)).willReturn(accountStore);
		given(infrastructureFactory.newTokenStore(accountStore, sideEffects, tokens, nfts, tokenRels))
				.willReturn(tokenStore);
		given(infrastructureFactory.newAssociateLogic(accountStore, tokenStore)).willReturn(associateLogic);
		given(feeCalculator.estimatedGasPriceInTinybars(HederaFunctionality.ContractCall, timestamp))
				.willReturn(1L);
		given(mockSynthBodyBuilder.build()).
				willReturn(TransactionBody.newBuilder().build());
		given(mockSynthBodyBuilder.setTransactionID(any(TransactionID.class)))
				.willReturn(mockSynthBodyBuilder);
		given(feeCalculator.computeFee(any(), any(), any(), any()))
				.willReturn(mockFeeObject);
		given(mockFeeObject.getServiceFee())
				.willReturn(1L);
		given(creator.createSuccessfulSyntheticRecord(Collections.emptyList(), sideEffects, EMPTY_MEMO))
				.willReturn(mockRecordBuilder);
		given(worldUpdater.aliases()).willReturn(aliases);
		given(aliases.resolveForEvm(any())).willAnswer(invocationOnMock -> invocationOnMock.getArgument(0));
		given(associateLogic.validateSyntax(any())).willReturn(OK);

		// when:
		subject.prepareFields(frame);
		subject.prepareComputation(pretendArguments, a -> a);
		subject.computeGasRequirement(TEST_CONSENSUS_TIME);
		final var result = subject.computeInternal(frame);

		// then:
		assertEquals(successResult, result);
		verify(associateLogic).associate(Id.fromGrpcAccount(accountMerkleId), Collections.singletonList(tokenMerkleId));
		verify(wrappedLedgers).commit();
		verify(worldUpdater).manageInProgressRecord(recordsHistorian, mockRecordBuilder, mockSynthBodyBuilder);
	}

	@Test
	void computeAssociateTokenBadSyntax() {
		given(frame.getContractAddress()).willReturn(contractAddress);
		given(frame.getRecipientAddress()).willReturn(contractAddress);
		given(frame.getSenderAddress()).willReturn(senderAddress);
		given(frame.getWorldUpdater()).willReturn(worldUpdater);
		given(frame.getRemainingGas()).willReturn(300L);
		given(frame.getValue()).willReturn(Wei.ZERO);
		Optional<WorldUpdater> parent = Optional.of(worldUpdater);
		given(worldUpdater.parentUpdater()).willReturn(parent);
		given(worldUpdater.wrappedTrackingLedgers(any())).willReturn(wrappedLedgers);
		given(frame.getRecipientAddress()).willReturn(recipientAddress);
		givenLedgers();
		Bytes pretendArguments = Bytes.ofUnsignedInt(ABI_ID_ASSOCIATE_TOKEN);
		given(decoder.decodeAssociation(eq(pretendArguments), any()))
				.willReturn(associateOp);
		given(syntheticTxnFactory.createAssociate(associateOp))
				.willReturn(mockSynthBodyBuilder);
		given(sigsVerifier.hasActiveKey(true,
				Id.fromGrpcAccount(accountMerkleId).asEvmAddress(), recipientAddress,
				wrappedLedgers))
				.willReturn(true);
		given(infrastructureFactory.newAccountStore(accounts)).willReturn(accountStore);
		given(infrastructureFactory.newTokenStore(accountStore, sideEffects, tokens, nfts, tokenRels))
				.willReturn(tokenStore);
		given(infrastructureFactory.newAssociateLogic(accountStore, tokenStore)).willReturn(associateLogic);
		given(feeCalculator.estimatedGasPriceInTinybars(HederaFunctionality.ContractCall, timestamp))
				.willReturn(1L);
		given(mockSynthBodyBuilder.build()).
				willReturn(TransactionBody.newBuilder().build());
		given(mockSynthBodyBuilder.setTransactionID(any(TransactionID.class)))
				.willReturn(mockSynthBodyBuilder);
		given(feeCalculator.computeFee(any(), any(), any(), any()))
				.willReturn(mockFeeObject);
		given(mockFeeObject.getServiceFee())
				.willReturn(1L);
		given(worldUpdater.aliases()).willReturn(aliases);
		given(aliases.resolveForEvm(any())).willAnswer(invocationOnMock -> invocationOnMock.getArgument(0));
		given(associateLogic.validateSyntax(any())).willReturn(TOKEN_ID_REPEATED_IN_TOKEN_LIST);
		given(creator.createUnsuccessfulSyntheticRecord(TOKEN_ID_REPEATED_IN_TOKEN_LIST)).willReturn(mockRecordBuilder);

		// when:
		subject.prepareFields(frame);
		subject.prepareComputation(pretendArguments, a -> a);
		subject.computeGasRequirement(TEST_CONSENSUS_TIME);
		subject.computeInternal(frame);

		verify(wrappedLedgers, never()).commit();
	}

	@Test
	void computeAssociateTokenHappyPathWorksWithDelegateCallFromParentFrame() {
		givenFrameContextWithDelegateCallFromParent();
		givenLedgers();
		Bytes pretendArguments = Bytes.ofUnsignedInt(ABI_ID_ASSOCIATE_TOKEN);
		given(decoder.decodeAssociation(eq(pretendArguments), any()))
				.willReturn(associateOp);
		given(syntheticTxnFactory.createAssociate(associateOp))
				.willReturn(mockSynthBodyBuilder);
		given(sigsVerifier.hasActiveKey(true, Id.fromGrpcAccount(accountMerkleId).asEvmAddress(),
				senderAddress, wrappedLedgers))
				.willReturn(true);
		given(infrastructureFactory.newAccountStore(accounts)).willReturn(accountStore);
		given(infrastructureFactory.newTokenStore(accountStore, sideEffects, tokens, nfts, tokenRels))
				.willReturn(tokenStore);
		given(infrastructureFactory.newAssociateLogic(accountStore, tokenStore)).willReturn(associateLogic);
		given(associateLogic.validateSyntax(any())).willReturn(OK);
		given(feeCalculator.estimatedGasPriceInTinybars(HederaFunctionality.ContractCall, timestamp)).willReturn(1L);
		given(mockSynthBodyBuilder.build()).willReturn(TransactionBody.newBuilder().build());
		given(mockSynthBodyBuilder.setTransactionID(any(TransactionID.class))).willReturn(mockSynthBodyBuilder);
		given(feeCalculator.computeFee(any(), any(), any(), any())).willReturn(mockFeeObject);
		given(mockFeeObject.getServiceFee()).willReturn(1L);
		given(creator.createSuccessfulSyntheticRecord(Collections.emptyList(), sideEffects, EMPTY_MEMO))
				.willReturn(mockRecordBuilder);

		// when:
		subject.prepareFields(frame);
		subject.prepareComputation(pretendArguments, a -> a);
		subject.computeGasRequirement(TEST_CONSENSUS_TIME);
		final var result = subject.computeInternal(frame);

		// then:
		assertEquals(successResult, result);
		verify(associateLogic).associate(Id.fromGrpcAccount(accountMerkleId), Collections.singletonList(tokenMerkleId));
		verify(wrappedLedgers).commit();
		verify(worldUpdater).manageInProgressRecord(recordsHistorian, mockRecordBuilder, mockSynthBodyBuilder);
	}

	@Test
	void computeAssociateTokenHappyPathWorksWithEmptyMessageFrameStack() {
		givenFrameContextWithEmptyMessageFrameStack();
		givenLedgers();
		Bytes pretendArguments = Bytes.ofUnsignedInt(ABI_ID_ASSOCIATE_TOKEN);
		given(decoder.decodeAssociation(eq(pretendArguments), any()))
				.willReturn(associateOp);
		given(syntheticTxnFactory.createAssociate(associateOp))
				.willReturn(mockSynthBodyBuilder);
		given(sigsVerifier.hasActiveKey(false, Id.fromGrpcAccount(accountMerkleId).asEvmAddress(),
				senderAddress, wrappedLedgers))
				.willReturn(true);
		given(infrastructureFactory.newAccountStore(accounts)).willReturn(accountStore);
		given(infrastructureFactory.newTokenStore(accountStore, sideEffects, tokens, nfts, tokenRels))
				.willReturn(tokenStore);
		given(infrastructureFactory.newAssociateLogic(accountStore, tokenStore)).willReturn(associateLogic);
		given(associateLogic.validateSyntax(any())).willReturn(OK);
		given(feeCalculator.estimatedGasPriceInTinybars(HederaFunctionality.ContractCall, timestamp))
				.willReturn(1L);
		given(mockSynthBodyBuilder.build()).
				willReturn(TransactionBody.newBuilder().build());
		given(mockSynthBodyBuilder.setTransactionID(any(TransactionID.class)))
				.willReturn(mockSynthBodyBuilder);
		given(feeCalculator.computeFee(any(), any(), any(), any()))
				.willReturn(mockFeeObject);
		given(mockFeeObject.getServiceFee())
				.willReturn(1L);
		given(creator.createSuccessfulSyntheticRecord(Collections.emptyList(), sideEffects, EMPTY_MEMO))
				.willReturn(mockRecordBuilder);

		// when:
		subject.prepareFields(frame);
		subject.prepareComputation(pretendArguments, a -> a);
		subject.computeGasRequirement(TEST_CONSENSUS_TIME);
		final var result = subject.computeInternal(frame);

		// then:
		assertEquals(successResult, result);
		verify(associateLogic).associate(Id.fromGrpcAccount(accountMerkleId), Collections.singletonList(tokenMerkleId));
		verify(wrappedLedgers).commit();
		verify(worldUpdater).manageInProgressRecord(recordsHistorian, mockRecordBuilder, mockSynthBodyBuilder);
	}

	@Test
	void computeAssociateTokenHappyPathWorksWithoutParentFrame() {
		givenFrameContextWithoutParentFrame();
		givenLedgers();
		Bytes pretendArguments = Bytes.ofUnsignedInt(ABI_ID_ASSOCIATE_TOKEN);
		given(decoder.decodeAssociation(eq(pretendArguments), any()))
				.willReturn(associateOp);
		given(syntheticTxnFactory.createAssociate(associateOp))
				.willReturn(mockSynthBodyBuilder);
		given(sigsVerifier.hasActiveKey(false, Id.fromGrpcAccount(accountMerkleId).asEvmAddress(), senderAddress,
				wrappedLedgers))
				.willReturn(true);
		given(infrastructureFactory.newAccountStore(accounts)).willReturn(accountStore);
		given(infrastructureFactory.newTokenStore(accountStore, sideEffects, tokens, nfts, tokenRels))
				.willReturn(tokenStore);
		given(infrastructureFactory.newAssociateLogic(accountStore, tokenStore)).willReturn(associateLogic);
		given(associateLogic.validateSyntax(any())).willReturn(OK);
		given(feeCalculator.estimatedGasPriceInTinybars(HederaFunctionality.ContractCall, timestamp))
				.willReturn(1L);
		given(mockSynthBodyBuilder.build()).
				willReturn(TransactionBody.newBuilder().build());
		given(mockSynthBodyBuilder.setTransactionID(any(TransactionID.class)))
				.willReturn(mockSynthBodyBuilder);
		given(feeCalculator.computeFee(any(), any(), any(), any()))
				.willReturn(mockFeeObject);
		given(mockFeeObject.getServiceFee())
				.willReturn(1L);
		given(creator.createSuccessfulSyntheticRecord(Collections.emptyList(), sideEffects, EMPTY_MEMO))
				.willReturn(mockRecordBuilder);

		// when:
		subject.prepareFields(frame);
		subject.prepareComputation(pretendArguments, a -> a);
		subject.computeGasRequirement(TEST_CONSENSUS_TIME);
		final var result = subject.computeInternal(frame);

		// then:
		assertEquals(successResult, result);
		verify(associateLogic).associate(Id.fromGrpcAccount(accountMerkleId), Collections.singletonList(tokenMerkleId));
		verify(wrappedLedgers).commit();
		verify(worldUpdater).manageInProgressRecord(recordsHistorian, mockRecordBuilder, mockSynthBodyBuilder);
	}

	@Test
	void computeMultiAssociateTokenHappyPathWorks() {
		givenCommonFrameContext();
		givenLedgers();
		Bytes pretendArguments = Bytes.ofUnsignedInt(ABI_ID_ASSOCIATE_TOKENS);
		given(decoder.decodeMultipleAssociations(eq(pretendArguments), any()))
				.willReturn(multiAssociateOp);
		given(syntheticTxnFactory.createAssociate(multiAssociateOp))
				.willReturn(mockSynthBodyBuilder);
		given(sigsVerifier.hasActiveKey(false,
				Id.fromGrpcAccount(accountMerkleId).asEvmAddress(), senderAddress, wrappedLedgers))
				.willReturn(true);
		given(infrastructureFactory.newAccountStore(accounts)).willReturn(accountStore);
		given(infrastructureFactory.newTokenStore(accountStore, sideEffects, tokens, nfts, tokenRels))
				.willReturn(tokenStore);
		given(infrastructureFactory.newAssociateLogic(accountStore, tokenStore)).willReturn(associateLogic);
		given(associateLogic.validateSyntax(any())).willReturn(OK);
		given(feeCalculator.estimatedGasPriceInTinybars(HederaFunctionality.ContractCall, timestamp))
				.willReturn(1L);
		given(mockSynthBodyBuilder.build()).
				willReturn(TransactionBody.newBuilder().build());
		given(mockSynthBodyBuilder.setTransactionID(any(TransactionID.class)))
				.willReturn(mockSynthBodyBuilder);
		given(feeCalculator.computeFee(any(), any(), any(), any()))
				.willReturn(mockFeeObject);
		given(mockFeeObject.getServiceFee())
				.willReturn(1L);
		given(creator.createSuccessfulSyntheticRecord(Collections.emptyList(), sideEffects, EMPTY_MEMO))
				.willReturn(mockRecordBuilder);

		// when:
		subject.prepareFields(frame);
		subject.prepareComputation(pretendArguments, a -> a);
		subject.computeGasRequirement(TEST_CONSENSUS_TIME);
		final var result = subject.computeInternal(frame);

		// then:
		assertEquals(successResult, result);
		verify(associateLogic).associate(Id.fromGrpcAccount(accountMerkleId), multiAssociateOp.tokenIds());
		verify(wrappedLedgers).commit();
		verify(worldUpdater).manageInProgressRecord(recordsHistorian, mockRecordBuilder, mockSynthBodyBuilder);
	}

	private void givenFrameContextWithDelegateCallFromParent() {
		given(parentFrame.getContractAddress()).willReturn(parentContractAddress);
		given(parentFrame.getRecipientAddress()).willReturn(parentRecipientAddress);
		givenCommonFrameContext();
		given(frame.getMessageFrameStack().descendingIterator().hasNext()).willReturn(true);
		given(frame.getMessageFrameStack().descendingIterator().next()).willReturn(parentFrame);
	}

	private void givenFrameContextWithEmptyMessageFrameStack() {
		givenCommonFrameContext();
		given(frame.getMessageFrameStack().descendingIterator().hasNext()).willReturn(false);
	}

	private void givenFrameContextWithoutParentFrame() {
		givenCommonFrameContext();
		given(frame.getMessageFrameStack().descendingIterator().hasNext()).willReturn(true, false);
	}

	private void givenCommonFrameContext() {
		given(frame.getContractAddress()).willReturn(contractAddress);
		given(frame.getRecipientAddress()).willReturn(contractAddress);
		given(frame.getSenderAddress()).willReturn(senderAddress);
		given(frame.getMessageFrameStack()).willReturn(frameDeque);
		given(frame.getMessageFrameStack().descendingIterator()).willReturn(dequeIterator);
		given(frame.getWorldUpdater()).willReturn(worldUpdater);
		given(frame.getRemainingGas()).willReturn(300L);
		given(frame.getValue()).willReturn(Wei.ZERO);
		given(worldUpdater.aliases()).willReturn(aliases);
		given(aliases.resolveForEvm(any())).willAnswer(invocationOnMock -> invocationOnMock.getArgument(0));
		given(worldUpdater.aliases()).willReturn(aliases);
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

}
