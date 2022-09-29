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
import static com.hederahashgraph.api.proto.java.ResponseType.ANSWER_ONLY;
import static com.hederahashgraph.api.proto.java.ResponseType.COST_ANSWER;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;

import com.hedera.services.context.primitives.StateView;
import com.hedera.services.usage.file.ExtantFileContext;
import com.hedera.services.usage.file.FileOpsUsage;
import com.hedera.test.factories.scenarios.TxnHandlingScenario;
import com.hederahashgraph.api.proto.java.FeeData;
import com.hederahashgraph.api.proto.java.FileGetInfoQuery;
import com.hederahashgraph.api.proto.java.FileGetInfoResponse;
import com.hederahashgraph.api.proto.java.FileID;
import com.hederahashgraph.api.proto.java.Key;
import com.hederahashgraph.api.proto.java.Query;
import com.hederahashgraph.api.proto.java.QueryHeader;
import com.hederahashgraph.api.proto.java.ResponseType;
import com.hederahashgraph.api.proto.java.Timestamp;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

class GetFileInfoResourceUsageTest {
    private static final long expiry = 1_234_567L;
    private static final long size = 123;
    private static final String memo = "Ok whatever";
    private static final FileID target = asFile("0.0.123");
    private static final Key wacl = TxnHandlingScenario.MISC_FILE_WACL_KT.asKey();
    private static final FileGetInfoResponse.FileInfo targetInfo =
            FileGetInfoResponse.FileInfo.newBuilder()
                    .setExpirationTime(Timestamp.newBuilder().setSeconds(expiry).build())
                    .setSize(size)
                    .setMemo(memo)
                    .setKeys(wacl.getKeyList())
                    .build();

    private StateView view;
    private FileOpsUsage fileOpsUsage;

    private GetFileInfoResourceUsage subject;

    @BeforeEach
    void setup() {
        fileOpsUsage = mock(FileOpsUsage.class);
        view = mock(StateView.class);

        subject = new GetFileInfoResourceUsage(fileOpsUsage);
    }

    @Test
    void returnsDefaultSchedulesOnMissing() {
        final var answerOnlyQuery = fileInfoQuery(target, ANSWER_ONLY);
        given(view.infoForFile(any())).willReturn(Optional.empty());

        assertSame(FeeData.getDefaultInstance(), subject.usageGiven(answerOnlyQuery, view));
    }

    @Test
    void invokesEstimatorAsExpectedForType() {
        final var expected = mock(FeeData.class);
        final var captor = ArgumentCaptor.forClass(ExtantFileContext.class);
        final var answerOnlyQuery = fileInfoQuery(target, ANSWER_ONLY);
        given(view.infoForFile(target)).willReturn(Optional.ofNullable(targetInfo));
        given(fileOpsUsage.fileInfoUsage(any(), any())).willReturn(expected);

        final var actual = subject.usageGiven(answerOnlyQuery, view);

        assertSame(expected, actual);
        verify(fileOpsUsage).fileInfoUsage(argThat(answerOnlyQuery::equals), captor.capture());

        final var ctxUsed = captor.getValue();
        assertEquals(expiry, ctxUsed.currentExpiry());
        assertEquals(memo, ctxUsed.currentMemo());
        assertEquals(wacl.getKeyList(), ctxUsed.currentWacl());
        assertEquals(size, ctxUsed.currentSize());
    }

    @Test
    void recognizesApplicableQuery() {
        final var fileInfoQuery = fileInfoQuery(target, COST_ANSWER);
        final var nonFileInfoQuery = Query.getDefaultInstance();

        assertTrue(subject.applicableTo(fileInfoQuery));
        assertFalse(subject.applicableTo(nonFileInfoQuery));
    }

    private static final Query fileInfoQuery(final FileID id, final ResponseType type) {
        final var op =
                FileGetInfoQuery.newBuilder()
                        .setFileID(id)
                        .setHeader(QueryHeader.newBuilder().setResponseType(type));
        return Query.newBuilder().setFileGetInfo(op).build();
    }
}
