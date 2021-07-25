package com.hedera.services.store.tokens.views.utils;

/*-
 * ‌
 * Hedera Services Node
 * ​
 * Copyright (C) 2018 - 2021 Hedera Hashgraph, LLC
 * ​
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 * 
 *      http://www.apache.org/licenses/LICENSE-2.0
 * 
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 * ‍
 */

public class MultiSourceRange {
	public static final int[] EMPTY_RANGE = new int[0];

	private final int start;
	private final int end;
	private int currentSourceSize;
	private int currentSourceStart;
	private int endAchieved;
	private boolean currentSourceIsExhausted;

	public MultiSourceRange(int start, int end, int firstSourceSize) {
		this.end = end;
		this.start = start;
		this.currentSourceSize = firstSourceSize;

		currentSourceIsExhausted = false;
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
		currentSourceIsExhausted = true;
		return ans;
	}

	public void moveToNewSource(int newSourceSize) {
		throwIfRangeExhausted();

		currentSourceStart += currentSourceSize;
		currentSourceSize = newSourceSize;
		currentSourceIsExhausted = false;
	}

	private void throwIfRangeExhausted() {
		if (isRequestedRangeExhausted()) {
			throw new IllegalStateException("The requested range is already exhausted");
		}
	}

	private void throwIfSourceExhausted() {
		if (currentSourceIsExhausted) {
			throw new IllegalStateException("The current source is already exhausted");
		}
	}

	private int[] range(int lo, int hi) {
		return new int[] { lo, hi };
	}
}
