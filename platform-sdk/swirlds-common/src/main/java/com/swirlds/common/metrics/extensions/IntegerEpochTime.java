package com.swirlds.common.metrics.extensions;

import com.swirlds.common.time.Time;
import com.swirlds.common.utility.Units;

/**
 * A wrapper around a {@link Time} instance that provides the lower 31 bits of the epoch, and calculates elapsed time
 */
public class IntegerEpochTime {
	private final Time time;

	public IntegerEpochTime(final Time time) {
		this.time = time;
	}

	/**
	 * @return the lower 31 bits of the current millisecond epoch according to the clock stored in this instance
	 */
	public int getMilliTime() {
		return (int) (time.currentTimeMillis() % Integer.MAX_VALUE);
	}

	public int getMicroTime() {
		return (int) ((time.nanoTime() / Units.MICROSECONDS_TO_NANOSECONDS) % Integer.MAX_VALUE);
	}

	public int millisElapsed(final int startTime) {
		return elapsed(startTime, getMilliTime());
	}

	public int microsElapsed(final int startTime) {
		return elapsed(startTime, getMicroTime());
	}

	public static int elapsed(final int startTime, final int currentTime){
		if (currentTime >= startTime) {
			return currentTime - startTime;
		} else {
			// if the lower 31 bits of the epoch has rolled over, the start time will be bigger than current time
			return Integer.MAX_VALUE - startTime + currentTime;
		}
	}

}
