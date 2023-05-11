package com.swirlds.platform.modules;

import com.swirlds.common.threading.interrupt.InterruptableConsumer;

import java.util.List;
import java.util.Random;

public class Poc {
	public static final TaskProcessorDef<String> TP1 = new TaskProcessorDef<>("tp1", String.class);
	public static final TaskProcessorDef<String> TP2 = new TaskProcessorDef<>("tp2", String.class);
	public static final NexusDef<Nexus1> NX1 = new NexusDef<>("nexus1", Nexus1.class);
	public static final NexusDef<Nexus2> NX2 = new NexusDef<>("nexus2", Nexus2.class);

	public static void main(String[] args) throws InterruptedException {
		// step 1: construct wiring
		Wiring wiring = new Wiring(
				List.of(TP1, TP2)
		);

		// step 2: add nexuses
		wiring.addNexus(
				NX1, new Nexus1()
		);
		wiring.addNexus(
				NX2, () -> "I am nexus 2"
		);

		// step 3: get dependencies from wiring
		Nexus1 nx1 = wiring.getNexus(NX1);
		Nexus2 nx2 = wiring.getNexus(NX2);
		InterruptableConsumer<String> ts1 = wiring.getTaskSubmitter(TP1);
		InterruptableConsumer<String> ts2 = wiring.getTaskSubmitter(TP2);

		// step 4: construct task processors
		InterruptableConsumer<String> tp1 = s->{
			System.out.printf("I am TP1, I received '%s'%n", s);
			System.out.printf("I am TP1, Nexus 1 has value '%d'%n", nx1.get());
			System.out.printf("I am TP1, Nexus 2 says '%s'%n", nx2.get());
			nx1.set(new Random().nextInt());
			Thread.sleep(1000);
			ts2.accept(String.format("%X", new Random().nextInt()));
		};
		InterruptableConsumer<String> tp2 = s->{
			System.out.printf("I am TP2, I received '%s'%n", s);
			Thread.sleep(1000);
			ts1.accept(String.format("%X", new Random().nextInt()));
		};

		// step 5: add task processors
		wiring.addTaskProcessor(TP1, tp1);
		wiring.addTaskProcessor(TP2, tp2);

		// step 6: start
		wiring.start();

		// step 7: submit initial task
		ts1.accept("start");

		// step 8: wait a bit
		Thread.sleep(10000);
	}
}
