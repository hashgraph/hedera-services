package com.swirlds.platform.poc.impl;

import com.swirlds.platform.poc.framework.Nexus;

import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;

public class Git implements Nexus {
	private final AtomicReference<String> code = new AtomicReference<>();
	public String pull(){
		return code.get();
	}
	public void push(String c){
		code.accumulateAndGet(c, (old, n) -> old+'\n'+n);
	}
}
