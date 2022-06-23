package com.hedera.services.usage.util;

/*-
 * ‌
 * Hedera Services API Fees
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

import com.google.protobuf.ByteString;
import com.hedera.services.usage.BaseTransactionMeta;
import com.hedera.services.usage.SigUsage;
import com.hedera.services.usage.state.UsageAccumulator;
import com.hederahashgraph.api.proto.java.RandomGenerateTransactionBody;
import com.hederahashgraph.api.proto.java.SignatureMap;
import com.hederahashgraph.api.proto.java.SignaturePair;
import com.hederahashgraph.api.proto.java.Timestamp;
import com.hederahashgraph.api.proto.java.TransactionBody;
import com.hederahashgraph.api.proto.java.TransactionID;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

class UtilOpsUsageTest {
	private static final long now = 1_234_567L;
	private UtilOpsUsage subject = new UtilOpsUsage();
	@Test
	void estimatesAutoRenewAsExpected() {
		final var op = RandomGenerateTransactionBody.newBuilder()
				.setRange(10)
				.build();
		final var txn = TransactionBody.newBuilder()
				.setTransactionID(TransactionID.newBuilder()
						.setTransactionValidStart(Timestamp.newBuilder()
								.setSeconds(now)))
				.setRandomGenerate(op).build();

		final ByteString canonicalSig = ByteString.copyFromUtf8(
				"0123456789012345678901234567890123456789012345678901234567890123");
		final SignatureMap onePairSigMap = SignatureMap.newBuilder()
				.addSigPair(SignaturePair.newBuilder()
						.setPubKeyPrefix(ByteString.copyFromUtf8("a"))
						.setEd25519(canonicalSig))
				.build();
		final SigUsage singleSigUsage = new SigUsage(
				1, onePairSigMap.getSerializedSize(), 1);
		final var opMeta = new RandomGenerateMeta(txn.getRandomGenerate());
		final var baseMeta = new BaseTransactionMeta(0, 0);

		var actual = new UsageAccumulator();
		var expected = new UsageAccumulator();

		var msgBytes = txn.getRandomGenerate().getSerializedSize();
		expected.resetForTransaction(baseMeta, singleSigUsage);
		expected.addBpt(msgBytes);

		subject.randomGenerateUsage(singleSigUsage, baseMeta, opMeta, actual);

		assertEquals(expected, actual);
	}
}
