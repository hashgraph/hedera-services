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

package com.hedera.node.app.service.mono.state.submerkle;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentCaptor.forClass;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.internal.verification.VerificationModeFactory.times;

import com.hedera.node.app.service.mono.utils.replay.NewId;
import com.hedera.node.app.service.mono.utils.replay.ReplayAssetRecording;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class RecordingSequenceNumberTest {
    @Mock
    private ReplayAssetRecording assetRecording;

    private RecordingSequenceNumber subject;

    @BeforeEach
    void setUp() {
        subject = new RecordingSequenceNumber(assetRecording, new SequenceNumber());
    }

    @Test
    void recordsSeqNos() {
        final ArgumentCaptor<NewId> captor = forClass(NewId.class);

        subject.getAndIncrement();
        subject.getAndIncrement();
        subject.decrement();
        subject.getAndIncrement();

        assertEquals(2, subject.current());
        verify(assetRecording, times(3))
                .appendJsonToAsset(eq(RecordingSequenceNumber.REPLAY_SEQ_NOS_ASSET), captor.capture());

        final var values = captor.getAllValues();
        assertEquals(0L, values.get(0).getNumber());
        assertEquals(1L, values.get(1).getNumber());
        assertEquals(1L, values.get(2).getNumber());
    }
}
