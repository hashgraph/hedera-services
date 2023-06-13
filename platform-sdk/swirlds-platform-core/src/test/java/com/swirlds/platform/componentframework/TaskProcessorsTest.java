package com.swirlds.platform.componentframework;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import java.util.List;
import java.util.Random;
import java.util.concurrent.Semaphore;
import java.util.concurrent.atomic.AtomicLong;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class TaskProcessorsTest {

	@ParameterizedTest
	@ValueSource(booleans = { true, false })
	void singleTaskProcessor(final boolean addAsLambda) throws InterruptedException {
		final TaskProcessors taskProcessors = new TaskProcessors(
				List.of(LongProcessor.class)
		);
		final LongProcessorImpl longProcessor = new LongProcessorImpl();
		if (addAsLambda) {
			taskProcessors.addImplementation(longProcessor::processLong, LongProcessor.class);
		} else {
			taskProcessors.addImplementation(longProcessor);
		}

		taskProcessors.start();

		assertEquals(LongProcessorImpl.INITIAL_LONG, longProcessor.getLastProcessed(),
				"Last processed should be the initial value");

		final Random random = new Random();
		final long randomLong = random.nextLong();
		taskProcessors.getSubmitter(LongProcessor.class).processLong(randomLong);
		taskProcessors.getQueueThread(LongProcessor.class).waitUntilNotBusy();
		assertEquals(randomLong, longProcessor.getLastProcessed(), "Last processed should be the random value we passed in");
		taskProcessors.stop();
	}

	@Test
	void twoProcessors() throws InterruptedException {
		final TaskProcessors taskProcessors = new TaskProcessors(
				List.of(LongProcessor.class, StringIntProcessor.class)
		);
		final LongProcessor longSubmitter = taskProcessors.getSubmitter(LongProcessor.class);
		final LongProcessorImpl longProcessor = new LongProcessorImpl();
		taskProcessors.addImplementation(new StringIntImpl(longSubmitter));
		taskProcessors.addImplementation(longProcessor);

		taskProcessors.start();

		assertEquals(LongProcessorImpl.INITIAL_LONG, longProcessor.getLastProcessed(),
				"Last processed should be the initial value");

		taskProcessors.getSubmitter(StringIntProcessor.class).number(123);
		taskProcessors.getQueueThread(StringIntProcessor.class).waitUntilNotBusy();
		taskProcessors.getQueueThread(LongProcessor.class).waitUntilNotBusy();
		assertEquals(123, longProcessor.getLastProcessed(), "Last processed should be 123");

		taskProcessors.getSubmitter(StringIntProcessor.class).string("321");
		taskProcessors.getQueueThread(StringIntProcessor.class).waitUntilNotBusy();
		taskProcessors.getQueueThread(LongProcessor.class).waitUntilNotBusy();
		assertEquals(321, longProcessor.getLastProcessed(), "Last processed should be 321");

		taskProcessors.stop();
	}

	@Test
	void badConstructorArguments() {
		assertThrows(NullPointerException.class, () -> new TaskProcessors(null),
				"Null constructor argument should throw NPE");
		assertThrows(IllegalArgumentException.class, () -> new TaskProcessors(List.of()),
				"Empty constructor argument should throw IAE");
		assertThrows(IllegalArgumentException.class,
				() -> new TaskProcessors(List.of(LongProcessor.class, LongProcessor.class)),
				"Duplicate constructor argument should throw IAE");
		assertThrows(IllegalArgumentException.class, () -> new TaskProcessors(List.of(NoMethodProcessor.class)),
				"Class with no methods should throw IAE");
		assertThrows(IllegalArgumentException.class, () -> new TaskProcessors(List.of(StringIntImpl.class)),
				"Non-interface should throw IAE");
	}

}