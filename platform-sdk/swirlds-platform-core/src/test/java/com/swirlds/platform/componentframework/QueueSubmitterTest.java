package com.swirlds.platform.componentframework;

import com.swirlds.platform.componentframework.internal.QueueSubmitter;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;

import java.util.concurrent.BlockingQueue;
import java.util.concurrent.LinkedBlockingQueue;

import static org.junit.jupiter.api.Assertions.*;

class QueueSubmitterTest {
	@Test
	@SuppressWarnings("unchecked")
	void basicTest() throws InterruptedException {
		final BlockingQueue<Long> queue = new LinkedBlockingQueue<>();
		final LongProcessor submitter = QueueSubmitter.create(
				LongProcessor.class,
				(BlockingQueue<Object>) (BlockingQueue<?>) queue
		);
		submitter.processLong(123);
		assertEquals(123, queue.poll());
		assertTrue(queue.isEmpty());
	}

	@Test
	void defaultMethods() {
		final LongProcessor submitter = QueueSubmitter.create(
				LongProcessor.class,
				new LinkedBlockingQueue<>()
		);

		assertDoesNotThrow(submitter::hashCode);
		assertDoesNotThrow(submitter::toString);
		assertDoesNotThrow(() -> submitter.equals(Mockito.mock(LongProcessor.class)));
		assertDoesNotThrow(submitter::getClass);
	}

}