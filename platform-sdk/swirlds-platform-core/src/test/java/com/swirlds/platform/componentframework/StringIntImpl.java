package com.swirlds.platform.componentframework;

/**
 * A test {@link StringIntProcessor} that converts {@link String}s and {@link Integer}s to {@link Long}s and passes
 * them to a {@link LongProcessor}.
 */
public class StringIntImpl implements StringIntProcessor {
	private final LongProcessor longProcessor;

	public StringIntImpl(final LongProcessor longProcessor) {
		this.longProcessor = longProcessor;
	}

	@Override
	public void string(final String s) throws InterruptedException {
		longProcessor.processLong(Long.parseLong(s));
	}

	@Override
	public void number(final Integer i) throws InterruptedException {
		longProcessor.processLong(i);
	}
}
