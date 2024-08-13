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

import static com.swirlds.state.merkle.StateUtils.computeLabel;
import static java.util.Comparator.comparing;

import com.hedera.hapi.block.stream.Block;
import com.hedera.hapi.block.stream.BlockItem;
import com.hedera.hapi.block.stream.output.MapChangeKey;
import com.hedera.hapi.block.stream.output.MapDeleteChange;
import com.hedera.hapi.block.stream.output.MapUpdateChange;
import com.hedera.hapi.block.stream.output.StateChanges;
import com.hedera.hapi.node.state.addressbook.Node;
import com.hedera.hapi.node.state.token.StakingNodeInfo;
import com.hedera.pbj.runtime.ParseException;
import com.hedera.pbj.runtime.io.buffer.Bytes;
import com.swirlds.state.merkle.StateUtils;
import edu.umd.cs.findbugs.annotations.NonNull;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.zip.GZIPInputStream;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

/**
 * Central utility for accessing blocks created by tests.
 */
public enum BlockStreamAccess {
    BLOCK_STREAM_ACCESS;

    private static final Logger log = LogManager.getLogger(BlockStreamAccess.class);

    private static final String UNCOMPRESSED_FILE_EXT = ".blk";
    private static final String COMPRESSED_FILE_EXT = UNCOMPRESSED_FILE_EXT + ".gz";

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
                computeLabel("AddressBookService", "NODES"),
                MapChangeKey::entityNumberKey,
                updateChange -> Map.entry(
                        updateChange.keyOrThrow().entityNumberKeyOrThrow(),
                        updateChange.valueOrThrow().nodeValueOrThrow()));
        return nodesById.entrySet().stream()
                .sorted(Comparator.comparing(e -> e.getKey().number()))
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
                computeLabel("TokenService", "STAKING_INFOS"),
                MapChangeKey::entityNumberKey,
                updateChange -> Map.entry(
                        updateChange.keyOrThrow().entityNumberKeyOrThrow(),
                        updateChange.valueOrThrow().stakingNodeInfoValueOrThrow()));
        return infosById.entrySet().stream()
                .sorted(Comparator.comparing(e -> e.getKey().number()))
                .map(Map.Entry::getValue)
                .toList();
    }

    /**
     * Given a list of blocks, computes a map of key-value pairs that reflects the state changes for a certain
     * key type and value type by applying the given functions to the {@link StateChanges} block items.
     *
     * @param blocks the list of blocks
     * @param stateName the name of the state to compute
     * @param deleteFn the function to apply to a {@link MapDeleteChange} to get the key to remove
     * @param updateFn the function to apply to a {@link MapUpdateChange} to get the key-value pair to update
     * @param <K> the key type
     * @param <V> the value type
     * @return the map of key-value pairs
     */
    public static <K, V> Map<K, V> computeMapFromUpdates(
            @NonNull final List<Block> blocks,
            @NonNull final String stateName,
            @NonNull final Function<MapChangeKey, K> deleteFn,
            @NonNull final Function<MapUpdateChange, Map.Entry<K, V>> updateFn) {
        final Map<K, V> upToDate = new HashMap<>();
        final var stateId = StateUtils.stateIdentifierOf(stateName);
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
            throw new RuntimeException(e);
        }
    }

    private List<Path> orderedBlocksFrom(@NonNull final Path path) throws IOException {
        try (final var stream = Files.walk(path)) {
            return stream.filter(this::isBlockFile)
                    .sorted(comparing(this::extractBlockNumber))
                    .toList();
        }
    }

    private boolean isBlockFile(@NonNull final Path path) {
        return path.toFile().isFile() && extractBlockNumber(path) != -1;
    }

    private long extractBlockNumber(@NonNull final Path path) {
        final var fileName = path.getFileName().toString();
        try {
            final var blockNumber = fileName.substring(0, fileName.indexOf(UNCOMPRESSED_FILE_EXT));
            return Long.parseLong(blockNumber);
        } catch (Exception ignore) {
            log.info("Ignoring non-block file {}", path);
        }
        return -1;
    }
}