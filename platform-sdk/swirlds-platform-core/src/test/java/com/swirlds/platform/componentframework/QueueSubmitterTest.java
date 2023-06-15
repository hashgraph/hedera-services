package com.swirlds.platform.componentframework;

import com.swirlds.platform.componentframework.internal.QueueSubmitter;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;

import java.util.concurrent.BlockingQueue;
import java.util.concurrent.LinkedBlockingQueue;

import static org.junit.jupiter.api.Assertions.*;

class QueueSubmitterTest {
	/**
	 * A simple test, call methods on the {@link LongProcessor} and check that the queue contains the expected values.
	 */
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

	/**
	 * Validates that the default methods on {@link QueueSubmitter} do not throw exceptions
	 */
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