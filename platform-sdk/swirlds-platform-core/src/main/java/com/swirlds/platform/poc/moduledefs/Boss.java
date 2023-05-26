package com.swirlds.platform.poc.moduledefs;

import com.swirlds.common.threading.interrupt.InterruptableConsumer;
import com.swirlds.platform.poc.framework.MultiTaskProcessor;
import org.apache.commons.lang3.tuple.Pair;

import java.util.List;
import java.util.Map;

public interface Boss extends MultiTaskProcessor {
	void requestPto(int days) throws InterruptedException;
	void deliverFeature(String s) throws InterruptedException;

	@Override
	default List<Pair<Class<?>, InterruptableConsumer<?>>> getProcessingMethods(){
		return List.of(
				Pair.of(
						Integer.class,
						(InterruptableConsumer<Integer>) this::requestPto
				),
				Pair.of(
						String.class,
						(InterruptableConsumer<String>) this::deliverFeature
				)
		);
	}

	default void aaa(){
		Map.of(
				Integer.class,
				(InterruptableConsumer<Integer>) this::requestPto,
				String.class,
				(InterruptableConsumer<String>) this::deliverFeature
		);
	}
}
