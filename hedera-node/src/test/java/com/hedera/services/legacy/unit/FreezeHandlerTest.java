package com.hedera.services.legacy.unit;

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


import com.hedera.services.fees.HbarCentExchange;
import com.hedera.services.files.HederaFs;
import com.hedera.services.legacy.handler.FreezeHandler;
import com.hedera.services.legacy.proto.utils.CommonUtils;
import com.hedera.test.extensions.LogCaptor;
import com.hedera.test.extensions.LogCaptureExtension;
import com.hedera.test.extensions.LoggingSubject;
import com.hederahashgraph.api.proto.java.ExchangeRateSet;
import com.hederahashgraph.api.proto.java.FileID;
import com.hederahashgraph.api.proto.java.ResponseCodeEnum;
import com.hederahashgraph.api.proto.java.Transaction;
import com.hederahashgraph.api.proto.java.TransactionBody;
import com.hederahashgraph.api.proto.java.TransactionRecord;
import com.swirlds.common.NodeId;
import com.swirlds.common.Platform;
import com.swirlds.common.internal.SettingsCommon;
import org.hamcrest.Matchers;
import org.junit.BeforeClass;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInstance;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mockito;

import javax.inject.Inject;
import java.io.File;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.time.Instant;
import java.util.Date;
import java.util.List;

import static com.hederahashgraph.api.proto.java.ResponseCodeEnum.INVALID_FREEZE_TRANSACTION_BODY;
import static java.lang.Thread.sleep;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.stringContainsInOrder;
import static org.hamcrest.collection.IsIterableContainingInOrder.contains;
import static org.mockito.BDDMockito.anyInt;
import static org.mockito.BDDMockito.given;
import static org.mockito.BDDMockito.mock;
import static org.mockito.BDDMockito.willThrow;

@ExtendWith(LogCaptureExtension.class)
class FreezeHandlerTest {
	Instant consensusTime = Instant.ofEpochSecond(1_234_567L);
	HederaFs hfs;
	Platform platform;
	ExchangeRateSet rates;
	HbarCentExchange exchange;

	@Inject
	private LogCaptor logCaptor;
	@LoggingSubject
	private FreezeHandler subject;


	@BeforeEach
	void setUp() {
		SettingsCommon.transactionMaxBytes = 1_234_567;

		hfs = mock(HederaFs.class);
		rates = mock(ExchangeRateSet.class);
		exchange = mock(HbarCentExchange.class);
		given(exchange.activeRates()).willReturn(rates);
		platform = Mockito.mock(Platform.class);

		given(platform.getSelfId()).willReturn(new NodeId(false, 1));

		subject = new FreezeHandler(hfs, platform, exchange);
	}

	@Test
	public void freezeTest() throws Exception {
		Transaction transaction = FreezeTestHelper.createFreezeTransaction(true, true, null);
		TransactionBody txBody = CommonUtils.extractTransactionBody(transaction);
		TransactionRecord record = subject.freeze(txBody, consensusTime);
		Assertions.assertEquals(record.getReceipt().getStatus(), ResponseCodeEnum.SUCCESS);
	}

	@Test
	public void freeze_InvalidFreezeTxBody_Test() throws Exception {
		willThrow(IllegalArgumentException.class).given(platform).setFreezeTime(anyInt(), anyInt(), anyInt(), anyInt());
		Transaction transaction = FreezeTestHelper.createFreezeTransaction(true, false, null);
		TransactionBody txBody = CommonUtils.extractTransactionBody(transaction);
		TransactionRecord record = subject.freeze(txBody, consensusTime);
		Assertions.assertEquals(INVALID_FREEZE_TRANSACTION_BODY, record.getReceipt().getStatus());
	}

	@Test
	public void freeze_updateFeature() throws Exception {
		String zipFile = "src/test/resources/testfiles/updateFeature/update.zip";
		byte[] data = Files.readAllBytes(Paths.get(zipFile));
		byte[] hash = CommonUtils.noThrowSha384HashOf(data);
		FileID fileID = FileID.newBuilder().setShardNum(0L).setRealmNum(0L).setFileNum(150L).build();

		Transaction transaction = FreezeTestHelper.createFreezeTransaction(true, true, fileID, hash);

		given(hfs.exists(fileID)).willReturn(true);
		given(hfs.cat(fileID)).willReturn(data);

		TransactionBody txBody = CommonUtils.extractTransactionBody(transaction);
		TransactionRecord record = subject.freeze(txBody, consensusTime);
		Assertions.assertEquals(record.getReceipt().getStatus(), ResponseCodeEnum.SUCCESS);

		subject.handleUpdateFeature();

		// Wait script to finish
		sleep(2000);

		//check whether new file has been added as expected
		File file3 = new File("new3.txt");
		Assertions.assertTrue(file3.exists());
		file3.delete();
	}

	@Test
	public void freezeOnlyNoUpdateFeature() throws Exception {
		Transaction transaction = FreezeTestHelper.createFreezeTransaction(true, true, null);

		TransactionBody txBody = CommonUtils.extractTransactionBody(transaction);
		TransactionRecord record = subject.freeze(txBody, consensusTime);
		Assertions.assertEquals(record.getReceipt().getStatus(), ResponseCodeEnum.SUCCESS);

		// when:
		subject.handleUpdateFeature();

		// then:
		assertThat(
				logCaptor.infoLogs(),
				contains(
						Matchers.startsWith("Freeze time starts"),
						stringContainsInOrder(List.of("Update file id is not defined"))));
	}

	@Test
	public void freeze_updateAbort_EmptyFile() throws Exception {
		byte[] data = new byte[0];
		byte[] hash = CommonUtils.noThrowSha384HashOf(data);
		FileID fileID = FileID.newBuilder().setShardNum(0L).setRealmNum(0L).setFileNum(150L).build();

		Transaction transaction = FreezeTestHelper.createFreezeTransaction(true, true, fileID, hash);

		given(hfs.exists(fileID)).willReturn(true);
		given(hfs.cat(fileID)).willReturn(data);

		TransactionBody txBody = CommonUtils.extractTransactionBody(transaction);
		TransactionRecord record = subject.freeze(txBody, consensusTime);
		Assertions.assertEquals(record.getReceipt().getStatus(), ResponseCodeEnum.SUCCESS);

		// when:
		subject.handleUpdateFeature();

		// then:
		assertThat(
				logCaptor.errorLogs(),
				contains(
						Matchers.equalTo("NETWORK_UPDATE Node 1 Update file is empty"),
						Matchers.equalTo("NETWORK_UPDATE Node 1 ABORT UPDATE PROCRESS")));
	}

	@Test
	public void freeze_updateFileHash_MisMatch() throws Exception {
		FileID fileID = FileID.newBuilder().setShardNum(0L).setRealmNum(0L).setFileNum(150L).build();

		Transaction transaction = FreezeTestHelper.createFreezeTransaction(true, true, fileID, new byte[48]);

		given(hfs.exists(fileID)).willReturn(true);
		given(hfs.cat(fileID)).willReturn(new byte[100]);

		TransactionBody txBody = CommonUtils.extractTransactionBody(transaction);
		TransactionRecord record = subject.freeze(txBody, consensusTime);
		Assertions.assertEquals(record.getReceipt().getStatus(), ResponseCodeEnum.SUCCESS);

		// when:
		subject.handleUpdateFeature();

		// then:
		assertThat(
				logCaptor.errorLogs(),
				contains(
						stringContainsInOrder(List.of("File hash mismatch")),
						Matchers.startsWith("NETWORK_UPDATE Node 1 Hash from transaction body"),
						Matchers.startsWith("NETWORK_UPDATE Node 1 Hash from file system"),
						Matchers.equalTo("NETWORK_UPDATE Node 1 ABORT UPDATE PROCRESS")));
	}


	@Test
	public void freeze_updateFileID_NonExist() throws Exception {
		FileID fileID = FileID.newBuilder().setShardNum(0L).setRealmNum(0L).setFileNum(150L).build();
		Transaction transaction = FreezeTestHelper.createFreezeTransaction(true, true, fileID, new byte[48]);
		given(hfs.exists(fileID)).willReturn(false);
		given(hfs.cat(fileID)).willReturn(new byte[100]);

		TransactionBody txBody = CommonUtils.extractTransactionBody(transaction);
		TransactionRecord record = subject.freeze(txBody, consensusTime);
		Assertions.assertEquals(record.getReceipt().getStatus(), ResponseCodeEnum.SUCCESS);

		// when:
		subject.handleUpdateFeature();

		// then:
		assertThat(
				logCaptor.errorLogs(),
				contains(stringContainsInOrder(List.of("not found in file system"))));
	}
}
