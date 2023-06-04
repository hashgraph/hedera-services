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

/* This code adapted from https://github.com/favware/java-result, by https://favware.tech,
  released under the MIT license as follows:

       The MIT License (MIT)

       Copyright © `2023` `Favware` (Jeroen Claassens)

       Permission is hereby granted, free of charge, to any person
       obtaining a copy of this software and associated documentation
       files (the “Software”), to deal in the Software without
       restriction, including without limitation the rights to use,
       copy, modify, merge, publish, distribute, sublicense, and/or sell
       copies of the Software, and to permit persons to whom the
       Software is furnished to do so, subject to the following
       conditions:

       The above copyright notice and this permission notice shall be
       included in all copies or substantial portions of the Software.

       THE SOFTWARE IS PROVIDED “AS IS”, WITHOUT WARRANTY OF ANY KIND,
       EXPRESS OR IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES
       OF MERCHANTABILITY, FITNESS FOR A PARTICULAR PURPOSE AND
       NONINFRINGEMENT. IN NO EVENT SHALL THE AUTHORS OR COPYRIGHT
       HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER LIABILITY,
       WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING
       FROM, OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR
       OTHER DEALINGS IN THE SOFTWARE.
*/

import edu.umd.cs.findbugs.annotations.NonNull;
import edu.umd.cs.findbugs.annotations.Nullable;
import java.util.NoSuchElementException;
import java.util.Objects;
import java.util.Optional;
import java.util.function.Function;
import java.util.function.Predicate;
import java.util.function.Supplier;

/**
 * Monadic {@link Result} type.
 * Represents a result type that could have succeeded with type {@link T} or errored with a {@link Throwable}.
 *
 * @param <T> The type that is wrapped by {@link Result}
 */
public interface Result<T> {

    /**
     * Creates a new {@link Result} of a method that might throw an {@link Exception}
     *
     * @param f The function that might throw an exception
     * @param <U> The type of the result if ok
     * @return A new {@link Result}
     */
    @NonNull
    static <U> Result<U> from(@NonNull final ResultSupplier<U> f) {
        Objects.requireNonNull(f);

        try {
            return Result.ok(f.get());
        } catch (Throwable t) {
            return Result.err(t);
        }
    }

    /**
     * Factory method for err.
     *
     * @param e throwable to create the errored {@link Result} with
     * @param <U> Type
     * @return a new {@link Err}
     */
    @NonNull
    static <U> Result<U> err(@NonNull final Throwable e) {
        return new Err<>(e);
    }

    /**
     * Factory method for {@link Ok}.
     *
     * @param x value to create the ok {@link Result} with
     * @param <U> Type
     * @return a new {@link Ok}
     */
    @NonNull
    static <U> Result<U> ok(@Nullable final U x) {
        return new Ok<>(x);
    }

    /**
     * Creates a new {@link Result} of an {@link Optional}
     *
     * @param op The {@link Optional} to convert to a {@link Result}
     * @param e The {@link Throwable} to use if the {@link Optional} is not {@link Optional#isPresent()}
     * @param <U> The type of the result if ok, this gets wrapped in an {@link Optional}
     * @return A {@link Ok} if the {@link  Optional} is {@link Optional#isPresent()} or a {@link Err} if the {@link Optional} is not {@link Optional#isPresent()}
     */
    @NonNull
    static <U> Result<U> ofOptional(@NonNull final Optional<U> op, @NonNull final Throwable e) {
        if (op.isPresent()) {
            return new Ok<>(op.get());
        } else {
            return new Err<>(e);
        }
    }

    /**
     * Creates a new {@link Result} of an {@link Optional}
     *
     * @param op The {@link Optional} to convert to a {@link Result}
     * @param <U> The type of the result if ok, this gets wrapped in an {@link Optional}
     * @return A {@link Ok} if the {@link  Optional} is {@link Optional#isPresent()} or a {@link Err} if the {@link Optional} is not {@link Optional#isPresent()}
     */
    @NonNull
    static <U> Result<U> ofOptional(@NonNull final Optional<U> op) {
        return ofOptional(op, new IllegalArgumentException("Missing Value"));
    }

    /**
     * @return true if the {@link Result} is {@link Ok}
     */
    boolean isOk();

    /**
     * Unwraps the value {@link T} on {@link Ok} or throws the cause of the err wrapped into a {@link RuntimeException}
     *
     * @return T
     * @throws RuntimeException The exception that caused the err
     */
    @Nullable
    T unwrapUnchecked();

    /**
     * Transform {@link Ok} or pass on {@link Err}.
     * Takes an optional type parameter of the new type.
     * You need to be specific about the new type if changing type
     * <pre>
     * {@code Result.from(() -> "1").<Integer>map((x) -> Integer.valueOf(x))}
     * </pre>
     *
     * @param f function to apply to ok value.
     * @param <U> new type (optional)
     * @return {@link Ok}{@code <}{@link U}{@code >} or {@link Err}{@code <}{@link U}{@code >}
     */
    @NonNull
    <U> Result<U> map(@NonNull final ResultMapFunction<? super T, ? extends U> f);

    /**
     * Transform {@link Ok} or pass on {@link Err}, taking a {@link Result}{@code <}{@link U}{@code >} as the result.
     * Takes an optional type parameter of the new type.
     * You need to be specific about the new type if changing type.
     * <pre>
     * {@code
     *   Result.from(() -> "1").<Integer>flatMap((x) -> Result.from(() -> Integer.valueOf(x)))
     *   // returns Integer(1)
     * }
     * </pre>
     *
     * @param f function to apply to ok value.
     * @param <U> new type (optional)
     * @return new composed {@link Result}
     */
    @NonNull
    <U> Result<U> flatMap(@NonNull final ResultMapFunction<? super T, Result<U>> f);

    /**
     * Specifies a result to use in case of err.
     * Gives access to the exception which can be used for a pattern match.
     * <pre>
     * {@code
     * Result.from(() -> "not a number")
     * .<Integer>flatMap((x) -> Result.from(() -> Integer.valueOf(x)))
     * .recover((t) -> 1)
     * // returns Integer(1)
     * }
     * </pre>
     *
     * @param f function to execute on ok result.
     * @return new composed {@link Result}
     */
    @Nullable
    T recover(@NonNull final Function<? super Throwable, T> f);

    /**
     * Try applying <code>f(t)</code> on the case of err.
     *
     * @param f function that takes throwable and returns result
     * @return a new {@link Result} in the case of {@link Err}, or the current {@link Ok}.
     */
    @NonNull
    Result<T> recoverWith(@NonNull final ResultMapFunction<? super Throwable, Result<T>> f);

    /**
     * Return a value in the case of a {@link Err}.
     * This is similar to recover but does not expose the exception type.
     *
     * @param value return the result's value or else the value specified.
     * @return new composed {@link Result}
     */
    @Nullable
    T orElse(@Nullable final T value);

    /**
     * Return another {@link Result} in the case of {@link Err}.
     * Like {@link Result#recoverWith} but without exposing the exception.
     *
     * @param f return the value or the value from the new {@link Result}.
     * @return new composed {@link Result}
     */
    @NonNull
    Result<T> orElseResult(@NonNull final ResultSupplier<T> f);

    /**
     * Unwraps the value {@link T} on {@link Ok} or throws the cause of the {@link Err}.
     *
     * @param <X> the type of the exception to be thrown
     * @param exceptionSupplier supplier function to produce the exception to be thrown
     * @return T
     * @throws X produced by the supplier function argument
     */
    @Nullable
    <X extends Throwable> T orElseThrow(@NonNull final Supplier<? extends X> exceptionSupplier) throws X;

    /**
     * Unwraps the value {@link T} on {@link Ok} or throws the cause of the {@link Err}.
     *
     * @return T
     * @throws Throwable The error that caused the {@link Err}
     */
    @SuppressWarnings("java:S112") // "generic exceptions should never be thrown"
    @Nullable
    T unwrap() throws Throwable;

    /**
     * Performs the provided action, when ok
     *
     * @param <E> the type of the exception to be thrown
     * @param action action to run
     * @return new composed {@link Result}
     * @throws E if the action throws an exception
     */
    @NonNull
    <E extends Throwable> Result<T> onOk(@NonNull final ResultConsumer<T, E> action) throws E;

    /**
     * Performs the provided action, when {@link Err}
     *
     * @param <E> the type of the exception to be thrown
     * @param action action to run
     * @return new composed {@link Result}
     * @throws E if the action throws an exception
     */
    @NonNull
    <E extends Throwable> Result<T> onErr(@NonNull final ResultConsumer<Throwable, E> action) throws E;

    /**
     * When the <code>clazz</code> {@link Err} type happens, that exception is thrown
     *
     * @param <E> the type of the exception to be thrown
     * @param clazz the expected exception class
     * @return new composed {@link Result}
     * @throws E if the {@link Err} type is the on provided
     */
    @NonNull
    <E extends Throwable> Result<T> raise(@NonNull final Class<E> clazz) throws E;

    /**
     * If a {@link Result} is a {@link Ok} and the predicate holds true, the {@link Ok} is passed further.
     * Otherwise, ({@link Err} or predicate doesn't hold), pass {@link Err}.
     *
     * @param pred predicate applied to the value held by {@link Result}
     * @return For {@link Ok}, the same {@link Ok} if predicate holds true, otherwise {@link Err}
     */
    @NonNull
    Result<T> filter(@NonNull final Predicate<T> pred);

    /**
     * {@link Result} contents wrapped in {@link Optional}.
     *
     * @return {@link Optional} of {@link T}, if {@link Ok}, Empty if {@link Err} or null value
     */
    @NonNull
    Optional<T> toOptional();

    /**
     * This is similar to the Java {@link java.util.function.Consumer Consumer} function type.
     * It has a checked exception on it to allow it to be used in lambda expressions on the {@link Result} monad.
     *
     * @param <T> The type that is wrapped by this {@link ResultConsumer}
     * @param <E> the type of throwable thrown by {@link #accept(Object)}
     */
    interface ResultConsumer<T, E extends Throwable> {

        /**
         * Performs this operation on the given argument.
         *
         * @param t the input argument
         * @throws E the type of throwable thrown by this method
         */
        void accept(@Nullable T t) throws E;
    }

    /**
     * @param <T> the type of the input to the function
     * @param <R> the type of the result of the function
     */
    interface ResultMapFunction<T, R> {

        /**
         * @param t the input argument
         * @return the result of the function
         * @throws Throwable the type of throwable thrown by this method
         */
        @SuppressWarnings("java:S112") // "generic exceptions should never be thrown"
        @NonNull
        R apply(@Nullable T t) throws Throwable;
    }

    /**
     * This is similar to the Java Supplier function type.
     * It has a checked exception on it to allow it to be used in lambda expressions on the Try monad.
     *
     * @param <T> The type that is wrapped by this {@link ResultSupplier}
     */
    interface ResultSupplier<T> {

        /**
         * @return the result of the operation
         * @throws Throwable the type of throwable thrown by this method
         */
        @SuppressWarnings("java:S112") // "generic exceptions should never be thrown"
        @Nullable
        T get() throws Throwable;
    }
}

class Ok<T> implements Result<T> {

    private final T value;

    Ok(@Nullable T value) {
        this.value = value;
    }

    @Override
    public boolean isOk() {
        return true;
    }

    @Override
    @Nullable
    public T unwrapUnchecked() {
        return value;
    }

    @NonNull
    @Override
    public <U> Result<U> flatMap(@NonNull final ResultMapFunction<? super T, Result<U>> f) {
        Objects.requireNonNull(f);
        try {
            return f.apply(value);
        } catch (Throwable t) {
            return Result.err(t);
        }
    }

    @Override
    public T recover(@NonNull Function<? super Throwable, T> f) {
        Objects.requireNonNull(f);
        return value;
    }

    @NonNull
    @Override
    public Result<T> recoverWith(@NonNull final ResultMapFunction<? super Throwable, Result<T>> f) {
        Objects.requireNonNull(f);
        return this;
    }

    @Nullable
    @Override
    public T orElse(@Nullable T value) {
        return this.value;
    }

    @Override
    @NonNull
    public Result<T> orElseResult(@NonNull final ResultSupplier<T> f) {
        Objects.requireNonNull(f);
        return this;
    }

    @Override
    @Nullable
    public <X extends Throwable> T orElseThrow(@NonNull final Supplier<? extends X> exceptionSupplier) throws X {
        return value;
    }

    @Override
    @SuppressWarnings("java:S4144") // "methods should not have identical implementations"
    public T unwrap() throws Throwable {
        return value;
    }

    @NonNull
    @Override
    public <U> Result<U> map(@NonNull final ResultMapFunction<? super T, ? extends U> f) {
        Objects.requireNonNull(f);
        try {
            return new Ok<>(f.apply(value));
        } catch (Throwable t) {
            return Result.err(t);
        }
    }

    @NonNull
    @Override
    public <E extends Throwable> Result<T> onOk(@NonNull final ResultConsumer<T, E> action) throws E {
        action.accept(value);
        return this;
    }

    @NonNull
    @Override
    public Result<T> filter(@NonNull final Predicate<T> p) {
        Objects.requireNonNull(p);

        if (p.test(value)) {
            return this;
        } else {
            return Result.err(new NoSuchElementException("Predicate does not match for " + value));
        }
    }

    @NonNull
    @Override
    public Optional<T> toOptional() {
        return Optional.ofNullable(value);
    }

    @NonNull
    @Override
    public <E extends Throwable> Result<T> onErr(@NonNull final ResultConsumer<Throwable, E> action) {
        return this;
    }

    @NonNull
    @Override
    public <E extends Throwable> Result<T> raise(@NonNull final Class<E> clazz) throws E {
        return this;
    }
}

class Err<T> implements Result<T> {

    private final Throwable e;

    Err(@NonNull final Throwable e) {
        this.e = e;
    }

    @Override
    public boolean isOk() {
        return false;
    }

    @Override
    @Nullable
    @SuppressWarnings("java:S112") // "generic exceptions should never be thrown"
    public T unwrapUnchecked() {
        if (e instanceof RuntimeException re) {
            throw re;
        } else {
            throw new RuntimeException(e);
        }
    }

    @NonNull
    @Override
    public <U> Result<U> map(@NonNull final ResultMapFunction<? super T, ? extends U> f) {
        Objects.requireNonNull(f);
        return Result.err(e);
    }

    @NonNull
    @Override
    public <U> Result<U> flatMap(@NonNull final ResultMapFunction<? super T, Result<U>> f) {
        Objects.requireNonNull(f);
        return Result.err(e);
    }

    @Override
    @Nullable
    public T recover(@NonNull final Function<? super Throwable, T> f) {
        Objects.requireNonNull(f);
        return f.apply(e);
    }

    @NonNull
    @Override
    public Result<T> recoverWith(@NonNull final ResultMapFunction<? super Throwable, Result<T>> f) {
        Objects.requireNonNull(f);
        try {
            return f.apply(e);
        } catch (Throwable t) {
            return Result.err(t);
        }
    }

    @Override
    @Nullable
    public T orElse(@Nullable T value) {
        return value;
    }

    @Override
    @NonNull
    public Result<T> orElseResult(@NonNull final ResultSupplier<T> f) {
        Objects.requireNonNull(f);
        return Result.from(f);
    }

    @Override
    @Nullable
    public <X extends Throwable> T orElseThrow(@NonNull final Supplier<? extends X> exceptionSupplier) throws X {
        throw exceptionSupplier.get();
    }

    @Override
    @Nullable
    public T unwrap() throws Throwable {
        throw e;
    }

    @NonNull
    @Override
    public <E extends Throwable> Result<T> onOk(@NonNull final ResultConsumer<T, E> action) {
        return this;
    }

    @NonNull
    @Override
    public Result<T> filter(@NonNull final Predicate<T> pred) {
        return this;
    }

    @NonNull
    @Override
    public Optional<T> toOptional() {
        return Optional.empty();
    }

    @NonNull
    @Override
    public <E extends Throwable> Result<T> onErr(@NonNull final ResultConsumer<Throwable, E> action) throws E {
        action.accept(e);
        return this;
    }

    @NonNull
    @Override
    @SuppressWarnings("unchecked")
    public <E extends Throwable> Result<T> raise(@NonNull final Class<E> clazz) throws E {
        return onErr(t -> {
            if (clazz.isAssignableFrom(Objects.requireNonNull(t).getClass())) {
                throw (E) t;
            }
        });
    }
}
