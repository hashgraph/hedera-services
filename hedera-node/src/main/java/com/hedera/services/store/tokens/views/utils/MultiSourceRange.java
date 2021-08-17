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

import org.apache.commons.lang3.tuple.Pair;

/**
 * A convenience class to help select a sub-list from a sequence of items, where the items in the
 * sequence come from multiple source lists.
 *
 * <p>For example, suppose we want to get the sub-list of eight items with indices {@code [1, 9)}
 * from a 10-item sequence made up of three source lists:
 *
 * <ol>
 *   <li>List {@code A}, which has two items.
 *   <li>List {@code B}, which has three items.
 *   <li>List {@code C}, which has five items.
 * </ol>
 *
 * Then the answer is the concatenation of {@code A.subList(1, 2)} with {@code B.sublist(0, 3)} with
 * {@code C.sublist(0, 4}.
 *
 * <p>This class provides the indices to use when selecting from the sub-lists.
 *
 * <pre>{@code
 * final var multiSourceRange = new MultiSourceRange(1, 9, 2);
 * multiSourceRange.rangeForCurrentSource(); // { 1, 2 } are the indices for our first source
 *
 * multiSourceRange.moveToNewSource(3); // the second source has three items
 * multiSourceRange.rangeForCurrentSource(); // { 0, 3 } are the indices for our second source
 *
 * multiSourceRange.moveToNewSource(5); // the third source has five items
 * multiSourceRange.rangeForCurrentSource(); // { 0, 4 } are the indices for our third source
 *
 * assert multiSourceRange.isRequestedRangeExhausted(); // we have all eight desired items
 *
 * }</pre>
 */
public class MultiSourceRange {
  static final Pair<Integer, Integer> EMPTY_RANGE = Pair.of(0, 0);

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

  /**
   * Indicates if the original requested range has been exhausted, based on the sub-list indices
   * returned from {@link MultiSourceRange#rangeForCurrentSource()} so far.
   *
   * @return if the requested range is exhausted
   */
  public boolean isRequestedRangeExhausted() {
    return endAchieved == end;
  }

  /**
   * Returns the indices to use with the current source to get the next items for the original
   * requested range. Note that these indices may be {@code (0, 0)} and indicate that the current
   * source has no items that belong in the requested range.
   *
   * @return the sub-list indices for the current source
   */
  public Pair<Integer, Integer> rangeForCurrentSource() {
    throwIfSourceExhausted();
    throwIfRangeExhausted();

    Pair<Integer, Integer> ans;
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

  /**
   * Updates the internal state to reflect a new source list of the given size.
   *
   * @param newSourceSize the number of items in the new source list
   */
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

  private Pair<Integer, Integer> range(int lo, int hi) {
    return Pair.of(lo, hi);
  }
}
