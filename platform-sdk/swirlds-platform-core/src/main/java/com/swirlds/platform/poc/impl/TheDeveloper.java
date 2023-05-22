package com.swirlds.platform.poc.impl;

import com.swirlds.platform.poc.moduledefs.Boss;
import com.swirlds.platform.poc.moduledefs.Developer;
import com.swirlds.platform.poc.moduledefs.Notion;

import java.util.Random;

public class TheDeveloper implements Developer {
	private final Git git;
	private final Notion notion;
	private final Boss boss;

	public TheDeveloper(final Git git, final Notion notion, final Boss boss) {
		this.git = git;
		this.notion = notion;
		this.boss = boss;
	}

	@Override
	public void implementFeature(final String s) throws InterruptedException {
		System.out.println("Received a feature request: " + s);

		// working on feature
		Thread.sleep(1000);

		git.push(s);
		notion.write(s);

		System.out.println("Feature done: " + s);
		boss.deliverFeature(s);

		if (new Random().nextBoolean()) {
			boss.requestPto(new Random().nextInt(10));
		}
	}
}
