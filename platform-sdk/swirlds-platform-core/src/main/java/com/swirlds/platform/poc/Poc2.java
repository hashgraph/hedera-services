package com.swirlds.platform.poc;

import com.swirlds.common.threading.interrupt.InterruptableConsumer;
import com.swirlds.platform.poc.impl.TP1;
import com.swirlds.platform.poc.impl.TP2;
import com.swirlds.platform.poc.impl.Nexus1;
import com.swirlds.platform.poc.moduledefs.Nexus2;
import com.swirlds.platform.poc.moduledefs.TaskProcessorExample1;
import com.swirlds.platform.poc.moduledefs.TaskProcessorExample2;

import java.util.List;
import java.util.Random;

public class Poc2 {
	public static void main(String[] args) throws InterruptedException {
		// step 1: construct wiring
		Wiring2 wiring = new Wiring2(List.of(
				TaskProcessorExample1.class,
				TaskProcessorExample2.class,
				Nexus1.class,
				Nexus2.class
		));

		// step 2: add nexuses
		wiring.addImplementation(new Nexus1());
		wiring.addImplementation(() -> "I am nexus 2", Nexus2.class);

		// step 3: add task processors
		wiring.addImplementation(new TP1(
				wiring.getComponent(Nexus1.class),
				wiring.getComponent(Nexus2.class),
				wiring.getComponent(TaskProcessorExample2.class)
		));
		wiring.addImplementation(new TP2(
				wiring.getComponent(TaskProcessorExample1.class)::process
		));

		// step 4: start
		wiring.start();

		// step 5: submit initial task
		wiring.getComponent(TaskProcessorExample1.class).process("start");

		// step 6: wait a bit
		Thread.sleep(10000);
	}
}
