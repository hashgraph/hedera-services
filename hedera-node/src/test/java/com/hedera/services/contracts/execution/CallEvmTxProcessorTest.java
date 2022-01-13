package com.hedera.services.contracts.execution;

/*
 * -
 * ‌
 * Hedera Services Node
 * ​
 * Copyright (C) 2018 - 2021 Hedera Hashgraph, LLC
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

import com.hedera.services.context.properties.GlobalDynamicProperties;
import com.hedera.services.store.contracts.CodeCache;
import com.hedera.services.store.contracts.HederaWorldState;
import com.hedera.services.store.models.Account;
import com.hedera.services.store.models.Id;
import com.hederahashgraph.api.proto.java.HederaFunctionality;
import com.hederahashgraph.api.proto.java.ResponseCodeEnum;
import org.apache.tuweni.bytes.Bytes;
import org.hyperledger.besu.datatypes.Address;
import org.hyperledger.besu.datatypes.Wei;
import org.hyperledger.besu.evm.Code;
import org.hyperledger.besu.evm.Gas;
import org.hyperledger.besu.evm.account.EvmAccount;
import org.hyperledger.besu.evm.account.MutableAccount;
import org.hyperledger.besu.evm.frame.BlockValues;
import org.hyperledger.besu.evm.frame.MessageFrame;
import org.hyperledger.besu.evm.gascalculator.GasCalculator;
import org.hyperledger.besu.evm.operation.Operation;
import org.hyperledger.besu.evm.precompile.PrecompiledContract;
import org.hyperledger.besu.evm.worldstate.WorldUpdater;
import org.hyperledger.besu.plugin.data.Transaction;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Instant;
import java.util.Deque;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

import static com.hedera.test.utils.TxnUtils.assertFailsWith;
import static com.hederahashgraph.api.proto.java.ResponseCodeEnum.FAIL_INVALID;
import static com.hederahashgraph.api.proto.java.ResponseCodeEnum.INSUFFICIENT_GAS;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.mock;

@ExtendWith(MockitoExtension.class)
class CallEvmTxProcessorTest {
	private static final int MAX_STACK_SIZE = 1024;

	@Mock
	private LivePricesSource livePricesSource;
	@Mock
	private HederaWorldState worldState;
	@Mock
	private CodeCache codeCache;
	@Mock
	private GlobalDynamicProperties globalDynamicProperties;
	@Mock
	private GasCalculator gasCalculator;
	@Mock
	private Set<Operation> operations;
	@Mock
	private Transaction transaction;
	@Mock
	private HederaWorldState.Updater updater;
	@Mock
	Map<String, PrecompiledContract> precompiledContractMap;

	private final Account sender = new Account(new Id(0, 0, 1002));
	private final Account receiver = new Account(new Id(0, 0, 1006));
	private final Address receiverAddress = receiver.getId().asEvmAddress();
	private final Instant consensusTime = Instant.now();
	private final int MAX_GAS_LIMIT = 10_000_000;
	private final int MAX_REFUND_PERCENT = 20;
	private final long INTRINSIC_GAS_COST = 290_000L;
	private final long GAS_LIMIT = 300_000L;

	private CallEvmTxProcessor callEvmTxProcessor;

	@BeforeEach
	private void setup() {
		CommonProcessorSetup.setup(gasCalculator);

		callEvmTxProcessor = new CallEvmTxProcessor(
				worldState, livePricesSource, codeCache, globalDynamicProperties, gasCalculator, 
                                operations, precompiledContractMap);
	}

	@Test
	void assertSuccessExecution() {
		givenValidMock();
		given(globalDynamicProperties.fundingAccount()).willReturn(new Id(0, 0, 1010).asGrpcAccount());

		givenSenderWithBalance(350_000L);
		var result = callEvmTxProcessor.execute(
				sender, receiverAddress, 33_333L, 1234L, Bytes.EMPTY, consensusTime);
		assertTrue(result.isSuccessful());
		assertEquals(receiver.getId().asGrpcContract(), result.toGrpc().getContractID());
	}

	@Test
	void throwsWhenCodeCacheFailsLoading() {
		given(worldState.updater()).willReturn(updater);
		given(worldState.updater().updater()).willReturn(updater);
		given(globalDynamicProperties.fundingAccount()).willReturn(new Id(0, 0, 1010).asGrpcAccount());

		var evmAccount = mock(EvmAccount.class);

		given(gasCalculator.transactionIntrinsicGasCost(Bytes.EMPTY, false)).willReturn(Gas.ZERO);

		given(updater.getOrCreateSenderAccount(sender.getId().asEvmAddress())).willReturn(evmAccount);
		given(worldState.updater()).willReturn(updater);

		givenSenderWithBalance(350_000L);
		assertFailsWith(() ->
						callEvmTxProcessor.execute(
								sender, receiver.getId().asEvmAddress(),
								33_333L, 1234L, Bytes.EMPTY, consensusTime),
				FAIL_INVALID);
	}

	@Test
	void assertSuccessExecutionChargesCorrectMinimumGas() {
		givenValidMock();
		given(globalDynamicProperties.maxGasRefundPercentage()).willReturn(MAX_REFUND_PERCENT);
		given(globalDynamicProperties.fundingAccount()).willReturn(new Id(0, 0, 1010).asGrpcAccount());
		givenSenderWithBalance(350_000L);
		var result = callEvmTxProcessor.execute(sender, receiver.getId().asEvmAddress(), GAS_LIMIT, 1234L, Bytes.EMPTY,
				consensusTime);
		assertTrue(result.isSuccessful());
		assertEquals(result.getGasUsed(), GAS_LIMIT - GAS_LIMIT * MAX_REFUND_PERCENT / 100);
		assertEquals(receiver.getId().asGrpcContract(), result.toGrpc().getContractID());
	}

	@Test
	void assertSuccessExecutionChargesCorrectGasWhenGasUsedIsLargerThanMinimum() {
		givenValidMock();
		given(globalDynamicProperties.maxGasRefundPercentage()).willReturn(MAX_REFUND_PERCENT);
		given(gasCalculator.transactionIntrinsicGasCost(Bytes.EMPTY, false)).willReturn(Gas.of(INTRINSIC_GAS_COST));
		given(globalDynamicProperties.fundingAccount()).willReturn(new Id(0, 0, 1010).asGrpcAccount());

		givenSenderWithBalance(350_000L);
		var result = callEvmTxProcessor.execute(sender, receiver.getId().asEvmAddress(), GAS_LIMIT, 1234L, Bytes.EMPTY,
				consensusTime);
		assertTrue(result.isSuccessful());
		assertEquals(INTRINSIC_GAS_COST, result.getGasUsed());
		assertEquals(receiver.getId().asGrpcContract(), result.toGrpc().getContractID());
	}

	@Test
	void throwsWhenSenderCannotCoverUpfrontCost() {
		givenInvalidMock();
		givenSenderWithBalance(123);

		assertFailsWith(
				() -> callEvmTxProcessor.execute(
						sender, receiverAddress, 333_333L, 1234L, Bytes.EMPTY, consensusTime),
				ResponseCodeEnum.INSUFFICIENT_PAYER_BALANCE);
	}

	@Test
	void throwsWhenIntrinsicGasCostExceedsGasLimit() {
		givenInvalidMock();
		final var wrappedSenderAccount = mock(EvmAccount.class);
		final var mutableSenderAccount = mock(MutableAccount.class);
		given(wrappedSenderAccount.getMutable()).willReturn(mutableSenderAccount);
		given(updater.getOrCreateSenderAccount(sender.getId().asEvmAddress())).willReturn(wrappedSenderAccount);

		assertFailsWith(() ->
						callEvmTxProcessor.execute(
								sender, receiverAddress, 33_333L, 1234L, Bytes.EMPTY, consensusTime),
				INSUFFICIENT_GAS);
	}

	@Test
	void throwsWhenIntrinsicGasCostExceedsGasLimitAndGasLimitIsEqualToMaxGasLimit() {
		givenInvalidMock();
		final var wrappedSenderAccount = mock(EvmAccount.class);
		final var mutableSenderAccount = mock(MutableAccount.class);
		given(wrappedSenderAccount.getMutable()).willReturn(mutableSenderAccount);
		given(updater.getOrCreateSenderAccount(sender.getId().asEvmAddress())).willReturn(wrappedSenderAccount);
		given(gasCalculator.transactionIntrinsicGasCost(Bytes.EMPTY, false)).willReturn(Gas.of(MAX_GAS_LIMIT + 1));

		assertFailsWith(
				() -> callEvmTxProcessor
						.execute(sender, receiverAddress, MAX_GAS_LIMIT, 1234L, Bytes.EMPTY, consensusTime),
				INSUFFICIENT_GAS);
	}

	@Test
	void assertIsContractCallFunctionality() {
		assertEquals(HederaFunctionality.ContractCall, callEvmTxProcessor.getFunctionType());
	}


	@Test
	void assertTransactionSenderAndValue() {
		// setup:
		doReturn(Optional.of(receiver.getId().asEvmAddress())).when(transaction).getTo();
		given(worldState.updater()).willReturn(mock(HederaWorldState.Updater.class));
		given(codeCache.getIfPresent(any())).willReturn(new Code());
		given(transaction.getSender()).willReturn(sender.getId().asEvmAddress());
		given(transaction.getValue()).willReturn(Wei.of(1L));
		final MessageFrame.Builder commonInitialFrame =
				MessageFrame.builder()
						.messageFrameStack(mock(Deque.class))
						.maxStackSize(MAX_STACK_SIZE)
						.worldUpdater(mock(WorldUpdater.class))
						.initialGas(mock(Gas.class))
						.originator(sender.getId().asEvmAddress())
						.gasPrice(mock(Wei.class))
						.sender(sender.getId().asEvmAddress())
						.value(Wei.of(transaction.getValue().getAsBigInteger()))
						.apparentValue(Wei.of(transaction.getValue().getAsBigInteger()))
						.blockValues(mock(BlockValues.class))
						.depth(0)
						.completer(__ -> {
						})
						.miningBeneficiary(mock(Address.class))
						.blockHashLookup(h -> null);
		//when:
		MessageFrame buildMessageFrame = callEvmTxProcessor.buildInitialFrame(commonInitialFrame, worldState.updater(),
				(Address) transaction.getTo().get(), Bytes.EMPTY);

		//expect:
		assertEquals(transaction.getSender(), buildMessageFrame.getSenderAddress());
		assertEquals(transaction.getValue(), buildMessageFrame.getApparentValue());
	}

	private void givenInvalidMock() {
		given(worldState.updater()).willReturn(updater);
		given(gasCalculator.transactionIntrinsicGasCost(Bytes.EMPTY, false)).willReturn(Gas.of(100_000L));
	}

	private void givenValidMock() {
		given(worldState.updater()).willReturn(updater);
		given(worldState.updater().updater()).willReturn(updater);
		given(globalDynamicProperties.fundingAccount()).willReturn(new Id(0, 0, 1010).asGrpcAccount());

		var evmAccount = mock(EvmAccount.class);

		given(gasCalculator.transactionIntrinsicGasCost(Bytes.EMPTY, false)).willReturn(Gas.ZERO);

		given(updater.getOrCreateSenderAccount(sender.getId().asEvmAddress())).willReturn(evmAccount);
		given(worldState.updater()).willReturn(updater);
		given(codeCache.getIfPresent(any())).willReturn(new Code());

		given(gasCalculator.getSelfDestructRefundAmount()).willReturn(Gas.ZERO);
		given(gasCalculator.getMaxRefundQuotient()).willReturn(2L);

		var senderMutableAccount = mock(MutableAccount.class);
		given(senderMutableAccount.decrementBalance(any())).willReturn(Wei.of(1234L));
		given(senderMutableAccount.incrementBalance(any())).willReturn(Wei.of(1500L));
		given(evmAccount.getMutable()).willReturn(senderMutableAccount);

		given(updater.getSenderAccount(any())).willReturn(evmAccount);
		given(updater.getSenderAccount(any()).getMutable()).willReturn(senderMutableAccount);
		given(updater.getOrCreate(any())).willReturn(evmAccount);
		given(updater.getOrCreate(any()).getMutable()).willReturn(senderMutableAccount);
		given(updater.getSbhRefund()).willReturn(Gas.ZERO);
	}

	private void givenSenderWithBalance(final long amount) {
		final var wrappedSenderAccount = mock(EvmAccount.class);
		final var mutableSenderAccount = mock(MutableAccount.class);
		given(wrappedSenderAccount.getMutable()).willReturn(mutableSenderAccount);
		given(updater.getOrCreateSenderAccount(sender.getId().asEvmAddress())).willReturn(wrappedSenderAccount);

		given(mutableSenderAccount.getBalance()).willReturn(Wei.of(amount));
	}
}
