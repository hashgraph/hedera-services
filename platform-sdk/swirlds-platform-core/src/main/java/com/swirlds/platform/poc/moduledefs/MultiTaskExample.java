package com.swirlds.platform.poc.moduledefs;

import com.swirlds.common.threading.interrupt.InterruptableConsumer;
import com.swirlds.platform.poc.infrastructure.MultiTaskProcessor;
import org.apache.commons.lang3.tuple.Pair;

import java.util.List;

public interface MultiTaskExample extends MultiTaskProcessor {
	void number(int num) throws InterruptedException;
	void string(String s) throws InterruptedException;

	@Override
	default List<Pair<Class<?>, InterruptableConsumer<?>>> getProcessingMethods(){
		return List.of(
				Pair.of(
						Integer.class,
						(InterruptableConsumer<Integer>) this::number
				),
				Pair.of(
						String.class,
						(InterruptableConsumer<String>) this::string
				)
		);
	}
}
