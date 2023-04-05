package com.hedera.node.app.service.mono.txns.network;

import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.function.BooleanSupplier;

@ExtendWith(MockitoExtension.class)
class NetworkLogicModuleTest {
    @Mock
    private FreezeTransitionLogic freezeTransitionLogic;
    @Mock
    private BooleanSupplier isRecordingFacilityMocks;

}