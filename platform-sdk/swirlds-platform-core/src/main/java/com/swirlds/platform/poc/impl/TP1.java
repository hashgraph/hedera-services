package com.swirlds.platform.poc.impl;

import com.swirlds.platform.poc.moduledefs.Nexus2;
import com.swirlds.platform.poc.moduledefs.TaskProcessorExample1;
import com.swirlds.platform.poc.moduledefs.TaskProcessorExample2;

import java.util.Random;

public class TP1 implements TaskProcessorExample1 {
	private final Nexus1 nx1;
	private final Nexus2 nx2;
	private final TaskProcessorExample2 ts2;

	public TP1(final Nexus1 nx1, final Nexus2 nx2, final TaskProcessorExample2 ts2) {
		this.nx1 = nx1;
		this.nx2 = nx2;
		this.ts2 = ts2;
	}

	@Override
	public void process(final String s) throws InterruptedException {
		System.out.printf("I am TP1, I received '%s'%n", s);
		System.out.printf("I am TP1, Nexus 1 has value '%d'%n", nx1.get());
		System.out.printf("I am TP1, Nexus 2 says '%s'%n", nx2.get());
		nx1.set(new Random().nextInt());
		Thread.sleep(1000);
		ts2.process(String.format("%X", new Random().nextInt()));
	}
}
