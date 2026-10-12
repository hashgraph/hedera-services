// SPDX-License-Identifier: Apache-2.0
package org.hiero.base.concurrent;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import org.junit.jupiter.api.Test;

class ResettableLatchTest {

    @Test
    void testAwaitBlocksUntilArmed() throws Exception {
        // given
        final ResettableLatch latch = new ResettableLatch();
        final CompletableFuture<Void> waiter = CompletableFuture.runAsync(() -> awaitUninterruptibly(latch));

        // then
        assertThat(waiter).failsWithin(100, TimeUnit.MILLISECONDS).withThrowableOfType(TimeoutException.class);

        // when
        latch.arm();

        // then
        waiter.get(1, TimeUnit.SECONDS);
        assertThat(latch.isArmed()).isTrue();
    }

    @Test
    void testAwaitReturnsImmediatelyWhenArmed() throws Exception {
        // given
        final ResettableLatch latch = new ResettableLatch();
        latch.arm();

        // when
        final CompletableFuture<Void> waiter = CompletableFuture.runAsync(() -> awaitUninterruptibly(latch));

        // then
        waiter.get(1, TimeUnit.SECONDS);
    }

    @Test
    void testRepeatedArmCollapsesIntoOneDisarm() {
        // given
        final ResettableLatch latch = new ResettableLatch();
        latch.arm();
        latch.arm();

        // when
        latch.disarm();

        // then
        assertThat(latch.isArmed()).isFalse();
        final CompletableFuture<Void> waiter = CompletableFuture.runAsync(() -> awaitUninterruptibly(latch));
        assertThat(waiter).failsWithin(100, TimeUnit.MILLISECONDS).withThrowableOfType(TimeoutException.class);
        latch.arm();
    }

    private static void awaitUninterruptibly(final ResettableLatch latch) {
        try {
            latch.awaitArmed();
        } catch (final InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new RuntimeException(e);
        }
    }
}
