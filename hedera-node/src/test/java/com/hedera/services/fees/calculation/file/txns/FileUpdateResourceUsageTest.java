package com.hedera.services.fees.calculation.file.txns;

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

import com.google.protobuf.ByteString;
import com.hedera.services.context.primitives.StateView;
import com.hedera.services.fees.calculation.UsageEstimatorUtils;
import com.hedera.test.factories.scenarios.TxnHandlingScenario;
import com.hedera.test.utils.IdUtils;
import com.hederahashgraph.api.proto.java.FeeComponents;
import com.hederahashgraph.api.proto.java.FileGetInfoResponse;
import com.hederahashgraph.api.proto.java.FileID;
import com.hederahashgraph.api.proto.java.FileUpdateTransactionBody;
import com.hederahashgraph.api.proto.java.KeyList;
import com.hederahashgraph.api.proto.java.Timestamp;
import com.hederahashgraph.api.proto.java.TransactionBody;
import com.hederahashgraph.api.proto.java.TransactionID;
import com.hederahashgraph.fee.FeeBuilder;
import com.hederahashgraph.fee.FileFeeBuilder;
import com.hederahashgraph.fee.SigValueObj;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.Optional;

import static com.hedera.services.fees.calculation.UsageEstimatorUtils.defaultPartitioning;
import static com.hedera.services.fees.calculation.UsageEstimatorUtils.withBaseTxnUsage;
import static com.hedera.services.fees.calculation.UsageEstimatorUtils.zeroedComponents;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.BDDMockito.given;
import static org.mockito.BDDMockito.mock;
import static org.mockito.BDDMockito.verify;

class FileUpdateResourceUsageTest {
	private SigValueObj sigUsage;
	private FileUpdateResourceUsage subject;

	StateView view;
	FileID fid = IdUtils.asFile("1.2.3");

	private TransactionBody nonFileUpdateTxn;
	private TransactionBody fileUpdateTxn;

	@BeforeEach
	private void setup() throws Throwable {
		FileUpdateTransactionBody update = mock(FileUpdateTransactionBody.class);
		given(update.getFileID()).willReturn(fid);
		fileUpdateTxn = mock(TransactionBody.class);
		given(fileUpdateTxn.hasFileUpdate()).willReturn(true);
		given(fileUpdateTxn.getFileUpdate()).willReturn(update);

		nonFileUpdateTxn = mock(TransactionBody.class);
		given(nonFileUpdateTxn.hasFileUpdate()).willReturn(false);

		sigUsage = mock(SigValueObj.class);

		view = mock(StateView.class);

		subject = new FileUpdateResourceUsage();
	}

	@Test
	public void recognizesApplicability() {
		// expect:
		assertTrue(subject.applicableTo(fileUpdateTxn));
		assertFalse(subject.applicableTo(nonFileUpdateTxn));
	}

	@Test
	public void understandsOpSize() {
		// given:
		var op = FileUpdateTransactionBody.newBuilder()
				.setFileID(fid)
				.setContents(ByteString.copyFrom("Though like waves breaking it may be".getBytes()))
				.setKeys(KeyList.newBuilder()
						.addKeys(TxnHandlingScenario.COMPLEX_KEY_ACCOUNT_KT.asKey())
						.addKeys(TxnHandlingScenario.MISC_ACCOUNT_KT.asKey())
						.build())
				.setExpirationTime(Timestamp.newBuilder().setSeconds(1_234_567L))
				.build();
		int expected = FileFeeBuilder.getFileUpdateBodyTxSize(TransactionBody.newBuilder().setFileUpdate(op).build());

		// when:
		int actual = subject.opBytes(op);

		// then:
		assertEquals(expected, actual);
	}

	@Test
	public void addsOpUsage() {
		// setup:
		long now = 500_000;
		long oldExpiry = 1_000_000, newExpiry = 2_000_000;
		long oldSize = 10;
		// and:
		var info = FileGetInfoResponse.FileInfo.newBuilder()
				.setExpirationTime(Timestamp.newBuilder().setSeconds(oldExpiry))
				.setKeys(TxnHandlingScenario.MISC_FILE_WACL_KT.asKey().getKeyList())
				.setSize(oldSize)
				.build();
		// and:
		long oldBytes = oldSize + UsageEstimatorUtils.keyBytes(info.getKeys().getKeysList());

		given(view.infoForFile(fid)).willReturn(Optional.of(info));
		// and:
		var op = FileUpdateTransactionBody.newBuilder()
				.setFileID(fid)
				.setContents(ByteString.copyFrom("Though like waves breaking it may be".getBytes()))
				.setKeys(KeyList.newBuilder()
						.addKeys(TxnHandlingScenario.COMPLEX_KEY_ACCOUNT_KT.asKey())
						.addKeys(TxnHandlingScenario.MISC_ACCOUNT_KT.asKey())
						.build())
				.setExpirationTime(Timestamp.newBuilder().setSeconds(newExpiry))
				.build();
		FeeComponents.Builder components = mock(FeeComponents.Builder.class);
		given(components.getBpt()).willReturn(123L);
		// and:
		long newBytes = op.getContents().size() + UsageEstimatorUtils.keyBytes(op.getKeys().getKeysList());

		// when:
		subject.withOpUsage(components, op, view, now);
		// and:
		long sbhDelta = UsageEstimatorUtils.nonDegenerateDiv(
				UsageEstimatorUtils.changeInSbsUsage(
						oldBytes, oldExpiry - now,
						newBytes, newExpiry - now),
				FeeBuilder.HRS_DIVISOR);

		// then:
		verify(components).setBpt(123L + subject.opBytes(op));
		verify(components).setSbh(sbhDelta);
	}

	@Test
	public void asExpected() {
		// setup:
		long now = 500_000;
		long oldExpiry = 1_000_000, newExpiry = 2_000_000;
		long oldSize = 10;
		// and:
		var info = FileGetInfoResponse.FileInfo.newBuilder()
				.setExpirationTime(Timestamp.newBuilder().setSeconds(oldExpiry))
				.setKeys(TxnHandlingScenario.MISC_FILE_WACL_KT.asKey().getKeyList())
				.setSize(oldSize)
				.build();
		// and:
		long oldBytes = oldSize + UsageEstimatorUtils.keyBytes(info.getKeys().getKeysList());

		given(view.infoForFile(fid)).willReturn(Optional.of(info));
		// and:
		var op = FileUpdateTransactionBody.newBuilder()
				.setFileID(fid)
				.setContents(ByteString.copyFrom("Though like waves breaking it may be".getBytes()))
				.setKeys(KeyList.newBuilder()
						.addKeys(TxnHandlingScenario.COMPLEX_KEY_ACCOUNT_KT.asKey())
						.addKeys(TxnHandlingScenario.MISC_ACCOUNT_KT.asKey())
						.build())
				.setExpirationTime(Timestamp.newBuilder().setSeconds(newExpiry))
				.build();
		// and:
		var txn = TransactionBody.newBuilder()
				.setTransactionID(TransactionID.newBuilder()
						.setTransactionValidStart(Timestamp.newBuilder().setSeconds(now)))
				.setFileUpdate(op)
				.build();
		// and:
		var sigUsage = new SigValueObj(5, 10, 100);
		var components = subject.withOpUsage(withBaseTxnUsage(zeroedComponents(), sigUsage, txn), op, view, now);
		var expected = defaultPartitioning(components.build(), 10);

		// then:
		assertEquals(expected, subject.usageGiven(txn, sigUsage, view));
	}
}
