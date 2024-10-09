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

import static com.hedera.hapi.block.stream.output.SingletonUpdateChange.NewValueOneOfType.BLOCK_STREAM_INFO_VALUE;
import static com.hedera.hapi.block.stream.output.StateIdentifier.STATE_ID_BLOCK_STREAM_INFO;
import static com.hedera.hapi.block.stream.output.StateIdentifier.STATE_ID_PLATFORM_STATE;
import static com.hedera.node.app.blocks.BlockStreamManager.ZERO_BLOCK_HASH;
import static com.hedera.node.app.blocks.schemas.V0540BlockStreamSchema.BLOCK_STREAM_INFO_KEY;
import static java.util.Objects.requireNonNull;
import static java.util.concurrent.CompletableFuture.completedFuture;

import com.hedera.hapi.block.stream.Block;
import com.hedera.hapi.block.stream.BlockItem;
import com.hedera.hapi.block.stream.output.SingletonUpdateChange;
import com.hedera.hapi.block.stream.output.StateChange;
import com.hedera.hapi.block.stream.output.StateChanges;
import com.hedera.hapi.node.base.SemanticVersion;
import com.hedera.hapi.node.base.Timestamp;
import com.hedera.hapi.node.state.blockstream.BlockStreamInfo;
import com.hedera.hapi.platform.state.PlatformState;
import com.hedera.node.app.blocks.impl.BlockStreamManagerImpl;
import com.hedera.node.app.blocks.impl.BoundaryStateChangeListener;
import com.hedera.node.app.blocks.schemas.V0540BlockStreamSchema;
import com.hedera.node.app.config.ConfigProviderImpl;
import com.hedera.node.app.fixtures.state.FakeState;
import com.hedera.node.app.tss.impl.PlaceholderTssBaseService;
import com.hedera.node.config.ConfigProvider;
import com.hedera.pbj.runtime.OneOf;
import com.hedera.pbj.runtime.ParseException;
import com.hedera.pbj.runtime.io.buffer.BufferedData;
import com.hedera.pbj.runtime.io.buffer.Bytes;
import com.swirlds.common.crypto.Hash;
import com.swirlds.platform.state.service.PlatformStateService;
import com.swirlds.platform.state.service.schemas.V0540PlatformStateSchema;
import com.swirlds.platform.system.Round;
import com.swirlds.platform.system.address.AddressBook;
import com.swirlds.platform.system.events.ConsensusEvent;
import com.swirlds.platform.system.state.notifications.StateHashedNotification;
import com.swirlds.state.spi.Schema;
import edu.umd.cs.findbugs.annotations.NonNull;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ForkJoinPool;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;
import java.util.zip.GZIPInputStream;
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

@State(Scope.Benchmark)
@Fork(value = 1)
@Warmup(iterations = 1)
@Measurement(iterations = 3)
public class BlockStreamManagerBenchmark {
    private static final long FIRST_ROUND_NO = 123L;
    private static final Bytes FAKE_START_OF_BLOCK_STATE_HASH = Bytes.fromHex("ab".repeat(48));
    private static final Hash FAKE_STATE_HASH = new Hash(FAKE_START_OF_BLOCK_STATE_HASH.toByteArray());
    private static final String SAMPLE_BLOCK = "sample.blk.gz";
    private static final Instant FAKE_CONSENSUS_NOW = Instant.ofEpochSecond(1_234_567L, 890);
    private static final Timestamp FAKE_CONSENSUS_TIME = new Timestamp(1_234_567L, 890);
    private static final SemanticVersion VERSION = new SemanticVersion(0, 56, 0, "", "");

    public static void main(String... args) throws Exception {
        org.openjdk.jmh.Main.main(new String[] {"com.hedera.node.app.blocks.BlockStreamManagerBenchmark.manageRound"});
    }

    private final Round round = new FakeRound();
    private final ConfigProvider configProvider = new ConfigProviderImpl(
            false,
            null,
            Map.of(
                    "blockStream.hashCombineBatchSize", "32",
                    "blockStream.serializationBatchSize", "32"));
    private final List<BlockItem> roundItems = new ArrayList<>();
    private final PlaceholderTssBaseService tssBaseService = new PlaceholderTssBaseService();
    private final BlockStreamManagerImpl subject = new BlockStreamManagerImpl(
            NoopBlockItemWriter::new,
            //                                    BaosBlockItemWriter::new,
            ForkJoinPool.commonPool(),
            configProvider,
            tssBaseService,
            new FakeBoundaryStateChangeListener(),
            new InitialStateHash(completedFuture(FAKE_START_OF_BLOCK_STATE_HASH), FIRST_ROUND_NO - 1),
            VERSION);

    @Param({"10"})
    private int numEvents;

    @Param({"100"})
    private int numTxnsPerEvent;

    private long roundNum = FIRST_ROUND_NO;
    private FakeState state;
    private BlockItem boundaryStateChanges;
    private PlatformState platformState;

    @Setup(Level.Trial)
    public void setup() throws IOException, ParseException {
        loadSampleItems();
        state = new FakeState();
        addServiceSingleton(new V0540BlockStreamSchema(ignore -> {}), BlockStreamService.NAME, BlockStreamInfo.DEFAULT);
        addServiceSingleton(new V0540PlatformStateSchema(), PlatformStateService.NAME, platformState);
        subject.initLastBlockHash(ZERO_BLOCK_HASH);
        tssBaseService.setExecutor(ForkJoinPool.commonPool());
        tssBaseService.registerLedgerSignatureConsumer(subject);
    }

    @Benchmark
    @BenchmarkMode(Mode.Throughput)
    @OutputTimeUnit(TimeUnit.SECONDS)
    public void manageRound() {
        subject.startRound(round, state);
        roundItems.forEach(subject::writeItem);
        subject.notify(new StateHashedNotification(roundNum, FAKE_STATE_HASH));
        subject.endRound(state, roundNum);
        roundNum++;
    }

    private <T> void addServiceSingleton(
            @NonNull final Schema schema, @NonNull final String serviceName, @NonNull final T singletonValue) {
        final Map<String, Object> stateDataSources = new HashMap<>();
        schema.statesToCreate(configProvider.getConfiguration()).forEach(def -> {
            if (def.singleton()) {
                stateDataSources.put(def.stateKey(), new AtomicReference<>(singletonValue));
            }
        });
        state.addService(serviceName, stateDataSources);
    }

    private void loadSampleItems() throws IOException, ParseException {
        BlockItem blockHeader = null;
        BlockItem roundHeader = null;
        BlockItem lastStateChanges = null;
        BlockItem penultimateStateChanges = null;
        BlockItem sampleEventHeader = null;
        BlockItem sampleEventTxn = null;
        BlockItem sampleTxnResult = null;
        BlockItem sampleTxnStateChanges = null;
        try (final var fin = SerializationBenchmark.class.getClassLoader().getResourceAsStream(SAMPLE_BLOCK)) {
            try (final var in = new GZIPInputStream(fin)) {
                final var block = Block.PROTOBUF.parse(Bytes.wrap(in.readAllBytes()));
                for (final var item : block.items()) {
                    switch (item.item().kind()) {
                        case BLOCK_HEADER -> blockHeader = item;
                        case ROUND_HEADER -> roundHeader = item;
                        case EVENT_HEADER -> {
                            if (sampleEventHeader == null) {
                                sampleEventHeader = item;
                            }
                        }
                        case EVENT_TRANSACTION -> {
                            if (sampleEventTxn == null) {
                                sampleEventTxn = item;
                            }
                        }
                        case TRANSACTION_RESULT -> {
                            if (sampleTxnResult == null) {
                                sampleTxnResult = item;
                            }
                        }
                        case STATE_CHANGES -> {
                            penultimateStateChanges = lastStateChanges;
                            lastStateChanges = item;
                            if (sampleTxnStateChanges == null) {
                                sampleTxnStateChanges = item;
                            }
                        }
                    }
                }
                roundItems.add(requireNonNull(blockHeader));
                roundItems.add(requireNonNull(roundHeader));
                for (int i = 0; i < numEvents; i++) {
                    roundItems.add(requireNonNull(sampleEventHeader));
                    for (int j = 0; j < numTxnsPerEvent; j++) {
                        roundItems.add(requireNonNull(sampleEventTxn));
                        roundItems.add(requireNonNull(sampleTxnResult));
                        roundItems.add(requireNonNull(sampleTxnStateChanges));
                    }
                }
            }
            boundaryStateChanges = requireNonNull(penultimateStateChanges);
            platformState = boundaryStateChanges.stateChangesOrThrow().stateChanges().stream()
                    .filter(stateChange -> stateChange.stateId() == STATE_ID_PLATFORM_STATE.protoOrdinal())
                    .findFirst()
                    .map(StateChange::singletonUpdateOrThrow)
                    .map(SingletonUpdateChange::platformStateValueOrThrow)
                    .orElseThrow();
        }
    }

    private class FakeBoundaryStateChangeListener extends BoundaryStateChangeListener {
        private boolean nextChangesAreFromState = false;

        @Override
        public BlockItem flushChanges() {
            if (nextChangesAreFromState) {
                final var blockStreamInfo = state.getReadableStates(BlockStreamService.NAME)
                        .<BlockStreamInfo>getSingleton(BLOCK_STREAM_INFO_KEY)
                        .get();
                requireNonNull(blockStreamInfo);
                final var stateChanges = new StateChanges(
                        FAKE_CONSENSUS_TIME,
                        List.of(StateChange.newBuilder()
                                .stateId(STATE_ID_BLOCK_STREAM_INFO.protoOrdinal())
                                .singletonUpdate(new SingletonUpdateChange(
                                        new OneOf<>(BLOCK_STREAM_INFO_VALUE, blockStreamInfo)))
                                .build()));
                nextChangesAreFromState = false;
                return BlockItem.newBuilder().stateChanges(stateChanges).build();
            } else {
                nextChangesAreFromState = true;
                return boundaryStateChanges;
            }
        }

        @NonNull
        @Override
        public Timestamp boundaryTimestampOrThrow() {
            return FAKE_CONSENSUS_TIME;
        }
    }

    private class FakeRound implements Round {
        @NonNull
        @Override
        public Iterator<ConsensusEvent> iterator() {
            return Collections.emptyIterator();
        }

        @Override
        public long getRoundNum() {
            return roundNum;
        }

        @Override
        public boolean isEmpty() {
            return true;
        }

        @Override
        public int getEventCount() {
            return 0;
        }

        @NonNull
        @Override
        public AddressBook getConsensusRoster() {
            throw new UnsupportedOperationException();
        }

        @NonNull
        @Override
        public Instant getConsensusTimestamp() {
            return FAKE_CONSENSUS_NOW;
        }
    }

    private static class NoopBlockItemWriter implements BlockItemWriter {
        @Override
        public void openBlock(final long blockNumber) {
            // No-op
        }

        @Override
        public BlockItemWriter writeItem(@NonNull final byte[] bytes) {
            return this;
        }

        @Override
        public BlockItemWriter writeItems(@NonNull BufferedData data) {
            return this;
        }

        @Override
        public void closeBlock() {
            // No-op
        }
    }

    private static class BaosBlockItemWriter implements BlockItemWriter {
        private static final int BLOCKS_TO_CHECK = 10;
        private static final String BLOCKS_DIR = "other-blocks";

        private static int numBlocksToWrite = BLOCKS_TO_CHECK;

        private ByteArrayOutputStream baos;

        @Override
        public void openBlock(final long blockNumber) {
            baos = new ByteArrayOutputStream();
        }

        @Override
        public BlockItemWriter writeItem(@NonNull final byte[] bytes) {
            try {
                baos.write(bytes);
            } catch (IOException e) {
                throw new UncheckedIOException(e);
            }
            return this;
        }

        @Override
        public BlockItemWriter writeItems(@NonNull final BufferedData data) {
            try {
                final var block = Block.PROTOBUF.parse(data);
                for (final var item : block.items()) {
                    writePbjItem(BlockItem.PROTOBUF.toBytes(item));
                }
            } catch (ParseException e) {
                throw new IllegalArgumentException(e);
            }
            return this;
        }

        @Override
        public void closeBlock() {
            if (numBlocksToWrite > 0) {
                final var blockNo = BLOCKS_TO_CHECK - numBlocksToWrite + 1;
                final var path = Paths.get(BLOCKS_DIR, "block" + blockNo + ".blk");
                try {
                    Files.createDirectories(path.getParent());
                } catch (IOException e) {
                    throw new UncheckedIOException(e);
                }
                try (final var fout = Files.newOutputStream(path)) {
                    baos.writeTo(fout);
                } catch (IOException e) {
                    throw new UncheckedIOException(e);
                }
                numBlocksToWrite--;
                if (numBlocksToWrite == 0) {
                    System.exit(0);
                }
            }
        }
    }
}
