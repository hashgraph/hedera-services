package com.hedera.services.txns.token;

/*-
 * ‌
 * Hedera Services Node
 * ​
 * Copyright (C) 2018 - 2020 Hedera Hashgraph, LLC
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

import com.hedera.services.context.TransactionContext;
import com.hedera.services.ledger.HederaLedger;
import com.hedera.services.utils.PlatformTxnAccessor;
import com.hedera.test.utils.IdUtils;
import com.hederahashgraph.api.proto.java.TokenRefTransferList;
import com.hederahashgraph.api.proto.java.TokenTransfers;
import com.hederahashgraph.api.proto.java.TransactionBody;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.platform.runner.JUnitPlatform;
import org.junit.runner.RunWith;

import java.util.List;

import static com.hedera.test.utils.IdUtils.adjustFrom;
import static com.hedera.test.utils.IdUtils.asAccount;
import static com.hedera.test.utils.IdUtils.refWith;
import static com.hederahashgraph.api.proto.java.ResponseCodeEnum.FAIL_INVALID;
import static com.hederahashgraph.api.proto.java.ResponseCodeEnum.OK;
import static com.hederahashgraph.api.proto.java.ResponseCodeEnum.SUCCESS;
import static com.hederahashgraph.api.proto.java.ResponseCodeEnum.TRANSFERS_NOT_ZERO_SUM_FOR_TOKEN;
import static junit.framework.TestCase.assertTrue;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.mockito.BDDMockito.any;
import static org.mockito.BDDMockito.given;
import static org.mockito.BDDMockito.mock;
import static org.mockito.BDDMockito.verify;

@RunWith(JUnitPlatform.class)
class TokenTransactTransitionLogicTest {
	TokenTransfers xfers = TokenTransfers.newBuilder()
			.addTokenTransfers(TokenRefTransferList.newBuilder()
					.setToken(refWith("NOTHBAR"))
					.addAllTransfers(List.of(
							adjustFrom(asAccount("0.0.2"), -1_000),
							adjustFrom(asAccount("0.0.3"), +1_000)
					)))
			.build();

	private HederaLedger ledger;
	private TransactionContext txnCtx;
	private PlatformTxnAccessor accessor;

	private TransactionBody tokenTransactTxn;
	private TokenTransactTransitionLogic subject;

	@BeforeEach
	private void setup() {
		ledger = mock(HederaLedger.class);
		accessor = mock(PlatformTxnAccessor.class);

		txnCtx = mock(TransactionContext.class);

		subject = new TokenTransactTransitionLogic(ledger, txnCtx);
	}

	@Test
	public void capturesInvalidXfers() {
		givenValidTxnCtx();
		// and:
		given(ledger.doAtomicZeroSumTokenTransfers(xfers)).willReturn(TRANSFERS_NOT_ZERO_SUM_FOR_TOKEN);

		// when:
		subject.doStateTransition();

		// then:
		verify(txnCtx).setStatus(TRANSFERS_NOT_ZERO_SUM_FOR_TOKEN);
	}

	@Test
	public void followsHappyPath() {
		givenValidTxnCtx();
		// and:
		given(ledger.doAtomicZeroSumTokenTransfers(xfers)).willReturn(OK);

		// when:
		subject.doStateTransition();

		// then:
		verify(ledger).doAtomicZeroSumTokenTransfers(xfers);
		verify(txnCtx).setStatus(SUCCESS);
	}

	@Test
	public void hasCorrectApplicability() {
		givenValidTxnCtx();

		// expect:
		assertTrue(subject.applicability().test(tokenTransactTxn));
		assertFalse(subject.applicability().test(TransactionBody.getDefaultInstance()));
	}

	@Test
	public void setsFailInvalidIfUnhandledException() {
		givenValidTxnCtx();
		// and:
		given(ledger.doAtomicZeroSumTokenTransfers(any())).willThrow(IllegalArgumentException.class);

		// when:
		subject.doStateTransition();

		// then:
		verify(txnCtx).setStatus(FAIL_INVALID);
	}

	private void givenValidTxnCtx() {
		tokenTransactTxn = TransactionBody.newBuilder()
				.setTokenTransfers(xfers)
				.build();
		given(accessor.getTxn()).willReturn(tokenTransactTxn);
		given(txnCtx.accessor()).willReturn(accessor);
	}
}