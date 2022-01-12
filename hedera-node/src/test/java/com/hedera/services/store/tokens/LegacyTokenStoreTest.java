package com.hedera.services.store.tokens;

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

import com.hedera.services.ledger.BalanceChange;
import com.hedera.services.state.merkle.MerkleToken;
import com.hedera.services.state.submerkle.EntityId;
import com.hedera.services.store.models.Id;
import com.hedera.services.store.models.NftId;
import com.hederahashgraph.api.proto.java.AccountAmount;
import com.hederahashgraph.api.proto.java.AccountID;
import com.hederahashgraph.api.proto.java.TokenID;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.Mockito;
import org.mockito.junit.jupiter.MockitoExtension;

import static com.hedera.services.ledger.BalanceChange.changingNftOwnership;
import static com.hedera.test.utils.IdUtils.asAccount;
import static com.hedera.test.utils.IdUtils.nftXfer;
import static com.hederahashgraph.api.proto.java.ResponseCodeEnum.OK;
import static com.hederahashgraph.api.proto.java.ResponseCodeEnum.UNEXPECTED_TOKEN_DECIMALS;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.doCallRealMethod;
import static org.mockito.Mockito.mock;

@ExtendWith(MockitoExtension.class)
class LegacyTokenStoreTest {
	private final Id t = new Id(1, 2, 3);
	private final TokenID tId = t.asGrpcToken();
	private final long delta = -1_234L;
	private final long serialNo = 1234L;
	private final AccountID a = asAccount("1.2.3");
	private final AccountID b = asAccount("2.3.4");
	private final NftId tNft = new NftId(1, 2, 3, serialNo);
	private final MerkleToken token = new MerkleToken(1_234_567L, 1_000_000L, 2,
			"testTokenA", "testTokenA", false, false,
			new EntityId(1, 2, 3));

	@Mock
	private TokenStore tokenStore;

	@Test
	void adaptsBehaviorToFungibleType() {
		// setup:
		final var aa = AccountAmount.newBuilder().setAccountID(a).setAmount(delta).build();
		final var fungibleChange = BalanceChange.changingFtUnits(t, t.asGrpcToken(), aa);
		fungibleChange.setExpectedDecimals(2);

		// and:
		doCallRealMethod().when(tokenStore).tryTokenChange(fungibleChange);
		given(tokenStore.exists(t.asGrpcToken())).willReturn(true);
		given(tokenStore.get(t.asGrpcToken())).willReturn(token);
		given(tokenStore.resolve(tId)).willReturn(tId);
		given(tokenStore.adjustBalance(a, tId, delta)).willReturn(OK);

		// when:
		final var result = tokenStore.tryTokenChange(fungibleChange);

		// then:
		Assertions.assertEquals(OK, result);
	}

	@Test
	void failsIfUnexpectedDecimals() {
		final var aa = AccountAmount.newBuilder().setAccountID(a).setAmount(delta).build();
		final var fungibleChange = BalanceChange.changingFtUnits(t, t.asGrpcToken(), aa);
		fungibleChange.setExpectedDecimals(4);

		given(tokenStore.resolve(tId)).willReturn(tId);
		given(tokenStore.adjustBalance(a, tId, delta)).willReturn(OK);
		doCallRealMethod().when(tokenStore).tryTokenChange(fungibleChange);

		final var result = tokenStore.tryTokenChange(fungibleChange);

		Assertions.assertEquals(UNEXPECTED_TOKEN_DECIMALS, result);
	}


	@Test
	void adaptsBehaviorToNonfungibleType() {
		// setup:
		final var nftChange = changingNftOwnership(t, t.asGrpcToken(), nftXfer(a, b, serialNo));

		// and:
		doCallRealMethod().when(tokenStore).tryTokenChange(nftChange);
		given(tokenStore.resolve(tId)).willReturn(tId);
		given(tokenStore.changeOwner(tNft, a, b)).willReturn(OK);

		// when:
		final var result = tokenStore.tryTokenChange(nftChange);

		// then:
		Assertions.assertEquals(OK, result);
	}
}
