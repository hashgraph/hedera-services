package com.swirlds.platform.modules;

import com.swirlds.common.threading.interrupt.InterruptableConsumer;

public interface Module1 {
	default InterruptableConsumer<String> getLambda(){
		return this::addTask;
	}
	
	void addTask(String task);
}
