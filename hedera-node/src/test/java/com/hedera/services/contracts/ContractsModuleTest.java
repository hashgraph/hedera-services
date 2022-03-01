package com.hedera.services.contracts;

import org.hyperledger.besu.evm.gascalculator.GasCalculator;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.junit.jupiter.api.Assertions.assertEquals;

@ExtendWith(MockitoExtension.class)
class ContractsModuleTest {
	@Mock
	private GasCalculator gasCalculator;

	@Test
	void logOperationsAreProvided() {
		final var log0 = ContractsModule.provideLog0Operation(gasCalculator);
		final var log1 = ContractsModule.provideLog1Operation(gasCalculator);
		final var log2 = ContractsModule.provideLog2Operation(gasCalculator);
		final var log3 = ContractsModule.provideLog3Operation(gasCalculator);
		final var log4 = ContractsModule.provideLog4Operation(gasCalculator);

		assertEquals("LOG0", log0.getName());
		assertEquals("LOG1", log1.getName());
		assertEquals("LOG2", log2.getName());
		assertEquals("LOG3", log3.getName());
		assertEquals("LOG4", log4.getName());
	}
}