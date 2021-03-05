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

import com.hedera.test.utils.IdUtils;
import com.hederahashgraph.api.proto.java.AccountAmount;
import com.hederahashgraph.api.proto.java.CryptoTransferTransactionBody;
import com.hederahashgraph.api.proto.java.TokenTransferList;
import com.hederahashgraph.api.proto.java.TransferList;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.List;

import static com.hedera.services.ledger.properties.AccountProperty.BALANCE;
import static com.hedera.services.ledger.properties.AccountProperty.IS_SMART_CONTRACT;
import static com.hedera.test.utils.IdUtils.adjustFrom;
import static com.hedera.test.utils.IdUtils.tokenWith;
import static com.hederahashgraph.api.proto.java.ResponseCodeEnum.INVALID_ACCOUNT_ID;
import static com.hederahashgraph.api.proto.java.ResponseCodeEnum.INVALID_TOKEN_ID;
import static com.hederahashgraph.api.proto.java.ResponseCodeEnum.OK;
import static com.hederahashgraph.api.proto.java.ResponseCodeEnum.TOKENS_PER_ACCOUNT_LIMIT_EXCEEDED;
import static com.hederahashgraph.api.proto.java.ResponseCodeEnum.TRANSFERS_NOT_ZERO_SUM_FOR_TOKEN;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.BDDMockito.any;
import static org.mockito.BDDMockito.given;
import static org.mockito.BDDMockito.never;
import static org.mockito.BDDMockito.times;
import static org.mockito.BDDMockito.verify;

public class HederLedgerTokenXfersTest extends BaseHederaLedgerTest {
	@BeforeEach
	private void setup() {
		commonSetup();
		setupWithMockLedger();
	}

	@Test
	public void requiresAllNetZeroTransfers() {
		givenAdjustBalanceUpdatingTokenXfers(any(), any(), anyLong());

		// when:
		var outcome = subject.doAtomicTransfers(unmatchedTokenTransfers);
		// and:
		var netXfers = subject.netTokenTransfersInTxn();

		// then:
		assertEquals(TRANSFERS_NOT_ZERO_SUM_FOR_TOKEN, outcome);
		// and:
		assertTrue(netXfers.isEmpty());
	}

	@Test
	public void atomicZeroSumTokenTransferRejectsMissingId() {
		given(tokenStore.adjustBalance(any(), any(), anyLong())).willReturn(OK);

		// when:
		var outcome = subject.doAtomicTransfers(missingIdTokenTransfers);
		// and:
		var netXfers = subject.netTokenTransfersInTxn();

		// then:
		assertEquals(INVALID_TOKEN_ID, outcome);
		// and:
		assertTrue(netXfers.isEmpty());
	}

	@Test
	public void tokenTransfersRollbackIfInvalidHbars() {
		given(tokenStore.adjustBalance(any(), any(), anyLong())).willReturn(OK);
		given(accountsLedger.get(rand, IS_SMART_CONTRACT)).willReturn(true);

		// when:
		var outcome = subject.doAtomicTransfers(validTokenXfersPlusInvalidHbarXfers);
		// and:
		var netXfers = subject.netTokenTransfersInTxn();

		// then:
		assertEquals(INVALID_ACCOUNT_ID, outcome);
		// and:
		assertTrue(netXfers.isEmpty());
	}

	@Test
	public void happyPathZeroSumTokenTransfers() {
		givenAdjustBalanceUpdatingTokenXfers(any(), any(), anyLong());

		// when:
		var outcome = subject.doAtomicTransfers(multipleValidTokenTransfers);
		// and:
		var netXfers = subject.netTokenTransfersInTxn();

		// then:
		assertEquals(OK, outcome);
		// and:
		assertEquals(frozenId, netXfers.get(0).getToken());
		assertEquals(
				List.of(aa(misc, 1_000), aa(rand, -1_000)),
				netXfers.get(0).getTransfersList());
		assertEquals(tokenId, netXfers.get(1).getToken());
		assertEquals(
				List.of(aa(misc, 1_000), aa(rand, -1_000)),
				netXfers.get(1).getTransfersList());
	}

	@Test
	public void happyPathZeroSumTokenTransfersWithHbar() {
		givenAdjustBalanceUpdatingTokenXfers(any(), any(), anyLong());

		// when:
		var outcome = subject.doAtomicTransfers(multipleValidTokenTransfersPlusHbars);
		// and:
		var netXfers = subject.netTokenTransfersInTxn();

		// then:
		assertEquals(OK, outcome);
		// and:
		assertEquals(frozenId, netXfers.get(0).getToken());
		assertEquals(
				List.of(aa(misc, 1_000), aa(rand, -1_000)),
				netXfers.get(0).getTransfersList());
		assertEquals(tokenId, netXfers.get(1).getToken());
		assertEquals(
				List.of(aa(misc, 1_000), aa(rand, -1_000)),
				netXfers.get(1).getTransfersList());
		// and:
		verify(accountsLedger).set(misc, BALANCE, MISC_BALANCE - 123);
		verify(accountsLedger).set(rand, BALANCE, RAND_BALANCE + 123);
	}

	@Test
	public void tokenTransferHappyPathWOrks() {
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
	public void tokenTransferRevertsChangesOnFirstAdjust() {
		// setup
		given(tokenStore.adjustBalance(misc, tokenId, -555))
				.willReturn(TOKENS_PER_ACCOUNT_LIMIT_EXCEEDED);

		// given:
		var status = subject.doTokenTransfer(tokenId, misc, rand, 555);

		// expect:
		assertEquals(TOKENS_PER_ACCOUNT_LIMIT_EXCEEDED, status);
		// and:
		assertEquals(0, subject.numTokenTouches);
		verify(tokenStore, times(1)).adjustBalance(any(), any(), anyLong());
		verify(tokenRelsLedger).rollback();
	}

	@Test
	public void tokenTransferRevertsChangesOnSecondAdjust() {
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
		assertEquals(0, subject.numTokenTouches);
		verify(tokenStore).adjustBalance(misc, tokenId, -555);
		verify(tokenStore).adjustBalance(rand, tokenId, 555);
		verify(tokenRelsLedger).rollback();
	}

	@Test
	public void tokenTransferHappyPath() {
		// setup
		givenAdjustBalanceUpdatingTokenXfers(misc, tokenId, -555);
		givenAdjustBalanceUpdatingTokenXfers(rand, tokenId, 555);

		// given
		var outcome = subject.doTokenTransfer(tokenId, misc, rand, 555);
		var netXfers = subject.netTokenTransfersInTxn();

		assertEquals(OK, outcome);
		assertEquals(tokenId, netXfers.get(0).getToken());
		assertEquals(List.of(aa(misc, -555), aa(rand, 555)),
				netXfers.get(0).getTransfersList());
	}

	@Test
	public void resetsTokenTransferTrackingAfterRollback() {
		// setup:
		subject.begin();
		// and:
		subject.numTokenTouches = 2;
		subject.tokensTouched[0] = tokenWith(111);
		subject.tokensTouched[1] = tokenWith(222);
		// and:
		subject.netTokenTransfers.put(
				tokenWith(111),
				TransferList.newBuilder()
						.addAccountAmounts(
								AccountAmount.newBuilder()
										.setAccountID(IdUtils.asAccount("0.0.2"))));
		subject.netTokenTransfers.put(
				tokenWith(222),
				TransferList.newBuilder()
						.addAccountAmounts(
								AccountAmount.newBuilder()
										.setAccountID(IdUtils.asAccount("0.0.3"))));

		// when:
		subject.rollback();

		// then:
		assertEquals(0, subject.numTokenTouches);
		assertEquals(0, subject.netTokenTransfers.get(tokenWith(111)).getAccountAmountsCount());
		assertEquals(0, subject.netTokenTransfers.get(tokenWith(222)).getAccountAmountsCount());
	}

	CryptoTransferTransactionBody unmatchedTokenTransfers = CryptoTransferTransactionBody.newBuilder()
			.addTokenTransfers(TokenTransferList.newBuilder()
					.setToken(tokenId)
					.addAllTransfers(List.of(
							adjustFrom(misc, +2_000),
							adjustFrom(rand, -1_000)
					)))
			.build();
	CryptoTransferTransactionBody missingIdTokenTransfers = CryptoTransferTransactionBody.newBuilder()
			.addTokenTransfers(TokenTransferList.newBuilder()
					.setToken(missingId)
					.addAllTransfers(List.of(
							adjustFrom(misc, +1_000),
							adjustFrom(rand, -1_000)
					)))
			.build();
	CryptoTransferTransactionBody multipleValidTokenTransfers = CryptoTransferTransactionBody.newBuilder()
			.addTokenTransfers(TokenTransferList.newBuilder()
					.setToken(frozenId)
					.addAllTransfers(List.of(
							adjustFrom(misc, +1_000),
							adjustFrom(rand, -1_000)
					)))
			.addTokenTransfers(TokenTransferList.newBuilder()
					.setToken(tokenId)
					.addAllTransfers(List.of(
							adjustFrom(misc, +1_000),
							adjustFrom(rand, -1_000)
					)))
			.build();
	CryptoTransferTransactionBody multipleValidTokenTransfersPlusHbars = CryptoTransferTransactionBody.newBuilder()
			.addTokenTransfers(TokenTransferList.newBuilder()
					.setToken(frozenId)
					.addAllTransfers(List.of(
							adjustFrom(misc, +1_000),
							adjustFrom(rand, -1_000)
					)))
			.addTokenTransfers(TokenTransferList.newBuilder()
					.setToken(tokenId)
					.addAllTransfers(List.of(
							adjustFrom(misc, +1_000),
							adjustFrom(rand, -1_000)
					)))
			.setTransfers(TransferList.newBuilder()
					.addAccountAmounts(adjustFrom(rand, +123))
					.addAccountAmounts(adjustFrom(misc, -123))
					.build())
			.build();
	CryptoTransferTransactionBody validTokenXfersPlusInvalidHbarXfers = CryptoTransferTransactionBody.newBuilder()
			.addTokenTransfers(TokenTransferList.newBuilder()
					.setToken(frozenId)
					.addAllTransfers(List.of(
							adjustFrom(misc, +1_000),
							adjustFrom(rand, -1_000)
					)))
			.addTokenTransfers(TokenTransferList.newBuilder()
					.setToken(tokenId)
					.addAllTransfers(List.of(
							adjustFrom(misc, +1_000),
							adjustFrom(rand, -1_000)
					)))
			.setTransfers(TransferList.newBuilder()
					.addAccountAmounts(adjustFrom(rand, +123))
					.addAccountAmounts(adjustFrom(misc, -123))
					.build())
			.build();
}
