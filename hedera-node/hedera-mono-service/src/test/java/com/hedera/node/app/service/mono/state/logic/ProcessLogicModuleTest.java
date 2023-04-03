package com.hedera.node.app.service.mono.state.logic;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.function.BooleanSupplier;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.BDDMockito.given;

@ExtendWith(MockitoExtension.class)
class ProcessLogicModuleTest {
    @Mock
    private StandardProcessLogic standardProcessLogic;
    @Mock
    private BooleanSupplier isRecordingFacilityMocks;

    @Test
    void recordTxnsIfRecordingFacilityMocks() {
        given(isRecordingFacilityMocks.getAsBoolean()).willReturn(true);

        final var logic = ProcessLogicModule.provideProcessLogic(
                standardProcessLogic, isRecordingFacilityMocks);

        assertInstanceOf(TxnRecordingProcessLogic.class, logic);
    }

    @Test
    void usesStandardLogicIfNotRecordingFacilityMocks() {
        final var logic = ProcessLogicModule.provideProcessLogic(
                standardProcessLogic, isRecordingFacilityMocks);

        assertInstanceOf(StandardProcessLogic.class, logic);
    }
}