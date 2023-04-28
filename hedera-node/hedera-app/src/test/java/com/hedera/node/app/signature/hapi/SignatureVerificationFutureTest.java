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

package com.hedera.node.app.signature.hapi;

import static java.util.concurrent.CompletableFuture.completedFuture;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.junit.jupiter.api.Named.named;

import com.hedera.hapi.node.base.Key;
import com.hedera.hapi.node.base.KeyList;
import com.hedera.hapi.node.state.token.Account;
import com.hedera.node.app.spi.fixtures.Scenarios;
import com.swirlds.common.crypto.TransactionSignature;
import com.swirlds.common.crypto.VerificationStatus;
import edu.umd.cs.findbugs.annotations.NonNull;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Map;
import java.util.Set;
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

/** Tests for {@link SignatureVerificationFuture} */
final class SignatureVerificationFutureTest implements Scenarios {

    /**
     * Null arguments are not allowed to the constructor.
     */
    @Test
    @DisplayName("Giving a null key or map to the constructor throws")
    void nullArgsThrows() {
        final Map<Key, TransactionSignature> sigs = Collections.emptyMap();
        // Given a null key, when we pass that null list to the constructor, then it throws an NPE
        //noinspection DataFlowIssue
        assertThatThrownBy(() -> new SignatureVerificationFuture(null, Account.DEFAULT, sigs))
                .isInstanceOf(NullPointerException.class);
        // Given a null map, when we pass that null list to the constructor, then it throws an NPE
        //noinspection DataFlowIssue
        assertThatThrownBy(() -> new SignatureVerificationFuture(Key.DEFAULT, Account.DEFAULT, null))
                .isInstanceOf(NullPointerException.class);
    }

    /**
     * If a map of {@link TransactionSignature}s are given, then the {@link SignatureVerificationFuture} will succeed
     * if each of those {@link Future}s succeed.
     */
    @ParameterizedTest
    @MethodSource("provideSuccessfulFutures")
    @DisplayName("Given multiple elements, the result is successful if all elements are successful")
    void successIfAllFuturesSucceed(@NonNull final Map<Key, TransactionSignature> allSucceed) {
        // When we create an instance with that key'd map
        final var key = keyListFrom(allSucceed.keySet());
        final var future = new SignatureVerificationFuture(key, null, allSucceed);

        // Then we find that the SignatureVerificationResult is done, and returns "true" from its get methods
        assertThat(future)
                .succeedsWithin(1, TimeUnit.MILLISECONDS)
                .extracting("passed")
                .isEqualTo(true);
        assertThat(future.isDone()).isTrue();
    }

    @Test
    @DisplayName("Get method blocks until all results are available")
    void getBlocksUntilAllResultsAreAvailable() {
        // Given keyed TransactionSignatures which are not yet complete
        final Map<Key, TransactionSignature> sigs = Map.of(
                ALICE.keyInfo().publicKey(), txSig(),
                BOB.keyInfo().publicKey(), txSig(),
                CAROL.keyInfo().publicKey(), txSig());

        // When we create an instance with that list, and then block on the get method
        final var key = keyListFrom(sigs.keySet());
        final var future = new SignatureVerificationFuture(key, null, sigs);
        assertThat(future).isNotDone();
        complete(sigs.get(ALICE.keyInfo().publicKey()), true);
        assertThat(future).isNotDone();
        complete(sigs.get(BOB.keyInfo().publicKey()), true);
        assertThat(future).isNotDone();
        complete(sigs.get(CAROL.keyInfo().publicKey()), true);
        assertThat(future)
                .succeedsWithin(1, TimeUnit.MILLISECONDS)
                .extracting("passed")
                .isEqualTo(true);
        assertThat(future.isDone()).isTrue();
    }

    @Test
    @DisplayName("Get method with timeout expires if at least one result is not available")
    void getTimeoutIfNotAllResultsAreAvailable() {
        // Given keyed TransactionSignatures, one of which will never complete
        final Map<Key, TransactionSignature> sigs = Map.of(
                ALICE.keyInfo().publicKey(), txSig(true),
                BOB.keyInfo().publicKey(), txSig(true),
                CAROL.keyInfo().publicKey(), txSig());

        // When we create an instance with that map, and then block on the get method with timeout
        // then it never completes because one of the futures never completes
        final var key = keyListFrom(sigs.keySet());
        final var future = new SignatureVerificationFuture(key, null, sigs);
        assertThatThrownBy(() -> future.get(1, TimeUnit.MILLISECONDS)).isInstanceOf(TimeoutException.class);
    }

    @ParameterizedTest
    @MethodSource("provideFailingFutures")
    @DisplayName("Given multiple elements, the result is unsuccessful if any element is unsuccessful")
    void failureIfAnyFutureFails(@NonNull final Map<Key, TransactionSignature> anyFail) {
        // When we create an instance with that key'd map
        final var key = keyListFrom(anyFail.keySet());
        final var future = new SignatureVerificationFuture(key, null, anyFail);

        // Then we find that the result is "done" and returns "false" from its get methods
        assertThat(future)
                .succeedsWithin(1, TimeUnit.MILLISECONDS)
                .extracting("passed")
                .isEqualTo(false);
        assertThat(future.isDone()).isTrue();
    }

    @ParameterizedTest
    @ValueSource(booleans = {true, false})
    @DisplayName("If canceled after done, nothing happens")
    void cancelAfterDoneDoesNothing(final boolean mayInterruptIfRunning) {
        // Given keyed TransactionSignatures,where the futures are done
        final Map<Key, TransactionSignature> sigs = Map.of(ALICE.keyInfo().publicKey(), txSig(true));

        // When we create an instance with that keyed map, and then call cancel
        final var key = keyListFrom(sigs.keySet());
        final var future = new SignatureVerificationFuture(key, null, sigs);
        final var wasCanceled = future.cancel(mayInterruptIfRunning);

        // Then we find that it was not canceled and didn't pretend to
        assertThat(wasCanceled).isFalse();
        assertThat(future.isCancelled()).isFalse();
        assertThat(future)
                .succeedsWithin(1, TimeUnit.MILLISECONDS)
                .extracting("passed")
                .isEqualTo(true);
        assertThat(future.isDone()).isTrue();
    }

    @ParameterizedTest
    @ValueSource(booleans = {true, false})
    @DisplayName("If canceled after canceled, nothing happens")
    void cancelAfterCancelDoesNothing(final boolean mayInterruptIfRunning) {
        // Given keyed TransactionSignatures,where the futures are NOT done
        final Map<Key, TransactionSignature> sigs = Map.of(ALICE.keyInfo().publicKey(), txSig());

        // When we create an instance with that map, and then call cancel twice
        final var key = keyListFrom(sigs.keySet());
        final var future = new SignatureVerificationFuture(key, null, sigs);
        final var wasCanceled1 = future.cancel(mayInterruptIfRunning);
        final var wasCanceled2 = future.cancel(mayInterruptIfRunning);

        // Then we find that the FIRST time it was canceled, but the SECOND time it was already canceled
        assertThat(wasCanceled1).isTrue();
        assertThat(wasCanceled2).isFalse();
        assertThat(future.isCancelled()).isTrue();
        assertThat(future.isDone()).isTrue(); // Even though the future was not "DONE" this future is because of cancel!
    }

    // Question: If we cancel and some of the nested futures cannot be canceled, do we return true or false?
    // FUTURE: A test to show that if mayInterruptIfRunning is true then the thread is interrupted

    private static Key keyListFrom(@NonNull final Set<Key> keys) {
        return Key.newBuilder()
                .keyList(KeyList.newBuilder().keys(new ArrayList<>(keys)))
                .build();
    }

    public static Stream<Arguments> provideSuccessfulFutures() {
        return Stream.of(
                Arguments.of(named("Keys: Alice", Map.of(ALICE.keyInfo().publicKey(), txSig(true)))),
                Arguments.of(named(
                        "Keys: Alice, Bob",
                        Map.of(
                                ALICE.keyInfo().publicKey(), txSig(true),
                                BOB.keyInfo().publicKey(), txSig(true)))),
                Arguments.of(named(
                        "Keys: Alice, Bob, Carol",
                        Map.of(
                                ALICE.keyInfo().publicKey(), txSig(true),
                                BOB.keyInfo().publicKey(), txSig(true),
                                CAROL.keyInfo().publicKey(), txSig(true)))));
    }

    public static Stream<Arguments> provideFailingFutures() {
        return Stream.of(
                Arguments.of(named("Keys: Alice (bad)", Map.of(ALICE.keyInfo().publicKey(), txSig(false)))),
                Arguments.of(named(
                        "Keys: Alice (good), Bob (bad)",
                        Map.of(
                                ALICE.keyInfo().publicKey(), txSig(true),
                                BOB.keyInfo().publicKey(), txSig(false)))),
                Arguments.of(named(
                        "Keys: Alice (bad), Bob (good)",
                        Map.of(
                                ALICE.keyInfo().publicKey(), txSig(false),
                                BOB.keyInfo().publicKey(), txSig(true)))),
                Arguments.of(named(
                        "Keys: Alice (bad), Bob (bad)",
                        Map.of(
                                ALICE.keyInfo().publicKey(), txSig(false),
                                BOB.keyInfo().publicKey(), txSig(false)))));
    }

    public static TransactionSignature txSig(boolean passed) {
        final var txSig = new TransactionSignature(new byte[3], 0, 1, 1, 1, 2, 1);
        complete(txSig, passed);
        return txSig;
    }

    public static TransactionSignature txSig() {
        return new TransactionSignature(new byte[3], 0, 1, 1, 1, 2, 1);
    }

    public static void complete(@NonNull final TransactionSignature txSig, final boolean passed) {
        txSig.setFuture(completedFuture(null));
        txSig.setSignatureStatus(passed ? VerificationStatus.VALID : VerificationStatus.INVALID);
    }
}
