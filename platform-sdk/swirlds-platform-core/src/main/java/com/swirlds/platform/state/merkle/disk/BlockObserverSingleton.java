/*
 * Copyright (C) 2024 Hedera Hashgraph, LLC
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

package com.swirlds.platform.state.merkle.disk;

import com.amh.config.ConfigProvider;

import edu.umd.cs.findbugs.annotations.NonNull;
import java.util.concurrent.atomic.AtomicReference;

/**
 * Singleton instance of the BlockObserver.
 */
public class BlockObserverSingleton {
    private static final AtomicReference<BlockObserver> instance = new AtomicReference<>(new NoOpBlockObserver());

    public static void initInstance(@NonNull final ConfigProvider configProvider) {
        final var current = instance.get();

        final var recordStreamConfig = configProvider.getConfiguration().getConfigData(
                BlockRecordStreamConfig.class);
        final var recordFileVersion = recordStreamConfig.recordFileVersion();
        if (recordStreamConfig.enabled() && recordFileVersion < 7) {
            // Instantiate NoOpBlockObserver for v6 and below.
            instance.compareAndSet(current, new NoOpBlockObserver());
            return;
        }

        final var blockStreamConfig = configProvider.getConfiguration().getConfigData(
                BlockStreamConfig.class);
        final var blockVersion = blockStreamConfig.blockVersion();
        if (blockStreamConfig.enabled() && blockVersion >= 7) {
            // Instantiate BlockObserverImpl for v7 and above.
            instance.compareAndSet(current, new BlockObserverImpl());
            return;
        }

        throw new IllegalArgumentException("BlockObserverSingleton: No valid block observer configuration found. "
                + "Please check the configuration for blockStreamConfig and blockRecordStreamConfig.");
    }

    @NonNull
    public static BlockObserver getInstanceOrThrow() {
        BlockObserver observer = instance.get();
        if (observer == null) {
            throw new IllegalStateException("BlockObserver has not been initialized.");
        }
        return observer;
    }
}
