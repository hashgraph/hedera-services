package com.hedera.services.ledger;

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

import com.hedera.services.exceptions.DeletedAccountException;
import com.hedera.services.exceptions.DetachedAccountException;
import com.hedera.services.exceptions.InsufficientFundsException;
import com.hedera.services.exceptions.NonZeroNetTransfersException;
import com.hedera.services.txns.validation.OptionValidator;
import com.hedera.test.utils.TxnUtils;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static com.hedera.services.exceptions.InsufficientFundsException.messageFor;
import static com.hedera.services.ledger.properties.AccountProperty.BALANCE;
import static com.hedera.services.ledger.properties.AccountProperty.EXPIRY;
import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.BDDMockito.any;
import static org.mockito.BDDMockito.doThrow;
import static org.mockito.BDDMockito.given;
import static org.mockito.BDDMockito.never;
import static org.mockito.BDDMockito.verify;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class HederaLedgerXfersTest extends BaseHederaLedgerTestHelper {
	@BeforeEach
	private void setup() {
		commonSetup();
		setupWithMockLedger();
	}

	@Test
	void throwsOnNetTransfersIfNotInTxn() {
		doThrow(IllegalStateException.class).when(accountsLedger).throwIfNotInTxn();

		assertThrows(IllegalStateException.class, () -> subject.netTransfersInTxn());
	}

	@Test
	void throwsOnTransferWithDeletedFromAccount() {
		final var e = assertThrows(DeletedAccountException.class,
				() -> subject.doTransfer(deleted, misc, 1L));

		assertEquals("0.0.3456", e.getMessage());
		verify(accountsLedger, never()).set(any(), any(), any());
	}

	@Test
	void throwsOnTransferWithDeletedToAccount() {
		final var e = assertThrows(DeletedAccountException.class,
				() -> subject.doTransfer(misc, deleted, 1L));

		assertEquals("0.0.3456", e.getMessage());
		verify(accountsLedger, never()).set(any(), any(), any());
	}

	@Test
	void throwsOnTransfersWithDeleted() {
		final var accountAmounts = TxnUtils.withAdjustments(misc, 1, deleted, -2, genesis, 1);
		final var e = assertThrows(DeletedAccountException.class,
				() -> subject.doTransfers(accountAmounts));

		assertEquals("0.0.3456", e.getMessage());
		verify(accountsLedger, never()).set(any(), any(), any());
	}

	@Test
	void throwsOnTransfersWithDetached() {
		final var accountAmounts = TxnUtils.withAdjustments(misc, -2, detached, 4, genesis, -2);
		final var mockValidator = mock(OptionValidator.class);
		when(accountsLedger.get(detached, EXPIRY)).thenReturn(666L);
		when(accountsLedger.get(detached, BALANCE)).thenReturn(0L);
		given(mockValidator.isAfterConsensusSecond(1_234_567_890L)).willReturn(true);
		given(mockValidator.isAfterConsensusSecond(666L)).willReturn(false);
		subject = new HederaLedger(tokenStore, ids, creator, mockValidator, historian, dynamicProps, accountsLedger);
		subject.setTokenRelsLedger(tokenRelsLedger);

		final var e = assertThrows(DetachedAccountException.class,
				() -> subject.doTransfers(accountAmounts));

		assertEquals("0.0.4567", e.getMessage());
		verify(accountsLedger, never()).set(any(), any(), any());
	}

	@Test
	void notDetachedUntilGivenChanceToRenew() {
		final var accountAmounts = TxnUtils.withAdjustments(misc, -2, detached, 4, genesis, -2);
		final var mockValidator = mock(OptionValidator.class);
		when(accountsLedger.get(detached, EXPIRY)).thenReturn(666L);
		when(accountsLedger.get(detached, BALANCE)).thenReturn(1L);
		given(mockValidator.isAfterConsensusSecond(1_234_567_890L)).willReturn(true);
		given(mockValidator.isAfterConsensusSecond(666L)).willReturn(false);

		subject = new HederaLedger(tokenStore, ids, creator, mockValidator, historian, dynamicProps, accountsLedger);
		subject.setTokenRelsLedger(tokenRelsLedger);

		assertDoesNotThrow(() -> subject.doTransfers(accountAmounts));
	}

	@Test
	void doesReasonableTransfers() {
		final var accountAmounts = TxnUtils.withAdjustments(misc, 1, rand, -2, genesis, 1);

		subject.doTransfers(accountAmounts);

		verify(accountsLedger).set(misc, BALANCE, MISC_BALANCE + 1);
		verify(accountsLedger).set(rand, BALANCE, RAND_BALANCE - 2);
		verify(accountsLedger).set(genesis, BALANCE, GENESIS_BALANCE + 1);
	}

	@Test
	void throwsOnImpossibleTransfers() {
		final var accountAmounts = TxnUtils.withAdjustments(misc, 1, rand, 2, genesis, 3);

		assertThrows(NonZeroNetTransfersException.class, () -> subject.doTransfers(accountAmounts));
	}

	@Test
	void doesReasonableTransfer() {
		final var amount = 1_234L;

		subject.doTransfer(genesis, misc, amount);

		verify(accountsLedger).set(genesis, BALANCE, GENESIS_BALANCE - amount);
		verify(accountsLedger).set(misc, BALANCE, MISC_BALANCE + amount);
	}

	@Test
	void throwsOnImpossibleTransferWithBrokerPayer() {
		final var amount = GENESIS_BALANCE + 1;
		final var e = assertThrows(InsufficientFundsException.class, () -> subject.doTransfer(genesis, misc, amount));

		assertEquals(messageFor(genesis, -1 * amount), e.getMessage());
		verify(accountsLedger, never()).set(any(), any(), any());
	}
}
