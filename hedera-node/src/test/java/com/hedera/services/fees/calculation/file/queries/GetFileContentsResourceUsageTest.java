package com.hedera.services.fees.calculation.file.queries;

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

import com.hedera.services.context.primitives.StateView;
import com.hedera.test.factories.scenarios.TxnHandlingScenario;
import com.hederahashgraph.api.proto.java.FeeData;
import com.hederahashgraph.api.proto.java.FileGetContentsQuery;
import com.hederahashgraph.api.proto.java.FileGetInfoResponse;
import com.hederahashgraph.api.proto.java.FileID;
import com.hederahashgraph.api.proto.java.Key;
import com.hederahashgraph.api.proto.java.Query;
import com.hederahashgraph.api.proto.java.QueryHeader;
import com.hederahashgraph.api.proto.java.ResponseType;
import com.hederahashgraph.fee.FileFeeBuilder;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.Optional;
import static com.hedera.test.utils.IdUtils.asFile;
import static com.hederahashgraph.api.proto.java.ResponseType.ANSWER_ONLY;
import static com.hederahashgraph.api.proto.java.ResponseType.COST_ANSWER;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.mock;

class GetFileContentsResourceUsageTest {
	FileID target = asFile("0.0.123");
	StateView view;
	FileFeeBuilder usageEstimator;
	GetFileContentsResourceUsage subject;
	Key wacl = TxnHandlingScenario.MISC_FILE_WACL_KT.asKey();
	long fileSize = 1_234;
	FileGetInfoResponse.FileInfo targetInfo = FileGetInfoResponse.FileInfo.newBuilder()
			.setSize(fileSize)
			.build();

	@BeforeEach
	private void setup() throws Throwable {
		usageEstimator = mock(FileFeeBuilder.class);
		view = mock(StateView.class);

		subject = new GetFileContentsResourceUsage(usageEstimator);
	}

	@Test
	public void returnsDefaultSchedulesOnMissing() {
		Query answerOnlyQuery = fileContentsQuery(target, ANSWER_ONLY);

		given(view.infoForFile(any())).willReturn(Optional.empty());

		// then:
		assertSame(FeeData.getDefaultInstance(), subject.usageGiven(answerOnlyQuery, view));
	}

	@Test
	public void invokesEstimatorAsExpectedForType() {
		// setup:
		FeeData costAnswerUsage = mock(FeeData.class);
		FeeData answerOnlyUsage = mock(FeeData.class);

		// given:
		Query answerOnlyQuery = fileContentsQuery(target, ANSWER_ONLY);
		Query costAnswerQuery = fileContentsQuery(target, COST_ANSWER);
		// and:
		given(view.infoForFile(target)).willReturn(Optional.ofNullable(targetInfo));
		// and:
		given(usageEstimator.getFileContentQueryFeeMatrices((int)fileSize, COST_ANSWER))
				.willReturn(costAnswerUsage);
		given(usageEstimator.getFileContentQueryFeeMatrices((int)fileSize, ANSWER_ONLY))
				.willReturn(answerOnlyUsage);

		// when:
		FeeData costAnswerEstimate = subject.usageGiven(costAnswerQuery, view);
		FeeData answerOnlyEstimate = subject.usageGiven(answerOnlyQuery, view);

		// then:
		assertTrue(costAnswerEstimate == costAnswerUsage);
		assertTrue(answerOnlyEstimate == answerOnlyUsage);
	}

	@Test
	public void recognizesApplicableQuery() {
		// given:
		Query fileContentsQuery = fileContentsQuery(target, COST_ANSWER);
		Query nonFileContentsQuery = nonFileContentsQuery();

		// expect:
		assertTrue(subject.applicableTo(fileContentsQuery));
		assertFalse(subject.applicableTo(nonFileContentsQuery));
	}

	private Query fileContentsQuery(FileID id, ResponseType type) {
		FileGetContentsQuery.Builder op = FileGetContentsQuery.newBuilder()
				.setFileID(id)
				.setHeader(QueryHeader.newBuilder().setResponseType(type));
		return Query.newBuilder()
				.setFileGetContents(op)
				.build();
	}

	private Query nonFileContentsQuery() {
		return Query.newBuilder().build();
	}
}
