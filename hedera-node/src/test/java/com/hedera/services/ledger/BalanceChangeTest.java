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

import com.hedera.services.store.models.Id;
import com.hedera.test.utils.IdUtils;
import org.junit.jupiter.api.Test;

import static com.hederahashgraph.api.proto.java.ResponseCodeEnum.INSUFFICIENT_ACCOUNT_BALANCE;
import static com.hederahashgraph.api.proto.java.ResponseCodeEnum.INSUFFICIENT_PAYER_BALANCE;
import static com.hederahashgraph.api.proto.java.ResponseCodeEnum.INSUFFICIENT_TOKEN_BALANCE;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

class BalanceChangeTest {
	private final Id a = new Id(1, 2, 3);
	private final Id t = new Id(1, 2, 3);
	private final long delta = -1_234L;

	@Test
	void objectContractSanityChecks() {
		// given:
		final var hbarChange = BalanceChange.hbarAdjust(a, delta);
		final var tokenChange = BalanceChange.tokenAdjust(t, a, delta);
		// and:
		final var hbarRepr = "BalanceChange{token=ℏ, account=Id{shard=1, realm=2, num=3}, units=-1234}";
		final var tokenRepr = "BalanceChange{token=Id{shard=1, realm=2, num=3}, " +
				"account=Id{shard=1, realm=2, num=3}, units=-1234}";

		// expect:
		assertNotEquals(hbarChange, tokenChange);
		assertNotEquals(hbarChange.hashCode(), tokenChange.hashCode());
		// and:
		assertEquals(hbarRepr, hbarChange.toString());
		assertEquals(tokenRepr, tokenChange.toString());
		// and:
		assertSame(a, hbarChange.account());
		assertSame(t, tokenChange.token());
	}

	@Test
	void recognizesIfForHbar() {
		// given:
		final var hbarChange = BalanceChange.hbarAdjust(a, delta);
		final var tokenChange = BalanceChange.tokenAdjust(t, a, delta);

		assertTrue(hbarChange.isForHbar());
		assertFalse(tokenChange.isForHbar());
	}

	@Test
	void usesExplicitGrpcAccountIdIfSet() {
		// given:
		final var explicitId = IdUtils.asAccount("1.2.3");
		final var hbarChange = BalanceChange.hbarAdjust(a, delta);

		// expect:
		assertEquals(explicitId, hbarChange.accountId());

		// and when:
		hbarChange.setExplicitAccountId(explicitId);

		// expect:
		assertSame(explicitId, hbarChange.accountId());
	}

	@Test
	void usesExplicitGrpcTokenIdIfSet() {
		// given:
		final var explicitId = IdUtils.asToken("1.2.3");
		final var tokenChange = BalanceChange.tokenAdjust(t, a, delta);

		// expect:
		assertEquals(explicitId, tokenChange.tokenId());

		// and when:
		tokenChange.setExplicitTokenId(explicitId);

		// expect:
		assertSame(explicitId, tokenChange.tokenId());
	}

	@Test
	void overrideIbeCodeWorksForHbar() {
		// given:
		final var hbarChange = BalanceChange.hbarAdjust(a, delta);

		// expect:
		assertEquals(INSUFFICIENT_ACCOUNT_BALANCE, hbarChange.codeForInsufficientBalance());

		// when:
		hbarChange.setCodeForInsufficientBalance(INSUFFICIENT_PAYER_BALANCE);

		// then:
		assertEquals(INSUFFICIENT_PAYER_BALANCE, hbarChange.codeForInsufficientBalance());
	}

	@Test
	void overrideIbeCodeWorksForToken() {
		// given:
		final var tokenChange = BalanceChange.tokenAdjust(t, a, delta);

		// expect:
		assertEquals(INSUFFICIENT_TOKEN_BALANCE, tokenChange.codeForInsufficientBalance());

		// when:
		tokenChange.setCodeForInsufficientBalance(INSUFFICIENT_PAYER_BALANCE);

		// then:
		assertEquals(INSUFFICIENT_PAYER_BALANCE, tokenChange.codeForInsufficientBalance());
	}
}
