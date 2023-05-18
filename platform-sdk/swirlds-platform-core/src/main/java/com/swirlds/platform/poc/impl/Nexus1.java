package com.swirlds.platform.poc.impl;

import com.swirlds.platform.poc.framework.Nexus;

import java.util.concurrent.atomic.AtomicInteger;

public class Nexus1 implements Nexus {
	private final AtomicInteger i = new AtomicInteger(0);
	public int get(){
		return i.get();
	}
	public void set(int i){
		this.i.set(i);
	}
}
