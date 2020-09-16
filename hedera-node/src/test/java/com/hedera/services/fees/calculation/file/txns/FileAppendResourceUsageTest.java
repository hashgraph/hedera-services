package com.hedera.services.fees.calculation.file.txns;

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

import static org.junit.jupiter.api.Assertions.*;
import com.google.protobuf.ByteString;
import com.hedera.services.context.primitives.StateView;
import com.hedera.test.utils.IdUtils;
import com.hederahashgraph.api.proto.java.FileAppendTransactionBody;
import com.hederahashgraph.api.proto.java.FileID;
import com.hederahashgraph.api.proto.java.Key;
import com.hederahashgraph.api.proto.java.Timestamp;
import com.hederahashgraph.api.proto.java.TransactionBody;
import com.hederahashgraph.fee.FileFeeBuilder;
import com.hederahashgraph.fee.SigValueObj;
import com.hedera.services.legacy.core.jproto.JFileInfo;
import com.hedera.services.legacy.core.jproto.JKey;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.platform.runner.JUnitPlatform;
import org.junit.runner.RunWith;

import java.util.Optional;

import static org.mockito.BDDMockito.*;

@RunWith(JUnitPlatform.class)
class FileAppendResourceUsageTest {
	private SigValueObj sigUsage;
	private FileFeeBuilder usageEstimator;
	private FileAppendResourceUsage subject;

	StateView view;
	FileID fid = IdUtils.asFile("1.2.3");

	private TransactionBody nonFileAppendTxn;
	private TransactionBody fileAppendTxn;

	@BeforeEach
	private void setup() throws Throwable {
		FileAppendTransactionBody append = mock(FileAppendTransactionBody.class);
		given(append.getFileID()).willReturn(fid);
		fileAppendTxn = mock(TransactionBody.class);
		given(fileAppendTxn.hasFileAppend()).willReturn(true);
		given(fileAppendTxn.getFileAppend()).willReturn(append);

		nonFileAppendTxn = mock(TransactionBody.class);
		given(nonFileAppendTxn.hasFileAppend()).willReturn(false);

		sigUsage = mock(SigValueObj.class);
		usageEstimator = mock(FileFeeBuilder.class);

		view = mock(StateView.class);

		subject = new FileAppendResourceUsage(usageEstimator);
	}

	@Test
	public void recognizesApplicability() {
		// expect:
		assertTrue(subject.applicableTo(fileAppendTxn));
		assertFalse(subject.applicableTo(nonFileAppendTxn));
	}

	@Test
	public void delegatesToCorrectEstimate() throws Exception {
		// setup:
		JKey wacl = JKey.mapKey(Key.newBuilder().setEd25519(ByteString.copyFrom("YUUP".getBytes())).build());
		JFileInfo jInfo = new JFileInfo(false, wacl, Long.MAX_VALUE);
		// and:
		Timestamp expiry = Timestamp.newBuilder().setSeconds(Long.MAX_VALUE).build();

		given(view.attrOf(fid)).willReturn(Optional.of(jInfo));

		// when:
		subject.usageGiven(fileAppendTxn, sigUsage, view);

		// then:
		verify(usageEstimator).getFileAppendTxFeeMatrices(fileAppendTxn, expiry, sigUsage);
	}
}
