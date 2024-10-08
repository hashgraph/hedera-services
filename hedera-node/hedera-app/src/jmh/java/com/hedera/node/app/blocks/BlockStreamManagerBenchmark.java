package com.hedera.node.app.blocks;

import com.hedera.hapi.block.stream.Block;
import com.hedera.hapi.block.stream.BlockItem;
import com.hedera.hapi.block.stream.output.StateChanges;
import com.hedera.hapi.block.stream.schema.BlockItemSchema;
import com.hedera.hapi.node.base.SemanticVersion;
import com.hedera.node.app.blocks.impl.BlockStreamManagerImpl;
import com.hedera.node.app.blocks.impl.BoundaryStateChangeListener;
import com.hedera.node.app.blocks.impl.KVStateChangeListener;
import com.hedera.node.app.blocks.schemas.V0540BlockStreamSchema;
import com.hedera.node.app.config.BootstrapConfigProviderImpl;
import com.hedera.node.app.config.ConfigProviderImpl;
import com.hedera.node.app.fixtures.state.FakeState;
import com.hedera.node.app.tss.impl.PlaceholderTssBaseService;
import com.hedera.node.config.ConfigProvider;
import com.hedera.node.config.VersionedConfigImpl;
import com.hedera.pbj.runtime.ParseException;
import com.hedera.pbj.runtime.io.buffer.BufferedData;
import com.hedera.pbj.runtime.io.buffer.Bytes;
import edu.umd.cs.findbugs.annotations.NonNull;
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

import java.io.IOException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentLinkedDeque;
import java.util.concurrent.ForkJoinPool;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;
import java.util.zip.GZIPInputStream;

import static com.hedera.node.app.blocks.BlockStreamManager.ZERO_BLOCK_HASH;
import static java.util.Objects.requireNonNull;

@State(Scope.Benchmark)
@Fork(value = 1)
@Warmup(iterations = 1)
@Measurement(iterations = 3)
public class BlockStreamManagerBenchmark {
    private static final long FIRST_ROUND_NO = 123L;
    private static final String SAMPLE_BLOCK = "sample.blk.gz";
    private static final SemanticVersion VERSION = new SemanticVersion(0, 56, 0, "", "");

    public static void main(String... args) throws Exception {
        org.openjdk.jmh.Main.main(new String[] {"com.hedera.node.app.blocks.BlockStreamManagerBenchmark.manageRound"});
    }

    private final ConfigProvider configProvider = new ConfigProviderImpl();
    private final BlockStreamManagerImpl subject = new BlockStreamManagerImpl(
            NoopBlockItemWriter::new,
            ForkJoinPool.commonPool(),
            configProvider,
            new PlaceholderTssBaseService(),
            new BoundaryStateChangeListener(),
            new InitialStateHash(CompletableFuture.completedFuture(ZERO_BLOCK_HASH), FIRST_ROUND_NO),
            VERSION);
    private final List<BlockItem> roundItems = new ArrayList<>();

    @Param({"10"})
    private int numEvents;
    @Param({"100"})
    private int numTxnsPerEvent;

    private FakeState state;

    @Setup(Level.Trial)
    public void setup() throws IOException, ParseException {
        state = new FakeState();
        final Map<String, Object> stateDataSources = new HashMap<>();
        new V0540BlockStreamSchema(ignore -> {}).statesToCreate(configProvider.getConfiguration()).forEach(def -> {
            if (def.singleton()) {
                stateDataSources.put(def.stateKey(), new AtomicReference<>());
            }
        });
        state.addService(BlockStreamService.NAME, stateDataSources);

        loadSampleItems();
    }

    @Benchmark
    @BenchmarkMode(Mode.Throughput)
    @OutputTimeUnit(TimeUnit.SECONDS)
    public void manageRound() {
    }

    private void loadSampleItems() throws IOException, ParseException {
        BlockItem blockHeader = null;
        BlockItem roundHeader = null;
        BlockItem blockProof = null;
        BlockItem lastStateChanges = null;
        BlockItem penultimateStateChanges = null;
        BlockItem sampleEventHeader = null;
        BlockItem sampleEventTransaction = null;
        BlockItem sampleTransactionResult = null;
        BlockItem sampleStateChanges = null;
        try (final var fin = SerializationBenchmark.class.getClassLoader().getResourceAsStream(SAMPLE_BLOCK)) {
            try (final GZIPInputStream in = new GZIPInputStream(fin)) {
                final var block = Block.PROTOBUF.parse(Bytes.wrap(in.readAllBytes()));
                for (final var item : block.items()) {
                    switch (item.item().kind()) {
                        case BLOCK_HEADER -> blockHeader = item;
                        case ROUND_HEADER -> roundHeader = item;
                        case BLOCK_PROOF -> blockProof = item;
                        case EVENT_HEADER -> {
                            if (sampleEventHeader == null) {
                                sampleEventHeader = item;
                            }
                        }
                        case EVENT_TRANSACTION -> {
                            if (sampleEventTransaction == null) {
                                sampleEventTransaction = item;
                            }
                        }
                        case TRANSACTION_RESULT -> {
                            if (sampleTransactionResult == null) {
                                sampleTransactionResult = item;
                            }
                        }
                        case STATE_CHANGES -> {
                            penultimateStateChanges = lastStateChanges;
                            lastStateChanges = item;
                            if (sampleStateChanges == null) {
                                sampleStateChanges = item;
                            }
                        }
                    }
                }
                roundItems.add(requireNonNull(blockHeader));
                roundItems.add(requireNonNull(roundHeader));
                for (int i = 0; i < numEvents; i++) {
                    roundItems.add(requireNonNull(sampleEventHeader));
                    for (int j = 0; j < numTxnsPerEvent; j++) {
                        roundItems.add(requireNonNull(sampleEventTransaction));
                        roundItems.add(requireNonNull(sampleTransactionResult));
                        roundItems.add(requireNonNull(sampleStateChanges));
                    }
                }
                roundItems.add(requireNonNull(penultimateStateChanges));
                roundItems.add(requireNonNull(lastStateChanges));
                roundItems.add(requireNonNull(blockProof));
            }
        }
    }

    private static class NoopBlockItemWriter implements BlockItemWriter {
        @Override
        public void openBlock(final long blockNumber) {
           // No-op
        }

        @Override
        public BlockItemWriter writeItem(@NonNull final Bytes serializedItem) {
            return this;
        }

        @Override
        public void closeBlock() {
            // No-op
        }
    }
}
