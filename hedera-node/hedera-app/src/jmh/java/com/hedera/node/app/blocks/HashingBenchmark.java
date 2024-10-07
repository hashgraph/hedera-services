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

package com.hedera.node.app.blocks;

import static com.hedera.hapi.block.stream.output.StateIdentifier.STATE_ID_ACCOUNTS;
import static com.hedera.node.app.blocks.impl.NaiveStreamingTreeHasher.hashNaively;

import com.hedera.hapi.block.stream.BlockItem;
import com.hedera.hapi.block.stream.output.MapChangeKey;
import com.hedera.hapi.block.stream.output.MapChangeValue;
import com.hedera.hapi.block.stream.output.MapUpdateChange;
import com.hedera.hapi.block.stream.output.StateChange;
import com.hedera.hapi.block.stream.output.StateChanges;
import com.hedera.hapi.node.base.AccountID;
import com.hedera.hapi.node.base.Timestamp;
import com.hedera.hapi.node.state.token.Account;
import com.hedera.node.app.blocks.impl.ConcurrentStreamingTreeHasher;
import com.hedera.pbj.runtime.io.buffer.Bytes;
import edu.umd.cs.findbugs.annotations.NonNull;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.SplittableRandom;
import java.util.concurrent.ForkJoinPool;
import java.util.concurrent.TimeUnit;
import org.openjdk.jmh.annotations.Benchmark;
import org.openjdk.jmh.annotations.BenchmarkMode;
import org.openjdk.jmh.annotations.Fork;
import org.openjdk.jmh.annotations.Level;
import org.openjdk.jmh.annotations.Measurement;
import org.openjdk.jmh.annotations.Mode;
import org.openjdk.jmh.annotations.OutputTimeUnit;
import org.openjdk.jmh.annotations.Param;
import org.openjdk.jmh.annotations.Scope;
import org.openjdk.jmh.annotations.Setup;
import org.openjdk.jmh.annotations.State;
import org.openjdk.jmh.annotations.Warmup;
import org.openjdk.jmh.infra.Blackhole;

@State(Scope.Benchmark)
@Fork(value = 1)
@Warmup(iterations = 1)
@Measurement(iterations = 3)
public class HashingBenchmark {
    private static final int MAX_STATE_CHANGES = 128;
    private static final SplittableRandom RANDOM = new SplittableRandom(1_234_567L);

    public static void main(String... args) throws Exception {
        org.openjdk.jmh.Main.main(new String[] {"com.hedera.node.app.blocks.HashingBenchmark.hashItemTree"});
    }

    @Param({"10000"})
    private int numLeaves;

    private List<Bytes> leaves;
    private Bytes expectedAnswer;

    @Setup(Level.Trial)
    public void setup() {
        leaves = new ArrayList<>(numLeaves);
        for (int i = 0; i < numLeaves; i++) {
            leaves.add(BlockItem.PROTOBUF.toBytes(randomBlockItem()));
        }
        expectedAnswer = hashNaively(leaves);
    }

    @Benchmark
    @BenchmarkMode(Mode.Throughput)
    @OutputTimeUnit(TimeUnit.SECONDS)
    public void hashItemTree(@NonNull final Blackhole blackhole) {
        //        final var subject = new NaiveStreamingTreeHasher();
        final var subject = new ConcurrentStreamingTreeHasher(ForkJoinPool.commonPool());
        for (final var item : leaves) {
            subject.addLeaf(item);
        }
        final var rootHash = subject.rootHash().join();
        if (!rootHash.equals(expectedAnswer)) {
            throw new IllegalStateException("Expected " + expectedAnswer + " but got " + rootHash);
        }
        blackhole.consume(rootHash);
    }

    private static BlockItem randomBlockItem() {
        return BlockItem.newBuilder()
                .stateChanges(StateChanges.newBuilder()
                        .consensusTimestamp(randomTimestamp())
                        .stateChanges(randomStateChanges()))
                .build();
    }

    private static StateChange[] randomStateChanges() {
        final var numStateChanges = RANDOM.nextInt(MAX_STATE_CHANGES);
        final var stateChanges = new StateChange[numStateChanges];
        for (int i = 0; i < numStateChanges; i++) {
            stateChanges[i] = StateChange.newBuilder()
                    .stateId(STATE_ID_ACCOUNTS.protoOrdinal())
                    .mapUpdate(MapUpdateChange.newBuilder()
                            .key(MapChangeKey.newBuilder()
                                    .accountIdKey(AccountID.newBuilder()
                                            .accountNum(RANDOM.nextLong(Long.MAX_VALUE))
                                            .build())
                                    .build())
                            .value(MapChangeValue.newBuilder().accountValue(Account.DEFAULT))
                            .build())
                    .build();
        }
        return stateChanges;
    }

    private static Timestamp randomTimestamp() {
        return Timestamp.newBuilder()
                .seconds(RANDOM.nextLong(Instant.MAX.getEpochSecond()))
                .nanos(RANDOM.nextInt(Instant.MAX.getNano()))
                .build();
    }
}
