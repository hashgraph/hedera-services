package com.hedera.services.contracts.operation;

import com.hedera.services.records.AccountRecordsHistorian;
import com.hedera.services.state.EntityCreator;
import com.hedera.services.store.contracts.HederaStackedWorldStateUpdater;
import com.hedera.services.store.contracts.precompile.SyntheticTxnFactory;
import org.apache.tuweni.bytes.Bytes;
import org.apache.tuweni.bytes.MutableBytes;
import org.hyperledger.besu.datatypes.Address;
import org.hyperledger.besu.datatypes.Wei;
import org.hyperledger.besu.evm.Gas;
import org.hyperledger.besu.evm.frame.BlockValues;
import org.hyperledger.besu.evm.frame.MessageFrame;
import org.hyperledger.besu.evm.gascalculator.GasCalculator;
import org.hyperledger.besu.evm.operation.Create2Operation;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Instant;
import java.util.Deque;
import java.util.Iterator;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.BDDMockito.given;

@ExtendWith(MockitoExtension.class)
class HederaCreate2OperationTest {
	private static final Bytes salt = Bytes.fromHexString("0x2a");
	private static final Bytes oneOffsetStackItem = Bytes.of(10);
	private static final Bytes twoOffsetStackItem = Bytes.of(20);
	private static final MutableBytes initcode = MutableBytes.of((byte) 0xaa);

	@Mock
	private MessageFrame evmMsgFrame;
	@Mock
	private BlockValues initialFrameBlockValues;
	@Mock
	private Wei gasPrice;
	@Mock
	private Gas gas;
	@Mock
	private MessageFrame lastStackedMsgFrame;
	@Mock
	private GasCalculator gasCalculator;
	@Mock
	private HederaStackedWorldStateUpdater stackedUpdater;
	@Mock
	private Address recipientAddr;
	@Mock
	private Deque<MessageFrame> messageFrameStack;
	@Mock
	private Iterator<MessageFrame> iterator;
	@Mock
	private SyntheticTxnFactory syntheticTxnFactory;
	@Mock
	private EntityCreator creator;
	@Mock
	private AccountRecordsHistorian recordsHistorian;

	private HederaCreate2Operation subject;

	@BeforeEach
	void setup() {
		subject = new HederaCreate2Operation(gasCalculator, creator, syntheticTxnFactory, recordsHistorian);
	}

	@Test
	void computesExpectedCost() {
		givenMemoryStackItems();
		given(evmMsgFrame.getMessageFrameStack()).willReturn(messageFrameStack);
		given(evmMsgFrame.getBlockValues()).willReturn(initialFrameBlockValues);
		given(evmMsgFrame.getGasPrice()).willReturn(gasPrice);
		given(messageFrameStack.getLast()).willReturn(lastStackedMsgFrame);
		given(lastStackedMsgFrame.getContextVariable("sbh")).willReturn(10L);
		given(initialFrameBlockValues.getTimestamp()).willReturn(Instant.MAX.getEpochSecond());
		given(gasPrice.toLong()).willReturn(10000000000L);
		given(messageFrameStack.iterator()).willReturn(iterator);
		given(iterator.hasNext()).willReturn(false);

		given(gasCalculator.create2OperationGasCost(any())).willReturn(gas);
		given(gasCalculator.memoryExpansionGasCost(any(), anyLong(), anyLong())).willReturn(gas);
		given(gas.plus(any())).willReturn(gas);

		var gas = subject.cost(evmMsgFrame);

		assertEquals(0, gas.toLong());
	}

	@Test
	void computesExpectedTargetAddress() {
		final var expectedAddress = Address.BLS12_G1ADD;
		final var besuOp = new Create2Operation(gasCalculator);

		givenMemoryStackItems();
		given(evmMsgFrame.getStackItem(3)).willReturn(salt);
		given(evmMsgFrame.getRecipientAddress()).willReturn(recipientAddr);
		given(evmMsgFrame.readMutableMemory(oneOffsetStackItem.toLong(), twoOffsetStackItem.toLong()))
				.willReturn(initcode);
		given(evmMsgFrame.getWorldUpdater()).willReturn(stackedUpdater);
		final var expectedAlias = besuOp.targetContractAddress(evmMsgFrame);
		given(stackedUpdater.newAliasedContractAddress(recipientAddr, expectedAlias)).willReturn(expectedAddress);

		final var actualAddr = subject.targetContractAddress(evmMsgFrame);
		assertEquals(expectedAlias, actualAddr);
	}

	private void givenMemoryStackItems() {
		given(evmMsgFrame.getStackItem(1)).willReturn(oneOffsetStackItem);
		given(evmMsgFrame.getStackItem(2)).willReturn(twoOffsetStackItem);
	}
}