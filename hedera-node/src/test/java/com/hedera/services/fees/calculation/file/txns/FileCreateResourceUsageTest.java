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

import com.hederahashgraph.api.proto.java.TransactionBody;
import com.hederahashgraph.fee.FileFeeBuilder;
import com.hederahashgraph.fee.SigValueObj;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.platform.runner.JUnitPlatform;
import org.junit.runner.RunWith;
import static org.mockito.BDDMockito.*;

@RunWith(JUnitPlatform.class)
class FileCreateResourceUsageTest {
	private SigValueObj sigUsage;
	private FileFeeBuilder usageEstimator;
	private FileCreateResourceUsage subject;

	private TransactionBody nonFileCreateTxn;
	private TransactionBody fileCreateTxn;

	@BeforeEach
	private void setup() throws Throwable {
		fileCreateTxn = mock(TransactionBody.class);
		given(fileCreateTxn.hasFileCreate()).willReturn(true);

		nonFileCreateTxn = mock(TransactionBody.class);
		given(nonFileCreateTxn.hasFileCreate()).willReturn(false);

		sigUsage = mock(SigValueObj.class);
		usageEstimator = mock(FileFeeBuilder.class);

		subject = new FileCreateResourceUsage(usageEstimator);
	}

	@Test
	public void recognizesApplicability() {
		// expect:
		assertTrue(subject.applicableTo(fileCreateTxn));
		assertFalse(subject.applicableTo(nonFileCreateTxn));
	}

	@Test
	public void delegatesToCorrectEstimate() throws Exception {
		// when:
		subject.usageGiven(fileCreateTxn, sigUsage, null);

		// then:
		verify(usageEstimator).getFileCreateTxFeeMatrices(fileCreateTxn, sigUsage);
	}
}
