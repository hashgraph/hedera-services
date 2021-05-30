package com.hedera.services.stream;

import com.hedera.services.context.properties.NodeLocalProperties;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.verify;

@ExtendWith(MockitoExtension.class)
class NonBlockingHandoffTest {
	private final int mockCap = 10;
	private final RecordStreamObject rso = new RecordStreamObject();

	@Mock
	private RecordStreamManager recordStreamManager;
	@Mock
	private NodeLocalProperties nodeLocalProperties;

	private NonBlockingHandoff subject;

	@Test
	void handoffWorksAsExpected() {
		given(nodeLocalProperties.recordStreamQueueCapacity()).willReturn(mockCap);
		// and:
		subject = new NonBlockingHandoff(recordStreamManager, nodeLocalProperties);

		// when:
		Assertions.assertTrue(subject.offer(rso));

		// and:
		subject.getExecutor().shutdownNow();

		// then:
		final var verification = verify(recordStreamManager);
		if (verification != null) {
			verification.addRecordStreamObject(rso);
		}
	}
}