package com.swirlds.platform.componentframework;

public class StringIntImpl implements StringIntProcessor{
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
