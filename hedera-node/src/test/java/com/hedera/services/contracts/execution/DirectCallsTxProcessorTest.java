package com.hedera.services.contracts.execution;

/*
 * -
 * ‌
 * Hedera Services Node
 * ​
 * Copyright (C) 2018 - 2022 Hedera Hashgraph, LLC
 * ​
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *       http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 * ‍
 *
 */

import com.esaulpaugh.headlong.util.Integers;
import com.hedera.services.context.SideEffectsTracker;
import com.hedera.services.context.primitives.StateView;
import com.hedera.services.context.properties.GlobalDynamicProperties;
import com.hedera.services.contracts.sources.TxnAwareEvmSigsVerifier;
import com.hedera.services.fees.FeeCalculator;
import com.hedera.services.fees.HbarCentExchange;
import com.hedera.services.fees.calculation.UsagePricesProvider;
import com.hedera.services.grpc.marshalling.ImpliedTransfersMarshal;
import com.hedera.services.pricing.AssetsLoader;
import com.hedera.services.records.RecordsHistorian;
import com.hedera.services.state.enums.TokenType;
import com.hedera.services.state.expiry.ExpiringCreations;
import com.hedera.services.store.contracts.HederaWorldState;
import com.hedera.services.store.contracts.WorldLedgers;
import com.hedera.services.store.contracts.precompile.HTSPrecompiledContract;
import com.hedera.services.store.contracts.precompile.InfrastructureFactory;
import com.hedera.services.store.contracts.precompile.SyntheticTxnFactory;
import com.hedera.services.store.contracts.precompile.codec.DecodingFacade;
import com.hedera.services.store.contracts.precompile.codec.EncodingFacade;
import com.hedera.services.store.contracts.precompile.utils.PrecompilePricingUtils;
import com.hedera.services.store.models.Account;
import com.hedera.services.store.models.Id;
import com.hederahashgraph.api.proto.java.HederaFunctionality;
import com.hederahashgraph.api.proto.java.ResponseCodeEnum;
import com.hederahashgraph.fee.FeeObject;
import org.apache.tuweni.bytes.Bytes;
import org.hyperledger.besu.datatypes.Address;
import org.hyperledger.besu.datatypes.Wei;
import org.hyperledger.besu.evm.account.EvmAccount;
import org.hyperledger.besu.evm.account.MutableAccount;
import org.hyperledger.besu.evm.gascalculator.GasCalculator;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigInteger;
import java.time.Instant;

import static com.hedera.services.ethereum.EthTxData.WEIBARS_TO_TINYBARS;
import static com.hedera.services.store.contracts.precompile.AbiConstants.ABI_ID_ERC_NAME;
import static com.hedera.services.store.contracts.precompile.HTSTestsUtil.TEST_CONSENSUS_TIME;
import static com.hedera.services.store.contracts.precompile.HTSTestsUtil.timestamp;
import static com.hedera.test.utils.TxnUtils.assertFailsWith;
import static com.hederahashgraph.api.proto.java.ResponseCodeEnum.INSUFFICIENT_GAS;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;

@ExtendWith(MockitoExtension.class)
class DirectCallsTxProcessorTest {
	public static final long ONE_HBAR = 100_000_000L;
	@Mock
	private LivePricesSource livePricesSource;
	@Mock
	private HederaWorldState worldState;
	@Mock
	private GasCalculator gasCalculator;
	@Mock
	private HederaWorldState.Updater updater;
	@Mock
	private GlobalDynamicProperties dynamicProperties;
	@Mock
	private WorldLedgers worldLedgers;
	@Mock
	private TxnAwareEvmSigsVerifier sigsVerifier;
	@Mock
	private RecordsHistorian recordsHistorian;
	@Mock
	private DecodingFacade decoder;
	@Mock
	private EncodingFacade encoder;
	@Mock
	private SideEffectsTracker sideEffects;
	@Mock
	private SyntheticTxnFactory syntheticTxnFactory;
	@Mock
	private ExpiringCreations creator;
	@Mock
	private ImpliedTransfersMarshal impliedTransfersMarshal;
	@Mock
	private FeeCalculator feeCalculator;
	@Mock
	private FeeObject mockFeeObject;
	@Mock
	private StateView stateView;
	@Mock
	private UsagePricesProvider resourceCosts;
	@Mock
	private InfrastructureFactory infrastructureFactory;
	@Mock
	private AssetsLoader assetLoader;
	@Mock
	private HbarCentExchange exchange;


	private final Account sender = new Account(new Id(0, 0, 1002));
	private final Account receiver = new Account(new Id(0, 0, 1006));
	private final Account relayer = new Account(new Id(0, 0, 1007));
	private final Bytes precompileCallData = Bytes.of(Integers.toBytes(ABI_ID_ERC_NAME));
	private final Address receiverAddress = receiver.getId().asEvmAddress();
	private final Instant consensusTime = Instant.ofEpochSecond(TEST_CONSENSUS_TIME);
	private final int MAX_GAS_LIMIT = 10_000_000;
	private final long GAS_LIMIT = 300_000L;

	private DirectCallsTxProcessor directCallsTxProcessor;

	@BeforeEach
	private void setup() {
		PrecompilePricingUtils precompilePricingUtils = new PrecompilePricingUtils(assetLoader, exchange, () -> feeCalculator, resourceCosts, stateView);
		HTSPrecompiledContract htsPrecompiledContract = new HTSPrecompiledContract(
				dynamicProperties, gasCalculator, recordsHistorian, sigsVerifier, decoder, encoder, syntheticTxnFactory,
				creator, impliedTransfersMarshal, () -> feeCalculator, stateView, precompilePricingUtils,
				infrastructureFactory);

		directCallsTxProcessor = new DirectCallsTxProcessor(
				gasCalculator, livePricesSource,
				dynamicProperties, htsPrecompiledContract, worldState);
	}

	@Test
	void assertSuccessExecution() {
		givenValidMock();
		given(dynamicProperties.fundingAccount()).willReturn(new Id(0, 0, 1010).asGrpcAccount());
		given(worldLedgers.wrapped(sideEffects)).willReturn(worldLedgers);
		given(worldLedgers.typeOf(any())).willReturn(TokenType.FUNGIBLE_COMMON);
		given(dynamicProperties.shouldEnableTraceability()).willReturn(true);
		given(feeCalculator.estimatePayment(any(), any(), any(), any(), any())).willReturn(mockFeeObject);
		given(feeCalculator.estimatedGasPriceInTinybars(HederaFunctionality.ContractCall, timestamp))
				.willReturn(1L);
		given(mockFeeObject.getNodeFee())
				.willReturn(1L);
		given(mockFeeObject.getNetworkFee())
				.willReturn(1L);
		given(mockFeeObject.getServiceFee())
				.willReturn(1L);
		givenSenderWithBalance(350_000L);
		//when
		var result = directCallsTxProcessor.execute(
				sender,
				GAS_LIMIT,
				1234L,
				precompileCallData,
				consensusTime,
				receiverAddress,
				null,
				0L,
				null,
				sender.getId(),
				worldLedgers);
		//then
		assertTrue(result.isSuccessful());
		assertEquals(receiver.getId().asGrpcContract(), result.toGrpc().getContractID());

	}

	@Test
	void assertSuccessExecutionEth() {
		givenValidMockEth();
		given(dynamicProperties.fundingAccount()).willReturn(new Id(0, 0, 1010).asGrpcAccount());
		given(dynamicProperties.shouldEnableTraceability()).willReturn(true);
		given(worldLedgers.wrapped(sideEffects)).willReturn(worldLedgers);
		given(worldLedgers.typeOf(any())).willReturn(TokenType.FUNGIBLE_COMMON);
		var evmAccount = mock(EvmAccount.class);
		given(updater.getOrCreateSenderAccount(any())).willReturn(evmAccount);
		var senderMutableAccount = mock(MutableAccount.class);
		given(evmAccount.getMutable()).willReturn(senderMutableAccount);
		givenSenderWithBalance(350_000L);
		//fees
		given(feeCalculator.estimatePayment(any(), any(), any(), any(), any())).willReturn(mockFeeObject);
		given(feeCalculator.estimatedGasPriceInTinybars(HederaFunctionality.ContractCall, timestamp))
				.willReturn(1L);
		given(mockFeeObject.getNodeFee())
				.willReturn(1L);
		given(mockFeeObject.getNetworkFee())
				.willReturn(1L);
		given(mockFeeObject.getServiceFee())
				.willReturn(1L);
		//when
		var result = directCallsTxProcessor.execute(
				sender,
				GAS_LIMIT,
				1234L,
				precompileCallData,
				consensusTime,
				receiverAddress,
				BigInteger.valueOf(10_000L),
				55_555L,
				relayer,
				sender.getId(),
				worldLedgers);
		//then
		assertTrue(result.isSuccessful());
		assertEquals(receiver.getId().asGrpcContract(), result.toGrpc().getContractID());
	}

	@Test
	void assertSuccessExecutionWithRefund() {
		final var gasUsedForERCNameFunction = 120;
		givenValidMock();
		given(dynamicProperties.maxGasRefundPercentage()).willReturn(100);
		given(dynamicProperties.fundingAccount()).willReturn(new Id(0, 0, 1010).asGrpcAccount());
		given(worldLedgers.wrapped(sideEffects)).willReturn(worldLedgers);
		given(worldLedgers.typeOf(any())).willReturn(TokenType.FUNGIBLE_COMMON);
		//fees
		given(feeCalculator.estimatePayment(any(), any(), any(), any(), any())).willReturn(mockFeeObject);
		given(feeCalculator.estimatedGasPriceInTinybars(HederaFunctionality.ContractCall, timestamp))
				.willReturn(1L);
		given(mockFeeObject.getNodeFee())
				.willReturn(1L);
		given(mockFeeObject.getNetworkFee())
				.willReturn(1L);
		given(mockFeeObject.getServiceFee())
				.willReturn(1L);
		givenSenderWithBalance(ONE_HBAR * 10);
		final long gasPrice = 10L;
		given(livePricesSource.currentGasPrice(consensusTime, HederaFunctionality.ContractCall))
				.willReturn(gasPrice);

		var result = directCallsTxProcessor.execute(
				sender,
				GAS_LIMIT,
				1234L,
				precompileCallData,
				consensusTime,
				receiverAddress,
				null,
				0L,
				null,
				sender.getId(),
				worldLedgers);

		assertTrue(result.isSuccessful());
		assertEquals(gasUsedForERCNameFunction, result.getGasUsed());
		assertEquals(receiver.getId().asGrpcContract(), result.toGrpc().getContractID());
	}

	@Test
	void assertSuccessExecutionWithRefundForEthCalls() {
		final var gasUsedForERCNameFunction = 120;
		givenValidMockEth();
		given(dynamicProperties.maxGasRefundPercentage()).willReturn(100);
		given(dynamicProperties.fundingAccount()).willReturn(new Id(0, 0, 1010).asGrpcAccount());
		given(worldLedgers.wrapped(sideEffects)).willReturn(worldLedgers);
		given(worldLedgers.typeOf(any())).willReturn(TokenType.FUNGIBLE_COMMON);
		//fees
		given(feeCalculator.estimatePayment(any(), any(), any(), any(), any())).willReturn(mockFeeObject);
		given(feeCalculator.estimatedGasPriceInTinybars(HederaFunctionality.ContractCall, timestamp))
				.willReturn(1L);
		given(mockFeeObject.getNodeFee())
				.willReturn(1L);
		given(mockFeeObject.getNetworkFee())
				.willReturn(1L);
		given(mockFeeObject.getServiceFee())
				.willReturn(1L);
		var evmAccount = mock(EvmAccount.class);
		given(updater.getOrCreateSenderAccount(any())).willReturn(evmAccount);
		var senderMutableAccount = mock(MutableAccount.class);
		given(evmAccount.getMutable()).willReturn(senderMutableAccount);
		given(senderMutableAccount.getBalance()).willReturn(Wei.of(GAS_LIMIT + 1));
		givenSenderWithBalance(ONE_HBAR * 10);
		final long gasPrice = 11L;
		given(livePricesSource.currentGasPrice(consensusTime, HederaFunctionality.EthereumTransaction))
				.willReturn(gasPrice);

		var result = directCallsTxProcessor.execute(
				sender,
				GAS_LIMIT,
				1234L,
				precompileCallData,
				consensusTime,
				receiverAddress,
				BigInteger.valueOf(10L).multiply(WEIBARS_TO_TINYBARS),
				ONE_HBAR * 10,
				relayer,
				sender.getId(),
				worldLedgers);

		assertTrue(result.isSuccessful());
		assertEquals(gasUsedForERCNameFunction, result.getGasUsed());
		assertEquals(receiver.getId().asGrpcContract(), result.toGrpc().getContractID());
	}

	@Test
	void assertFailedExecutionWhenRemainingGasIsLesserTHanRequired() {
		givenValidMock();
		given(dynamicProperties.fundingAccount()).willReturn(new Id(0, 0, 1010).asGrpcAccount());
		given(worldLedgers.wrapped(sideEffects)).willReturn(worldLedgers);
		given(worldLedgers.typeOf(any())).willReturn(TokenType.FUNGIBLE_COMMON);
		//fees
		given(feeCalculator.estimatePayment(any(), any(), any(), any(), any())).willReturn(mockFeeObject);
		given(feeCalculator.estimatedGasPriceInTinybars(HederaFunctionality.ContractCall, timestamp))
				.willReturn(1L);
		given(mockFeeObject.getNodeFee())
				.willReturn(1111111L);
		given(mockFeeObject.getNetworkFee())
				.willReturn(1111111L);
		given(mockFeeObject.getServiceFee())
				.willReturn(1111111L);
		givenSenderWithBalance(350_000L);
		//when
		var result = directCallsTxProcessor.execute(
				sender,
				GAS_LIMIT,
				1234L,
				precompileCallData,
				consensusTime,
				receiverAddress,
				null,
				0L,
				null,
				sender.getId(),
				worldLedgers);
		//then
		assertFalse(result.isSuccessful());
		assertEquals(org.hyperledger.besu.evm.frame.ExceptionalHaltReason.INSUFFICIENT_GAS, result.getHaltReason().get());
	}

	@Test
	void assertFailedETHExecutionWhenRemainingGasIsLesserTHanRequired() {
		givenValidMockEth();
		given(dynamicProperties.fundingAccount()).willReturn(new Id(0, 0, 1010).asGrpcAccount());
		given(worldLedgers.wrapped(sideEffects)).willReturn(worldLedgers);
		given(worldLedgers.typeOf(any())).willReturn(TokenType.FUNGIBLE_COMMON);
		var evmAccount = mock(EvmAccount.class);
		given(updater.getOrCreateSenderAccount(any())).willReturn(evmAccount);
		var senderMutableAccount = mock(MutableAccount.class);
		given(evmAccount.getMutable()).willReturn(senderMutableAccount);
		givenSenderWithBalance(350_000L);
		//fees
		given(feeCalculator.estimatePayment(any(), any(), any(), any(), any())).willReturn(mockFeeObject);
		given(feeCalculator.estimatedGasPriceInTinybars(HederaFunctionality.ContractCall, timestamp))
				.willReturn(1L);
		given(mockFeeObject.getNodeFee())
				.willReturn(1111111L);
		given(mockFeeObject.getNetworkFee())
				.willReturn(1111111L);
		given(mockFeeObject.getServiceFee())
				.willReturn(1111111L);
		//when
		var result = directCallsTxProcessor.execute(
				sender,
				GAS_LIMIT,
				1234L,
				precompileCallData,
				consensusTime,
				receiverAddress,
				BigInteger.valueOf(10_000L),
				55_555L,
				relayer,
				sender.getId(),
				worldLedgers);
		//then
		assertFalse(result.isSuccessful());
		assertEquals(org.hyperledger.besu.evm.frame.ExceptionalHaltReason.INSUFFICIENT_GAS, result.getHaltReason().get());
	}

	@Test
	void throwsWhenSenderCannotCoverUpfrontCost() {
		givenInvalidMock();
		givenSenderWithBalance(123);

		assertFailsWith(
				() -> directCallsTxProcessor.execute(
						sender,
						333_333L,
						1234L,
						precompileCallData,
						consensusTime,
						receiverAddress,
						null,
						0L,
						null,
						sender.getId(),
						worldLedgers),
				ResponseCodeEnum.INSUFFICIENT_PAYER_BALANCE);
	}


	@Test
	void throwsWhenIntrinsicGasCostExceedsGasLimit() {
		givenInvalidMock();
		var evmAccount = mock(EvmAccount.class);
		given(updater.getOrCreateSenderAccount(any())).willReturn(evmAccount);
		var senderMutableAccount = mock(MutableAccount.class);
		given(evmAccount.getMutable()).willReturn(senderMutableAccount);
		given(gasCalculator.transactionIntrinsicGasCost(Bytes.EMPTY, false)).willReturn(MAX_GAS_LIMIT + 1L);
		assertFailsWith(() ->
						directCallsTxProcessor.execute(
								sender,
								GAS_LIMIT,
								1234L,
								precompileCallData,
								consensusTime,
								receiverAddress,
								BigInteger.valueOf(10_000L),
								55_555L,
								relayer,
								sender.getId(),
								worldLedgers),
				INSUFFICIENT_GAS);
	}

	@Test
	void throwsWhenIntrinsicGasCostExceedsGasLimitAndGasLimitIsEqualToMaxGasLimit() {
		givenInvalidMock();
		final var wrappedSenderAccount = mock(EvmAccount.class);
		final var mutableSenderAccount = mock(MutableAccount.class);
		given(wrappedSenderAccount.getMutable()).willReturn(mutableSenderAccount);
		given(updater.getOrCreateSenderAccount(sender.getId().asEvmAddress())).willReturn(wrappedSenderAccount);
		given(gasCalculator.transactionIntrinsicGasCost(Bytes.EMPTY, false)).willReturn(MAX_GAS_LIMIT + 1L);

		assertFailsWith(
				() -> directCallsTxProcessor.execute(
						sender,
						33_333L,
						1234L,
						precompileCallData,
						consensusTime,
						receiverAddress,
						null,
						0L,
						null,
						sender.getId(),
						worldLedgers),
				INSUFFICIENT_GAS);
	}

	@Test
	void throwsWhenIntrinsicGasCostExceedsGasLimitForEthTransactions() {
		givenInvalidMock();
		final var wrappedSenderAccount = mock(EvmAccount.class);
		final var mutableSenderAccount = mock(MutableAccount.class);
		given(wrappedSenderAccount.getMutable()).willReturn(mutableSenderAccount);
		given(updater.getOrCreateSenderAccount(sender.getId().asEvmAddress())).willReturn(wrappedSenderAccount);
		given(gasCalculator.transactionIntrinsicGasCost(Bytes.EMPTY, false)).willReturn(MAX_GAS_LIMIT + 1L);

		assertFailsWith(
				() -> directCallsTxProcessor.execute(
						sender,
						33_333L,
						1234L,
						precompileCallData,
						consensusTime,
						receiverAddress,
						null,
						0L,
						null,
						sender.getId(),
						worldLedgers),
				INSUFFICIENT_GAS);
	}

	@Test
	void assertSuccessEthereumTransactionExecutionChargesRelayerWhenSenderGasPriceIs120() {
		givenValidMockEth();
		final var MAX_REFUND_PERCENTAGE = 100;
		given(worldLedgers.wrapped(sideEffects)).willReturn(worldLedgers);
		given(worldLedgers.typeOf(any())).willReturn(TokenType.FUNGIBLE_COMMON);
		given(dynamicProperties.maxGasRefundPercentage()).willReturn(MAX_REFUND_PERCENTAGE);
		given(dynamicProperties.fundingAccount()).willReturn(new Id(0, 0, 1010).asGrpcAccount());
		final var wrappedSenderAccount = mock(EvmAccount.class);
		final var mutableSenderAccount = mock(MutableAccount.class);
		given(wrappedSenderAccount.getMutable()).willReturn(mutableSenderAccount);
		given(updater.getOrCreateSenderAccount(sender.getId().asEvmAddress())).willReturn(wrappedSenderAccount);
		given(mutableSenderAccount.getBalance()).willReturn(Wei.of(100 * ONE_HBAR));
		final var wrappedRelayerAccount = mock(EvmAccount.class);
		final var mutableRelayerAccount = mock(MutableAccount.class);
		given(wrappedRelayerAccount.getMutable()).willReturn(mutableRelayerAccount);
		given(updater.getOrCreateSenderAccount(relayer.getId().asEvmAddress())).willReturn(wrappedRelayerAccount);
		given(mutableRelayerAccount.getBalance()).willReturn(Wei.of(100 * ONE_HBAR));
		final long gasPrice = 40L;
		given(livePricesSource.currentGasPrice(consensusTime, HederaFunctionality.EthereumTransaction))
				.willReturn(gasPrice);

		final long offeredGasPrice = 0L;
		final long gasLimit = 1120;
		given(gasCalculator.transactionIntrinsicGasCost(Bytes.EMPTY, false)).willReturn(1000L);
		//
		given(feeCalculator.estimatePayment(any(), any(), any(), any(), any())).willReturn(mockFeeObject);
		given(feeCalculator.estimatedGasPriceInTinybars(HederaFunctionality.ContractCall, timestamp))
				.willReturn(1L);
		given(mockFeeObject.getNodeFee())
				.willReturn(0L);
		given(mockFeeObject.getNetworkFee())
				.willReturn(0L);
		given(mockFeeObject.getServiceFee())
				.willReturn(0L);
		//when
		var result = directCallsTxProcessor.execute(
				sender,
				gasLimit,
				1234L,
				precompileCallData,
				consensusTime,
				receiverAddress,
				BigInteger.valueOf(offeredGasPrice).multiply(WEIBARS_TO_TINYBARS),
				10 * ONE_HBAR,
				relayer,
				sender.getId(),
				worldLedgers);

		assertTrue(result.isSuccessful());
		assertEquals(result.getGasUsed(), gasLimit);
		assertEquals(receiver.getId().asGrpcContract(), result.toGrpc().getContractID());
		verify(mutableRelayerAccount).decrementBalance(Wei.of(gasPrice * gasLimit));
	}

	//Helpers
	private void givenValidMock() {
		given(infrastructureFactory.newSideEffects()).willReturn(sideEffects);
		given(worldState.updater()).willReturn(updater);
		given(dynamicProperties.fundingAccount()).willReturn(new Id(0, 0, 1010).asGrpcAccount());
		var evmAccount = mock(EvmAccount.class);
		given(gasCalculator.transactionIntrinsicGasCost(Bytes.EMPTY, false)).willReturn(0L);
		given(updater.getOrCreateSenderAccount(sender.getId().asEvmAddress())).willReturn(evmAccount);
		given(worldState.updater()).willReturn(updater);
		var senderMutableAccount = mock(MutableAccount.class);
		given(senderMutableAccount.incrementBalance(any())).willReturn(Wei.of(1500L));
		given(evmAccount.getMutable()).willReturn(senderMutableAccount);
		given(updater.getSenderAccount(any())).willReturn(evmAccount);
		given(updater.getSenderAccount(any()).getMutable()).willReturn(senderMutableAccount);
		given(updater.getOrCreate(any())).willReturn(evmAccount);
		given(updater.getOrCreate(any()).getMutable()).willReturn(senderMutableAccount);
		given(updater.getSbhRefund()).willReturn(0L);
	}

	private void givenInvalidMock() {
		given(worldState.updater()).willReturn(updater);
		given(gasCalculator.transactionIntrinsicGasCost(Bytes.EMPTY, false)).willReturn(100_000L);
	}

	private void givenValidMockEth() {
		given(infrastructureFactory.newSideEffects()).willReturn(sideEffects);
		given(worldState.updater()).willReturn(updater);
		given(dynamicProperties.fundingAccount()).willReturn(new Id(0, 0, 1010).asGrpcAccount());
		var evmAccount = mock(EvmAccount.class);
		given(gasCalculator.transactionIntrinsicGasCost(Bytes.EMPTY, false)).willReturn(0L);
		given(worldState.updater()).willReturn(updater);
		var senderMutableAccount = mock(MutableAccount.class);
		given(senderMutableAccount.incrementBalance(any())).willReturn(Wei.of(1500L));
		given(evmAccount.getMutable()).willReturn(senderMutableAccount);
		given(updater.getSenderAccount(any())).willReturn(evmAccount);
		given(updater.getSenderAccount(any()).getMutable()).willReturn(senderMutableAccount);
		given(updater.getOrCreate(any())).willReturn(evmAccount);
		given(updater.getOrCreate(any()).getMutable()).willReturn(senderMutableAccount);
		given(updater.getSbhRefund()).willReturn(0L);
	}

	private void givenSenderWithBalance(final long amount) {
		final var wrappedSenderAccount = mock(EvmAccount.class);
		final var mutableSenderAccount = mock(MutableAccount.class);
		given(wrappedSenderAccount.getMutable()).willReturn(mutableSenderAccount);
		given(updater.getOrCreateSenderAccount(sender.getId().asEvmAddress())).willReturn(wrappedSenderAccount);
		given(mutableSenderAccount.getBalance()).willReturn(Wei.of(amount));
	}

}