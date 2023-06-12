package com.swirlds.platform.componentframework;

import com.swirlds.common.threading.interrupt.InterruptableConsumer;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;
import java.util.Random;
import java.util.concurrent.Semaphore;
import java.util.concurrent.atomic.AtomicLong;

import static org.junit.jupiter.api.Assertions.*;

class ComponentsTest {
	@Test
	void singleTaskProcessor() throws InterruptedException {
		final long initialLong = 0;
		final AtomicLong lastProcessed = new AtomicLong(initialLong);
		final Semaphore processingDone = new Semaphore(0);

		final LongProcessor processor = new LongProcessor() {
			@Override
			public void processLong(long l) throws InterruptedException {
				lastProcessed.set(l);
				processingDone.release();
			}

			@Override
			public Map<Class<?>, InterruptableConsumer<?>> getProcessingMethods() {
				return Map.of(
						Long.class,
						(InterruptableConsumer<Long>) this::processLong
				);
			}
		};

		final Components components = new Components(
				List.of(LongProcessor.class)
		);
		components.addImplementation(processor);
		components.start();

		assertEquals(initialLong, lastProcessed.get(), "Last processed should be the initial value");

		final Random random = new Random();
		final long randomLong = random.nextLong();
		components.getComponent(LongProcessor.class).processLong(randomLong);
		processingDone.acquire();
		assertEquals(randomLong, lastProcessed.get(), "Last processed should be the random value we passed in");
	}

}