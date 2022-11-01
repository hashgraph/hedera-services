package com.hedera.services.evm.contracts.operations;

import com.hedera.services.evm.store.contracts.AbstractLedgerEvmWorldUpdater;
import org.apache.tuweni.bytes.Bytes;
import org.hyperledger.besu.datatypes.Address;
import org.hyperledger.besu.datatypes.Wei;
import org.hyperledger.besu.evm.EVM;
import org.hyperledger.besu.evm.account.Account;
import org.hyperledger.besu.evm.frame.MessageFrame;
import org.hyperledger.besu.evm.gascalculator.GasCalculator;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Optional;
import java.util.function.BiPredicate;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyBoolean;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.BDDMockito.given;

@ExtendWith(MockitoExtension.class)
class HederaEvmCallOperationTest {

	@Mock
	private GasCalculator calc;
	@Mock private MessageFrame evmMsgFrame;
	@Mock private EVM evm;
	@Mock private AbstractLedgerEvmWorldUpdater worldUpdater;
	@Mock private Account acc;
	@Mock private BiPredicate<Address, MessageFrame> addressValidator;

	private final long cost = 100L;
	private HederaEvmCallOperation subject;

	@BeforeEach
	void setup() {
		subject =
				new HederaEvmCallOperation(calc, addressValidator);
	}

	@Test
	void haltWithInvalidAddr() {
		given(evmMsgFrame.getWorldUpdater()).willReturn(worldUpdater);
		given(worldUpdater.get(any())).willReturn(acc);
		given(worldUpdater.get(any())).willReturn(null);
		given(
				calc.callOperationGasCost(
						any(), anyLong(), anyLong(), anyLong(), anyLong(), anyLong(), any(),
						any(), any()))
				.willReturn(cost);
		given(evmMsgFrame.getStackItem(0)).willReturn(Bytes.EMPTY);
		given(evmMsgFrame.getStackItem(1)).willReturn(Bytes.EMPTY);
		given(evmMsgFrame.getStackItem(2)).willReturn(Bytes.EMPTY);
		given(evmMsgFrame.getStackItem(3)).willReturn(Bytes.EMPTY);
		given(evmMsgFrame.getStackItem(4)).willReturn(Bytes.EMPTY);
		given(evmMsgFrame.getStackItem(5)).willReturn(Bytes.EMPTY);
		given(evmMsgFrame.getStackItem(6)).willReturn(Bytes.EMPTY);
		given(addressValidator.test(any(), any())).willReturn(false);

		var opRes = subject.execute(evmMsgFrame, evm);

		assertEquals(
				opRes.getHaltReason(),
				Optional.of(HederaExceptionalHaltReason.INVALID_SOLIDITY_ADDRESS));
		assertTrue(opRes.getGasCost().isPresent());
		assertEquals(opRes.getGasCost().getAsLong(), cost);
	}

	@Test
	void executesAsExpected() {
		given(evmMsgFrame.getWorldUpdater()).willReturn(worldUpdater);
		given(worldUpdater.get(any())).willReturn(acc);
		given(
				calc.callOperationGasCost(
						any(), anyLong(), anyLong(), anyLong(), anyLong(), anyLong(), any(),
						any(), any()))
				.willReturn(cost);
		// and:
		given(evmMsgFrame.getStackItem(0)).willReturn(Bytes.EMPTY);
		given(evmMsgFrame.getStackItem(1)).willReturn(Bytes.EMPTY);
		given(evmMsgFrame.getStackItem(2)).willReturn(Bytes.EMPTY);
		given(evmMsgFrame.getStackItem(3)).willReturn(Bytes.EMPTY);
		given(evmMsgFrame.getStackItem(4)).willReturn(Bytes.EMPTY);
		given(evmMsgFrame.getStackItem(5)).willReturn(Bytes.EMPTY);
		given(evmMsgFrame.getStackItem(6)).willReturn(Bytes.EMPTY);
		// and:
		given(evmMsgFrame.stackSize()).willReturn(20);
		given(evmMsgFrame.getRemainingGas()).willReturn(cost);
		given(evmMsgFrame.getMessageStackDepth()).willReturn(1025);

		given(evmMsgFrame.getRecipientAddress()).willReturn(Address.ALTBN128_ADD);

		given(worldUpdater.get(any())).willReturn(acc);
		given(acc.getBalance()).willReturn(Wei.of(100));
		given(calc.gasAvailableForChildCall(any(), anyLong(), anyBoolean())).willReturn(10L);
		given(addressValidator.test(any(), any())).willReturn(true);

		var opRes = subject.execute(evmMsgFrame, evm);
		assertEquals(Optional.empty(), opRes.getHaltReason());
		assertTrue(opRes.getGasCost().isPresent());
		assertEquals(opRes.getGasCost().getAsLong(), cost);
	}
}