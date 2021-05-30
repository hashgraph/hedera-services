package com.hedera.services.stream;

import com.hedera.services.context.properties.NodeLocalProperties;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.verify;

@ExtendWith(MockitoExtension.class)
class NonBlockingHandoffTest {
	private final RecordStreamObject rso = new RecordStreamObject();
	private final int mockCap = 10;

	@Mock
	private RecordStreamManager recordStreamManager;
	@Mock
	private NodeLocalProperties nodeLocalProperties;

	private NonBlockingHandoff subject;

	@BeforeEach
	void setUp() {
		given(nodeLocalProperties.recordStreamQueueCapacity()).willReturn(mockCap);

		subject = new NonBlockingHandoff(recordStreamManager, nodeLocalProperties);
	}

	@AfterEach
	void cleanup() {
		subject.getExecutor().shutdownNow();
	}

	@Test
	void worksAsExpected() {
		// when:
		Assertions.assertTrue(subject.offer(rso));

		// then:
		verify(recordStreamManager).addRecordStreamObject(rso);
	}
}