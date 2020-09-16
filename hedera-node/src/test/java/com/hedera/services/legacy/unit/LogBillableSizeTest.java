package com.hedera.services.legacy.unit;

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

import com.hedera.services.legacy.handler.SmartContractRequestHandler;
import org.apache.commons.lang3.RandomUtils;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;
import org.junit.platform.runner.JUnitPlatform;
import org.junit.runner.RunWith;
import com.google.protobuf.ByteString;
import com.hederahashgraph.api.proto.java.ContractFunctionResult;
import com.hederahashgraph.api.proto.java.ContractID;
import com.hederahashgraph.api.proto.java.ContractLoginfo;
import com.hederahashgraph.api.proto.java.TransactionRecord;

@RunWith(JUnitPlatform.class)
public class LogBillableSizeTest {
	private static final int BLOOM_SIZE = 256;
	private static final int TOPIC_SIZE = 32;
	private static final int CONTRACT_ID_SIZE = 24;

	@Test
	void testNoLogsRecord() {
		TransactionRecord.Builder recBuilder = TransactionRecord.newBuilder();

		ContractFunctionResult.Builder funcResultsBuilder = ContractFunctionResult.newBuilder();
		funcResultsBuilder.setContractID(ContractID.newBuilder().setContractNum(9999).setShardNum(888).setRealmNum(777));
		recBuilder.setContractCallResult(funcResultsBuilder);
		TransactionRecord recordToTest = recBuilder.build();
		long billableLogsSize = SmartContractRequestHandler.getLogsBillableSize(recordToTest);
		Assertions.assertEquals(0, billableLogsSize);
	}

	@Test
	void testLogsRecordOneTopicOneLine() {
		TransactionRecord.Builder recBuilder = TransactionRecord.newBuilder();

		ContractFunctionResult.Builder funcResultsBuilder = ContractFunctionResult.newBuilder();
		funcResultsBuilder.setContractID(ContractID.newBuilder().setContractNum(9999).setShardNum(888).setRealmNum(777));
		ContractLoginfo.Builder logBuilder = ContractLoginfo.newBuilder();
		byte[] dataBytes = RandomUtils.nextBytes(100);
		byte[] topic1Bytes = RandomUtils.nextBytes(32);
		logBuilder.setData(ByteString.copyFrom(dataBytes));
		logBuilder.addTopic(ByteString.copyFrom(topic1Bytes));
		logBuilder.setContractID(ContractID.newBuilder().setContractNum(444).setShardNum(888).setRealmNum(777).build());
		funcResultsBuilder.addLogInfo(logBuilder);
		recBuilder.setContractCallResult(funcResultsBuilder);
		TransactionRecord recordToTest = recBuilder.build();
		long expectedBillableSize = CONTRACT_ID_SIZE + BLOOM_SIZE + TOPIC_SIZE + dataBytes.length;
		long billableLogsSize = SmartContractRequestHandler.getLogsBillableSize(recordToTest);
		Assertions.assertEquals(expectedBillableSize, billableLogsSize);
	}

	@Test
	void testLogsRecordMultiTopicMultiLine() {
		TransactionRecord.Builder recBuilder = TransactionRecord.newBuilder();

		ContractFunctionResult.Builder funcResultsBuilder = ContractFunctionResult.newBuilder();
		funcResultsBuilder.setContractID(ContractID.newBuilder().setContractNum(9999).setShardNum(888).setRealmNum(777));
		ContractLoginfo.Builder logBuilderLine1 = ContractLoginfo.newBuilder();
		byte[] line1dataBytes = RandomUtils.nextBytes(100);
		byte[] line1topic1Bytes = RandomUtils.nextBytes(32);
		logBuilderLine1.setData(ByteString.copyFrom(line1dataBytes));
		logBuilderLine1.addTopic(ByteString.copyFrom(line1topic1Bytes));
		logBuilderLine1.setContractID(
				ContractID.newBuilder().setContractNum(444).setShardNum(888).setRealmNum(777).build());

		ContractLoginfo.Builder logBuilderLine2 = ContractLoginfo.newBuilder();
		byte[] line2dataBytes = RandomUtils.nextBytes(200);
		byte[] line2topic1Bytes = RandomUtils.nextBytes(32);
		byte[] line2topic2Bytes = RandomUtils.nextBytes(32);
		logBuilderLine2.setData(ByteString.copyFrom(line2dataBytes));
		logBuilderLine2.addTopic(ByteString.copyFrom(line2topic1Bytes));
		logBuilderLine2.addTopic(ByteString.copyFrom(line2topic2Bytes));
		logBuilderLine2.setContractID(
				ContractID.newBuilder().setContractNum(444).setShardNum(888).setRealmNum(777).build());

		ContractLoginfo.Builder logBuilderLine3 = ContractLoginfo.newBuilder();
		byte[] line3topic1Bytes = RandomUtils.nextBytes(32);
		byte[] line3topic2Bytes = RandomUtils.nextBytes(32);
		byte[] line3topic3Bytes = RandomUtils.nextBytes(32);
		logBuilderLine3.addTopic(ByteString.copyFrom(line3topic1Bytes));
		logBuilderLine3.addTopic(ByteString.copyFrom(line3topic2Bytes));
		logBuilderLine3.addTopic(ByteString.copyFrom(line3topic3Bytes));
		logBuilderLine3.setContractID(
				ContractID.newBuilder().setContractNum(444).setShardNum(888).setRealmNum(777).build());

		funcResultsBuilder.addLogInfo(logBuilderLine1);
		funcResultsBuilder.addLogInfo(logBuilderLine2);
		funcResultsBuilder.addLogInfo(logBuilderLine3);

		recBuilder.setContractCallResult(funcResultsBuilder);
		TransactionRecord recordToTest = recBuilder.build();
		long logLine1ExpectedSize = CONTRACT_ID_SIZE + BLOOM_SIZE + TOPIC_SIZE + line1dataBytes.length;
		long logLine2ExpectedSize = CONTRACT_ID_SIZE + BLOOM_SIZE + TOPIC_SIZE * 2 + line2dataBytes.length;
		long logLine3ExpectedSize = CONTRACT_ID_SIZE + BLOOM_SIZE + TOPIC_SIZE * 3;
		long expectedBillableSize = logLine1ExpectedSize + logLine2ExpectedSize + logLine3ExpectedSize;
		long billableLogsSize = SmartContractRequestHandler.getLogsBillableSize(recordToTest);
		Assertions.assertEquals(expectedBillableSize, billableLogsSize);
	}
}
