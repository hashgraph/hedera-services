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
import com.hedera.services.exceptions.InsufficientFundsException;
import com.hedera.services.exceptions.NonZeroNetTransfersException;
import com.hedera.test.utils.TxnUtils;
import com.hederahashgraph.api.proto.java.TransferList;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static com.hedera.services.exceptions.InsufficientFundsException.messageFor;
import static com.hedera.services.ledger.properties.AccountProperty.BALANCE;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.BDDMockito.any;
import static org.mockito.BDDMockito.doThrow;
import static org.mockito.BDDMockito.never;
import static org.mockito.BDDMockito.verify;

public class HederaLedgerXfersTest extends BaseHederaLedgerTest {
	@BeforeEach
	private void setup() {
		commonSetup();
		setupWithMockLedger();
	}

	@Test
	public void throwsOnNetTransfersIfNotInTxn() {
		// setup:
		doThrow(IllegalStateException.class).when(accountsLedger).throwIfNotInTxn();

		// expect:
		assertThrows(IllegalStateException.class, () -> subject.netTransfersInTxn());
	}

	@Test
	public void throwsOnTransferWithDeletedFromAccount() {
		// setup:
		DeletedAccountException e = null;

		// when:
		try {
			subject.doTransfer(deleted, misc, 1L);
		} catch (DeletedAccountException aide) {
			e = aide;
		}

		// then:
		assertEquals("0.0.3456", e.getMessage());
		verify(accountsLedger, never()).set(any(), any(), any());
	}

	@Test
	public void throwsOnTransferWithDeletedToAccount() {
		// setup:
		DeletedAccountException e = null;

		// when:
		try {
			subject.doTransfer(misc, deleted, 1L);
		} catch (DeletedAccountException aide) {
			e = aide;
		}

		// then:
		assertEquals("0.0.3456", e.getMessage());
		verify(accountsLedger, never()).set(any(), any(), any());
	}

	@Test
	public void throwsOnTransfersWithDeleted() {
		// given:
		TransferList accountAmounts = TxnUtils.withAdjustments(misc, 1, deleted, -2, genesis, 1);
		DeletedAccountException e = null;

		// expect:
		try {
			subject.doTransfers(accountAmounts);
		} catch (DeletedAccountException aide) {
			e = aide;
		}

		// then:
		assertEquals("0.0.3456", e.getMessage());
		verify(accountsLedger, never()).set(any(), any(), any());
	}

	@Test
	public void doesReasonableTransfers() {
		// given:
		TransferList accountAmounts = TxnUtils.withAdjustments(misc, 1, rand, -2, genesis, 1);

		// expect:
		subject.doTransfers(accountAmounts);

		// then:
		verify(accountsLedger).set(misc, BALANCE, MISC_BALANCE + 1);
		verify(accountsLedger).set(rand, BALANCE, RAND_BALANCE - 2);
		verify(accountsLedger).set(genesis, BALANCE, GENESIS_BALANCE + 1);
	}

	@Test
	public void throwsOnImpossibleTransfers() {
		// given:
		TransferList accountAmounts = TxnUtils.withAdjustments(misc, 1, rand, 2, genesis, 3);

		// expect:
		assertThrows(NonZeroNetTransfersException.class, () -> subject.doTransfers(accountAmounts));
	}

	@Test
	public void doesReasonableTransfer() {
		// setup:
		long amount = 1_234L;

		// when:
		subject.doTransfer(genesis, misc, amount);

		// then:
		verify(accountsLedger).set(genesis, BALANCE, GENESIS_BALANCE - amount);
		verify(accountsLedger).set(misc, BALANCE, MISC_BALANCE + amount);
	}

	@Test
	public void throwsOnImpossibleTransferWithBrokerPayer() {
		// setup:
		long amount = GENESIS_BALANCE + 1;
		InsufficientFundsException e = null;

		// when:
		try {
			subject.doTransfer(genesis, misc, amount);
		} catch (InsufficientFundsException ibce) {
			e = ibce;
		}

		// then:
		assertEquals(messageFor(genesis, -1 * amount), e.getMessage());
		verify(accountsLedger, never()).set(any(), any(), any());
	}
}
