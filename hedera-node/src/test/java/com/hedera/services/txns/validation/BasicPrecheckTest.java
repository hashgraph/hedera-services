package com.hedera.services.txns.validation;

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

import com.hederahashgraph.api.proto.java.AccountID;
import com.hederahashgraph.api.proto.java.Duration;
import com.hederahashgraph.api.proto.java.TransactionBody;
import com.hederahashgraph.api.proto.java.TransactionID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.platform.runner.JUnitPlatform;
import org.junit.runner.RunWith;

import java.time.Instant;

import static com.hedera.services.txns.validation.PureValidationTest.from;
import static com.hedera.test.utils.IdUtils.asAccount;
import static com.hederahashgraph.api.proto.java.ResponseCodeEnum.INSUFFICIENT_TX_FEE;
import static com.hederahashgraph.api.proto.java.ResponseCodeEnum.INVALID_NODE_ACCOUNT;
import static com.hederahashgraph.api.proto.java.ResponseCodeEnum.INVALID_TRANSACTION_DURATION;
import static com.hederahashgraph.api.proto.java.ResponseCodeEnum.INVALID_TRANSACTION_ID;
import static com.hederahashgraph.api.proto.java.ResponseCodeEnum.INVALID_TRANSACTION_START;
import static com.hederahashgraph.api.proto.java.ResponseCodeEnum.MEMO_TOO_LONG;
import static com.hederahashgraph.api.proto.java.ResponseCodeEnum.OK;
import static com.hederahashgraph.api.proto.java.ResponseCodeEnum.PAYER_ACCOUNT_NOT_FOUND;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.BDDMockito.any;
import static org.mockito.BDDMockito.anyLong;
import static org.mockito.BDDMockito.argThat;
import static org.mockito.BDDMockito.given;
import static org.mockito.BDDMockito.longThat;
import static org.mockito.BDDMockito.mock;

@RunWith(JUnitPlatform.class)
class BasicPrecheckTest {
	int validityBufferOverride = 7;
	AccountID node = asAccount("0.0.3");
	AccountID payer = asAccount("0.0.13257");
	long duration = 1_234;
	Instant startTime = Instant.now();
	TransactionID txnId = TransactionID.newBuilder()
			.setAccountID(payer)
			.setTransactionValidStart(from(startTime.getEpochSecond(), startTime.getNano()))
			.build();
	String memo = "Our souls, which to advance their state / Were gone out, hung twixt her and me.";
	TransactionBody txn;

	OptionValidator validator;

	BasicPrecheck subject;

	@BeforeEach
	private void setup() {
		validator = mock(OptionValidator.class);

		given(validator.isValidTxnDuration(anyLong())).willReturn(true);
		given(validator.isPlausibleTxnFee(anyLong())).willReturn(true);
		given(validator.isPlausibleAccount(node)).willReturn(true);
		given(validator.isPlausibleAccount(payer)).willReturn(true);
		given(validator.isValidEntityMemo(memo)).willReturn(true);
		given(validator.chronologyStatusForTxn(any(), anyLong(), any())).willReturn(OK);

		subject = new BasicPrecheck(validator);
		subject.setMinValidityBufferSecs(validityBufferOverride);

		txn = TransactionBody.newBuilder()
				.setTransactionID(txnId)
				.setTransactionValidDuration(Duration.newBuilder().setSeconds(duration))
				.setNodeAccountID(node)
				.setMemo(memo)
				.build();
	}

	@Test
	public void assertsValidDuration() {
		given(validator.isValidTxnDuration(anyLong())).willReturn(false);

		// when:
		var status = subject.validate(txn);

		// then:
		assertEquals(INVALID_TRANSACTION_DURATION, status);
	}

	@Test
	public void assertsValidChronology() {
		given(validator.chronologyStatusForTxn(
				argThat(startTime::equals),
				longThat(l -> l == (duration - validityBufferOverride)),
				any())).willReturn(INVALID_TRANSACTION_START);

		// when:
		var status = subject.validate(txn);

		// then:
		assertEquals(INVALID_TRANSACTION_START, status);
	}

	@Test
	public void assertsExtantTransactionId() {
		// when:
		var status = subject.validate(TransactionBody.getDefaultInstance());

		// then:
		assertEquals(INVALID_TRANSACTION_ID, status);
	}

	@Test
	public void assertsPlausibleTxnFee() {
		given(validator.isPlausibleTxnFee(anyLong())).willReturn(false);

		// when:
		var status = subject.validate(txn);

		// then:
		assertEquals(INSUFFICIENT_TX_FEE, status);
	}

	@Test
	public void assertsPlausiblePayer() {
		given(validator.isPlausibleAccount(payer)).willReturn(false);

		// when:
		var status = subject.validate(txn);

		// then:
		assertEquals(PAYER_ACCOUNT_NOT_FOUND, status);
	}

	@Test
	public void assertsPlausibleNode() {
		given(validator.isPlausibleAccount(node)).willReturn(false);

		// when:
		var status = subject.validate(txn);

		// then:
		assertEquals(INVALID_NODE_ACCOUNT, status);
	}

	@Test
	public void assertsValidMemo() {
		given(validator.isValidEntityMemo(memo)).willReturn(false);

		// when:
		var status = subject.validate(txn);

		// then:
		assertEquals(MEMO_TOO_LONG, status);
	}
}
