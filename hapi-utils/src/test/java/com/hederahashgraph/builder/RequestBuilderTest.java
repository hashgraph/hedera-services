package com.hederahashgraph.builder;

/*-
 * ‌
 * Hedera Services API Utilities
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

import com.google.protobuf.InvalidProtocolBufferException;
import com.hederahashgraph.api.proto.java.FileID;
import com.hederahashgraph.api.proto.java.Timestamp;
import com.hederahashgraph.api.proto.java.TransactionBody;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.time.Instant;

class RequestBuilderTest {
	@Test
	void testExpirationTime() {
		final var seconds = 500L;
		final var duration = RequestBuilder.getDuration(seconds);
		final var now = Instant.now();

		final var expirationTime = RequestBuilder.getExpirationTime(now, duration);
		Assertions.assertNotNull(expirationTime);

		final var expirationInstant = RequestBuilder.convertProtoTimeStamp(expirationTime);
		final var between = Duration.between(now, expirationInstant);
		Assertions.assertEquals(seconds, between.getSeconds());
	}

	@Test
	void testGetFileDeleteBuilder() throws InvalidProtocolBufferException {
		final var payerAccountNum = 1L;
		final var realmNum = 0L;
		final var shardNum = 0L;
		final var nodeAccountNum = 2L;
		final var fileNo = 3L;
		final var transactionFee = 100L;
		final var timestamp = Timestamp.newBuilder().setSeconds(500L).setNanos(500).build();
		final var duration = RequestBuilder.getDuration(500L);
		final var generateRecord = false;
		final var memo = "Just saying...";
		final var fileId = FileID.newBuilder()
				.setFileNum(fileNo)
				.setRealmNum(realmNum)
				.setShardNum(shardNum)
				.build();

		final var transaction = RequestBuilder.getFileDeleteBuilder(payerAccountNum, realmNum, shardNum,
				nodeAccountNum, realmNum, shardNum, transactionFee, timestamp, duration, generateRecord, memo, fileId);
		final var transactionBody = TransactionBody.parseFrom(transaction.getBodyBytes());

		Assertions.assertEquals(fileId, transactionBody.getFileDelete().getFileID());
		Assertions.assertEquals(payerAccountNum, transactionBody.getTransactionID().getAccountID().getAccountNum());
		Assertions.assertEquals(timestamp, transactionBody.getTransactionID().getTransactionValidStart());
		Assertions.assertEquals(realmNum, transactionBody.getTransactionID().getAccountID().getRealmNum());
		Assertions.assertEquals(shardNum, transactionBody.getTransactionID().getAccountID().getShardNum());
		Assertions.assertEquals(nodeAccountNum, transactionBody.getNodeAccountID().getAccountNum());
		Assertions.assertEquals(duration, transactionBody.getTransactionValidDuration());
		Assertions.assertEquals(generateRecord, transactionBody.getGenerateRecord());
		Assertions.assertEquals(memo, transactionBody.getMemo());
	}
}
