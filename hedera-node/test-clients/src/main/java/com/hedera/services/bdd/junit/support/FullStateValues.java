// SPDX-License-Identifier: Apache-2.0
package com.hedera.services.bdd.junit.support;

import static java.util.Objects.requireNonNull;

import com.hedera.hapi.block.stream.Block;
import com.hedera.hapi.block.stream.BlockItem;
import com.hedera.node.app.hapi.utils.blocks.BlockStreamAccess;
import com.hedera.node.app.hapi.utils.blocks.StateChangeDeltas;
import com.hedera.node.app.hapi.utils.blocks.StateChangeReconstructor;
import edu.umd.cs.findbugs.annotations.NonNull;
import edu.umd.cs.findbugs.annotations.Nullable;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.TreeMap;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

/**
 * Expands partial state changes in the blocks of one directory, delivered in increasing order, for listeners that
 * translate state values. Stays idle until the first block with a partial update, then replays the completed
 * blocks before it from disk; later gaps are filled from disk the same way. An expansion failure is terminal
 * and must be surfaced to consumers; sparse values must never be presented as complete state.
 */
final class FullStateValues {
    private static final Logger log = LogManager.getLogger(FullStateValues.class);

    @Nullable
    private StateChangeReconstructor reconstructor;

    private long nextBlock;

    @Nullable
    private IllegalStateException failure;

    /**
     * Returns the block with every partial update replaced by its full value, or the block itself when nothing
     * needs expanding. Throws if a complete baseline cannot be recovered. The result must not be used to verify proofs.
     */
    synchronized Block expand(@NonNull final Path blockDir, @NonNull final Block block) {
        requireNonNull(blockDir);
        requireNonNull(block);
        if (failure != null) throw failure;
        if (reconstructor == null && !StateChangeDeltas.hasPartialUpdates(block)) {
            return block;
        }
        try {
            final long number = numberOf(block);
            if (reconstructor == null) {
                log.info("Expanding partial state changes in {} from block #{}", blockDir, number);
                reconstructor = new StateChangeReconstructor();
                nextBlock = -1;
            } else if (number < nextBlock) {
                throw new IllegalStateException("Out-of-order block #" + number + "; expected #" + nextBlock);
            }
            for (final var earlier : completedBlocks(blockDir, nextBlock, number)) {
                reconstructor.fullValuesForTranslation(earlier);
            }
            final var expanded = reconstructor.fullValuesForTranslation(block);
            nextBlock = number + 1;
            return expanded;
        } catch (final RuntimeException e) {
            failure = new IllegalStateException("Cannot expand partial state changes in " + blockDir, e);
            throw failure;
        }
    }

    /** The completed blocks numbered from {@code from} (or the first on disk, if negative) up to {@code to}. */
    private static List<Block> completedBlocks(@NonNull final Path dir, final long from, final long to) {
        // One file per block number, preferring a finished block file over a pending one
        final var paths = new TreeMap<Long, Path>();
        try (final var files = Files.list(dir)) {
            files.filter(path -> BlockStreamAccess.isBlockFile(path, true)).forEach(path -> {
                final long number = BlockStreamAccess.extractBlockNumber(path);
                if (number >= from
                        && number < to
                        && !path.getFileName().toString().contains(".pnd")) {
                    paths.put(number, path);
                } else if (number >= from && number < to) {
                    paths.putIfAbsent(number, path);
                }
            });
        } catch (final IOException e) {
            throw new UncheckedIOException(e);
        }
        final long first = from >= 0 ? from : paths.isEmpty() ? to : paths.firstKey();
        if (to - first != paths.size()) {
            throw new IllegalStateException("Blocks #" + first + " to #" + (to - 1) + " are incomplete in " + dir);
        }
        return paths.values().stream().map(BlockStreamAccess::blockFrom).toList();
    }

    private static long numberOf(@NonNull final Block block) {
        return block.items().stream()
                .filter(BlockItem::hasBlockHeader)
                .findFirst()
                .orElseThrow(() -> new IllegalArgumentException("Block has no header"))
                .blockHeaderOrThrow()
                .number();
    }
}
