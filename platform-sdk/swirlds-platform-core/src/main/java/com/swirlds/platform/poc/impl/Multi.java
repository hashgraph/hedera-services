package com.swirlds.platform.poc.impl;

import com.swirlds.platform.poc.moduledefs.MultiTaskExample;

public class Multi implements MultiTaskExample {
	@Override
	public void number(final int num) throws InterruptedException {
		System.out.println("I am Multi, I received a number " + num);
	}

	@Override
	public void string(final String s) throws InterruptedException {
		System.out.println("I am Multi, I received a string " + s);
	}
}
