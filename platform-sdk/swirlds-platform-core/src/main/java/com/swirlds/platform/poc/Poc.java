package com.swirlds.platform.poc;

import com.swirlds.platform.poc.impl.TheBoss;
import com.swirlds.platform.poc.impl.Git;
import com.swirlds.platform.poc.impl.TheDeveloper;
import com.swirlds.platform.poc.moduledefs.Boss;
import com.swirlds.platform.poc.moduledefs.Notion;
import com.swirlds.platform.poc.moduledefs.Developer;

import java.util.List;

public class Poc {
	public static void main(String[] args) throws InterruptedException {
		// step 1: construct wiring
		Wiring wiring = new Wiring(List.of(
				Developer.class,
				Boss.class,
				Git.class,
				Notion.class
		));

		// step 2: add nexuses
		wiring.addImplementation(new Git());
		wiring.addImplementation((s) -> System.out.println("Written to notion: "+s), Notion.class);

		// step 3: add task processors
		wiring.addImplementation(new TheDeveloper(
				wiring.getComponent(Git.class),
				wiring.getComponent(Notion.class),
				wiring.getComponent(Boss.class)
		));
		wiring.addImplementation(new TheBoss(
				wiring.getComponent(Developer.class)
		));

		// step 4: start
		wiring.start();

		// step 5: submit initial task
		//wiring.getComponent(Developer.class).implementFeature("start");

		// step 6: wait a bit
		Thread.sleep(10000);
	}
}
