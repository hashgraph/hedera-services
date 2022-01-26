package com.hedera.services.contracts.operation;

import com.hedera.services.store.contracts.precompile.SyntheticTxnFactory;
import org.hyperledger.besu.datatypes.Address;
import org.hyperledger.besu.evm.EVM;
import org.hyperledger.besu.evm.Gas;
import org.hyperledger.besu.evm.frame.MessageFrame;
import org.hyperledger.besu.evm.gascalculator.GasCalculator;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.junit.jupiter.api.Assertions.assertSame;
import static org.mockito.BDDMockito.given;

@ExtendWith(MockitoExtension.class)
class AbstractRecordingCreateOperationTest {
	@Mock
	private SyntheticTxnFactory syntheticTxnFactory;
	@Mock
	private GasCalculator gasCalculator;
	@Mock
	private EVM evm;
	@Mock
	private MessageFrame frame;

	private Subject subject;

	@BeforeEach
	void setUp() {
		subject = new Subject(
				0xF0,
				"ħCREATE",
				3,
				1,
				1,
				gasCalculator,
				syntheticTxnFactory);
	}

	@Test
	void returnsUnderflowWhenStackSizeTooSmall() {
		given(frame.stackSize()).willReturn(2);

		assertSame(Subject.UNDERFLOW_RESPONSE, subject.execute(frame, evm));
	}

	static class Subject extends AbstractRecordingCreateOperation {
		static final long PRETEND_GAS_COST = 123L;

		protected Subject(
				final int opcode,
				final String name,
				final int stackItemsConsumed,
				final int stackItemsProduced,
				final int opSize,
				final GasCalculator gasCalculator,
				final SyntheticTxnFactory syntheticTxnFactory
		) {
			super(opcode, name, stackItemsConsumed, stackItemsProduced, opSize, gasCalculator, syntheticTxnFactory);
		}

		@Override
		protected Gas cost(final MessageFrame frame) {
			return Gas.of(PRETEND_GAS_COST);
		}

		@Override
		protected Address targetContractAddress(final MessageFrame frame) {
			return Address.ALTBN128_ADD;
		}
	}
}