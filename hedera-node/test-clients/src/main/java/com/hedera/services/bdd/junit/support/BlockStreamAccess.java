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

import static java.util.Comparator.comparing;

import com.hedera.hapi.block.stream.Block;
import com.hedera.pbj.runtime.ParseException;
import com.hedera.pbj.runtime.io.buffer.Bytes;
import edu.umd.cs.findbugs.annotations.NonNull;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
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
