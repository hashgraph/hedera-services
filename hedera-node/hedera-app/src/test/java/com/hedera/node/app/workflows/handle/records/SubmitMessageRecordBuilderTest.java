/*
 * Copyright (C) 2023 Hedera Hashgraph, LLC
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

package com.hedera.node.app.workflows.handle.records;

import static com.hederahashgraph.api.proto.java.ResponseCodeEnum.SUCCESS;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;

import com.hedera.node.app.service.mono.context.TransactionContext;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class SubmitMessageRecordBuilderTest {
    @Mock
    private TransactionContext txnCtx;

    private SubmitMessageRecordBuilder subject = new SubmitMessageRecordBuilder();

    @Test
    void recordsNothingInMonoContextIfNothingTracked() {
        subject.exposeSideEffectsToMono(txnCtx);

        verifyNoInteractions(txnCtx);
    }

    @Test
    void recordsTrackedSideEffectsInMonoContext() {
        final var pretendTopicRunningHash = new byte[] {1, 2, 3};

        final var returnedSubject = subject.setFinalStatus(SUCCESS).setNewTopicMetadata(pretendTopicRunningHash, 2, 3L);
        assertSame(subject, returnedSubject);
        assertSame(subject, subject.self());

        subject.exposeSideEffectsToMono(txnCtx);

        verify(txnCtx).setTopicRunningHash(pretendTopicRunningHash, 2);
        verify(txnCtx).setStatus(SUCCESS);
    }
}
