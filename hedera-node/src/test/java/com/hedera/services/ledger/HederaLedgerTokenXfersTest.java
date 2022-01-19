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

import com.hedera.services.store.tokens.views.UniqueTokenViewsManager;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;

import static com.hederahashgraph.api.proto.java.ResponseCodeEnum.OK;
import static com.hederahashgraph.api.proto.java.ResponseCodeEnum.TOKENS_PER_ACCOUNT_LIMIT_EXCEEDED;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.BDDMockito.any;
import static org.mockito.BDDMockito.given;
import static org.mockito.BDDMockito.never;
import static org.mockito.BDDMockito.times;
import static org.mockito.BDDMockito.verify;
import static org.mockito.Mockito.mock;

@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class HederaLedgerTokenXfersTest extends BaseHederaLedgerTestHelper {
	@BeforeEach
	private void setup() {
		commonSetup();
		setupWithMockLedger();
		subject.setTokenViewsManager(mock(UniqueTokenViewsManager.class));
	}

	@Test
	void tokenTransferHappyPathWOrks() {
		// setup
		given(subject.adjustTokenBalance(misc, tokenId, -1_000)).willReturn(OK);
		given(subject.adjustTokenBalance(rand, tokenId, 1_000)).willReturn(OK);

		// when:
		var outcome = subject.doTokenTransfer(tokenId, misc, rand, 1_000);

		// then:
		assertEquals(OK, outcome);
		verify(tokenStore, never()).exists(tokenId);
	}

	@Test
	void tokenTransferRevertsChangesOnFirstAdjust() {
		// setup
		given(tokenStore.adjustBalance(misc, tokenId, -555))
				.willReturn(TOKENS_PER_ACCOUNT_LIMIT_EXCEEDED);

		// given:
		var status = subject.doTokenTransfer(tokenId, misc, rand, 555);

		// expect:
		assertEquals(TOKENS_PER_ACCOUNT_LIMIT_EXCEEDED, status);
		// and:
		verify(tokenStore, times(1)).adjustBalance(any(), any(), anyLong());
		verify(tokenRelsLedger).rollback();
		verify(sideEffectsTracker).resetTrackedTokenChanges();
	}

	@Test
	void tokenTransferRevertsChangesOnSecondAdjust() {
		// setup
		given(tokenStore.adjustBalance(misc, tokenId, -555))
				.willReturn(OK);
		given(tokenStore.adjustBalance(rand, tokenId, 555))
				.willReturn(TOKENS_PER_ACCOUNT_LIMIT_EXCEEDED);

		// given:
		var status = subject.doTokenTransfer(tokenId, misc, rand, 555);

		// expect:
		assertEquals(TOKENS_PER_ACCOUNT_LIMIT_EXCEEDED, status);
		// and:
		verify(tokenStore).adjustBalance(misc, tokenId, -555);
		verify(tokenStore).adjustBalance(rand, tokenId, 555);
		verify(sideEffectsTracker).resetTrackedTokenChanges();
		verify(tokenRelsLedger).rollback();
	}
}
