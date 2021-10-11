package com.hedera.services.contracts.operation;

import com.hedera.services.store.contracts.HederaWorldState;
import com.hedera.services.store.contracts.HederaWorldUpdater;
import org.hyperledger.besu.datatypes.Address;
import org.hyperledger.besu.evm.frame.MessageFrame;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.ArrayDeque;
import java.util.Deque;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.BDDMockito.given;
import static org.mockito.BDDMockito.never;
import static org.mockito.BDDMockito.verify;

@ExtendWith(MockitoExtension.class)
class HederaOperationUtilTest {
	@Mock
	private MessageFrame messageFrame;

	@Mock
	private HederaWorldUpdater hederaWorldUpdater;

	@Mock
	private HederaWorldState.WorldStateAccount worldStateAccount;

	@Test
	void computeExpiryForNewContractHappyPath() {
		final var expectedExpiry = 20L;

		Deque<MessageFrame> frameDeque = new ArrayDeque<>();
		frameDeque.add(messageFrame);

		given(messageFrame.getMessageFrameStack()).willReturn(frameDeque);
		given(messageFrame.getContextVariable("expiry")).willReturn(Optional.of(expectedExpiry));

		var actualExpiry = HederaOperationUtil.computeExpiryForNewContract(messageFrame);

		assertEquals(expectedExpiry, actualExpiry);
		verify(messageFrame).getMessageFrameStack();
		verify(messageFrame).getContextVariable("expiry");
	}

	@Test
	void computeExpiryForNewContractMultipleFrames() {
		final var expectedExpiry = 21L;

		Deque<MessageFrame> frameDeque = new ArrayDeque<>();
		frameDeque.add(messageFrame);
		frameDeque.add(messageFrame);

		final var customAddress = Address.fromHexString("0x0000000000001");

		given(messageFrame.getMessageFrameStack()).willReturn(frameDeque);
		given(messageFrame.getSenderAddress()).willReturn(customAddress);
		given(messageFrame.getWorldUpdater()).willReturn(hederaWorldUpdater);
		given(hederaWorldUpdater.getHederaAccount(customAddress)).willReturn(worldStateAccount);
		given(worldStateAccount.getExpiry()).willReturn(expectedExpiry);

		var actualExpiry = HederaOperationUtil.computeExpiryForNewContract(messageFrame);

		assertEquals(expectedExpiry, actualExpiry);
		verify(messageFrame).getMessageFrameStack();
		verify(messageFrame).getSenderAddress();
		verify(hederaWorldUpdater).getHederaAccount(customAddress);
		verify(worldStateAccount).getExpiry();
		verify(messageFrame, never()).getContextVariable("expiry");
	}
}
