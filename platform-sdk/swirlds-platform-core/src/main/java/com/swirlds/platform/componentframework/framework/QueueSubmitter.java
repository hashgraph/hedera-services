package com.swirlds.platform.componentframework.framework;

import java.lang.reflect.InvocationHandler;
import java.lang.reflect.Method;
import java.lang.reflect.Proxy;
import java.util.concurrent.BlockingQueue;

public class QueueSubmitter implements InvocationHandler {
	private final BlockingQueue<Object> queue;

	private QueueSubmitter(final BlockingQueue<Object> queue) {
		this.queue = queue;
	}

	@SuppressWarnings("unchecked")
	public static <T> T create(final Class<T> clazz, final BlockingQueue<Object> queue) {
		return (T) Proxy.newProxyInstance(
				QueueSubmitter.class.getClassLoader(),
				new Class[] { clazz },
				new QueueSubmitter(queue));
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
