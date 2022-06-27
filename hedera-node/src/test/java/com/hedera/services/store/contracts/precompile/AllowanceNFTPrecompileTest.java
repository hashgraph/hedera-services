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
import com.hedera.services.pricing.AssetsLoader;
import com.hedera.services.records.RecordsHistorian;
import com.hedera.services.state.expiry.ExpiringCreations;
import com.hedera.services.state.merkle.MerkleAccount;
import com.hedera.services.state.merkle.MerkleToken;
import com.hedera.services.state.merkle.MerkleTokenRelStatus;
import com.hedera.services.state.merkle.MerkleUniqueToken;
import com.hedera.services.state.submerkle.EntityId;
import com.hedera.services.state.submerkle.ExpirableTxnRecord;
import com.hedera.services.state.submerkle.FcTokenAllowanceId;
import com.hedera.services.store.AccountStore;
import com.hedera.services.store.TypedTokenStore;
import com.hedera.services.store.contracts.HederaStackedWorldStateUpdater;
import com.hedera.services.store.contracts.WorldLedgers;
import com.hedera.services.store.contracts.precompile.codec.DecodingFacade;
import com.hedera.services.store.contracts.precompile.codec.EncodingFacade;
import com.hedera.services.store.contracts.precompile.utils.PrecompilePricingUtils;
import com.hedera.services.store.models.Account;
import com.hedera.services.store.models.NftId;
import com.hedera.services.txns.token.AssociateLogic;
import com.hedera.services.utils.EntityIdUtils;
import com.hedera.services.utils.EntityNum;
import com.hederahashgraph.api.proto.java.AccountID;
import com.hederahashgraph.api.proto.java.CryptoAllowance;
import com.hederahashgraph.api.proto.java.ExchangeRate;
import com.hederahashgraph.api.proto.java.HederaFunctionality;
import com.hederahashgraph.api.proto.java.NftAllowance;
import com.hederahashgraph.api.proto.java.NftRemoveAllowance;
import com.hederahashgraph.api.proto.java.SubType;
import com.hederahashgraph.api.proto.java.TokenAllowance;
import com.hederahashgraph.api.proto.java.TokenID;
import com.hederahashgraph.api.proto.java.TransactionBody;
import com.hederahashgraph.api.proto.java.TransactionID;
import com.hederahashgraph.fee.FeeObject;
import org.apache.commons.lang3.tuple.Pair;
import org.apache.tuweni.bytes.Bytes;
import org.hyperledger.besu.datatypes.Address;
import org.hyperledger.besu.datatypes.Wei;
import org.hyperledger.besu.evm.frame.MessageFrame;
import org.hyperledger.besu.evm.gascalculator.GasCalculator;
import org.hyperledger.besu.evm.worldstate.WorldUpdater;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.io.IOException;
import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Deque;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.TreeSet;

import static com.hedera.services.state.EntityCreator.EMPTY_MEMO;
import static com.hedera.services.store.contracts.precompile.AbiConstants.ABI_ID_APPROVE;
import static com.hedera.services.store.contracts.precompile.AbiConstants.ABI_ID_GET_APPROVED;
import static com.hedera.services.store.contracts.precompile.AbiConstants.ABI_ID_IS_APPROVED_FOR_ALL;
import static com.hedera.services.store.contracts.precompile.AbiConstants.ABI_ID_SET_APPROVAL_FOR_ALL;
import static com.hedera.services.store.contracts.precompile.HTSTestsUtil.DEFAULT_GAS_PRICE;
import static com.hedera.services.store.contracts.precompile.HTSTestsUtil.TEST_CONSENSUS_TIME;
import static com.hedera.services.store.contracts.precompile.HTSTestsUtil.accountId;
import static com.hedera.services.store.contracts.precompile.HTSTestsUtil.contractAddress;
import static com.hedera.services.store.contracts.precompile.HTSTestsUtil.failResult;
import static com.hedera.services.store.contracts.precompile.HTSTestsUtil.feeCollector;
import static com.hedera.services.store.contracts.precompile.HTSTestsUtil.parentContractAddress;
import static com.hedera.services.store.contracts.precompile.HTSTestsUtil.parentRecipientAddress;
import static com.hedera.services.store.contracts.precompile.HTSTestsUtil.receiver;
import static com.hedera.services.store.contracts.precompile.HTSTestsUtil.sender;
import static com.hedera.services.store.contracts.precompile.HTSTestsUtil.senderAddress;
import static com.hedera.services.store.contracts.precompile.HTSTestsUtil.senderId;
import static com.hedera.services.store.contracts.precompile.HTSTestsUtil.successResult;
import static com.hedera.services.store.contracts.precompile.HTSTestsUtil.timestamp;
import static com.hedera.services.store.contracts.precompile.HTSTestsUtil.token;
import static com.hederahashgraph.api.proto.java.ResponseCodeEnum.FAIL_INVALID;
import static com.hederahashgraph.api.proto.java.ResponseCodeEnum.INVALID_ALLOWANCE_OWNER_ID;
import static com.hederahashgraph.api.proto.java.ResponseCodeEnum.OK;
import static com.hederahashgraph.api.proto.java.ResponseCodeEnum.SENDER_DOES_NOT_OWN_NFT_SERIAL_NO;
import static org.hyperledger.besu.datatypes.Address.RIPEMD160;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.BDDMockito.given;
import static org.mockito.BDDMockito.willThrow;
import static org.mockito.Mockito.verify;

@ExtendWith(MockitoExtension.class)
@SuppressWarnings("rawtypes")
class AllowanceNFTPrecompileTest {
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

	private static final long TEST_SERVICE_FEE = 5_000_000;
	private static final long TEST_NETWORK_FEE = 400_000;
	private static final long TEST_NODE_FEE = 300_000;
	private static final int CENTS_RATE = 12;
	private static final int HBAR_RATE = 1;
	private static final long EXPECTED_GAS_PRICE =
			(TEST_SERVICE_FEE + TEST_NETWORK_FEE + TEST_NODE_FEE) / DEFAULT_GAS_PRICE * 6 / 5;

	private HTSPrecompiledContract subject;

	@BeforeEach
	void setUp() throws IOException {
		Map<HederaFunctionality, Map<SubType, BigDecimal>> canonicalPrices = new HashMap<>();
		canonicalPrices.put(HederaFunctionality.TokenAssociateToAccount, Map.of(SubType.DEFAULT, BigDecimal.valueOf(0)));
		given(assetLoader.loadCanonicalPrices()).willReturn(canonicalPrices);
		PrecompilePricingUtils precompilePricingUtils = new PrecompilePricingUtils(assetLoader, exchange, () -> feeCalculator, resourceCosts, stateView);
		subject = new HTSPrecompiledContract(
				dynamicProperties, gasCalculator, recordsHistorian, sigsVerifier, decoder, encoder, syntheticTxnFactory,
				creator, impliedTransfersMarshal, () -> feeCalculator, stateView, precompilePricingUtils,
				infrastructureFactory);

		given(infrastructureFactory.newSideEffects()).willReturn(sideEffects);
		given(worldUpdater.permissivelyUnaliased(any())).willAnswer(invocationOnMock -> invocationOnMock.getArgument(0));
	}

//	@Test
//	void isApprovedForAllWorksWithBothOwnerAndOperatorExtant() {
//		Set<FcTokenAllowanceId> allowances = new TreeSet<>();
//		FcTokenAllowanceId fcTokenAllowanceId = FcTokenAllowanceId.from(EntityNum.fromLong(token.getTokenNum()),
//				EntityNum.fromLong(receiver.getAccountNum()));
//		allowances.add(fcTokenAllowanceId);
//
//		Bytes nestedPretendArguments = Bytes.of(Integers.toBytes(ABI_ID_IS_APPROVED_FOR_ALL));
//		Bytes pretendArguments = givenMinimalFrameContext(nestedPretendArguments);
//		given(wrappedLedgers.accounts()).willReturn(accounts);
//		given(accounts.contains(IS_APPROVE_FOR_ALL_WRAPPER.owner())).willReturn(true);
//		given(accounts.contains(IS_APPROVE_FOR_ALL_WRAPPER.operator())).willReturn(true);
//		given(syntheticTxnFactory.createTransactionCall(1L, pretendArguments)).willReturn(mockSynthBodyBuilder);
//		given(creator.createSuccessfulSyntheticRecord(Collections.emptyList(), sideEffects, EMPTY_MEMO))
//				.willReturn(mockRecordBuilder);
//
//		given(feeCalculator.estimatedGasPriceInTinybars(HederaFunctionality.ContractCall, timestamp))
//				.willReturn(1L);
//		given(feeCalculator.estimatePayment(any(), any(), any(), any(), any())).willReturn(mockFeeObject);
//		given(mockFeeObject.getNodeFee())
//				.willReturn(1L);
//		given(mockFeeObject.getNetworkFee())
//				.willReturn(1L);
//		given(mockFeeObject.getServiceFee())
//				.willReturn(1L);
//
//		given(encoder.encodeIsApprovedForAll(true)).willReturn(successResult);
//		given(decoder.decodeIsApprovedForAll(eq(nestedPretendArguments), any())).willReturn(
//				IS_APPROVE_FOR_ALL_WRAPPER);
//		given(dynamicProperties.areAllowancesEnabled()).willReturn(true);
//		given(accounts.get(any(), any())).willReturn(allowances);
//
//		// when:
//		subject.prepareFields(frame);
//		subject.prepareComputation(pretendArguments, a -> a);
//		subject.getPrecompile().getGasRequirement(TEST_CONSENSUS_TIME);
//		final var result = subject.computeInternal(frame);
//
//		// then:
//		assertEquals(successResult, result);
//		verify(wrappedLedgers).commit();
//		verify(worldUpdater).manageInProgressRecord(recordsHistorian, mockRecordBuilder, mockSynthBodyBuilder);
//	}
//
//	@Test
//	void isApprovedForAllWorksWithOperatorMissing() {
//		Bytes nestedPretendArguments = Bytes.of(Integers.toBytes(ABI_ID_IS_APPROVED_FOR_ALL));
//		Bytes pretendArguments = givenMinimalFrameContext(nestedPretendArguments);
//		given(wrappedLedgers.accounts()).willReturn(accounts);
//		given(accounts.contains(IS_APPROVE_FOR_ALL_WRAPPER.owner())).willReturn(true);
//		given(syntheticTxnFactory.createTransactionCall(1L, pretendArguments)).willReturn(mockSynthBodyBuilder);
//		given(creator.createSuccessfulSyntheticRecord(Collections.emptyList(), sideEffects, EMPTY_MEMO))
//				.willReturn(mockRecordBuilder);
//
//		given(feeCalculator.estimatedGasPriceInTinybars(HederaFunctionality.ContractCall, timestamp))
//				.willReturn(1L);
//		given(feeCalculator.estimatePayment(any(), any(), any(), any(), any())).willReturn(mockFeeObject);
//		given(mockFeeObject.getNodeFee())
//				.willReturn(1L);
//		given(mockFeeObject.getNetworkFee())
//				.willReturn(1L);
//		given(mockFeeObject.getServiceFee())
//				.willReturn(1L);
//
//		given(encoder.encodeIsApprovedForAll(false)).willReturn(successResult);
//		given(decoder.decodeIsApprovedForAll(eq(nestedPretendArguments), any())).willReturn(
//				IS_APPROVE_FOR_ALL_WRAPPER);
//		given(dynamicProperties.areAllowancesEnabled()).willReturn(true);
//
//		// when:
//		subject.prepareFields(frame);
//		subject.prepareComputation(pretendArguments, a -> a);
//		subject.getPrecompile().getGasRequirement(TEST_CONSENSUS_TIME);
//		final var result = subject.computeInternal(frame);
//
//		// then:
//		assertEquals(successResult, result);
//		verify(wrappedLedgers).commit();
//		verify(worldUpdater).manageInProgressRecord(recordsHistorian, mockRecordBuilder, mockSynthBodyBuilder);
//	}
//
//	@Test
//	void approve() {
//		List<CryptoAllowance> cryptoAllowances = new ArrayList<>();
//		List<TokenAllowance> tokenAllowances = new ArrayList<>();
//		List<NftAllowance> nftAllowances = new ArrayList<>();
//		Bytes nestedPretendArguments = Bytes.of(Integers.toBytes(ABI_ID_APPROVE));
//		Bytes pretendArguments = givenMinimalFrameContext(nestedPretendArguments);
//		givenLedgers();
//		givenPricingUtilsContext();
//
//		given(wrappedLedgers.tokens()).willReturn(tokens);
//		given(wrappedLedgers.accounts()).willReturn(accounts);
//		given(creator.createSuccessfulSyntheticRecord(Collections.emptyList(), sideEffects, EMPTY_MEMO))
//				.willReturn(mockRecordBuilder);
//
//		given(feeCalculator.estimatedGasPriceInTinybars(HederaFunctionality.ContractCall, timestamp))
//				.willReturn(1L);
//		given(feeCalculator.computeFee(any(), any(), any(), any()))
//				.willReturn(mockFeeObject);
//		given(mockFeeObject.getServiceFee())
//				.willReturn(1L);
//
//		given(syntheticTxnFactory.createNonfungibleApproval(eq(APPROVE_WRAPPER), any(), any()))
//				.willReturn(mockSynthBodyBuilder);
//		given(mockSynthBodyBuilder.build()).
//				willReturn(TransactionBody.newBuilder().build());
//		given(mockSynthBodyBuilder.setTransactionID(any(TransactionID.class)))
//				.willReturn(mockSynthBodyBuilder);
//		given(mockSynthBodyBuilder.getCryptoApproveAllowance()).willReturn(cryptoApproveAllowanceTransactionBody);
//
//		given(infrastructureFactory.newAccountStore(accounts)).willReturn(accountStore);
//		given(infrastructureFactory.newTokenStore(accountStore, sideEffects, tokens, nfts, tokenRels))
//				.willReturn(tokenStore);
//		given(infrastructureFactory.newApproveAllowanceLogic(accountStore, tokenStore)).willReturn(approveAllowanceLogic);
//		given(EntityIdUtils.accountIdFromEvmAddress((Address) any())).willReturn(sender);
//		given(accountStore.loadAccount(any())).willReturn(new Account(accountId));
//		given(dynamicProperties.areAllowancesEnabled()).willReturn(true);
//		given(wrappedLedgers.ownerIfPresent(any())).willReturn(senderId);
//		given(wrappedLedgers.hasApprovedForAll(any(), any(), any())).willReturn(true);
//		given(infrastructureFactory.newApproveAllowanceChecks()).willReturn(allowanceChecks);
//		given(infrastructureFactory.newDeleteAllowanceChecks()).willReturn(deleteAllowanceChecks);
//
//		given(allowanceChecks.allowancesValidation(cryptoAllowances, tokenAllowances, nftAllowances,
//				new Account(accountId), stateView))
//				.willReturn(OK);
//
//		given(decoder.decodeTokenApprove(eq(nestedPretendArguments), eq(token), eq(false), any())).willReturn(
//				APPROVE_WRAPPER);
//		given(encoder.encodeApprove(true)).willReturn(successResult);
//
//		// when:
//		subject.prepareFields(frame);
//		subject.prepareComputation(pretendArguments, a -> a);
//		subject.getPrecompile().getGasRequirement(TEST_CONSENSUS_TIME);
//		final var result = subject.computeInternal(frame);
//
//		// then:
//		assertEquals(successResult, result);
//		verify(wrappedLedgers).commit();
//		verify(worldUpdater).manageInProgressRecord(recordsHistorian, mockRecordBuilder, mockSynthBodyBuilder);
//	}
//
//	@Test
//	void approveRevertsIfItFails() {
//		givenPricingUtilsContext();
//		List<CryptoAllowance> cryptoAllowances = new ArrayList<>();
//		List<TokenAllowance> tokenAllowances = new ArrayList<>();
//		List<NftAllowance> nftAllowances = new ArrayList<>();
//		Bytes nestedPretendArguments = Bytes.of(Integers.toBytes(ABI_ID_APPROVE));
//		Bytes pretendArguments = givenMinimalFrameContext(nestedPretendArguments);
//
//		given(wrappedLedgers.tokens()).willReturn(tokens);
//		given(wrappedLedgers.accounts()).willReturn(accounts);
//
//		given(feeCalculator.estimatedGasPriceInTinybars(HederaFunctionality.ContractCall, timestamp))
//				.willReturn(1L);
//		given(feeCalculator.computeFee(any(), any(), any(), any()))
//				.willReturn(mockFeeObject);
//		given(mockFeeObject.getServiceFee())
//				.willReturn(1L);
//
//		given(syntheticTxnFactory.createNonfungibleApproval(eq(APPROVE_WRAPPER), any(), any()))
//				.willReturn(mockSynthBodyBuilder);
//		given(mockSynthBodyBuilder.build()).
//				willReturn(TransactionBody.newBuilder().build());
//		given(mockSynthBodyBuilder.setTransactionID(any(TransactionID.class)))
//				.willReturn(mockSynthBodyBuilder);
//		given(mockSynthBodyBuilder.getCryptoApproveAllowance()).willReturn(cryptoApproveAllowanceTransactionBody);
//
//		given(infrastructureFactory.newAccountStore(accounts)).willReturn(accountStore);
//		given(EntityIdUtils.accountIdFromEvmAddress((Address) any())).willReturn(sender);
//		given(accountStore.loadAccount(any())).willReturn(new Account(accountId));
//		given(dynamicProperties.areAllowancesEnabled()).willReturn(true);
//
//		given(allowanceChecks.allowancesValidation(cryptoAllowances, tokenAllowances, nftAllowances,
//				new Account(accountId), stateView))
//				.willReturn(OK);
//		given(wrappedLedgers.ownerIfPresent(any())).willReturn(senderId);
//		given(wrappedLedgers.hasApprovedForAll(any(), any(), any())).willReturn(true);
//
//		given(decoder.decodeTokenApprove(eq(nestedPretendArguments), eq(token), eq(false), any())).willReturn(
//				APPROVE_WRAPPER);
//		given(infrastructureFactory.newApproveAllowanceLogic(any(), any()))
//				.willReturn(approveAllowanceLogic);
//		given(infrastructureFactory.newApproveAllowanceChecks()).willReturn(allowanceChecks);
//		given(infrastructureFactory.newDeleteAllowanceChecks()).willReturn(deleteAllowanceChecks);
//		willThrow(new InvalidTransactionException(SENDER_DOES_NOT_OWN_NFT_SERIAL_NO))
//				.given(approveAllowanceLogic)
//				.approveAllowance(any(), any(), any(), any());
//
//		// when:
//		subject.prepareFields(frame);
//		subject.prepareComputation(pretendArguments, a -> a);
//		subject.getPrecompile().getGasRequirement(TEST_CONSENSUS_TIME);
//		final var result = subject.computeInternal(frame);
//		final var expectedFailure = EncodingFacade.resultFrom(SENDER_DOES_NOT_OWN_NFT_SERIAL_NO);
//
//		verify(frame).setRevertReason(Bytes.of(SENDER_DOES_NOT_OWN_NFT_SERIAL_NO.name().getBytes()));
//		assertEquals(expectedFailure, result);
//	}
//
//	@Test
//	void approveSpender0WhenOwner() {
//		givenPricingUtilsContext();
//		Bytes nestedPretendArguments = Bytes.of(Integers.toBytes(ABI_ID_APPROVE));
//		Bytes pretendArguments = givenMinimalFrameContext(nestedPretendArguments);
//
//		given(wrappedLedgers.tokens()).willReturn(tokens);
//		given(wrappedLedgers.accounts()).willReturn(accounts);
//		given(wrappedLedgers.nfts()).willReturn(nfts);
//		given(creator.createSuccessfulSyntheticRecord(Collections.emptyList(), sideEffects, EMPTY_MEMO))
//				.willReturn(mockRecordBuilder);
//
//		given(feeCalculator.estimatedGasPriceInTinybars(HederaFunctionality.ContractCall, timestamp))
//				.willReturn(1L);
//		given(feeCalculator.computeFee(any(), any(), any(), any()))
//				.willReturn(mockFeeObject);
//		given(mockFeeObject.getServiceFee())
//				.willReturn(1L);
//
//		given(wrappedLedgers.ownerIfPresent(any())).willReturn(senderId);
//		given(syntheticTxnFactory.createDeleteAllowance(APPROVE_WRAPPER_0, EntityId.fromGrpcAccountId(sender)))
//				.willReturn(mockSynthBodyBuilder);
//		given(mockSynthBodyBuilder.build()).
//				willReturn(TransactionBody.newBuilder().build());
//		given(mockSynthBodyBuilder.setTransactionID(any(TransactionID.class)))
//				.willReturn(mockSynthBodyBuilder);
//		given(mockSynthBodyBuilder.getCryptoDeleteAllowance()).willReturn(cryptoDeleteAllowanceTransactionBody);
//		given(cryptoDeleteAllowanceTransactionBody.getNftAllowancesList()).willReturn(List.of(
//				NftRemoveAllowance.newBuilder()
//						.setOwner(sender)
//						.build()
//		));
//
//		given(infrastructureFactory.newAccountStore(accounts)).willReturn(accountStore);
//		given(EntityIdUtils.accountIdFromEvmAddress((Address) any())).willReturn(sender);
//		given(accountStore.loadAccount(any())).willReturn(new Account(accountId));
//		given(dynamicProperties.areAllowancesEnabled()).willReturn(true);
//		given(deleteAllowanceChecks.deleteAllowancesValidation(any(), any(), any()))
//				.willReturn(OK);
//
//		given(decoder.decodeTokenApprove(eq(nestedPretendArguments), eq(token), eq(false), any())).willReturn(
//				APPROVE_WRAPPER_0);
//		given(encoder.encodeApprove(true)).willReturn(successResult);
//		given(infrastructureFactory.newDeleteAllowanceLogic(any(), any())).willReturn(deleteAllowanceLogic);
//		given(wrappedLedgers.ownerIfPresent(any())).willReturn(senderId);
//		given(wrappedLedgers.hasApprovedForAll(any(), any(), any())).willReturn(true);
//		given(infrastructureFactory.newApproveAllowanceChecks()).willReturn(allowanceChecks);
//		given(infrastructureFactory.newDeleteAllowanceChecks()).willReturn(deleteAllowanceChecks);
//
//		// when:
//		subject.prepareFields(frame);
//		subject.prepareComputation(pretendArguments, a -> a);
//		subject.getPrecompile().getGasRequirement(TEST_CONSENSUS_TIME);
//		final var result = subject.computeInternal(frame);
//
//		// then:
//		assertEquals(successResult, result);
//		verify(wrappedLedgers).commit();
//		verify(worldUpdater).manageInProgressRecord(recordsHistorian, mockRecordBuilder, mockSynthBodyBuilder);
//	}
//
//	@Test
//	void approveSpender0WhenGrantedApproveForAll() {
//		givenPricingUtilsContext();
//		Bytes nestedPretendArguments = Bytes.of(Integers.toBytes(ABI_ID_APPROVE));
//		Bytes pretendArguments = givenMinimalFrameContext(nestedPretendArguments);
//
//		given(wrappedLedgers.tokens()).willReturn(tokens);
//		given(wrappedLedgers.accounts()).willReturn(accounts);
//		given(wrappedLedgers.nfts()).willReturn(nfts);
//		given(creator.createSuccessfulSyntheticRecord(Collections.emptyList(), sideEffects, EMPTY_MEMO))
//				.willReturn(mockRecordBuilder);
//
//		given(feeCalculator.estimatedGasPriceInTinybars(HederaFunctionality.ContractCall, timestamp))
//				.willReturn(1L);
//		given(feeCalculator.computeFee(any(), any(), any(), any()))
//				.willReturn(mockFeeObject);
//		given(mockFeeObject.getServiceFee())
//				.willReturn(1L);
//
//		given(wrappedLedgers.ownerIfPresent(any())).willReturn(senderId);
//		given(syntheticTxnFactory.createDeleteAllowance(APPROVE_WRAPPER_0, EntityId.fromGrpcAccountId(sender)))
//				.willReturn(mockSynthBodyBuilder);
//		given(mockSynthBodyBuilder.build()).
//				willReturn(TransactionBody.newBuilder().build());
//		given(mockSynthBodyBuilder.setTransactionID(any(TransactionID.class)))
//				.willReturn(mockSynthBodyBuilder);
//		given(mockSynthBodyBuilder.getCryptoDeleteAllowance()).willReturn(cryptoDeleteAllowanceTransactionBody);
//		given(cryptoDeleteAllowanceTransactionBody.getNftAllowancesList()).willReturn(List.of(
//				NftRemoveAllowance.newBuilder()
//						.setOwner(feeCollector)
//						.build()
//		));
//		given(wrappedLedgers.hasApprovedForAll(any(), any(), any())).willReturn(true);
//
//		given(infrastructureFactory.newAccountStore(accounts)).willReturn(accountStore);
//		given(EntityIdUtils.accountIdFromEvmAddress((Address) any())).willReturn(sender);
//		given(accountStore.loadAccount(any())).willReturn(new Account(accountId));
//		given(dynamicProperties.areAllowancesEnabled()).willReturn(true);
//		given(deleteAllowanceChecks.deleteAllowancesValidation(any(), any(), any()))
//				.willReturn(OK);
//
//		given(decoder.decodeTokenApprove(eq(nestedPretendArguments), eq(token), eq(false), any())).willReturn(
//				APPROVE_WRAPPER_0);
//		given(encoder.encodeApprove(true)).willReturn(successResult);
//		given(infrastructureFactory.newDeleteAllowanceLogic(any(), any())).willReturn(deleteAllowanceLogic);
//		given(infrastructureFactory.newApproveAllowanceChecks()).willReturn(allowanceChecks);
//		given(infrastructureFactory.newDeleteAllowanceChecks()).willReturn(deleteAllowanceChecks);
//
//		// when:
//		subject.prepareFields(frame);
//		subject.prepareComputation(pretendArguments, a -> a);
//		subject.getPrecompile().getGasRequirement(TEST_CONSENSUS_TIME);
//		final var result = subject.computeInternal(frame);
//
//		// then:
//		assertEquals(successResult, result);
//		verify(wrappedLedgers).commit();
//		verify(worldUpdater).manageInProgressRecord(recordsHistorian, mockRecordBuilder, mockSynthBodyBuilder);
//	}
//
//	@Test
//	void approveSpender0NoGoodIfNotPermissioned() {
//		givenPricingUtilsContext();
//		Bytes nestedPretendArguments = Bytes.of(Integers.toBytes(ABI_ID_APPROVE));
//		Bytes pretendArguments = givenMinimalFrameContext(nestedPretendArguments);
//
//		given(feeCalculator.estimatedGasPriceInTinybars(HederaFunctionality.ContractCall, timestamp))
//				.willReturn(1L);
//		given(feeCalculator.computeFee(any(), any(), any(), any()))
//				.willReturn(mockFeeObject);
//		given(mockFeeObject.getServiceFee())
//				.willReturn(1L);
//
//		given(wrappedLedgers.ownerIfPresent(any())).willReturn(senderId);
//		given(syntheticTxnFactory.createDeleteAllowance(APPROVE_WRAPPER_0, EntityId.fromGrpcAccountId(sender)))
//				.willReturn(mockSynthBodyBuilder);
//		given(mockSynthBodyBuilder.build()).
//				willReturn(TransactionBody.newBuilder().build());
//		given(mockSynthBodyBuilder.setTransactionID(any(TransactionID.class)))
//				.willReturn(mockSynthBodyBuilder);
//		given(wrappedLedgers.ownerIfPresent(any())).willReturn(senderId);
//		given(EntityIdUtils.accountIdFromEvmAddress((Address) any())).willReturn(sender);
//		given(dynamicProperties.areAllowancesEnabled()).willReturn(true);
//
//		given(decoder.decodeTokenApprove(eq(nestedPretendArguments), eq(token), eq(false), any())).willReturn(
//				APPROVE_WRAPPER_0);
//
//		// when:
//		subject.prepareFields(frame);
//		subject.prepareComputation(pretendArguments, a -> a);
//		subject.getPrecompile().getGasRequirement(TEST_CONSENSUS_TIME);
//		final var result = subject.computeInternal(frame);
//		final var expectedFailure = EncodingFacade.resultFrom(SENDER_DOES_NOT_OWN_NFT_SERIAL_NO);
//
//		verify(frame).setRevertReason(Bytes.of(SENDER_DOES_NOT_OWN_NFT_SERIAL_NO.name().getBytes()));
//		assertEquals(expectedFailure, result);
//	}
//
//	@Test
//	void validatesImpliedNftApprovalDeletion() {
//		givenPricingUtilsContext();
//		Bytes nestedPretendArguments = Bytes.of(Integers.toBytes(ABI_ID_APPROVE));
//		Bytes pretendArguments = givenMinimalFrameContext(nestedPretendArguments);
//		given(wrappedLedgers.tokens()).willReturn(tokens);
//		given(wrappedLedgers.accounts()).willReturn(accounts);
//		given(wrappedLedgers.nfts()).willReturn(nfts);
//		given(creator.createUnsuccessfulSyntheticRecord(INVALID_ALLOWANCE_OWNER_ID)).willReturn(mockRecordBuilder);
//
//		given(feeCalculator.estimatedGasPriceInTinybars(HederaFunctionality.ContractCall, timestamp))
//				.willReturn(1L);
//		given(feeCalculator.computeFee(any(), any(), any(), any()))
//				.willReturn(mockFeeObject);
//		given(mockFeeObject.getServiceFee())
//				.willReturn(1L);
//
//		given(wrappedLedgers.ownerIfPresent(any())).willReturn(senderId);
//		given(syntheticTxnFactory.createDeleteAllowance(APPROVE_WRAPPER_0, EntityId.fromGrpcAccountId(sender)))
//				.willReturn(mockSynthBodyBuilder);
//		given(mockSynthBodyBuilder.build()).
//				willReturn(TransactionBody.newBuilder().build());
//		given(mockSynthBodyBuilder.setTransactionID(any(TransactionID.class)))
//				.willReturn(mockSynthBodyBuilder);
//		given(mockSynthBodyBuilder.getCryptoDeleteAllowance()).willReturn(cryptoDeleteAllowanceTransactionBody);
//
//		given(infrastructureFactory.newAccountStore(accounts)).willReturn(accountStore);
//		given(EntityIdUtils.accountIdFromEvmAddress((Address) any())).willReturn(sender);
//		given(accountStore.loadAccount(any())).willReturn(new Account(accountId));
//		given(dynamicProperties.areAllowancesEnabled()).willReturn(true);
//
//		given(cryptoDeleteAllowanceTransactionBody.getNftAllowancesList()).willReturn(Collections.emptyList());
//		given(wrappedLedgers.ownerIfPresent(any())).willReturn(senderId);
//		given(wrappedLedgers.hasApprovedForAll(any(), any(), any())).willReturn(true);
//		given(infrastructureFactory.newApproveAllowanceChecks()).willReturn(allowanceChecks);
//		given(infrastructureFactory.newDeleteAllowanceChecks()).willReturn(deleteAllowanceChecks);
//
//		given(decoder.decodeTokenApprove(eq(nestedPretendArguments), eq(token), eq(false), any())).willReturn(
//				APPROVE_WRAPPER_0);
//		given(deleteAllowanceChecks.deleteAllowancesValidation(any(), any(), any()))
//				.willReturn(INVALID_ALLOWANCE_OWNER_ID);
//
//		// when:
//		subject.prepareFields(frame);
//		subject.prepareComputation(pretendArguments, a -> a);
//		subject.getPrecompile().getGasRequirement(TEST_CONSENSUS_TIME);
//		final var result = subject.computeInternal(frame);
//		final var expectedFailure = EncodingFacade.resultFrom(INVALID_ALLOWANCE_OWNER_ID);
//
//		verify(frame).setRevertReason(Bytes.of(INVALID_ALLOWANCE_OWNER_ID.name().getBytes()));
//		assertEquals(expectedFailure, result);
//	}
//
//	@Test
//	void allowanceValidation() {
//		givenPricingUtilsContext();
//		List<CryptoAllowance> cryptoAllowances = new ArrayList<>();
//		List<TokenAllowance> tokenAllowances = new ArrayList<>();
//		List<NftAllowance> nftAllowances = new ArrayList<>();
//
//		Bytes nestedPretendArguments = Bytes.of(Integers.toBytes(ABI_ID_APPROVE));
//		Bytes pretendArguments = givenMinimalFrameContext(nestedPretendArguments);
//
//		given(wrappedLedgers.tokens()).willReturn(tokens);
//		given(wrappedLedgers.accounts()).willReturn(accounts);
//
//		given(feeCalculator.estimatedGasPriceInTinybars(HederaFunctionality.ContractCall, timestamp))
//				.willReturn(1L);
//		given(feeCalculator.computeFee(any(), any(), any(), any()))
//				.willReturn(mockFeeObject);
//		given(mockFeeObject.getServiceFee())
//				.willReturn(1L);
//
//		given(wrappedLedgers.ownerIfPresent(any())).willReturn(senderId);
//		given(wrappedLedgers.hasApprovedForAll(any(), any(), any())).willReturn(true);
//		given(syntheticTxnFactory.createNonfungibleApproval(eq(APPROVE_WRAPPER), any(), any()))
//				.willReturn(mockSynthBodyBuilder);
//		given(mockSynthBodyBuilder.build()).
//				willReturn(TransactionBody.newBuilder().build());
//		given(mockSynthBodyBuilder.setTransactionID(any(TransactionID.class)))
//				.willReturn(mockSynthBodyBuilder);
//		given(mockSynthBodyBuilder.getCryptoApproveAllowance()).willReturn(cryptoApproveAllowanceTransactionBody);
//
//		given(infrastructureFactory.newAccountStore(accounts)).willReturn(accountStore);
//		given(infrastructureFactory.newApproveAllowanceChecks()).willReturn(allowanceChecks);
//		given(infrastructureFactory.newDeleteAllowanceChecks()).willReturn(deleteAllowanceChecks);
//		given(EntityIdUtils.accountIdFromEvmAddress((Address) any())).willReturn(sender);
//		given(accountStore.loadAccount(any())).willReturn(new Account(accountId));
//		given(dynamicProperties.areAllowancesEnabled()).willReturn(true);
//
//		given(allowanceChecks.allowancesValidation(cryptoAllowances, tokenAllowances, nftAllowances,
//				new Account(accountId), stateView))
//				.willReturn(FAIL_INVALID);
//
//		given(decoder.decodeTokenApprove(eq(nestedPretendArguments), eq(token), eq(false), any())).willReturn(
//				APPROVE_WRAPPER);
//
//		// when:
//		subject.prepareFields(frame);
//		subject.prepareComputation(pretendArguments, a -> a);
//		subject.getPrecompile().getGasRequirement(TEST_CONSENSUS_TIME);
//		final var result = subject.computeInternal(frame);
//
//		// then:
//		assertEquals(failResult, result);
//	}
//
//	@Test
//	void setApprovalForAll() {
//		List<CryptoAllowance> cryptoAllowances = new ArrayList<>();
//		List<TokenAllowance> tokenAllowances = new ArrayList<>();
//		List<NftAllowance> nftAllowances = new ArrayList<>();
//
//		Bytes nestedPretendArguments = Bytes.of(Integers.toBytes(ABI_ID_SET_APPROVAL_FOR_ALL));
//		Bytes pretendArguments = givenMinimalFrameContext(nestedPretendArguments);
//		givenLedgers();
//		givenPricingUtilsContext();
//
//		given(wrappedLedgers.tokens()).willReturn(tokens);
//		given(wrappedLedgers.accounts()).willReturn(accounts);
//		given(creator.createSuccessfulSyntheticRecord(Collections.emptyList(), sideEffects, EMPTY_MEMO))
//				.willReturn(mockRecordBuilder);
//
//		given(feeCalculator.estimatedGasPriceInTinybars(HederaFunctionality.ContractCall, timestamp))
//				.willReturn(1L);
//		given(feeCalculator.computeFee(any(), any(), any(), any()))
//				.willReturn(mockFeeObject);
//		given(mockFeeObject.getServiceFee())
//				.willReturn(1L);
//
//		given(syntheticTxnFactory.createApproveAllowanceForAllNFT(SET_APPROVAL_FOR_ALL_WRAPPER, token))
//				.willReturn(mockSynthBodyBuilder);
//		given(mockSynthBodyBuilder.build()).
//				willReturn(TransactionBody.newBuilder().build());
//		given(mockSynthBodyBuilder.setTransactionID(any(TransactionID.class)))
//				.willReturn(mockSynthBodyBuilder);
//		given(mockSynthBodyBuilder.getCryptoApproveAllowance()).willReturn(cryptoApproveAllowanceTransactionBody);
//
//		given(infrastructureFactory.newAccountStore(accounts)).willReturn(accountStore);
//		given(infrastructureFactory.newTokenStore(accountStore, sideEffects, tokens, nfts, tokenRels))
//				.willReturn(tokenStore);
//		given(infrastructureFactory.newApproveAllowanceLogic(accountStore, tokenStore)).willReturn(approveAllowanceLogic);
//		given(EntityIdUtils.accountIdFromEvmAddress((Address) any())).willReturn(sender);
//		given(accountStore.loadAccount(any())).willReturn(new Account(accountId));
//		given(dynamicProperties.areAllowancesEnabled()).willReturn(true);
//		given(infrastructureFactory.newApproveAllowanceChecks()).willReturn(allowanceChecks);
//
//		given(allowanceChecks.allowancesValidation(cryptoAllowances, tokenAllowances, nftAllowances,
//				new Account(accountId), stateView))
//				.willReturn(OK);
//
//		given(decoder.decodeSetApprovalForAll(eq(nestedPretendArguments), any())).willReturn(
//				SET_APPROVAL_FOR_ALL_WRAPPER);
//
//		// when:
//		subject.prepareFields(frame);
//		subject.prepareComputation(pretendArguments, a -> a);
//		subject.getPrecompile().getGasRequirement(TEST_CONSENSUS_TIME);
//		final var result = subject.computeInternal(frame);
//
//		// then:
//		assertEquals(successResult, result);
//		verify(wrappedLedgers).commit();
//		verify(worldUpdater).manageInProgressRecord(recordsHistorian, mockRecordBuilder, mockSynthBodyBuilder);
//	}
//
//	@Test
//	void getApproved() {
//		Set<FcTokenAllowanceId> allowances = new TreeSet<>();
//		FcTokenAllowanceId fcTokenAllowanceId = FcTokenAllowanceId.from(EntityNum.fromLong(token.getTokenNum()),
//				EntityNum.fromLong(receiver.getAccountNum()));
//		allowances.add(fcTokenAllowanceId);
//
//		Bytes nestedPretendArguments = Bytes.of(Integers.toBytes(ABI_ID_GET_APPROVED));
//		Bytes pretendArguments = givenMinimalFrameContext(nestedPretendArguments);
//
//		given(wrappedLedgers.nfts()).willReturn(nfts);
//		final var nftId = NftId.fromGrpc(token, GET_APPROVED_WRAPPER.serialNo());
//		given(nfts.contains(nftId)).willReturn(true);
//		given(nfts.get(nftId, NftProperty.SPENDER)).willReturn(EntityId.fromAddress(RIPEMD160));
//		given(dynamicProperties.areAllowancesEnabled()).willReturn(true);
//		given(syntheticTxnFactory.createTransactionCall(1L, pretendArguments)).willReturn(mockSynthBodyBuilder);
//		given(creator.createSuccessfulSyntheticRecord(Collections.emptyList(), sideEffects, EMPTY_MEMO)).willReturn(
//				mockRecordBuilder);
//
//		given(feeCalculator.estimatedGasPriceInTinybars(HederaFunctionality.ContractCall, timestamp)).willReturn(1L);
//		given(feeCalculator.estimatePayment(any(), any(), any(), any(), any())).willReturn(mockFeeObject);
//		given(mockFeeObject.getNodeFee()).willReturn(1L);
//		given(mockFeeObject.getNetworkFee()).willReturn(1L);
//		given(mockFeeObject.getServiceFee()).willReturn(1L);
//
//		given(encoder.encodeGetApproved(RIPEMD160)).willReturn(successResult);
//		given(decoder.decodeGetApproved(nestedPretendArguments)).willReturn(GET_APPROVED_WRAPPER);
//
//		// when:
//		subject.prepareFields(frame);
//		subject.prepareComputation(pretendArguments, a -> a);
//		subject.getPrecompile().getGasRequirement(TEST_CONSENSUS_TIME);
//		final var result = subject.computeInternal(frame);
//
//		// then:
//		assertEquals(successResult, result);
//		verify(wrappedLedgers).commit();
//		verify(worldUpdater).manageInProgressRecord(recordsHistorian, mockRecordBuilder, mockSynthBodyBuilder);
//	}

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

	private void givenPricingUtilsContext() {
		given(exchange.rate(any())).willReturn(exchangeRate);
		given(exchangeRate.getCentEquiv()).willReturn(CENTS_RATE);
		given(exchangeRate.getHbarEquiv()).willReturn(HBAR_RATE);
	}

	private void givenFrameContext() {
		given(frame.getSenderAddress()).willReturn(contractAddress);
		given(frame.getWorldUpdater()).willReturn(worldUpdater);
		given(worldUpdater.wrappedTrackingLedgers(any())).willReturn(wrappedLedgers);
	}
}
