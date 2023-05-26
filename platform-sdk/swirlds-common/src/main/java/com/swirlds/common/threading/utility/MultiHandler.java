package com.swirlds.common.threading.utility;

import com.swirlds.common.threading.interrupt.InterruptableConsumer;
import org.apache.commons.lang3.tuple.Pair;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

/**
 * Invokes different handlers based on the type of the object.
 */
public class MultiHandler {
	/**
	 * A map of data type to handler for that type.
	 */
	private final Map<Class<?>, InterruptableConsumer<?>> subHandlers;

	public MultiHandler(final Map<Class<?>, InterruptableConsumer<?>> subHandlers) {
		this.subHandlers = new HashMap<>(subHandlers);
	}

	public boolean containsHandlerFor(final Class<?> clazz){
		return subHandlers.containsKey(clazz);
	}

	/**
	 * Handle an object from the queue.
	 *
	 * @param object
	 * 		the object to be handled
	 */
	@SuppressWarnings("unchecked")
	public void handle(final Object object) throws InterruptedException {
		Objects.requireNonNull(object, "null objects not supported");
		final Class<?> clazz = object.getClass();
		final InterruptableConsumer<Object> handler = (InterruptableConsumer<Object>) subHandlers.get(clazz);
		if (handler == null) {
			throw new IllegalStateException("no handler for " + clazz);
		}
		handler.accept(object);
	}
}
