package com.hedera.node.app.service.contract.impl.test.exec.gas;

import com.hedera.node.app.service.contract.impl.exec.gas.CustomGasCharging;
import com.hedera.node.app.service.contract.impl.exec.gas.GasChargingResult;
import com.hedera.node.app.service.contract.impl.hevm.HederaEvmBlocks;
import com.hedera.node.app.service.contract.impl.hevm.HederaEvmContext;
import com.hedera.node.app.service.contract.impl.hevm.HederaWorldUpdater;
import com.hedera.node.app.service.contract.impl.state.HederaEvmAccount;
import edu.umd.cs.findbugs.annotations.NonNull;
import org.hyperledger.besu.evm.gascalculator.GasCalculator;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class CustomGasChargingTest {
    @Mock
    private HederaEvmAccount sender;
    @Mock
    private HederaEvmAccount relayer;
    @Mock
    private HederaEvmContext context;
    @Mock
    private HederaEvmBlocks blocks;
    @Mock
    private HederaWorldUpdater worldUpdater;
    @Mock
    private GasCalculator gasCalculator;

    private CustomGasCharging subject;

    @BeforeEach
    void setUp() {
        subject = new CustomGasCharging(gasCalculator);
    }

    @Test
    void name() {
    }
}