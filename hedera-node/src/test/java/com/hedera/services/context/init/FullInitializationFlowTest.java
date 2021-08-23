package com.hedera.services.context.init;

import com.hedera.services.ServicesState;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.mockito.Mockito.verify;

@ExtendWith(MockitoExtension.class)
class FullInitializationFlowTest {
	@Mock
	private StateInitializationFlow stateFlow;
	@Mock
	private StoreInitializationFlow storeFlow;
	@Mock
	private EntitiesInitializationFlow entitiesFlow;
	@Mock
	private ServicesState activeState;

	private FullInitializationFlow subject;

	@BeforeEach
	void setUp() {
		subject = new FullInitializationFlow(stateFlow, storeFlow, entitiesFlow);
	}

	@Test
	void flowsAsExpected() {
		// when:
		subject.runWith(activeState);

		// then:
		verify(stateFlow).runWith(activeState);
		verify(storeFlow).run();
		verify(entitiesFlow).run();
	}
}