package com.hedera.node.app.service.mono.txns.network;

import com.hedera.node.app.service.mono.state.logic.TxnRecordingProcessLogic;
import com.hedera.node.app.service.mono.txns.ProcessLogic;
import com.hederahashgraph.api.proto.java.FreezeTransactionBody;
import com.hederahashgraph.api.proto.java.FreezeType;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.io.File;
import java.io.IOException;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.verify;


@ExtendWith(MockitoExtension.class)
class ReplayFlushingFreezeLogicTest {
    @Mock
    private TxnRecordingProcessLogic processLogic;
    @Mock
    private FreezeTransitionLogic freezeTransitionLogic;

    private ReplayFlushingFreezeLogic subject;

    @BeforeEach
    void setUp() {
        subject = new ReplayFlushingFreezeLogic(processLogic, freezeTransitionLogic);
    }

    @Test
    void flushesReplayDataOnFreezeOnly() throws IOException {
        final ArgumentCaptor<File> captor = ArgumentCaptor.forClass(File.class);

        final var op = FreezeTransactionBody.newBuilder()
                .setFreezeType(FreezeType.FREEZE_ONLY)
                .build();
        given(freezeTransitionLogic.inContextOp()).willReturn(op);

        subject.doStateTransition();

        verify(processLogic).recordTo(captor.capture());

    }
}