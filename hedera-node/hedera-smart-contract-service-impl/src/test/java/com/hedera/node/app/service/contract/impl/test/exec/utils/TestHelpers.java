package com.hedera.node.app.service.contract.impl.test.exec.utils;

import org.hyperledger.besu.datatypes.Address;
import org.hyperledger.besu.evm.operation.Operation;

import java.math.BigInteger;

import static org.junit.jupiter.api.Assertions.assertEquals;

public class TestHelpers {
    public static Address SYSTEM_ADDRESS = Address.fromHexString(BigInteger.valueOf(750).toString(16));
    public static Address NOT_SYSTEM_ADDRESS = Address.fromHexString("0x1234576890");
    public static void assertSameResult(final Operation.OperationResult expected, final Operation.OperationResult actual) {
        assertEquals(expected.getHaltReason(), actual.getHaltReason());
        assertEquals(expected.getGasCost(), actual.getGasCost());
    }
}