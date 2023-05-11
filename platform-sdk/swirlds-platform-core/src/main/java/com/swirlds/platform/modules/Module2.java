package com.swirlds.platform.modules;

import com.swirlds.common.threading.interrupt.InterruptableConsumer;

import java.util.Random;

public class Module2 implements TaskModule<String>{
	private final InterruptableConsumer<String> ts1;

	public Module2(final InterruptableConsumer<String> ts1) {
		this.ts1 = ts1;
	}

	private void process(String s) throws InterruptedException {
		System.out.printf("I am TP2, I received '%s'%n", s);
		Thread.sleep(1000);
		ts1.accept(String.format("%X", new Random().nextInt()));
	}

	@Override
	public InterruptableConsumer<String> getTaskProcessor() {
		return this::process;
	}
}
