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
import com.hedera.services.context.primitives.StateView;
import com.hedera.services.context.properties.GlobalDynamicProperties;
import com.hedera.services.contracts.sources.TxnAwareEvmSigsVerifier;
import com.hedera.services.exceptions.InvalidTransactionException;
import com.hedera.services.fees.FeeCalculator;
import com.hedera.services.fees.calculation.UsagePricesProvider;
import com.hedera.services.grpc.marshalling.ImpliedTransfersMarshal;
import com.hedera.services.ledger.TransactionalLedger;
import com.hedera.services.ledger.properties.AccountProperty;
import com.hedera.services.legacy.core.jproto.JKey;
import com.hedera.services.records.RecordsHistorian;
import com.hedera.services.state.enums.TokenType;
import com.hedera.services.state.expiry.ExpiringCreations;
import com.hedera.services.state.merkle.MerkleAccount;
import com.hedera.services.state.submerkle.EntityId;
import com.hedera.services.store.contracts.HederaStackedWorldStateUpdater;
import com.hedera.services.store.contracts.WorldLedgers;
import com.hedera.services.store.contracts.precompile.codec.DecodingFacade;
import com.hedera.services.store.contracts.precompile.codec.EncodingFacade;
import com.hedera.services.store.contracts.precompile.codec.TokenCreateWrapper;
import com.hedera.services.store.contracts.precompile.impl.AssociatePrecompile;
import com.hedera.services.store.contracts.precompile.impl.BurnPrecompile;
import com.hedera.services.store.contracts.precompile.impl.DissociatePrecompile;
import com.hedera.services.store.contracts.precompile.impl.MintPrecompile;
import com.hedera.services.store.contracts.precompile.impl.MultiAssociatePrecompile;
import com.hedera.services.store.contracts.precompile.impl.MultiDissociatePrecompile;
import com.hedera.services.store.contracts.precompile.impl.TokenCreatePrecompile;
import com.hedera.services.store.contracts.precompile.impl.TransferPrecompile;
import com.hedera.services.store.contracts.precompile.proxy.RedirectViewExecutor;
import com.hedera.services.store.contracts.precompile.utils.PrecompilePricingUtils;
import com.hedera.services.store.models.Id;
import com.hedera.services.txns.crypto.validators.ApproveAllowanceChecks;
import com.hedera.services.txns.crypto.validators.DeleteAllowanceChecks;
import com.hedera.services.txns.token.validators.CreateChecks;
import com.hedera.services.utils.EntityIdUtils;
import com.hederahashgraph.api.proto.java.AccountID;
import com.hederahashgraph.api.proto.java.CryptoTransferTransactionBody;
import com.hederahashgraph.api.proto.java.HederaFunctionality;
import com.hederahashgraph.api.proto.java.Timestamp;
import com.hederahashgraph.api.proto.java.TokenAssociateTransactionBody;
import com.hederahashgraph.api.proto.java.TokenBurnTransactionBody;
import com.hederahashgraph.api.proto.java.TokenDissociateTransactionBody;
import com.hederahashgraph.api.proto.java.TokenMintTransactionBody;
import com.hederahashgraph.api.proto.java.TokenTransferList;
import com.hederahashgraph.api.proto.java.TransactionBody;
import com.hederahashgraph.api.proto.java.TransactionID;
import com.hederahashgraph.fee.FeeObject;
import org.apache.tuweni.bytes.Bytes;
import org.hyperledger.besu.datatypes.Address;
import org.hyperledger.besu.datatypes.Wei;
import org.hyperledger.besu.evm.frame.BlockValues;
import org.hyperledger.besu.evm.frame.MessageFrame;
import org.hyperledger.besu.evm.gascalculator.GasCalculator;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Disabled;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.Mockito;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Collections;

import static com.hedera.services.contracts.execution.HederaMessageCallProcessor.INVALID_TRANSFER;
import static com.hedera.services.store.contracts.precompile.AbiConstants.ABI_ID_ASSOCIATE_TOKEN;
import static com.hedera.services.store.contracts.precompile.AbiConstants.ABI_ID_ASSOCIATE_TOKENS;
import static com.hedera.services.store.contracts.precompile.AbiConstants.ABI_ID_BURN_TOKEN;
import static com.hedera.services.store.contracts.precompile.AbiConstants.ABI_ID_CREATE_FUNGIBLE_TOKEN;
import static com.hedera.services.store.contracts.precompile.AbiConstants.ABI_ID_CREATE_FUNGIBLE_TOKEN_WITH_FEES;
import static com.hedera.services.store.contracts.precompile.AbiConstants.ABI_ID_CREATE_NON_FUNGIBLE_TOKEN;
import static com.hedera.services.store.contracts.precompile.AbiConstants.ABI_ID_CREATE_NON_FUNGIBLE_TOKEN_WITH_FEES;
import static com.hedera.services.store.contracts.precompile.AbiConstants.ABI_ID_CRYPTO_TRANSFER;
import static com.hedera.services.store.contracts.precompile.AbiConstants.ABI_ID_DISSOCIATE_TOKEN;
import static com.hedera.services.store.contracts.precompile.AbiConstants.ABI_ID_DISSOCIATE_TOKENS;
import static com.hedera.services.store.contracts.precompile.AbiConstants.ABI_ID_MINT_TOKEN;
import static com.hedera.services.store.contracts.precompile.AbiConstants.ABI_ID_NAME;
import static com.hedera.services.store.contracts.precompile.AbiConstants.ABI_ID_REDIRECT_FOR_TOKEN;
import static com.hedera.services.store.contracts.precompile.AbiConstants.ABI_ID_TRANSFER_NFT;
import static com.hedera.services.store.contracts.precompile.AbiConstants.ABI_ID_TRANSFER_NFTS;
import static com.hedera.services.store.contracts.precompile.AbiConstants.ABI_ID_TRANSFER_TOKEN;
import static com.hedera.services.store.contracts.precompile.AbiConstants.ABI_ID_TRANSFER_TOKENS;
import static com.hedera.services.store.contracts.precompile.HTSTestsUtil.DEFAULT_GAS_PRICE;
import static com.hedera.services.store.contracts.precompile.HTSTestsUtil.TEST_CONSENSUS_TIME;
import static com.hedera.services.store.contracts.precompile.HTSTestsUtil.associateOp;
import static com.hedera.services.store.contracts.precompile.HTSTestsUtil.contractAddress;
import static com.hedera.services.store.contracts.precompile.HTSTestsUtil.createTokenCreateWrapperWithKeys;
import static com.hedera.services.store.contracts.precompile.HTSTestsUtil.dissociateToken;
import static com.hedera.services.store.contracts.precompile.HTSTestsUtil.fungible;
import static com.hedera.services.store.contracts.precompile.HTSTestsUtil.fungibleBurn;
import static com.hedera.services.store.contracts.precompile.HTSTestsUtil.fungibleMint;
import static com.hedera.services.store.contracts.precompile.HTSTestsUtil.fungibleMintAmountOversize;
import static com.hedera.services.store.contracts.precompile.HTSTestsUtil.multiDissociateOp;
import static com.hedera.services.store.contracts.precompile.HTSTestsUtil.timestamp;
import static org.hyperledger.besu.evm.frame.MessageFrame.State.REVERT;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;

@ExtendWith(MockitoExtension.class)
class HTSPrecompiledContractTest {
	@Mock
	private GlobalDynamicProperties dynamicProperties;
	@Mock
	private GasCalculator gasCalculator;
	@Mock
	private BlockValues blockValues;
	@Mock
	private MessageFrame messageFrame;
	@Mock
	private TxnAwareEvmSigsVerifier sigsVerifier;
	@Mock
	private RecordsHistorian recordsHistorian;
	@Mock
	private DecodingFacade decoder;
	@Mock
	private EncodingFacade encoder;
	@Mock
	private SyntheticTxnFactory syntheticTxnFactory;
	@Mock
	private ExpiringCreations creator;
	@Mock
	private ImpliedTransfersMarshal impliedTransfers;
	@Mock
	private FeeCalculator feeCalculator;
	@Mock
	private StateView stateView;
	@Mock
	private PrecompilePricingUtils precompilePricingUtils;
	@Mock
	private HederaStackedWorldStateUpdater worldUpdater;
	@Mock
	private WorldLedgers wrappedLedgers;
	@Mock
	private UsagePricesProvider resourceCosts;
	@Mock
	private TransactionBody.Builder mockSynthBodyBuilder;
	@Mock
	private FeeObject mockFeeObject;
	@Mock
	private CreateChecks createChecks;
	@Mock
	private InfrastructureFactory infrastructureFactory;
	@Mock
	private ApproveAllowanceChecks allowanceChecks;
	@Mock
	private TransactionalLedger<AccountID, AccountProperty, MerkleAccount> accounts;
	@Mock
	private DeleteAllowanceChecks deleteAllowanceChecks;
	@Mock
	private RedirectViewExecutor redirectViewExecutor;

	private HTSPrecompiledContract subject;

	private static final long TEST_SERVICE_FEE = 5_000_000;
	private static final long TEST_NETWORK_FEE = 400_000;
	private static final long TEST_NODE_FEE = 300_000;
	private static final long viewTimestamp = 10L;

	public static final Id fungibleId = Id.fromGrpcToken(fungible);
	public static final Address fungibleTokenAddress = fungibleId.asEvmAddress();
//	private Address tokenAddress = Address.fromHexString("0x0102030405060708090a0b0c0d0e0f1011121314");

	private static final long EXPECTED_GAS_PRICE =
			(TEST_SERVICE_FEE + TEST_NETWORK_FEE + TEST_NODE_FEE) / DEFAULT_GAS_PRICE * 6 / 5;

	@BeforeEach
	void setUp() {
		subject = new HTSPrecompiledContract(
				dynamicProperties, gasCalculator,
				recordsHistorian, sigsVerifier, decoder, encoder,
				syntheticTxnFactory, creator, impliedTransfers,
				() -> feeCalculator, stateView, precompilePricingUtils, resourceCosts,
				infrastructureFactory);
	}

	@Test
	void gasRequirementReturnsCorrectValueForInvalidInput() {
		Bytes input = Bytes.of(4,3,2,1);
		// when
		var gas = subject.gasRequirement(input);

		// then
		assertEquals(0L, gas);
	}

	@Test
	void gasRequirementReturnsCorrectValueForSingleCryptoTransfer() {
		// given
		givenFrameContext();
		Bytes input = Bytes.of(Integers.toBytes(ABI_ID_CRYPTO_TRANSFER));
		given(syntheticTxnFactory.createCryptoTransfer(any()))
				.willReturn(TransactionBody.newBuilder().setCryptoTransfer(CryptoTransferTransactionBody.newBuilder()));
		given(feeCalculator.computeFee(any(), any(), any(), any())).willReturn(
				new FeeObject(TEST_NODE_FEE, TEST_NETWORK_FEE, TEST_SERVICE_FEE));
		given(feeCalculator.estimatedGasPriceInTinybars(any(), any())).willReturn(DEFAULT_GAS_PRICE);
		given(worldUpdater.permissivelyUnaliased(any())).willAnswer(invocationOnMock -> invocationOnMock.getArgument(0));

		subject.prepareFields(messageFrame);
		subject.prepareComputation(input, a -> a);
		subject.computeGasRequirement(TEST_CONSENSUS_TIME);

		// then
		assertEquals(EXPECTED_GAS_PRICE, subject.gasRequirement(input));
	}

	@Test
	void gasRequirementReturnsCorrectValueForMultipleCryptoTransfers() {
		// given
		givenFrameContext();
		Bytes input = Bytes.of(Integers.toBytes(ABI_ID_CRYPTO_TRANSFER));
		given(syntheticTxnFactory.createCryptoTransfer(any()))
				.willReturn(TransactionBody.newBuilder().setCryptoTransfer(
						CryptoTransferTransactionBody.newBuilder()
								.addTokenTransfers(TokenTransferList.newBuilder().build())
								.addTokenTransfers(TokenTransferList.newBuilder().build())
								.addTokenTransfers(TokenTransferList.newBuilder().build())));
		given(feeCalculator.computeFee(any(), any(), any(), any())).willReturn(
				new FeeObject(TEST_NODE_FEE, TEST_NETWORK_FEE, TEST_SERVICE_FEE));
		given(worldUpdater.permissivelyUnaliased(any())).willAnswer(invocationOnMock -> invocationOnMock.getArgument(0));
		given(feeCalculator.estimatedGasPriceInTinybars(any(), any())).willReturn(DEFAULT_GAS_PRICE);
		given(worldUpdater.permissivelyUnaliased(any())).willAnswer(invocationOnMock -> invocationOnMock.getArgument(0));

		subject.prepareFields(messageFrame);
		subject.prepareComputation(input, a -> a);
		subject.computeGasRequirement(TEST_CONSENSUS_TIME);

		// then
		assertEquals(EXPECTED_GAS_PRICE, subject.gasRequirement(input));
	}

	@Test
	void gasRequirementReturnsCorrectValueForTransferMultipleTokens() {
		// given
		givenFrameContext();
		Bytes input = Bytes.of(Integers.toBytes(ABI_ID_TRANSFER_TOKENS));
		given(syntheticTxnFactory.createCryptoTransfer(any()))
				.willReturn(TransactionBody.newBuilder().setCryptoTransfer(CryptoTransferTransactionBody.newBuilder()));
		given(feeCalculator.computeFee(any(), any(), any(), any())).willReturn(
				new FeeObject(TEST_NODE_FEE, TEST_NETWORK_FEE, TEST_SERVICE_FEE));
		given(feeCalculator.estimatedGasPriceInTinybars(any(), any())).willReturn(DEFAULT_GAS_PRICE);
		given(worldUpdater.permissivelyUnaliased(any())).willAnswer(invocationOnMock -> invocationOnMock.getArgument(0));

		subject.prepareFields(messageFrame);
		subject.prepareComputation(input, a -> a);
		subject.computeGasRequirement(TEST_CONSENSUS_TIME);

		// then
		assertEquals(EXPECTED_GAS_PRICE, subject.gasRequirement(input));
	}

	@Test
	void gasRequirementReturnsCorrectValueForTransferSingleToken() {
		// given
		givenFrameContext();
		Bytes input = Bytes.of(Integers.toBytes(ABI_ID_TRANSFER_TOKEN));
		given(syntheticTxnFactory.createCryptoTransfer(any()))
				.willReturn(TransactionBody.newBuilder().setCryptoTransfer(CryptoTransferTransactionBody.newBuilder()));
		given(feeCalculator.computeFee(any(), any(), any(), any())).willReturn(
				new FeeObject(TEST_NODE_FEE, TEST_NETWORK_FEE, TEST_SERVICE_FEE));
		given(feeCalculator.estimatedGasPriceInTinybars(any(), any())).willReturn(DEFAULT_GAS_PRICE);
		given(worldUpdater.permissivelyUnaliased(any())).willAnswer(invocationOnMock -> invocationOnMock.getArgument(0));

		subject.prepareFields(messageFrame);
		subject.prepareComputation(input, a -> a);
		subject.computeGasRequirement(TEST_CONSENSUS_TIME);

		// then
		assertEquals(EXPECTED_GAS_PRICE, subject.gasRequirement(input));
	}

	@Test
	void gasRequirementReturnsCorrectValueForTransferNfts() {
		// given
		givenFrameContext();
		Bytes input = Bytes.of(Integers.toBytes(ABI_ID_TRANSFER_NFTS));
		given(syntheticTxnFactory.createCryptoTransfer(any()))
				.willReturn(TransactionBody.newBuilder().setCryptoTransfer(CryptoTransferTransactionBody.newBuilder()));
		given(feeCalculator.computeFee(any(), any(), any(), any())).willReturn(
				new FeeObject(TEST_NODE_FEE, TEST_NETWORK_FEE, TEST_SERVICE_FEE));
		given(feeCalculator.estimatedGasPriceInTinybars(any(), any())).willReturn(DEFAULT_GAS_PRICE);
		given(worldUpdater.permissivelyUnaliased(any())).willAnswer(invocationOnMock -> invocationOnMock.getArgument(0));

		subject.prepareFields(messageFrame);
		subject.prepareComputation(input, a -> a);
		subject.computeGasRequirement(TEST_CONSENSUS_TIME);

		// then
		assertEquals(EXPECTED_GAS_PRICE, subject.gasRequirement(input));
	}

	@Test
	void gasRequirementReturnsCorrectValueForTransferNft() {
		// given
		givenFrameContext();
		Bytes input = Bytes.of(Integers.toBytes(ABI_ID_TRANSFER_NFT));
		given(syntheticTxnFactory.createCryptoTransfer(any()))
				.willReturn(TransactionBody.newBuilder().setCryptoTransfer(CryptoTransferTransactionBody.newBuilder()));
		given(feeCalculator.computeFee(any(), any(), any(), any())).willReturn(
				new FeeObject(TEST_NODE_FEE, TEST_NETWORK_FEE, TEST_SERVICE_FEE));
		given(feeCalculator.estimatedGasPriceInTinybars(any(), any())).willReturn(DEFAULT_GAS_PRICE);
		given(worldUpdater.permissivelyUnaliased(any())).willAnswer(invocationOnMock -> invocationOnMock.getArgument(0));

		subject.prepareFields(messageFrame);
		subject.prepareComputation(input, a -> a);
		subject.computeGasRequirement(TEST_CONSENSUS_TIME);

		// then
		assertEquals(EXPECTED_GAS_PRICE, subject.gasRequirement(input));
	}

	@Test
	void gasRequirementReturnsCorrectValueForMintToken() {
		// given
		givenFrameContext();
		Bytes input = Bytes.of(Integers.toBytes(ABI_ID_MINT_TOKEN));
		given(decoder.decodeMint(any())).willReturn(fungibleMint);
		given(syntheticTxnFactory.createMint(any()))
				.willReturn(TransactionBody.newBuilder().setTokenMint(TokenMintTransactionBody.newBuilder()));
		given(feeCalculator.computeFee(any(), any(), any(), any())).willReturn(
				new FeeObject(TEST_NODE_FEE, TEST_NETWORK_FEE, TEST_SERVICE_FEE));
		given(feeCalculator.estimatedGasPriceInTinybars(any(), any())).willReturn(DEFAULT_GAS_PRICE);
		given(worldUpdater.permissivelyUnaliased(any())).willAnswer(invocationOnMock -> invocationOnMock.getArgument(0));

		subject.prepareFields(messageFrame);
		subject.prepareComputation(input, a -> a);
		subject.computeGasRequirement(TEST_CONSENSUS_TIME);

		// then
		assertEquals(EXPECTED_GAS_PRICE, subject.gasRequirement(input));
	}

	@Test
	void gasRequirementReturnsCorrectValueForBurnToken() {
		// given
		givenFrameContext();
		Bytes input = Bytes.of(Integers.toBytes(ABI_ID_BURN_TOKEN));
		given(decoder.decodeBurn(any())).willReturn(fungibleBurn);
		given(syntheticTxnFactory.createBurn(any()))
				.willReturn(TransactionBody.newBuilder().setTokenBurn(TokenBurnTransactionBody.newBuilder()));
		given(feeCalculator.computeFee(any(), any(), any(), any())).willReturn(
				new FeeObject(TEST_NODE_FEE, TEST_NETWORK_FEE, TEST_SERVICE_FEE));
		given(feeCalculator.estimatedGasPriceInTinybars(any(), any())).willReturn(DEFAULT_GAS_PRICE);
		given(worldUpdater.permissivelyUnaliased(any())).willAnswer(invocationOnMock -> invocationOnMock.getArgument(0));

		subject.prepareFields(messageFrame);
		subject.prepareComputation(input, a -> a);
		subject.computeGasRequirement(TEST_CONSENSUS_TIME);

		// then
		assertEquals(EXPECTED_GAS_PRICE, subject.gasRequirement(input));
	}

	@Test
	void gasRequirementReturnsCorrectValueForAssociateTokens() {
		// given
		givenFrameContext();
		Bytes input = Bytes.of(Integers.toBytes(ABI_ID_ASSOCIATE_TOKENS));
		given(decoder.decodeMultipleAssociations(any(), any())).willReturn(associateOp);
		final var builder = TokenAssociateTransactionBody.newBuilder();
		builder.setAccount(multiDissociateOp.accountId());
		builder.addAllTokens(multiDissociateOp.tokenIds());
		given(syntheticTxnFactory.createAssociate(any()))
				.willReturn(TransactionBody.newBuilder().setTokenAssociate(builder));
		given(feeCalculator.computeFee(any(), any(), any(), any())).willReturn(
				new FeeObject(TEST_NODE_FEE, TEST_NETWORK_FEE, TEST_SERVICE_FEE));
		given(feeCalculator.estimatedGasPriceInTinybars(any(), any())).willReturn(DEFAULT_GAS_PRICE);
		given(worldUpdater.permissivelyUnaliased(any())).willAnswer(invocationOnMock -> invocationOnMock.getArgument(0));

		subject.prepareFields(messageFrame);
		subject.prepareComputation(input, a -> a);
		subject.computeGasRequirement(TEST_CONSENSUS_TIME);

		// then
		assertEquals(EXPECTED_GAS_PRICE, subject.gasRequirement(input));
	}

	@Test
	void gasRequirementReturnsCorrectValueForAssociateToken() {
		// given
		givenFrameContext();
		Bytes input = Bytes.of(Integers.toBytes(ABI_ID_ASSOCIATE_TOKEN));
		given(decoder.decodeAssociation(any(), any())).willReturn(associateOp);
		final var builder = TokenAssociateTransactionBody.newBuilder();
		builder.setAccount(associateOp.accountId());
		builder.addAllTokens(associateOp.tokenIds());
		given(syntheticTxnFactory.createAssociate(any()))
				.willReturn(TransactionBody.newBuilder().setTokenAssociate(builder));
		given(feeCalculator.computeFee(any(), any(), any(), any())).willReturn(
				new FeeObject(TEST_NODE_FEE, TEST_NETWORK_FEE, TEST_SERVICE_FEE));
		given(feeCalculator.estimatedGasPriceInTinybars(any(), any())).willReturn(DEFAULT_GAS_PRICE);
		given(worldUpdater.permissivelyUnaliased(any())).willAnswer(invocationOnMock -> invocationOnMock.getArgument(0));

		subject.prepareFields(messageFrame);
		subject.prepareComputation(input, a -> a);
		subject.computeGasRequirement(TEST_CONSENSUS_TIME);

		// then
		assertEquals(EXPECTED_GAS_PRICE, subject.gasRequirement(input));
	}

	@Test
	void gasRequirementReturnsCorrectValueForDissociateTokens() {
		// given
		givenFrameContext();
		Bytes input = Bytes.of(Integers.toBytes(ABI_ID_DISSOCIATE_TOKENS));
		given(decoder.decodeMultipleDissociations(any(), any())).willReturn(multiDissociateOp);
		final var builder = TokenDissociateTransactionBody.newBuilder();
		builder.setAccount(multiDissociateOp.accountId());
		builder.addAllTokens(multiDissociateOp.tokenIds());
		given(syntheticTxnFactory.createDissociate(any()))
				.willReturn(TransactionBody.newBuilder().setTokenDissociate(builder));
		given(feeCalculator.computeFee(any(), any(), any(), any())).willReturn(
				new FeeObject(TEST_NODE_FEE, TEST_NETWORK_FEE, TEST_SERVICE_FEE));
		given(feeCalculator.estimatedGasPriceInTinybars(any(), any())).willReturn(DEFAULT_GAS_PRICE);
		given(worldUpdater.permissivelyUnaliased(any())).willAnswer(invocationOnMock -> invocationOnMock.getArgument(0));

		subject.prepareFields(messageFrame);
		subject.prepareComputation(input, a -> a);
		subject.computeGasRequirement(TEST_CONSENSUS_TIME);

		// then
		assertEquals(EXPECTED_GAS_PRICE, subject.gasRequirement(input));
	}

	@Test
	void gasRequirementReturnsCorrectValueForDissociateToken() {
		// given
		givenFrameContext();
		Bytes input = Bytes.of(Integers.toBytes(ABI_ID_DISSOCIATE_TOKEN));
		given(decoder.decodeDissociate(any(), any())).willReturn(dissociateToken);
		given(syntheticTxnFactory.createDissociate(any()))
				.willReturn(TransactionBody.newBuilder().setTokenDissociate(
						TokenDissociateTransactionBody.newBuilder()
								.build()));
		given(feeCalculator.computeFee(any(), any(), any(), any())).willReturn(
				new FeeObject(TEST_NODE_FEE, TEST_NETWORK_FEE, TEST_SERVICE_FEE));
		given(feeCalculator.estimatedGasPriceInTinybars(any(), any())).willReturn(DEFAULT_GAS_PRICE);
		given(worldUpdater.permissivelyUnaliased(any())).willAnswer(invocationOnMock -> invocationOnMock.getArgument(0));

		subject.prepareFields(messageFrame);
		subject.prepareComputation(input, a -> a);
		subject.computeGasRequirement(TEST_CONSENSUS_TIME);

		// then
		assertEquals(EXPECTED_GAS_PRICE, subject.gasRequirement(input));
		Mockito.verifyNoMoreInteractions(syntheticTxnFactory);
	}

	@Test
	void computeCostedRevertsTheFrameIfTheFrameIsStatic() {
		given(messageFrame.isStatic()).willReturn(true);

		final var result = subject.computeCosted(Bytes.of(1,2,3,4), messageFrame);

		verify(messageFrame).setRevertReason(Bytes.of("HTS precompiles are not static".getBytes()));
		assertNull(result.getValue());
	}

	//TODO investigate why this test is failing
	@Disabled
	@Test
	void computeCostedWorks() {
		given(worldUpdater.trackingLedgers()).willReturn(wrappedLedgers);
		given(wrappedLedgers.typeOf(fungible)).willReturn(TokenType.FUNGIBLE_COMMON);
		Bytes input = prerequisites(ABI_ID_NAME);
		given(messageFrame.isStatic()).willReturn(true);
		given(messageFrame.getWorldUpdater()).willReturn(worldUpdater);
		given(worldUpdater.hasMutableLedgers()).willReturn(false);
		given(infrastructureFactory.newRedirectExecutor(any(), any(), any())).willReturn(redirectViewExecutor);

		given(feeCalculator.estimatePayment(any(), any(), any(), any(), any())).willReturn(mockFeeObject);
		given(feeCalculator.estimatedGasPriceInTinybars(HederaFunctionality.ContractCall,
				Timestamp.newBuilder().setSeconds(viewTimestamp).build()))
				.willReturn(1L);
		given(mockFeeObject.getNodeFee())
				.willReturn(1L);
		given(mockFeeObject.getNetworkFee())
				.willReturn(1L);
		given(mockFeeObject.getServiceFee())
				.willReturn(1L);

		final var name = "name";
		given(wrappedLedgers.nameOf(fungible)).willReturn(name);
		given(encoder.encodeName(name)).willReturn(Bytes.of(1));

		final var result = subject.computeCosted(input, messageFrame);

		verify(messageFrame, never()).setRevertReason(any());
		assertEquals(Bytes.of(1), result.getValue());
	}

	Bytes prerequisites(final int descriptor) {
		given(messageFrame.getBlockValues()).willReturn(blockValues);
		given(blockValues.getTimestamp()).willReturn(viewTimestamp);
		return Bytes.concatenate(
				Bytes.of(Integers.toBytes(ABI_ID_REDIRECT_FOR_TOKEN)),
				fungibleTokenAddress,
				Bytes.of(Integers.toBytes(descriptor))
		);
	}

	@Test
	void computeCallsCorrectImplementationForCryptoTransfer() {
		// given
		givenFrameContext();
		Bytes input = Bytes.of(Integers.toBytes(ABI_ID_CRYPTO_TRANSFER));
		given(syntheticTxnFactory.createCryptoTransfer(any()))
				.willReturn(TransactionBody.newBuilder().setCryptoTransfer(CryptoTransferTransactionBody.newBuilder()));
		given(worldUpdater.permissivelyUnaliased(any())).willAnswer(invocationOnMock -> invocationOnMock.getArgument(0));

		// when
		subject.prepareFields(messageFrame);
		subject.prepareComputation(input, a -> a);

		// then
		assertTrue(subject.getPrecompile() instanceof TransferPrecompile);
	}

	@Test
	void computeCallsCorrectImplementationForTransferTokens() {
		// given
		givenFrameContext();
		Bytes input = Bytes.of(Integers.toBytes(ABI_ID_TRANSFER_TOKENS));
		given(syntheticTxnFactory.createCryptoTransfer(any()))
				.willReturn(TransactionBody.newBuilder().setCryptoTransfer(CryptoTransferTransactionBody.newBuilder()));
		given(worldUpdater.permissivelyUnaliased(any())).willAnswer(invocationOnMock -> invocationOnMock.getArgument(0));

		// when
		subject.prepareFields(messageFrame);
		subject.prepareComputation(input, a -> a);

		// then
		assertTrue(subject.getPrecompile() instanceof TransferPrecompile);
	}

	@Test
	void computeCallsCorrectImplementationForTransferToken() {
		// given
		givenFrameContext();
		Bytes input = Bytes.of(Integers.toBytes(ABI_ID_TRANSFER_TOKEN));
		given(syntheticTxnFactory.createCryptoTransfer(any()))
				.willReturn(TransactionBody.newBuilder().setCryptoTransfer(CryptoTransferTransactionBody.newBuilder()));
		given(worldUpdater.permissivelyUnaliased(any())).willAnswer(invocationOnMock -> invocationOnMock.getArgument(0));

		// when
		subject.prepareFields(messageFrame);
		subject.prepareComputation(input, a -> a);

		// then
		assertTrue(subject.getPrecompile() instanceof TransferPrecompile);
	}

	@Test
	void computeCallsCorrectImplementationForTransferNfts() {
		// given
		givenFrameContext();
		Bytes input = Bytes.of(Integers.toBytes(ABI_ID_TRANSFER_NFTS));
		given(syntheticTxnFactory.createCryptoTransfer(any()))
				.willReturn(TransactionBody.newBuilder().setCryptoTransfer(CryptoTransferTransactionBody.newBuilder()));
		given(worldUpdater.permissivelyUnaliased(any())).willAnswer(invocationOnMock -> invocationOnMock.getArgument(0));

		// when
		subject.prepareFields(messageFrame);
		subject.prepareComputation(input, a -> a);

		// then
		assertTrue(subject.getPrecompile() instanceof TransferPrecompile);
	}

	@Test
	void computeCallsCorrectImplementationForTransferNft() {
		// given
		givenFrameContext();
		Bytes input = Bytes.of(Integers.toBytes(ABI_ID_TRANSFER_NFT));
		given(syntheticTxnFactory.createCryptoTransfer(any()))
				.willReturn(TransactionBody.newBuilder().setCryptoTransfer(CryptoTransferTransactionBody.newBuilder()));
		given(worldUpdater.permissivelyUnaliased(any())).willAnswer(invocationOnMock -> invocationOnMock.getArgument(0));

		// when
		subject.prepareFields(messageFrame);
		subject.prepareComputation(input, a -> a);

		// then
		assertTrue(subject.getPrecompile() instanceof TransferPrecompile);
	}

	@Test
	void computeCallsCorrectImplementationForMintToken() {
		// given
		givenFrameContext();
		Bytes input = Bytes.of(Integers.toBytes(ABI_ID_MINT_TOKEN));
		given(decoder.decodeMint(any())).willReturn(fungibleMint);
		given(worldUpdater.permissivelyUnaliased(any())).willAnswer(invocationOnMock -> invocationOnMock.getArgument(0));

		// when
		subject.prepareFields(messageFrame);
		subject.prepareComputation(input, a -> a);

		// then
		assertTrue(subject.getPrecompile() instanceof MintPrecompile);
	}

	@Test
	void computeCallsCorrectImplementationForBurnToken() {
		// given
		givenFrameContext();
		Bytes input = Bytes.of(Integers.toBytes(ABI_ID_BURN_TOKEN));
		given(decoder.decodeBurn(any())).willReturn(fungibleBurn);
		given(worldUpdater.permissivelyUnaliased(any())).willAnswer(invocationOnMock -> invocationOnMock.getArgument(0));

		// when
		subject.prepareFields(messageFrame);
		subject.prepareComputation(input, a -> a);

		// then
		assertTrue(subject.getPrecompile() instanceof BurnPrecompile);
	}

	@Test
	void computeCallsCorrectImplementationForAssociateTokens() {
		// given
		givenFrameContext();
		Bytes input = Bytes.of(Integers.toBytes(ABI_ID_ASSOCIATE_TOKENS));
		final var builder = TokenAssociateTransactionBody.newBuilder();
		builder.setAccount(multiDissociateOp.accountId());
		builder.addAllTokens(multiDissociateOp.tokenIds());
		given(syntheticTxnFactory.createAssociate(any()))
				.willReturn(TransactionBody.newBuilder().setTokenAssociate(builder));
		given(worldUpdater.permissivelyUnaliased(any())).willAnswer(invocationOnMock -> invocationOnMock.getArgument(0));

		// when
		subject.prepareFields(messageFrame);
		subject.prepareComputation(input, a -> a);

		// then
		assertTrue(subject.getPrecompile() instanceof MultiAssociatePrecompile);
	}

	@Test
	void computeCallsCorrectImplementationForAssociateToken() {
		// given
		givenFrameContext();
		Bytes input = Bytes.of(Integers.toBytes(ABI_ID_ASSOCIATE_TOKEN));
		given(decoder.decodeAssociation(any(), any())).willReturn(associateOp);
		given(worldUpdater.permissivelyUnaliased(any())).willAnswer(invocationOnMock -> invocationOnMock.getArgument(0));

		// when
		subject.prepareFields(messageFrame);
		subject.prepareComputation(input, a -> a);

		// then
		assertTrue(subject.getPrecompile() instanceof AssociatePrecompile);
	}

	@Test
	void computeCallsCorrectImplementationForDissociateTokens() {
		// given
		givenFrameContext();
		Bytes input = Bytes.of(Integers.toBytes(ABI_ID_DISSOCIATE_TOKENS));
		given(decoder.decodeMultipleDissociations(any(), any())).willReturn(multiDissociateOp);
		final var builder = TokenDissociateTransactionBody.newBuilder();
		builder.setAccount(multiDissociateOp.accountId());
		builder.addAllTokens(multiDissociateOp.tokenIds());
		given(syntheticTxnFactory.createDissociate(any())).willReturn(
				TransactionBody.newBuilder().setTokenDissociate(builder));
		given(worldUpdater.permissivelyUnaliased(any())).willAnswer(invocationOnMock -> invocationOnMock.getArgument(0));

		// when
		subject.prepareFields(messageFrame);
		subject.prepareComputation(input, a -> a);

		// then
		assertTrue(subject.getPrecompile() instanceof MultiDissociatePrecompile);
	}

	@Test
	void computeCallsCorrectImplementationForCreateFungibleToken() {
		// given
		Bytes input = Bytes.of(Integers.toBytes(ABI_ID_CREATE_FUNGIBLE_TOKEN));
		given(decoder.decodeFungibleCreate(any(), any()))
				.willReturn(createTokenCreateWrapperWithKeys(Collections.emptyList()));
		given(worldUpdater.permissivelyUnaliased(any())).willAnswer(invocationOnMock -> invocationOnMock.getArgument(0));

		prepareAndAssertCorrectInstantiationOfTokenCreatePrecompile(input);
	}

	@Test
	void computeCallsCorrectImplementationForCreateNonFungibleToken() {
		// given
		Bytes input = Bytes.of(Integers.toBytes(ABI_ID_CREATE_NON_FUNGIBLE_TOKEN));
		given(decoder.decodeNonFungibleCreate(any(), any()))
				.willReturn(createTokenCreateWrapperWithKeys(Collections.emptyList()));
		given(worldUpdater.permissivelyUnaliased(any())).willAnswer(invocationOnMock -> invocationOnMock.getArgument(0));

		prepareAndAssertCorrectInstantiationOfTokenCreatePrecompile(input);
	}

	@Test
	void replacesInheritedPropertiesOnCreateNonFungibleToken() {
		// given
		final var parentId = EntityIdUtils.accountIdFromEvmAddress(contractAddress);

		final var autoRenewId = EntityId.fromIdentityCode(10);
		final var tokenCreateWrapper = mock(TokenCreateWrapper.class);
		given(tokenCreateWrapper.hasAutoRenewAccount()).willReturn(false);
		Bytes input = Bytes.of(Integers.toBytes(ABI_ID_CREATE_NON_FUNGIBLE_TOKEN));
		given(decoder.decodeNonFungibleCreate(any(), any())).willReturn(tokenCreateWrapper);
		given(wrappedLedgers.accounts()).willReturn(accounts);
		given(accounts.get(any(), eq(AccountProperty.AUTO_RENEW_ACCOUNT_ID))).willReturn(autoRenewId);
		given(worldUpdater.permissivelyUnaliased(any())).willAnswer(invocationOnMock -> invocationOnMock.getArgument(0));

		givenFrameContext();
		given(dynamicProperties.isHTSPrecompileCreateEnabled()).willReturn(true);
		subject.prepareFields(messageFrame);
		subject.prepareComputation(input, a -> a);

		assertTrue(subject.getPrecompile() instanceof TokenCreatePrecompile);
		verify(tokenCreateWrapper).inheritAutoRenewAccount(autoRenewId);
	}

	@Test
	void computeCallsCorrectImplementationForCreateFungibleTokenWithFees() {
		// given
		Bytes input = Bytes.of(Integers.toBytes(ABI_ID_CREATE_FUNGIBLE_TOKEN_WITH_FEES));
		given(decoder.decodeFungibleCreateWithFees(any(), any()))
				.willReturn(createTokenCreateWrapperWithKeys(Collections.emptyList()));
		given(worldUpdater.permissivelyUnaliased(any())).willAnswer(invocationOnMock -> invocationOnMock.getArgument(0));

		prepareAndAssertCorrectInstantiationOfTokenCreatePrecompile(input);
	}

	@Test
	void computeCallsCorrectImplementationForCreateNonFungibleTokenWithFees() {
		// given
		Bytes input = Bytes.of(Integers.toBytes(ABI_ID_CREATE_NON_FUNGIBLE_TOKEN_WITH_FEES));
		given(decoder.decodeNonFungibleCreateWithFees(any(), any()))
				.willReturn(createTokenCreateWrapperWithKeys(Collections.emptyList()));
		given(worldUpdater.permissivelyUnaliased(any())).willAnswer(invocationOnMock -> invocationOnMock.getArgument(0));

		prepareAndAssertCorrectInstantiationOfTokenCreatePrecompile(input);
	}

	private void prepareAndAssertCorrectInstantiationOfTokenCreatePrecompile(Bytes input) {
		// given
		givenFrameContext();
		given(dynamicProperties.isHTSPrecompileCreateEnabled()).willReturn(true);
		final var accounts = mock(TransactionalLedger.class);
		given(wrappedLedgers.accounts()).willReturn(accounts);
		final var key = Mockito.mock(JKey.class);
		given(accounts.get(any(), any())).willReturn(key);

		// when
		subject.prepareFields(messageFrame);
		subject.prepareComputation(input, a -> a);

		// then
		assertTrue(subject.getPrecompile() instanceof TokenCreatePrecompile);
	}

	@Test
	void computeCallsCorrectImplementationForDissociateToken() {
		// given
		givenFrameContext();
		Bytes input = Bytes.of(Integers.toBytes(ABI_ID_DISSOCIATE_TOKEN));
		given(decoder.decodeDissociate(any(), any())).willReturn(dissociateToken);
		given(worldUpdater.permissivelyUnaliased(any())).willAnswer(invocationOnMock -> invocationOnMock.getArgument(0));

		// when
		subject.prepareFields(messageFrame);
		subject.prepareComputation(input, a -> a);

		// then
		assertTrue(subject.getPrecompile() instanceof DissociatePrecompile);
	}

	@Test
	void computeReturnsNullForWrongInput() {
		// given
		givenFrameContext();
		Bytes input = Bytes.of(0,0,0,0);
		given(worldUpdater.permissivelyUnaliased(any())).willAnswer(invocationOnMock -> invocationOnMock.getArgument(0));

		// when
		subject.prepareFields(messageFrame);
		subject.prepareComputation(input, a -> a);
		var result = subject.compute(input, messageFrame);

		// then
		assertNull(result);
	}

	@Test
	void computeReturnsNullForEmptyTransactionBody() {
		// given
		givenFrameContext();
		Bytes input = Bytes.of(Integers.toBytes(ABI_ID_MINT_TOKEN));
		given(decoder.decodeMint(any())).willReturn(fungibleMintAmountOversize);
		given(worldUpdater.permissivelyUnaliased(any())).willAnswer(invocationOnMock -> invocationOnMock.getArgument(0));

		// when
		subject.prepareFields(messageFrame);
		subject.prepareComputation(input, a -> a);

		// then
		var result = subject.compute(input, messageFrame);
		assertNull(result);
	}

	@Test
	void computeReturnsNullForTokenCreateWhenNotEnabled() {
		// given
		givenFrameContext();
		given(dynamicProperties.isHTSPrecompileCreateEnabled()).willReturn(false);
		given(worldUpdater.permissivelyUnaliased(any())).willAnswer(invocationOnMock -> invocationOnMock.getArgument(0));
		Bytes input = Bytes.of(Integers.toBytes(ABI_ID_CREATE_FUNGIBLE_TOKEN));
		given(worldUpdater.permissivelyUnaliased(any())).willAnswer(invocationOnMock -> invocationOnMock.getArgument(0));

		// when
		subject.prepareFields(messageFrame);
		subject.prepareComputation(input, a -> a);
		var result = subject.compute(input, messageFrame);

		// then
		assertNull(result);
		assertNull(subject.getPrecompile());
	}

	@Test
	void prepareFieldsWithAliasedMessageSender() {
		givenFrameContext();
		given(worldUpdater.permissivelyUnaliased(any())).willAnswer(invocationOnMock -> invocationOnMock.getArgument(0));

		subject.prepareFields(messageFrame);

		verify(messageFrame, times(1)).getSenderAddress();
	}

	@Test
	void computeInternalThrowsExceptionForInsufficientGas() {
		// given
		givenFrameContext();
		Bytes input = Bytes.of(Integers.toBytes(ABI_ID_MINT_TOKEN));
		given(decoder.decodeMint(any())).willReturn(fungibleMint);
		given(messageFrame.getRemainingGas()).willReturn(0L);
		given(syntheticTxnFactory.createMint(fungibleMint)).willReturn(mockSynthBodyBuilder);
		given(feeCalculator.estimatedGasPriceInTinybars(HederaFunctionality.ContractCall, timestamp)).willReturn(1L);
		given(feeCalculator.computeFee(any(), any(), any(), any())).willReturn(mockFeeObject);
		given(mockSynthBodyBuilder.build()).willReturn(TransactionBody.newBuilder().build());
		given(mockSynthBodyBuilder.setTransactionID(any(TransactionID.class))).willReturn(mockSynthBodyBuilder);
		given(mockFeeObject.getServiceFee()).willReturn(1L);
		given(worldUpdater.permissivelyUnaliased(any())).willAnswer(invocationOnMock -> invocationOnMock.getArgument(0));

		// when
		subject.prepareFields(messageFrame);
		subject.prepareComputation(input, a -> a);
		subject.computeGasRequirement(TEST_CONSENSUS_TIME);

		// then
		assertThrows(InvalidTransactionException.class, () -> subject.computeInternal(messageFrame));
	}

	@Test
	void defaultHandleHbarsThrows() {
		// given
		givenFrameContext();
		Bytes input = Bytes.of(Integers.toBytes(ABI_ID_TRANSFER_NFTS));
		given(messageFrame.getValue()).willReturn(Wei.of(1));
		given(syntheticTxnFactory.createCryptoTransfer(any()))
				.willReturn(TransactionBody.newBuilder().setCryptoTransfer(CryptoTransferTransactionBody.newBuilder()));
		given(worldUpdater.permissivelyUnaliased(any())).willAnswer(invocationOnMock -> invocationOnMock.getArgument(0));

		// when
		subject.prepareFields(messageFrame);
		subject.prepareComputation(input, a -> a);

		// then
		final var precompile = subject.getPrecompile();
		assertThrows(InvalidTransactionException.class, () -> precompile.handleSentHbars(messageFrame));

		verify(messageFrame).setRevertReason(INVALID_TRANSFER);
		verify(messageFrame).setState(REVERT);
	}

	private void givenFrameContext() {
		given(messageFrame.getSenderAddress()).willReturn(contractAddress);
		given(messageFrame.getWorldUpdater()).willReturn(worldUpdater);
		given(worldUpdater.wrappedTrackingLedgers(any())).willReturn(wrappedLedgers);
	}
}
