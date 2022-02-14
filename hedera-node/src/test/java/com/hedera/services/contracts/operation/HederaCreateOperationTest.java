package com.hedera.services.contracts.operation;

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


import com.hedera.services.records.AccountRecordsHistorian;
import com.hedera.services.state.EntityCreator;
import com.hedera.services.store.contracts.HederaWorldUpdater;
import com.hedera.services.store.contracts.precompile.SyntheticTxnFactory;
import org.apache.tuweni.bytes.Bytes;
import org.hyperledger.besu.datatypes.Address;
import org.hyperledger.besu.datatypes.Wei;
import org.hyperledger.besu.evm.Gas;
import org.hyperledger.besu.evm.frame.BlockValues;
import org.hyperledger.besu.evm.frame.MessageFrame;
import org.hyperledger.besu.evm.gascalculator.GasCalculator;
import org.junit.jupiter.api.Assertions;
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
class HederaCreateOperationTest {
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
	private HederaWorldUpdater hederaWorldUpdater;
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

	private HederaCreateOperation subject;

	@BeforeEach
	void setup() {
		subject = new HederaCreateOperation(gasCalculator, creator, syntheticTxnFactory, recordsHistorian);
	}

	@Test
	void isAlwaysEnabled() {
		Assertions.assertTrue(subject.isEnabled());
	}

	@Test
	void computesExpectedCost() {
		final var oneOffsetStackItem = Bytes.of(10);
		final var twoOffsetStackItem = Bytes.of(20);
		given(evmMsgFrame.getStackItem(1)).willReturn(oneOffsetStackItem);
		given(evmMsgFrame.getStackItem(2)).willReturn(twoOffsetStackItem);
		given(evmMsgFrame.getMessageFrameStack()).willReturn(messageFrameStack);
		given(evmMsgFrame.getBlockValues()).willReturn(initialFrameBlockValues);
		given(evmMsgFrame.getGasPrice()).willReturn(gasPrice);
		given(messageFrameStack.getLast()).willReturn(lastStackedMsgFrame);
		given(lastStackedMsgFrame.getContextVariable("sbh")).willReturn(10L);
		given(initialFrameBlockValues.getTimestamp()).willReturn(Instant.MAX.getEpochSecond());
		given(gasPrice.toLong()).willReturn(10000000000L);
		given(messageFrameStack.iterator()).willReturn(iterator);
		given(iterator.hasNext()).willReturn(false);

		given(gasCalculator.createOperationGasCost(any())).willReturn(gas);
		given(gasCalculator.memoryExpansionGasCost(any(), anyLong(), anyLong())).willReturn(gas);
		given(gas.plus(any())).willReturn(gas);

		var gas = subject.cost(evmMsgFrame);

		assertEquals(0, gas.toLong());
	}

	@Test
	void computesExpectedTargetAddress() {
		given(evmMsgFrame.getWorldUpdater()).willReturn(hederaWorldUpdater);
		given(evmMsgFrame.getRecipientAddress()).willReturn(recipientAddr);
		given(hederaWorldUpdater.newContractAddress(recipientAddr)).willReturn(Address.ZERO);
		var targetAddr = subject.targetContractAddress(evmMsgFrame);
		assertEquals(Address.ZERO, targetAddr);
	}
}