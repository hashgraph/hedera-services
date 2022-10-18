/*
 * Copyright (C) 2020-2022 Hedera Hashgraph, LLC
 *
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
 */
package com.hedera.services.fees.calculation.file.queries;

import static com.hedera.test.utils.IdUtils.asFile;
import static com.hedera.test.utils.QueryUtils.queryHeaderOf;
import static com.hedera.test.utils.QueryUtils.queryOf;
import static com.hederahashgraph.api.proto.java.ResponseType.ANSWER_ONLY;
import static com.hederahashgraph.api.proto.java.ResponseType.COST_ANSWER;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.mock;

import com.google.protobuf.ByteString;
import com.hedera.services.context.primitives.StateView;
import com.hederahashgraph.api.proto.java.FeeData;
import com.hederahashgraph.api.proto.java.FileGetContentsQuery;
import com.hederahashgraph.api.proto.java.FileGetInfoResponse;
import com.hederahashgraph.api.proto.java.FileID;
import com.hederahashgraph.api.proto.java.Query;
import com.hederahashgraph.api.proto.java.ResponseType;
import com.hederahashgraph.fee.FileFeeBuilder;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class GetFileContentsResourceUsageTest {
    private static final ByteString ledgerId = ByteString.copyFromUtf8("0xff");
    private static final FileID target = asFile("0.0.123");
    private StateView view;
    private FileFeeBuilder usageEstimator;
    private GetFileContentsResourceUsage subject;
    private static final long fileSize = 1_234;
    private static final FileGetInfoResponse.FileInfo targetInfo =
            FileGetInfoResponse.FileInfo.newBuilder()
                    .setLedgerId(ledgerId)
                    .setSize(fileSize)
                    .build();

    @BeforeEach
    void setup() {
        usageEstimator = mock(FileFeeBuilder.class);
        view = mock(StateView.class);

        subject = new GetFileContentsResourceUsage(usageEstimator);
    }

    @Test
    void returnsDefaultSchedulesOnMissing() {
        final var answerOnlyQuery = fileContentsQuery(target, ANSWER_ONLY);
        given(view.infoForFile(any())).willReturn(Optional.empty());

        assertSame(FeeData.getDefaultInstance(), subject.usageGiven(answerOnlyQuery, view));
    }

    @Test
    void invokesEstimatorAsExpectedForType() {
        final var costAnswerUsage = mock(FeeData.class);
        final var answerOnlyUsage = mock(FeeData.class);
        final var answerOnlyQuery = fileContentsQuery(target, ANSWER_ONLY);
        final var costAnswerQuery = fileContentsQuery(target, COST_ANSWER);
        given(view.infoForFile(target)).willReturn(Optional.ofNullable(targetInfo));
        given(usageEstimator.getFileContentQueryFeeMatrices((int) fileSize, COST_ANSWER))
                .willReturn(costAnswerUsage);
        given(usageEstimator.getFileContentQueryFeeMatrices((int) fileSize, ANSWER_ONLY))
                .willReturn(answerOnlyUsage);

        final var costAnswerEstimate = subject.usageGiven(costAnswerQuery, view);
        final var answerOnlyEstimate = subject.usageGiven(answerOnlyQuery, view);

        assertSame(costAnswerUsage, costAnswerEstimate);
        assertSame(answerOnlyUsage, answerOnlyEstimate);
    }

    @Test
    void recognizesApplicableQuery() {
        final var fileContentsQuery = fileContentsQuery(target, COST_ANSWER);
        final var nonFileContentsQuery = Query.getDefaultInstance();

        assertTrue(subject.applicableTo(fileContentsQuery));
        assertFalse(subject.applicableTo(nonFileContentsQuery));
    }

    private Query fileContentsQuery(final FileID id, final ResponseType type) {
        final var op =
                FileGetContentsQuery.newBuilder().setFileID(id).setHeader(queryHeaderOf(type));
        return queryOf(op);
    }
}
