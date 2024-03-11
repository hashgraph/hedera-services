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

package com.swirlds.benchmark.reconnect;

import static com.swirlds.common.merkle.copy.MerkleInitialize.initializeTreeAfterCopy;
import static com.swirlds.common.threading.manager.AdHocThreadManager.getStaticThreadManager;

import com.swirlds.base.time.Time;
import com.swirlds.benchmark.BenchmarkKey;
import com.swirlds.benchmark.BenchmarkValue;
import com.swirlds.common.merkle.MerkleInternal;
import com.swirlds.common.merkle.MerkleNode;
import com.swirlds.common.merkle.crypto.MerkleCryptoFactory;
import com.swirlds.common.merkle.synchronization.LearningSynchronizer;
import com.swirlds.common.merkle.synchronization.TeachingSynchronizer;
import com.swirlds.common.merkle.synchronization.config.ReconnectConfig;
import com.swirlds.common.merkle.synchronization.utility.MerkleSynchronizationException;
import com.swirlds.common.threading.pool.StandardWorkGroup;
import com.swirlds.config.api.Configuration;
import com.swirlds.platform.network.SocketConfig;
import com.swirlds.virtualmap.VirtualMap;
import java.io.IOException;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Function;

/**
 * A utility class to support benchmarks for reconnect.
 */
public class MerkleBenchmarkUtils {

    public static MerkleInternal createTreeForMap(final VirtualMap<BenchmarkKey, BenchmarkValue> map) {
        final BenchmarkMerkleInternal tree = new BenchmarkMerkleInternal("root");
        initializeTreeAfterCopy(tree);
        tree.setChild(0, map);
        tree.reserve();
        return tree;
    }

    public static <T extends MerkleNode> T hashAndTestSynchronization(
            final MerkleNode startingTree, final MerkleNode desiredTree, final Configuration configuration)
            throws Exception {
        System.out.println("------------");
        System.out.println("starting: " + startingTree);
        System.out.println("desired: " + desiredTree);

        final ReconnectConfig reconnectConfig = configuration.getConfigData(ReconnectConfig.class);

        if (startingTree != null && startingTree.getHash() == null) {
            MerkleCryptoFactory.getInstance().digestTreeSync(startingTree);
        }
        if (desiredTree != null && desiredTree.getHash() == null) {
            MerkleCryptoFactory.getInstance().digestTreeSync(desiredTree);
        }
        return testSynchronization(startingTree, desiredTree, configuration, reconnectConfig);
    }

    /**
     * Synchronize two trees and verify that the end result is the expected result.
     */
    @SuppressWarnings("unchecked")
    private static <T extends MerkleNode> T testSynchronization(
            final MerkleNode startingTree,
            final MerkleNode desiredTree,
            final Configuration configuration,
            final ReconnectConfig reconnectConfig)
            throws Exception {
        try (PairedStreams streams = new PairedStreams(configuration.getConfigData(SocketConfig.class))) {
            final LearningSynchronizer learner;
            final TeachingSynchronizer teacher;

            learner = new LearningSynchronizer(
                    getStaticThreadManager(),
                    streams.getLearnerInput(),
                    streams.getLearnerOutput(),
                    startingTree,
                    () -> {
                        try {
                            streams.disconnect();
                        } catch (final IOException e) {
                            // test code, no danger
                            e.printStackTrace();
                        }
                    },
                    reconnectConfig);
            teacher = new TeachingSynchronizer(
                    configuration,
                    Time.getCurrent(),
                    getStaticThreadManager(),
                    streams.getTeacherInput(),
                    streams.getTeacherOutput(),
                    desiredTree,
                    () -> {
                        try {
                            streams.disconnect();
                        } catch (final IOException e) {
                            // test code, no danger
                            e.printStackTrace();
                        }
                    },
                    reconnectConfig);

            final AtomicReference<Throwable> firstReconnectException = new AtomicReference<>();
            final Function<Throwable, Boolean> exceptionListener = t -> {
                firstReconnectException.compareAndSet(null, t);
                return false;
            };
            final StandardWorkGroup workGroup =
                    new StandardWorkGroup(getStaticThreadManager(), "synchronization-test", null, exceptionListener);
            workGroup.execute("teaching-synchronizer-main", () -> teachingSynchronizerThread(teacher));
            workGroup.execute("learning-synchronizer-main", () -> learningSynchronizerThread(learner));

            try {
                workGroup.waitForTermination();
            } catch (InterruptedException e) {
                workGroup.shutdown();
                Thread.currentThread().interrupt();
            }

            if (workGroup.hasExceptions()) {
                throw new MerkleSynchronizationException(
                        "Exception(s) in synchronization test", firstReconnectException.get());
            }

            final MerkleNode generatedTree = learner.getRoot();
            return (T) generatedTree;
        }
    }

    private static void teachingSynchronizerThread(final TeachingSynchronizer teacher) {
        try {
            teacher.synchronize();
        } catch (final InterruptedException ex) {
            Thread.currentThread().interrupt();
        }
    }

    private static void learningSynchronizerThread(final LearningSynchronizer learner) {
        try {
            learner.synchronize();
        } catch (final InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }
}
