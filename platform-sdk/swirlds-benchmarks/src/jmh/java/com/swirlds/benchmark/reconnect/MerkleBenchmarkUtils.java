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
import com.swirlds.common.context.PlatformContext;
import com.swirlds.common.merkle.MerkleInternal;
import com.swirlds.common.merkle.MerkleLeaf;
import com.swirlds.common.merkle.MerkleNode;
import com.swirlds.common.merkle.crypto.MerkleCryptoFactory;
import com.swirlds.common.merkle.synchronization.LearningSynchronizer;
import com.swirlds.common.merkle.synchronization.TeachingSynchronizer;
import com.swirlds.common.merkle.synchronization.config.ReconnectConfig;
import com.swirlds.common.merkle.synchronization.utility.MerkleSynchronizationException;
import com.swirlds.common.threading.pool.StandardWorkGroup;
import java.io.IOException;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Function;

/**
 * A utility class to support benchmarks for reconnect.
 */
public class MerkleBenchmarkUtils {
    /**
     * Returns the following tree:
     *
     * <pre>
     *             root
     *           / |   \
     *          A  i0  i1
     *             /\  /\
     *            B C D null
     * </pre>
     */
    public static BenchmarkMerkleInternal buildLessSimpleTree() {
        final BenchmarkMerkleInternal root = new BenchmarkMerkleInternal("root");

        final MerkleLeaf A = new BenchmarkMerkleLeaf("A");
        final MerkleInternal i0 = new BenchmarkMerkleInternal("i0");
        final MerkleInternal i1 = new BenchmarkMerkleInternal("i1");
        root.setChild(0, A);
        root.setChild(1, i0);
        root.setChild(2, i1);

        final MerkleLeaf B = new BenchmarkMerkleLeaf("B");
        final MerkleLeaf C = new BenchmarkMerkleLeaf("C");
        i0.setChild(0, B);
        i0.setChild(1, C);

        final MerkleLeaf D = new BenchmarkMerkleLeaf("D");
        i1.setChild(0, D);
        i1.setChild(1, null);

        initializeTreeAfterCopy(root);
        return root;
    }

    public static <T extends MerkleNode> T hashAndTestSynchronization(
            final MerkleNode startingTree, final MerkleNode desiredTree, final ReconnectConfig reconnectConfig)
            throws Exception {
        System.out.println("------------");
        System.out.println("starting: " + startingTree);
        System.out.println("desired: " + desiredTree);

        if (startingTree != null && startingTree.getHash() == null) {
            MerkleCryptoFactory.getInstance().digestTreeSync(startingTree);
        }
        if (desiredTree != null && desiredTree.getHash() == null) {
            MerkleCryptoFactory.getInstance().digestTreeSync(desiredTree);
        }
        return testSynchronization(startingTree, desiredTree, reconnectConfig);
    }

    /**
     * Synchronize two trees and verify that the end result is the expected result.
     */
    @SuppressWarnings("unchecked")
    public static <T extends MerkleNode> T testSynchronization(
            final MerkleNode startingTree, final MerkleNode desiredTree, final ReconnectConfig reconnectConfig)
            throws Exception {
        try (PairedStreams streams = new PairedStreams()) {
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
            final PlatformContext platformContext =
                    BenchmarkPlatformContextBuilder.create().build();
            teacher = new TeachingSynchronizer(
                    platformContext.getConfiguration(),
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
