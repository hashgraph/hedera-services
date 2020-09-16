package com.hedera.services.fees.calculation.consensus.txns;

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

import com.google.common.base.Charsets;
import com.google.protobuf.ByteString;
import com.hederahashgraph.api.proto.java.*;
import com.hederahashgraph.exception.InvalidTxBodyException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.platform.runner.JUnitPlatform;
import org.junit.runner.RunWith;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;
import static org.junit.jupiter.api.Assertions.assertThrows;

@RunWith(JUnitPlatform.class)
class SubmitMessageResourceUsageTest extends TopicResourceUsageTestBase {
	SubmitMessageResourceUsage subject;

	@BeforeEach
	void setup() throws Throwable {
		super.setup();
		subject = new SubmitMessageResourceUsage();
	}

	@Test
	public void recognizesApplicableQuery() {
		// setup:
		TransactionBody submitMessageTopicTx = TransactionBody.newBuilder()
				.setConsensusSubmitMessage(ConsensusSubmitMessageTransactionBody.newBuilder().build())
				.build();
		TransactionBody nonSubmitMessageTopicTx = TransactionBody.newBuilder().build();

		// expect:
		assertTrue(subject.applicableTo(submitMessageTopicTx));
		assertFalse(subject.applicableTo(nonSubmitMessageTopicTx));
	}

	@Test
	public void getFeeThrowsExceptionForBadTxBody() {
		// setup:
		TransactionBody nonSubmitMessageTopicTx = TransactionBody.newBuilder().build();

		// expect:
		assertThrows(InvalidTxBodyException.class, () -> subject.usageGiven(null, sigValueObj, view));
		assertThrows(InvalidTxBodyException.class, () -> subject.usageGiven(nonSubmitMessageTopicTx, sigValueObj,
				view));
	}


	@ParameterizedTest
	@CsvSource({
			"x, 25",  // 24 (topicId) + 1 (message)
			"x12345, 30", // +5 bpt (message)
	})
	public void feeDataAsExpected(String message, int expectedExtraBpt) throws Exception {
		// setup:
		TransactionBody txBody = makeTransactionBody(topicId, ByteString.copyFrom(message, Charsets.UTF_8));

		// when:
		FeeData feeData = subject.usageGiven(txBody, sigValueObj, view);

		// expect:
		int expectedExtraNetworkRbh = 3;  // Extra rbh due to seq number and running hash in receipt
		checkServicesFee(feeData, 0);
		checkNetworkFee(feeData, expectedExtraBpt, expectedExtraNetworkRbh);
		checkNodeFee(feeData, expectedExtraBpt);
	}

	private TransactionBody makeTransactionBody(TopicID topicId, ByteString message) {
		ConsensusSubmitMessageTransactionBody submitMessageTxBodyBuilder =
				ConsensusSubmitMessageTransactionBody.newBuilder().setTopicID(topicId).setMessage(message).build();
		return TransactionBody.newBuilder()
				.setConsensusSubmitMessage(submitMessageTxBodyBuilder)
				.build();
	}
}
