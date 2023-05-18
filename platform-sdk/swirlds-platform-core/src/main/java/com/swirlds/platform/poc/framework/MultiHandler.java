package com.swirlds.platform.poc.framework;

import com.swirlds.common.threading.interrupt.InterruptableConsumer;
import org.apache.commons.lang3.tuple.Pair;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

public class MultiHandler {
	/**
	 * A map of data type to handler for that type.
	 */
	private final Map<Class<?>, InterruptableConsumer<Object>> subHandlers;

	@SuppressWarnings("unchecked")
	public MultiHandler(final List<Pair<Class<?>, InterruptableConsumer<?>>> processingMethods) {
		this.subHandlers = new HashMap<>();
		for (Pair<Class<?>, InterruptableConsumer<?>> processingMethod : processingMethods) {
			subHandlers.put(processingMethod.getLeft(), (InterruptableConsumer<Object>) processingMethod.getRight());
		}
	}

	/**
	 * Handle an object from the queue.
	 *
	 * @param object
	 * 		the object to be handled
	 */
	public void handle(final Object object) throws InterruptedException {
		Objects.requireNonNull(object, "null objects not supported");
		final Class<?> clazz = object.getClass();
		final InterruptableConsumer<Object> handler = subHandlers.get(clazz);
		if (handler == null) {
			throw new IllegalStateException("no handler for " + clazz);
		}
		handler.accept(object);
	}
}
