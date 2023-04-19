package com.hedera.node.app.signature;

import edu.umd.cs.findbugs.annotations.NonNull;
import java.util.List;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import static java.util.Objects.requireNonNull;

public class SignatureVerificationResult implements Future<Boolean> {
    private final List<Future<Boolean>> futures;

    public SignatureVerificationResult(@NonNull final List<Future<Boolean>> futures) {
        this.futures = requireNonNull(futures);
    }

    @Override
    public boolean cancel(boolean mayInterruptIfRunning) {
        throw new UnsupportedOperationException("Not implemented yet!");
    }

    @Override
    public boolean isCancelled() {
        throw new UnsupportedOperationException("Not implemented yet!");
    }

    @Override
    public boolean isDone() {
        throw new UnsupportedOperationException("Not implemented yet!");
    }

    @Override
    public Boolean get() throws InterruptedException, ExecutionException {
        var passed = true;
        for (final var future : futures) {
            if (passed) {
                passed = future.get();
            } else {
                future.cancel(true);
            }
        }
        return passed;
    }

    @Override
    public Boolean get(long timeout, TimeUnit unit) throws InterruptedException, ExecutionException, TimeoutException {
        var millisRemaining = unit.toMillis(timeout);
        var passed = true;
        for (final var future : futures) {
            if (passed) {
                final var now = System.currentTimeMillis();
                passed = future.get(millisRemaining, TimeUnit.MILLISECONDS);
                millisRemaining -= System.currentTimeMillis() - now;
            } else {
                future.cancel(true);
            }
        }
        return passed;
    }
}
