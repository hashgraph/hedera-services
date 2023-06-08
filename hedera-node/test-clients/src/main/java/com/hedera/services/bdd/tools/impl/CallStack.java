/*
 * Copyright (C) 2023 Hedera Hashgraph, LLC
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

package com.hedera.services.bdd.tools.impl;

import com.google.common.collect.ImmutableList;
import edu.umd.cs.findbugs.annotations.NonNull;
import java.lang.StackWalker.Option;
import java.lang.StackWalker.StackFrame;
import java.lang.reflect.Method;
import java.util.Optional;
import java.util.Set;
import java.util.function.Predicate;

/** Utilities to inspect the callstack */
public class CallStack {

    final ImmutableList<StackFrame> frames;

    CallStack(@NonNull final ImmutableList<StackFrame> frames) {
        this.frames = frames;
    }

    public static CallStack grabFrames(final int nTopmostFrames) {
        return new CallStack(StackWalker.getInstance(Option.RETAIN_CLASS_REFERENCE)
                .walk(s -> s.limit(nTopmostFrames).collect(ImmutableList.toImmutableList())));
    }

    public static CallStack grabFramesUpToButNotIncluding(@NonNull final Class<?> klass) {
        return new CallStack(StackWalker.getInstance(Option.RETAIN_CLASS_REFERENCE)
                .walk(s -> s.takeWhile(sf -> !(sf.getDeclaringClass().equals(klass)))
                        .collect(ImmutableList.toImmutableList())));
    }

    public int size() {
        return frames.size();
    }

    @NonNull
    public ImmutableList<StackFrame> frames() {
        return frames;
    }

    @NonNull
    public StackFrame get(final int n) {
        if (n < 0 || n >= frames.size())
            throw new IndexOutOfBoundsException("frame #%d out of bounds [0..%d)".formatted(n, frames.size()));
        return frames.get(n);
    }

    @NonNull
    public Class<?> getDeclaringClassOfFrame(final int n) {
        return get(n).getDeclaringClass();
    }

    @NonNull
    public Optional<StackFrame> getTopmostFrameOfClassSatisfying(
            @NonNull final Class<?> klass, @NonNull final Predicate<StackFrame> predicate) {
        return getTopmostFrameSatisfying(f -> f.getDeclaringClass().equals(klass) && predicate.test(f));
    }

    @NonNull
    public Optional<StackFrame> getTopmostFrameOfAnyOfTheseClassesSatisfying(
            @NonNull final Set<Class<?>> klasses, @NonNull final Predicate<StackFrame> predicate) {
        return getTopmostFrameSatisfying(f -> klasses.contains(f.getDeclaringClass()) && predicate.test(f));
    }

    @NonNull
    public Optional<StackFrame> getTopmostFrameOfClass(@NonNull final Class<?> klass) {
        return getTopmostFrameSatisfying(f -> f.getDeclaringClass().equals(klass));
    }

    @NonNull
    public Optional<StackFrame> getTopmostFrameOfAnyOfTheseClasses(@NonNull Set<Class<?>> klasses) {
        return getTopmostFrameSatisfying(f -> klasses.contains(f.getDeclaringClass()));
    }

    @NonNull
    public Optional<StackFrame> getTopmostFrameSatisfying(@NonNull final Predicate<StackFrame> predicate) {
        return frames.stream().dropWhile(sf -> !predicate.test(sf)).findFirst();
    }

    /** Returns a frame at a relative offset to the given frame.  N.B.: offset is POSITIVE to go deeper in
     * the stack (away from top) and NEGATIVE to go closer to top.
     */
    public enum Towards {
        TOP,
        BASE
    }

    @NonNull
    public StackFrame frameRelativeTo(@NonNull final StackFrame frame, @NonNull Towards direction, int offset) {
        if (offset < 0)
            throw new IllegalArgumentException(
                    "frame offset must be positive (chose direction with `Towards` parameter)");
        if (direction == Towards.TOP) offset = -offset;
        return get(find(frame) + offset);
    }

    public int find(@NonNull final StackFrame frame) {
        for (int i = 0; i < size(); i++) if (get(i) == frame) return i;
        throw new IllegalArgumentException("frame not found in callstack: %s".formatted(frame));
    }

    /** Given a stack frame return a `java.lang.reflect.Method` for its method.
     *
     * (For some reason you can't get that easily via the API, you can only get the `MethodType` which is not as
     * powerful.)
     */
    @NonNull
    public static Method getMethodFromFrame(@NonNull final StackFrame frame) {
        final var klass = frame.getDeclaringClass();
        final var methodName = frame.getMethodName();
        final var methodSignature = frame.getMethodType();
        final var methodParameters = methodSignature.parameterArray();
        Method method;
        try {
            method = klass.getDeclaredMethod(methodName, methodParameters);
        } catch (final NoSuchMethodException | SecurityException ex) {
            throw new IllegalStateException(
                    "can't find method in class '%s.%s'".formatted(klass.getName(), methodName), ex);
        }
        return method;
    }

    @NonNull
    public Method getMethodFromFrame(final int n) {
        return getMethodFromFrame(get(n));
    }

    public enum WithLineNumbers {
        NO,
        YES
    }

    /** Debugging routine to dump the stack frames found by the stackwalker */
    @NonNull
    public String dump(@NonNull final WithLineNumbers withLineNumbers) {
        final var sb = new StringBuilder(10000);
        sb.append("stack (depth captured: %d)%n".formatted(frames.size()));
        for (int i = 0; i < frames.size(); i++) {
            final var frame = frames.get(i);
            sb.append("%2d: %s%n".formatted(i, dump(frame, withLineNumbers)));
        }
        return sb.toString();
    }

    /** Debugging routine to dump a single `StackFrame` */
    @NonNull
    public static String dump(@NonNull final StackFrame frame, @NonNull final WithLineNumbers withLineNumbers) {
        return (withLineNumbers == WithLineNumbers.YES ? "%s.%s (%d)" : "%s.%s")
                .formatted(frame.getClassName(), frame.getMethodName(), frame.getLineNumber());
    }
}
