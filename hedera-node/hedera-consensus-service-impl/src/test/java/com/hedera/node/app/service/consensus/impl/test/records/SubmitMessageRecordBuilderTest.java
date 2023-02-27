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

package com.hedera.node.app.service.consensus.impl.test.records;

import static com.hederahashgraph.api.proto.java.ResponseCodeEnum.SUCCESS;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;

import com.hedera.node.app.service.consensus.impl.records.SubmitMessageRecordBuilder;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class SubmitMessageRecordBuilderTest {
    private SubmitMessageRecordBuilder subject = new SubmitMessageRecordBuilder();

    @Test
    void recordsNothingInMonoContextIfNothingTracked() {
        assertThrows(IllegalStateException.class, subject::getNewTopicRunningHash);
        assertThrows(IllegalStateException.class, subject::getNewTopicSequenceNumber);
        assertThrows(IllegalStateException.class, subject::getUsedRunningHashVersion);
    }

    @Test
    void recordsTrackedSideEffectsInMonoContext() {
        final var pretendTopicRunningHash = new byte[] {1, 2, 3};

        final var returnedSubject = subject.setFinalStatus(SUCCESS).setNewTopicMetadata(pretendTopicRunningHash, 2, 3L);
        assertSame(subject, returnedSubject);

        assertSame(pretendTopicRunningHash, subject.getNewTopicRunningHash());
        assertEquals(3L, subject.getUsedRunningHashVersion());
        assertEquals(2, subject.getNewTopicSequenceNumber());
    }
}
