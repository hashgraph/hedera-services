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

package com.hedera.services.bdd.junit.support;

import static com.hedera.hapi.node.base.ResponseCodeEnum.BUSY;
import static com.hedera.services.bdd.junit.hedera.utils.WorkingDirUtils.workingDirFor;
import static java.util.Comparator.comparing;
import static java.util.Objects.requireNonNull;

import com.hedera.hapi.block.stream.Block;
import com.hedera.hapi.block.stream.BlockItem;
import com.hedera.hapi.block.stream.output.MapChangeKey;
import com.hedera.hapi.block.stream.output.MapDeleteChange;
import com.hedera.hapi.block.stream.output.MapUpdateChange;
import com.hedera.hapi.block.stream.output.SingletonUpdateChange;
import com.hedera.hapi.block.stream.output.StateChange;
import com.hedera.hapi.block.stream.output.StateChanges;
import com.hedera.hapi.block.stream.output.TransactionResult;
import com.hedera.hapi.node.base.SemanticVersion;
import com.hedera.hapi.node.base.Transaction;
import com.hedera.hapi.node.state.addressbook.Node;
import com.hedera.hapi.node.state.token.StakingNodeInfo;
import com.hedera.hapi.platform.state.PlatformState;
import com.hedera.node.app.blocks.impl.BlockImplUtils;
import com.hedera.node.app.hapi.utils.CommonPbjConverters;
import com.hedera.node.app.hapi.utils.forensics.TransactionParts;
import com.hedera.pbj.runtime.ParseException;
import com.hedera.pbj.runtime.io.buffer.Bytes;
import edu.umd.cs.findbugs.annotations.NonNull;
import edu.umd.cs.findbugs.annotations.Nullable;
import java.io.File;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Function;
import java.util.stream.Stream;
import java.util.zip.GZIPInputStream;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.checkerframework.checker.units.qual.K;

/**
 * Central utility for accessing blocks created by tests.
 */
public enum BlockStreamAccess {
    BLOCK_STREAM_ACCESS;

    private static final Logger log = LogManager.getLogger(BlockStreamAccess.class);

    private static final String UNCOMPRESSED_FILE_EXT = ".blk";
    private static final String COMPRESSED_FILE_EXT = UNCOMPRESSED_FILE_EXT + ".gz";

    public static void main(String[] args) throws ParseException {
        final var node0Dir = Paths.get("hedera-node/test-clients")
                .resolve(workingDirFor(0, "hapi"))
                .toAbsolutePath()
                .normalize();
        final var blocks =
                BlockStreamAccess.BLOCK_STREAM_ACCESS.readBlocks(node0Dir.resolve("data/block-streams/block-0.0.3"));
        SemanticVersion lastVersion = null;
        boolean postUpgradeChangesPending = false;
        for (final var block : blocks) {
            final var items = block.items();
            final var header = block.items().getFirst().blockHeaderOrThrow();
            final var version = header.softwareVersionOrThrow();
            if (!version.equals(lastVersion)) {
                if (lastVersion != null) {
                    // For every non-genesis upgrade we expect the state changes in the post-upgrade transaction
                    // to include state changes for every node in the address book service node store
                    if (!postUpgradeChangesPending) {
                        System.out.println("Upgraded from " + lastVersion + " to " + version);
                    }
                    postUpgradeChangesPending = true;
                } else {
                    lastVersion = version;
                }
            }
            if (postUpgradeChangesPending) {
                TransactionParts postUpgradeParts = null;
                int i = 0, n = items.size();
                for (; i < n; i++) {
                    final var item = items.get(i);
                    if (item.hasEventTransaction()) {
                        TransactionResult result = null;
                        for (int j = i; j < n && result == null; j++) {
                            final var nextItem = items.get(j);
                            if (nextItem.hasTransactionResult()) {
                                result = nextItem.transactionResultOrThrow();
                            }
                        }
                        requireNonNull(result);
                        if (result.status() != BUSY) {
                            try {
                                postUpgradeParts =
                                        TransactionParts.from(CommonPbjConverters.fromPbj(Transaction.PROTOBUF.parse(
                                                item.eventTransactionOrThrow().applicationTransactionOrThrow())));
                                break;
                            } catch (ParseException e) {
                                throw new RuntimeException(e);
                            }
                        }
                    }
                }
                if (postUpgradeParts != null) {
                    System.out.println("Post-upgrade transaction was type " + postUpgradeParts.function());
                    postUpgradeChangesPending = false;
                    lastVersion = version;
                    StateChanges stateChanges = null;
                    for (int j = i; j < n && stateChanges == null; j++) {
                        final var item = items.get(j);
                        if (item.hasStateChanges()) {
                            stateChanges = item.stateChangesOrThrow();
                        }
                    }
                    requireNonNull(stateChanges);
                    for (final var changes : stateChanges.stateChanges()) {
                        if (changes.hasMapUpdate()
                                && changes.mapUpdateOrThrow().valueOrThrow().hasNodeValue()) {
                            final var update = changes.mapUpdateOrThrow();
                            final var key = update.keyOrThrow();
                            final var value = update.valueOrThrow();
                            System.out.println("Node " + key.entityNumberKeyOrThrow() + " has new data "
                                    + value.nodeValueOrThrow());
                        }
                    }
                }
            }
        }
    }

    /**
     * Reads all files matching the block file pattern from the given path and returns them in
     * ascending order of block number.
     *
     * @param path the path to read blocks from
     * @return the list of blocks
     * @throws UncheckedIOException if an I/O error occurs
     */
    public List<Block> readBlocks(@NonNull final Path path) {
        try {
            return orderedBlocksFrom(path).stream().map(this::blockFrom).toList();
        } catch (IOException e) {
            log.error("Failed to read blocks from path {}", path, e);
            throw new UncheckedIOException(e);
        }
    }

    /**
     * Given a list of blocks, returns the final list of address book nodes generated by the state changes
     * in these blocks, in ascending order of their entity numbers.
     *
     * @param blocks the list of blocks
     * @return the list of nodes
     */
    public static List<Node> orderedNodesFrom(@NonNull final List<Block> blocks) {
        final var nodesById = computeMapFromUpdates(
                blocks,
                MapChangeKey::entityNumberKey,
                updateChange -> Map.entry(
                        updateChange.keyOrThrow().entityNumberKeyOrThrow(),
                        updateChange.valueOrThrow().nodeValueOrThrow()),
                "AddressBookService",
                "NODES");
        return nodesById.entrySet().stream()
                .sorted(Map.Entry.comparingByKey())
                .map(Map.Entry::getValue)
                .toList();
    }

    /**
     * Given a list of blocks, returns the final list of staking node infos generated by the state changes
     * in these blocks, in ascending order of their entity numbers.
     *
     * @param blocks the list of blocks
     * @return the list of staking node infos
     */
    public static List<StakingNodeInfo> orderedStakingInfosFrom(@NonNull final List<Block> blocks) {
        final var infosById = computeMapFromUpdates(
                blocks,
                MapChangeKey::entityNumberKey,
                updateChange -> Map.entry(
                        updateChange.keyOrThrow().entityNumberKeyOrThrow(),
                        updateChange.valueOrThrow().stakingNodeInfoValueOrThrow()),
                "TokenService",
                "STAKING_INFOS");
        return infosById.entrySet().stream()
                .sorted(Map.Entry.comparingByKey())
                .map(Map.Entry::getValue)
                .toList();
    }

    /**
     * Given a list of blocks, returns the final platform state generated by the state changes in these blocks.
     *
     * @param blocks the list of blocks
     * @return the platform state
     */
    public static PlatformState lastPlatformStateFrom(@NonNull final List<Block> blocks) {
        return computeSingletonValueFromUpdates(
                blocks, SingletonUpdateChange::platformStateValueOrThrow, "PlatformStateService", "PLATFORM_STATE");
    }

    /**
     * Given a list of blocks, computes the last singleton value for a certain state by applying the given
     * function to the {@link SingletonUpdateChange} block items.
     *
     * @param <V> the value type
     * @param blocks the list of blocks
     * @param extractFn the function to apply to a {@link SingletonUpdateChange} to get the value
     * @param serviceName the name of the service
     * @param stateKey the key of the state
     * @return the last singleton value
     */
    @Nullable
    public static <V> V computeSingletonValueFromUpdates(
            @NonNull final List<Block> blocks,
            @NonNull final Function<SingletonUpdateChange, V> extractFn,
            @NonNull final String serviceName,
            @NonNull final String stateKey) {
        final AtomicReference<V> lastValue = new AtomicReference<>();
        final var stateId = BlockImplUtils.stateIdFor(serviceName, stateKey);
        stateChangesForState(blocks, stateId)
                .filter(StateChange::hasSingletonUpdate)
                .map(StateChange::singletonUpdateOrThrow)
                .forEach(update -> lastValue.set(extractFn.apply(update)));
        return lastValue.get();
    }

    /**
     * Given a list of blocks, computes a map of key-value pairs that reflects the state changes for a certain
     * key type and value type by applying the given functions to the {@link StateChanges} block items.
     *
     * @param <K> the key type
     * @param <V> the value type
     * @param blocks the list of blocks
     * @param deleteFn the function to apply to a {@link MapDeleteChange} to get the key to remove
     * @param updateFn the function to apply to a {@link MapUpdateChange} to get the key-value pair to update
     * @param serviceName the name of the service
     * @param stateKey the key of the state
     * @return the map of key-value pairs
     */
    public static <K, V> Map<K, V> computeMapFromUpdates(
            @NonNull final List<Block> blocks,
            @NonNull final Function<MapChangeKey, K> deleteFn,
            @NonNull final Function<MapUpdateChange, Map.Entry<K, V>> updateFn,
            @NonNull final String serviceName,
            @NonNull final String stateKey) {
        final Map<K, V> upToDate = new HashMap<>();
        final var stateId = BlockImplUtils.stateIdFor(serviceName, stateKey);
        blocks.forEach(block -> block.items().stream()
                .filter(BlockItem::hasStateChanges)
                .flatMap(item -> item.stateChangesOrThrow().stateChanges().stream())
                .filter(change -> change.stateId() == stateId)
                .forEach(change -> {
                    if (change.hasMapDelete()) {
                        final var removedKey =
                                deleteFn.apply(change.mapDeleteOrThrow().keyOrThrow());
                        upToDate.remove(removedKey);
                    } else if (change.hasMapUpdate()) {
                        final var mapUpdate = change.mapUpdateOrThrow();
                        final var entry = updateFn.apply(mapUpdate);
                        upToDate.put(entry.getKey(), entry.getValue());
                    }
                }));
        return upToDate;
    }

    private static Stream<StateChange> stateChangesForState(@NonNull final List<Block> blocks, final int stateId) {
        return blocks.stream().flatMap(block -> block.items().stream()
                .filter(BlockItem::hasStateChanges)
                .flatMap(item -> item.stateChangesOrThrow().stateChanges().stream())
                .filter(change -> change.stateId() == stateId));
    }

    private Block blockFrom(@NonNull final Path path) {
        final var fileName = path.getFileName().toString();
        try {
            if (fileName.endsWith(COMPRESSED_FILE_EXT)) {
                try (final GZIPInputStream in = new GZIPInputStream(Files.newInputStream(path))) {
                    return Block.PROTOBUF.parse(Bytes.wrap(in.readAllBytes()));
                }
            } else {
                return Block.PROTOBUF.parse(Bytes.wrap(Files.readAllBytes(path)));
            }
        } catch (IOException | ParseException e) {
            throw new RuntimeException("Failed reading block @ " + path, e);
        }
    }

    private List<Path> orderedBlocksFrom(@NonNull final Path path) throws IOException {
        try (final var stream = Files.walk(path)) {
            return stream.filter(BlockStreamAccess::isBlockFile)
                    .sorted(comparing(BlockStreamAccess::extractBlockNumber))
                    .toList();
        }
    }

    private static boolean isBlockFile(@NonNull final Path path) {
        return path.toFile().isFile() && extractBlockNumber(path) != -1;
    }

    private static long extractBlockNumber(@NonNull final Path path) {
        return extractBlockNumber(path.getFileName().toString());
    }

    public static boolean isBlockFile(@NonNull final File file) {
        return file.isFile() && extractBlockNumber(file.getName()) != -1;
    }

    private static long extractBlockNumber(@NonNull final String fileName) {
        try {
            final var blockNumber = fileName.substring(0, fileName.indexOf(UNCOMPRESSED_FILE_EXT));
            return Long.parseLong(blockNumber);
        } catch (Exception ignore) {
        }
        return -1;
    }
}
