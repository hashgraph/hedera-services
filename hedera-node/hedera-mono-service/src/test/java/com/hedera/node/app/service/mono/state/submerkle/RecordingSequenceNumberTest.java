package com.hedera.node.app.service.mono.state.submerkle;

import com.hedera.node.app.service.mono.utils.replay.NewId;
import com.hedera.node.app.service.mono.utils.replay.ReplayAssetRecording;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentCaptor.forClass;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.internal.verification.VerificationModeFactory.times;

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
        verify(assetRecording, times(3)).appendJsonLineToReplayAsset(
            eq(RecordingSequenceNumber.REPLAY_SEQ_NOS_ASSET), captor.capture());

        final var values = captor.getAllValues();
        assertEquals(0L, values.get(0).getNumber());
        assertEquals(1L, values.get(1).getNumber());
        assertEquals(1L, values.get(2).getNumber());
    }
}