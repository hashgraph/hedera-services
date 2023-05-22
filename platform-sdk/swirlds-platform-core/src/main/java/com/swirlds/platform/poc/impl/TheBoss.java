package com.swirlds.platform.poc.impl;

import com.swirlds.platform.poc.moduledefs.Boss;
import com.swirlds.platform.poc.moduledefs.Developer;

public class TheBoss implements Boss {
	private final Developer developer;
	private int featureCount = 1;

	public TheBoss(final Developer developer) throws InterruptedException {
		this.developer = developer;
		developer.implementFeature("feature " + featureCount++);
	}

	@Override
	public void requestPto(final int days) throws InterruptedException {
		System.out.println("Requested TPO for " + days + " days");
	}

	@Override
	public void deliverFeature(final String s) throws InterruptedException {
		System.out.println("Feature delivered: " + s);
		developer.implementFeature("feature " + featureCount++);
	}
}
