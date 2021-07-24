package com.hedera.services.store.tokens.utils;

public class MultiSourceRange {
	public static final int[] EMPTY_RANGE = new int[0];

	private final int start;
	private final int end;
	private int currentSourceSize;
	private int currentSourceStart;
	private int endAchieved;
	private boolean currentSourceExhausted;

	public MultiSourceRange(int start, int end, int firstSourceSize) {
		this.end = end;
		this.start = start;
		this.currentSourceSize = firstSourceSize;

		currentSourceExhausted = false;
		currentSourceStart = 0;
		endAchieved = start;
	}

	public boolean isRequestedRangeExhausted() {
		return endAchieved == end;
	}

	public int[] rangeForCurrentSource() {
		throwIfSourceExhausted();
		throwIfRangeExhausted();

		int[] ans;
		final var endOfCurrentSource = currentSourceStart + currentSourceSize;
		if (endOfCurrentSource > start) {
			final var sourceEnd = Math.min(endOfCurrentSource, end);
			ans = range(endAchieved - currentSourceStart, sourceEnd - currentSourceStart);
			endAchieved = sourceEnd;
		} else {
			ans = EMPTY_RANGE;
		}
		currentSourceExhausted = true;
		return ans;
	}

	public void moveToNewSource(int newSourceSize) {
		throwIfRangeExhausted();

		currentSourceStart += currentSourceSize;
		currentSourceSize = newSourceSize;
		currentSourceExhausted = false;
	}

	private void throwIfRangeExhausted() {
		if (isRequestedRangeExhausted()) {
			throw new IllegalStateException("The requested range is already exhausted");
		}
	}

	private void throwIfSourceExhausted() {
		if (currentSourceExhausted) {
			throw new IllegalStateException("The current source is already exhausted");
		}
	}

	private int[] range(int lo, int hi) {
		return new int[] { lo, hi };
	}
}
