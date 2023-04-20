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

package com.hedera.node.app.signature;

import static java.util.concurrent.CompletableFuture.completedFuture;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import edu.umd.cs.findbugs.annotations.NonNull;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ForkJoinPool;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import java.util.stream.Stream;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import org.junit.jupiter.params.provider.ValueSource;

/** Tests for {@link SignatureVerificationResult} */
final class SignatureVerificationResultTest {

    /**
     * Null arguments are not allowed to the constructor.
     */
    @Test
    @DisplayName("Giving a null list to the constructor throws")
    void nullListThrows() {
        // Given a null list, when we pass that null list to the constructor, then it throws an NPE
        //noinspection DataFlowIssue
        assertThatThrownBy(() -> new SignatureVerificationResult(null)).isInstanceOf(NullPointerException.class);
    }

    /**
     * If an empty list of {@link Future}s are given, then the {@link SignatureVerificationResult} is immediately
     * successful.
     */
    @Test
    @DisplayName("Giving an empty list to the constructor is DONE and succeeds")
    void emptyListIsDoneAndSucceeds() {
        // Given an empty list of futures
        final List<Future<Boolean>> emptyList = Collections.emptyList();

        // When we create an instance with that empty list
        final var result = new SignatureVerificationResult(emptyList);

        // Then we find that the SignatureVerificationResult is done, and returns "true" from its get methods
        assertThat(result.isDone()).isTrue();
        assertThat(result).succeedsWithin(1, TimeUnit.MILLISECONDS).isEqualTo(true);
    }

    /**
     * If a list of {@link Future}s are given, then the {@link SignatureVerificationResult} will succeed if each of
     * those {@link Future}s succeed.
     */
    @ParameterizedTest
    @MethodSource("provideSuccessfulFutures")
    @DisplayName("Given multiple elements, the result is successful if all elements are successful")
    void successIfAllFuturesSucceed(@NonNull final List<Future<Boolean>> allSucceed) {
        // When we create an instance with that list
        final var result = new SignatureVerificationResult(allSucceed);

        // Then we find that the SignatureVerificationResult is done, and returns "true" from its get methods
        assertThat(result.isDone()).isTrue();
        assertThat(result).succeedsWithin(1, TimeUnit.MILLISECONDS).isEqualTo(true);
    }

    @Test
    @DisplayName("Get method blocks until all results are available")
    void getBlocksUntilAllResultsAreAvailable() {
        // Given a list of CompletableFutures, which are not yet complete
        final List<CompletableFuture<Boolean>> futures =
                List.of(new CompletableFuture<>(), new CompletableFuture<>(), new CompletableFuture<>());

        // When we create an instance with that list, and then block on the get method
        final var result = new SignatureVerificationResult(futures);
        final var getFuture =
                ForkJoinPool.commonPool().submit(() -> assertThat(result).succeedsWithin(1, TimeUnit.MILLISECONDS));
        assertThat(getFuture).isNotDone();
        futures.get(0).complete(true);
        assertThat(getFuture).isNotDone();
        futures.get(1).complete(true);
        assertThat(getFuture).isNotDone();
        futures.get(2).complete(true);
        assertThat(getFuture).succeedsWithin(1, TimeUnit.MILLISECONDS);
    }

    @Test
    @DisplayName("Get method with timeout expires if at least one result is not available")
    void getTimeoutIfNotAllResultsAreAvailable() {
        // Given a list of CompletableFutures, one of which will never complete
        final List<CompletableFuture<Boolean>> futures =
                List.of(completedFuture(true), completedFuture(true), new CompletableFuture<>());

        // When we create an instance with that list, and then block on the get method with timeout
        // then it never completes because one of the futures never completes
        final var result = new SignatureVerificationResult(futures);
        assertThatThrownBy(() -> result.get(1, TimeUnit.MILLISECONDS)).isInstanceOf(TimeoutException.class);
    }

    @ParameterizedTest
    @MethodSource("provideFailingFutures")
    @DisplayName("Given multiple elements, the result is unsuccessful if any element is unsuccessful")
    void failureIfAnyFutureFails(@NonNull final List<Future<Boolean>> anyFail)
            throws ExecutionException, InterruptedException {
        // When we create an instance with that list
        final var result = new SignatureVerificationResult(anyFail);

        // Then we find that the result is "done" and returns "false" from its get methods
        assertThat(result.isDone()).isTrue();
        assertThat(result).succeedsWithin(1, TimeUnit.MILLISECONDS).isEqualTo(false);
        assertThat(result.get()).isFalse();
    }

    @ParameterizedTest
    @ValueSource(booleans = {true, false})
    @DisplayName("If canceled after done, nothing happens")
    void cancelAfterDoneDoesNothing(final boolean mayInterruptIfRunning) {
        // Given a list of CompletableFutures, which are done
        final List<CompletableFuture<Boolean>> futures = List.of(completedFuture(true));

        // When we create an instance with that list, and then call cancel
        final var result = new SignatureVerificationResult(futures);
        final var wasCanceled = result.cancel(mayInterruptIfRunning);

        // Then we find that it was not canceled and didn't pretend to
        assertThat(wasCanceled).isFalse();
        assertThat(result.isCancelled()).isFalse();
        assertThat(result).isDone();
    }

    @ParameterizedTest
    @ValueSource(booleans = {true, false})
    @DisplayName("If canceled after canceled, nothing happens")
    void cancelAfterCancelDoesNothing(final boolean mayInterruptIfRunning) {
        // Given a list of CompletableFutures, which are NOT done
        final List<CompletableFuture<Boolean>> futures = List.of(new CompletableFuture<>());

        // When we create an instance with that list, and then call cancel twice
        final var result = new SignatureVerificationResult(futures);
        final var wasCanceled1 = result.cancel(mayInterruptIfRunning);
        final var wasCanceled2 = result.cancel(mayInterruptIfRunning);

        // Then we find that the FIRST time it was canceled, but the SECOND time it was already canceled
        assertThat(wasCanceled1).isTrue();
        assertThat(wasCanceled2).isFalse();
        assertThat(result.isCancelled()).isTrue();
        assertThat(result).isDone(); // Even though the future was not "DONE" this future is because of cancel!
    }

    // Question: If we cancel and some of the nested futures cannot be canceled, do we return true or false?
    // FUTURE: A test to show that if mayInterruptIfRunning is true then the thread is interrupted

    public static Stream<Arguments> provideSuccessfulFutures() {
        return Stream.of(
                Arguments.of(List.of(completedFuture(true))),
                Arguments.of(List.of(completedFuture(true), completedFuture(true))),
                Arguments.of(List.of(completedFuture(true), completedFuture(true), completedFuture(true))));
    }

    public static Stream<Arguments> provideFailingFutures() {
        return Stream.of(
                Arguments.of(List.of(completedFuture(false))),
                Arguments.of(List.of(completedFuture(true), completedFuture(false))),
                Arguments.of(List.of(completedFuture(false), completedFuture(true))),
                Arguments.of(List.of(completedFuture(false), completedFuture(false))));
    }
}
