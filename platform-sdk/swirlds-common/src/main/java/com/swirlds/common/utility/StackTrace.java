/*
 * Copyright (C) 2022-2023 Hedera Hashgraph, LLC
 *
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
 */

package com.swirlds.common.utility;

/**
 * This class is a convenience wrapper around for the java stack trace.
 */
public record StackTrace(StackTraceElement[] frames) {

    /**
     * Convenience method for getting a stack trace of the current thread.
     *
     * @return a stack grace
     */
    public static StackTrace getStackTrace() {
        return new StackTrace(ignoreFrames(Thread.currentThread().getStackTrace(), 2));
    }

    /**
     * Convenience method for getting the stack trace of a thread.
     *
     * @param thread
     * 		the target thread
     * @return a stack trace
     */
    public static StackTrace getStackTrace(final Thread thread) {
        return new StackTrace(thread.getStackTrace());
    }

    /**
     * Extract a stack trace from a throwable
     *
     * @param t
     * 		a throwable
     * @return a stack trace
     */
    public static StackTrace getStackTrace(final Throwable t) {
        return new StackTrace(t.getStackTrace());
    }

    /**
     * Return a list of stack trace elements, less a few ignored frames at the beginning.
     *
     * @param frames
     * 		an array of frames
     * @param ignoredFrames
     * 		the number of frames to remove from the beginning
     * @return a new array that does not contain the ignored frames
     */
    private static StackTraceElement[] ignoreFrames(final StackTraceElement[] frames, final int ignoredFrames) {
        if (frames.length <= ignoredFrames) {
            return new StackTraceElement[0];
        }
        final StackTraceElement[] reducedFrames = new StackTraceElement[frames.length - ignoredFrames];
        System.arraycopy(frames, ignoredFrames, reducedFrames, 0, reducedFrames.length);
        return reducedFrames;
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public String toString() {
        final StringBuilder sb = new StringBuilder();
        for (final StackTraceElement element : frames) {
            if (element != frames[0]) {
                sb.append("\tat ");
            }
            sb.append(element.getClassName())
                    .append(".")
                    .append(element.getMethodName())
                    .append("(")
                    .append(element.getFileName())
                    .append(":")
                    .append(element.getLineNumber())
                    .append(")")
                    .append("\n");
        }
        return sb.toString();
    }
}
