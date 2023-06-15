package com.swirlds.platform.componentframework;

import com.swirlds.common.threading.framework.config.QueueThreadConfiguration;
import com.swirlds.common.threading.manager.AdHocThreadManager;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import java.util.List;
import java.util.Random;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

@Timeout(10)
class TaskProcessorsTest {

	/**
	 * A simple test with a single {@link LongProcessor} that processes a single {@link Long}.
	 *
	 * @param addAsLambda
	 * 		if true, add the processor as a lambda, otherwise add it as an object
	 */
	@ParameterizedTest
	@ValueSource(booleans = { true, false })
	void singleTaskProcessor(final boolean addAsLambda) throws InterruptedException {
		final TaskProcessors taskProcessors = build(
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
		assertEquals(randomLong, longProcessor.getLastProcessed(),
				"Last processed should be the random value we passed in");
		taskProcessors.stop();
	}

	/**
	 * A test with two {@link TaskProcessor}s, where one depends on the other.
	 */
	@Test
	void twoProcessors() throws InterruptedException {
		final TaskProcessors taskProcessors = build(
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

	/**
	 * Tests bad constructor arguments.
	 */
	@Test
	void badConstructorArguments() {
		assertThrows(NullPointerException.class, () -> new TaskProcessors(null, null),
				"Null constructor argument should throw NPE");
		final List<Class<? extends TaskProcessor>> emptyList = List.of();
		assertThrows(IllegalArgumentException.class, () -> build(emptyList),
				"Empty constructor argument should throw IAE");
		assertThrows(IllegalArgumentException.class,
				() -> build(List.of(LongProcessor.class, LongProcessor.class)),
				"Duplicate constructor argument should throw IAE");
		assertThrows(IllegalArgumentException.class, () -> build(List.of(NoMethodProcessor.class)),
				"Class with no methods should throw IAE");
		assertThrows(IllegalArgumentException.class, () -> build(List.of(StringIntImpl.class)),
				"Non-interface should throw IAE");
	}

	/**
	 * Tests if exceptions get thrown when calling methods out of order
	 */
	@Test
	void illegalStateTest() {
		final TaskProcessors taskProcessors = build(List.of(LongProcessor.class));
		assertThrows(IllegalStateException.class, taskProcessors::start,
				"Should throw IllegalStateException when not started");
		assertThrows(IllegalStateException.class, () -> taskProcessors.getQueueThread(LongProcessor.class),
				"Should throw IllegalStateException when not started");
		taskProcessors.addImplementation(new LongProcessorImpl());
		taskProcessors.start();
		assertThrows(IllegalStateException.class, () -> taskProcessors.addImplementation(new LongProcessorImpl()),
				"Should throw IllegalStateException when started");
		assertThrows(IllegalStateException.class, taskProcessors::start,
				"Should throw IllegalStateException when not started");
		taskProcessors.stop();
	}

	private static TaskProcessors build(final List<Class<? extends TaskProcessor>> taskProcessorDefs) {
		return new TaskProcessors(
				new QueueThreadConfiguration<>(AdHocThreadManager.getStaticThreadManager()),
				taskProcessorDefs.stream()
						.map(d -> new TaskProcessorConfig(d, "a name", null))
						.toList()
		);
	}

}