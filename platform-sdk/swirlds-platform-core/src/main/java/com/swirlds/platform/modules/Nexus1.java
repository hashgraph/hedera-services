package com.swirlds.platform.modules;

import java.util.concurrent.atomic.AtomicInteger;

public class Nexus1 {
	private final AtomicInteger i = new AtomicInteger(0);
	public int get(){
		return i.get();
	}
	public void set(int i){
		this.i.set(i);
	}
}
