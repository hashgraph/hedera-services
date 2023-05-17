package com.swirlds.platform.poc.impl;

import com.swirlds.common.threading.interrupt.InterruptableConsumer;
import com.swirlds.platform.poc.Module2;
import com.swirlds.platform.poc.moduledefs.TaskProcessorExample2;

import java.util.Random;

public class TP2 implements Module2, TaskProcessorExample2 {
	private final InterruptableConsumer<String> ts1;

	public TP2(final InterruptableConsumer<String> ts1) {
		this.ts1 = ts1;
	}

	@Override
	public void process(String s) throws InterruptedException {
		System.out.printf("I am TP2, I received '%s'%n", s);
		Thread.sleep(1000);
		ts1.accept(String.format("%X", new Random().nextInt()));
	}
}
