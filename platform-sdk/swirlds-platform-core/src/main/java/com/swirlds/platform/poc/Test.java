package com.swirlds.platform.poc;

import com.swirlds.platform.poc.infrastructure.QueueSubmitter;

import java.lang.reflect.InvocationHandler;
import java.lang.reflect.Proxy;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.LinkedBlockingQueue;

public class Test {
	public static void main(String[] args) throws InterruptedException {
		BlockingQueue<Object> q = new LinkedBlockingQueue<>();
		QueueSubmitter qs = new QueueSubmitter(q);

		Module2 m2 = (Module2) Proxy.newProxyInstance(
				Test.class.getClassLoader(),
				new Class[] { Module2.class },
				qs);
		m2.process("Hello");
		m2.hashCode();
		System.out.println(q.take());

	}
}
