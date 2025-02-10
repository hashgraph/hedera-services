/*
 * Copyright (C) 2024-2025 Hedera Hashgraph, LLC
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

package com.hedera.services.bdd.spec.utilops.pauses;

import static com.hedera.services.bdd.junit.hedera.ExternalPath.BLOCK_STREAMS_DIR;
import static com.hedera.services.bdd.spec.transactions.TxnVerbs.cryptoTransfer;
import static com.hedera.services.bdd.spec.transactions.crypto.HapiCryptoTransfer.tinyBarsFromTo;
import static com.hedera.services.bdd.spec.utilops.CustomSpecAssert.allRunFor;
import static com.hedera.services.bdd.suites.HapiSuite.FUNDING;
import static com.hedera.services.bdd.suites.HapiSuite.GENESIS;

import com.hedera.node.app.blocks.impl.FileBlockItemWriter;
import com.hedera.services.bdd.junit.support.BlockStreamAccess;
import com.hedera.services.bdd.spec.HapiSpec;
import com.hedera.services.bdd.spec.utilops.UtilOp;
import edu.umd.cs.findbugs.annotations.NonNull;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.stream.Stream;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

public class HapiSpecWaitUntilNextBlock extends UtilOp {
    private static final Logger log = LogManager.getLogger(HapiSpecWaitUntilNextBlock.class);
    private static final String BLOCK_FILE_EXTENSION = ".blk";
    private static final String COMPRESSED_BLOCK_FILE_EXTENSION = BLOCK_FILE_EXTENSION + ".gz";
    private static final String MARKER_FILE_EXTENSION = ".mf";
    private static final Duration POLL_INTERVAL = Duration.ofMillis(100);
    private static final Duration BACKGROUND_TRAFFIC_INTERVAL = Duration.ofMillis(1000);

    private boolean backgroundTraffic;

    public HapiSpecWaitUntilNextBlock withBackgroundTraffic(final boolean backgroundTraffic) {
        this.backgroundTraffic = backgroundTraffic;
        return this;
    }

    @Override
    protected boolean submitOp(@NonNull final HapiSpec spec) throws Throwable {
        final var blockDir = spec.targetNetworkOrThrow().nodes().getFirst().getExternalPath(BLOCK_STREAMS_DIR);
        if (blockDir == null) {
            throw new IllegalStateException("Block stream directory not available");
        }

        final var currentBlock = findLatestBlockNumber(blockDir);
        final var targetBlock = currentBlock + 1;

        log.info("Waiting for block {} to appear (current block is {})", targetBlock, currentBlock);

        // Start background traffic if configured
        final var stopTraffic = new AtomicBoolean(false);
        CompletableFuture<?> trafficFuture = null;
        if (backgroundTraffic) {
            trafficFuture = CompletableFuture.runAsync(() -> {
                while (!stopTraffic.get()) {
                    try {
                        // Execute the background traffic operation
                        allRunFor(
                                spec,
                                cryptoTransfer(tinyBarsFromTo(GENESIS, FUNDING, 1))
                                        .deferStatusResolution()
                                        .noLogging()
                                        .hasAnyStatusAtAll());
                        // Advance consensus time after successful execution
                        spec.sleepConsensusTime(BACKGROUND_TRAFFIC_INTERVAL);
                    } catch (Exception e) {
                        // Log but continue trying
                        log.info("Background traffic iteration failed", e);
                    }
                }
            });
        }

        try {
            while (true) {
                if (isBlockComplete(blockDir, targetBlock)) {
                    log.info("Block {} has been created and completed", targetBlock);
                    return false;
                }
                spec.sleepConsensusTime(POLL_INTERVAL);
            }
        } finally {
            if (trafficFuture != null) {
                stopTraffic.set(true);
                trafficFuture.join();
            }
        }
    }

    private long findLatestBlockNumber(Path blockDir) throws IOException {
        try (Stream<Path> files = Files.walk(blockDir)) {
            return files.filter(this::isBlockFile)
                    .map(BlockStreamAccess::extractBlockNumber)
                    .filter(num -> num >= 0)
                    .max(Long::compareTo)
                    .orElse(-1L);
        }
    }

    private boolean isBlockComplete(Path blockDir, long blockNumber) throws IOException {
        try (Stream<Path> files = Files.walk(blockDir)) {
            return files.anyMatch(path -> {
                String fileName = path.getFileName().toString();
                return fileName.startsWith(FileBlockItemWriter.longToFileName(blockNumber))
                        && fileName.endsWith(MARKER_FILE_EXTENSION);
            });
        }
    }

    private boolean isBlockFile(Path path) {
        String fileName = path.getFileName().toString();
        return Files.isRegularFile(path)
                && (fileName.endsWith(BLOCK_FILE_EXTENSION) || fileName.endsWith(COMPRESSED_BLOCK_FILE_EXTENSION));
    }
}
