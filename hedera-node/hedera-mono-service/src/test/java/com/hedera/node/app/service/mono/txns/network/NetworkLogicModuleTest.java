package com.hedera.node.app.service.mono.txns.network;

import com.hedera.node.app.service.mono.state.logic.ProcessLogicModule;
import com.hedera.node.app.service.mono.state.logic.StandardProcessLogic;
import com.hedera.node.app.service.mono.state.logic.TxnRecordingProcessLogic;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.function.BooleanSupplier;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.BDDMockito.given;

@ExtendWith(MockitoExtension.class)
class NetworkLogicModuleTest {
    @Mock
    private FreezeTransitionLogic freezeTransitionLogic;
    @Mock
    private BooleanSupplier isRecordingFacilityMocks;

}