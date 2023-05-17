package com.swirlds.platform.poc.infrastructure;

import java.lang.reflect.InvocationHandler;
import java.lang.reflect.Method;
import java.util.concurrent.BlockingQueue;

public class QueueSubmitter implements InvocationHandler {
	private final BlockingQueue<Object> queue;

	public QueueSubmitter(final BlockingQueue<Object> queue) {
		this.queue = queue;
	}

	@Override
	public Object invoke(final Object proxy, final Method method, final Object[] args) throws Throwable {
		switch (method.getName()) {
			case "hashCode":
				return System.identityHashCode(proxy);
			case "equals":
				return false;
			case "toString":
				return proxy.getClass().getName() + "@" + Integer.toHexString(System.identityHashCode(proxy));
			default:
				queue.put(args[0]);
		}
		return null;
	}
}
