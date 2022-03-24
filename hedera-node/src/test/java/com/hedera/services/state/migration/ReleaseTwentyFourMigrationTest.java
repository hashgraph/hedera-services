package com.hedera.services.state.migration;

/*-
 * ‌
 * Hedera Services Node
 * ​
 * Copyright (C) 2018 - 2022 Hedera Hashgraph, LLC
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

import com.hedera.services.ServicesState;
import com.hedera.services.state.merkle.MerkleAccount;
import com.hedera.services.state.merkle.MerkleAccountTokens;
import com.hedera.services.utils.EntityNum;
import com.swirlds.merkle.map.MerkleMap;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import static com.hedera.services.context.BasicTransactionContext.EMPTY_KEY;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.verify;

@ExtendWith(MockitoExtension.class)
class ReleaseTwentyFourMigrationTest {
	@Mock
	private MerkleAccount extantButWrong801;
	@Mock
	private ServicesState state;
	@Mock
	private MerkleMap<EntityNum, MerkleAccount> accounts;

	private static final long expectedExpiry = 33197904000L;
	private static final EntityNum num800 = EntityNum.fromLong(800);
	private static final EntityNum num801 = EntityNum.fromLong(801);

	@Test
	void ensuresAsExpected() {
		final var captor = ArgumentCaptor.forClass(MerkleAccount.class);
		given(state.accounts()).willReturn(accounts);
		given(accounts.containsKey(num800)).willReturn(false);
		given(accounts.containsKey(num801)).willReturn(true);
		given(accounts.getForModify(num801)).willReturn(extantButWrong801);

		ReleaseTwentyFourMigration.ensureStakingFundAccounts(state);

		verify(accounts).put(eq(num800), captor.capture());
		verify(extantButWrong801).setExpiry(33197904000L);
		verify(extantButWrong801).setTokens(new MerkleAccountTokens());
		verify(extantButWrong801).setAccountKey(EMPTY_KEY);
		verify(extantButWrong801).setSmartContract(false);
		verify(extantButWrong801).setReceiverSigRequired(false);
		verify(extantButWrong801).setMaxAutomaticAssociations(0);
		final var newNum800 = captor.getValue();
		assertEquals(33197904000L, newNum800.getExpiry());
		assertEquals(0, newNum800.tokens().numAssociations());
		assertEquals(EMPTY_KEY, newNum800.getAccountKey());
		assertFalse(newNum800.isReceiverSigRequired());
		assertFalse(newNum800.isSmartContract());
		assertEquals(0, newNum800.getMaxAutomaticAssociations());
	}
}
