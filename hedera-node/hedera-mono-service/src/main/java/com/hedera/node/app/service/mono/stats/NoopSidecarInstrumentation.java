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

package com.hedera.node.app.service.mono.stats;

import com.hedera.services.stream.proto.SidecarType;
import edu.umd.cs.findbugs.annotations.NonNull;
import java.io.Closeable;
import java.time.Duration;

/**
 * A noop (aka "do nothing") implementation of sidecar instrumentation suitable for tests or other
 * contexts where a sidecar instrumentation is required but actual metrics collecting is not needed.
 */
class NoopSidecarInstrumentation implements SidecarInstrumentation {
    @Override
    public void addSample(@NonNull final SidecarType type, final long size) {}

    @Override
    public void addSample(@NonNull final SidecarType type, @NonNull final Duration duration) {}

    @Override
    public void addSample(@NonNull final SidecarType type, final long size, @NonNull final Duration duration) {}

    @Override
    public void addSamples(@NonNull final SidecarInstrumentation samples) {}

    @Override
    public Closeable startTimer(@NonNull final SidecarType type) {
        return () -> {};
    }

    @Override
    public void captureDurationSplit(@NonNull final SidecarType type, @NonNull final Runnable fn) {
        fn.run();
    }

    @Override
    public <V> V captureDurationSplit(@NonNull final SidecarType type, @NonNull final ExceptionFreeCallable<V> fn) {
        return fn.call();
    }

    @Override
    public void addDurationSplitsAsDurationSample(@NonNull final SidecarType type) {}

    @Override
    public void reset() {}

    @Override
    public void addCopy() {}

    @Override
    public boolean removeCopy() {
        return false;
    }

    @Override
    public String toString(@NonNull final SidecarType type) {
        return "%s[INVALID]".formatted(type.name());
    }
}
