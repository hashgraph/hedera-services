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


import com.hedera.services.files.HederaFs;
import com.hedera.services.legacy.handler.FreezeHandler;
import com.hederahashgraph.api.proto.java.FileID;
import com.hederahashgraph.api.proto.java.ResponseCodeEnum;
import com.hederahashgraph.api.proto.java.Transaction;
import com.hederahashgraph.api.proto.java.TransactionBody;
import com.hederahashgraph.api.proto.java.TransactionRecord;
import com.hedera.services.state.merkle.MerkleBlobMeta;
import com.hedera.services.state.merkle.MerkleOptionalBlob;
import com.hedera.services.legacy.proto.utils.CommonUtils;
import com.swirlds.common.Platform;
import com.swirlds.common.internal.SettingsCommon;
import com.swirlds.fcmap.FCMap;
import org.junit.BeforeClass;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInstance;
import org.junit.platform.runner.JUnitPlatform;
import org.junit.runner.RunWith;
import org.mockito.Mockito;

import java.io.File;
import java.nio.file.Files;
import java.nio.file.Paths;

import java.time.Instant;
import java.util.Date;


import static com.hederahashgraph.api.proto.java.ResponseCodeEnum.INVALID_FREEZE_TRANSACTION_BODY;

import static org.mockito.BDDMockito.*;

@RunWith(JUnitPlatform.class)
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
public class FreezeHandlerTest {
	Platform platform;
	FreezeHandler freezeHandler;
	Instant consensusTime;
	private HederaFs hfs;

	private FCMap<MerkleBlobMeta, MerkleOptionalBlob> storageMap = null;

	@BeforeAll
	@BeforeClass
	public static void setupAll() {
		SettingsCommon.transactionMaxBytes = 1_234_567;
	}

	@BeforeAll
	public void init() {
		consensusTime = new Date().toInstant();
	}

	@Test
	public void freezeTest() throws Exception {
		hfs = mock(HederaFs.class);
		platform = Mockito.mock(Platform.class);
		freezeHandler = new FreezeHandler(hfs, platform);
		Transaction transaction = FreezeTestHelper.createFreezeTransaction(true, true, null);
		TransactionBody txBody = CommonUtils.extractTransactionBody(transaction);
		TransactionRecord record = freezeHandler.freeze(txBody, consensusTime);
		Assertions.assertEquals(record.getReceipt().getStatus(), ResponseCodeEnum.SUCCESS);
	}

	@Test
	public void freeze_InvalidFreezeTxBody_Test() throws Exception {
		hfs = mock(HederaFs.class);
		platform = Mockito.mock(Platform.class);
		willThrow(IllegalArgumentException.class).given(platform).setFreezeTime(anyInt(), anyInt(), anyInt(), anyInt());
		freezeHandler = new FreezeHandler(hfs, platform);
		Transaction transaction = FreezeTestHelper.createFreezeTransaction(true, false, null);
		TransactionBody txBody = CommonUtils.extractTransactionBody(transaction);
		TransactionRecord record = freezeHandler.freeze(txBody, consensusTime);
		Assertions.assertEquals(INVALID_FREEZE_TRANSACTION_BODY, record.getReceipt().getStatus());
	}

	@Test
	public void freeze_updateFeature() throws Exception {
		String zipFile = "src/test/resources/testfiles/updateFeature/update.zip";
		byte[] data = Files.readAllBytes(Paths.get(zipFile));

		hfs = mock(HederaFs.class);
		platform = Mockito.mock(Platform.class);
		freezeHandler = new FreezeHandler(hfs, platform);
		FileID fileID = FileID.newBuilder().setShardNum(0L).setRealmNum(0L).setFileNum(2000L).build();

		Transaction transaction = FreezeTestHelper.createFreezeTransaction(true, true, fileID);

		given(hfs.exists(fileID)).willReturn(true);
		given(hfs.cat(fileID)).willReturn(data);

		TransactionBody txBody = CommonUtils.extractTransactionBody(transaction);
		TransactionRecord record = freezeHandler.freeze(txBody, consensusTime);
		Assertions.assertEquals(record.getReceipt().getStatus(), ResponseCodeEnum.SUCCESS);

		freezeHandler.handleUpdateFeature();

		//check whether file has been deleted as expected
		File file1 = new File("new1.txt");
		Assertions.assertFalse(file1.exists());

		File file2 = new File("new1.txt");
		Assertions.assertFalse(file2.exists());

		//check whether new file has been added as expected
		File file3 = new File("new3.txt");
		Assertions.assertTrue(file3.exists());
		file3.delete();
	}
}
