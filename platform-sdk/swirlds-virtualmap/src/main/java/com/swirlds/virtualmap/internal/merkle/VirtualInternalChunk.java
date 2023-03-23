/*
 * Copyright (C) 2023 Hedera Hashgraph, LLC
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

package com.swirlds.virtualmap.internal.merkle;

import com.swirlds.common.crypto.DigestType;
import com.swirlds.common.crypto.Hash;
import com.swirlds.common.io.streams.SerializableDataOutputStream;
import java.io.IOException;
import java.nio.ByteBuffer;
import java.util.Arrays;
import java.util.Objects;

/**
 * A chunk of internal node hashes stored as a unit in data file collections to minimize
 * disk I/O. Chunk IDs start from 1, as 0 is reserved for non-existent data. That is, if
 * chunk depth (number of levels) is 2, chunk 1 will contain internal nodes from 1 to 6
 * inclusively, chunk 2 will contain nodes 7, 8, and 15 to 18, and so on.
 */
public class VirtualInternalChunk {

    public static final int DEFAULT_CHUNK_DEPTH = 5;

    private static final int DIGEST_LENGTH = DigestType.SHA_384.digestLength();

    private static final byte[] NULL_HASH_DATA = new byte[DIGEST_LENGTH];

    // Number of levels in a virtual tree to span
    private final int depth;

    // Starts from 1, as 0 is reserved for "non existent data location"
    private final long chunkId;

    // Internal hashes, stored one by one in path ascending order
    private final byte[] chunkData;

    // Lowest node path in the chunk
    private final long firstPath;

    // Chunk level, starting from 1 for chunk 1
    private final int level;

    static {
        Arrays.fill(NULL_HASH_DATA, 0, DIGEST_LENGTH, (byte) 0);
    }

    /**
     * Creates a new chunk with the given ID and no hash data.
     *
     * @param depth
     *      Chunk depth
     * @param chunkId
     *      Chunk ID
     */
    public VirtualInternalChunk(final int depth, final long chunkId) {
        this.depth = depth;
        this.chunkId = chunkId;
        chunkData = new byte[DIGEST_LENGTH * getChunkLength()];
        this.firstPath = firstPathInChunk(chunkId, depth);
        this.level = getLevel(chunkId, depth);
    }

    /**
     * Creates a new chunk with the given ID and hash data from a byte array. The chunk
     * reads {@link #getChunkLength()} hashes from the array.
     *
     * @param depth
     *      Chunk depth
     * @param chunkId
     *      Chunk ID
     * @param chunkData
     *      Byte array with internal node hashes
     */
    public VirtualInternalChunk(final int depth, final long chunkId, final byte[] chunkData) {
        this.depth = depth;
        this.chunkId = chunkId;
        this.chunkData = chunkData;
        this.firstPath = firstPathInChunk(chunkId, depth);
        this.level = getLevel(chunkId, depth);
    }

    /**
     * Creates a new chunk with chunk ID and hash data from a byte buffer. The chunk
     * reads its ID (a long) and {@link #getChunkLength()} hashes from the buffer.
     *
     * @param depth
     *      Chunk depth
     * @param source
     *      Byte buffer to read ID and hashes from
     */
    public VirtualInternalChunk(final int depth, final ByteBuffer source) {
        this.depth = depth;
        this.chunkId = source.getLong();
        this.chunkData = new byte[DIGEST_LENGTH * getChunkLength()];
        source.get(chunkData);
        this.firstPath = firstPathInChunk(chunkId, depth);
        this.level = getLevel(chunkId, depth);
    }

    public int getDepth() {
        return depth;
    }

    /**
     * Chunk ID. The first chunk has ID 1.
     *
     * @return
     *      Chunk ID
     */
    public long getChunkId() {
        return chunkId;
    }

    public byte[] getChunkData() {
        return chunkData;
    }

    /**
     * Chunk level. Chunk 1 is at level 1. Chunks 2 to 5 are at level 2 if chunk depth is 2. And so on.
     *
     * @return
     *      Chunk level
     */
    public int getLevel() {
        return level;
    }

    public long getFirstPath() {
        return firstPath;
    }

    /**
     * Returns an index, starting from 0, of the given path in this chunk. Paths are global,
     * not relative to the chunk. Max index is {@link #getChunkLength()}. If the path is not
     * in the chunk, an {@link IllegalArgumentException} is thrown.
     *
     * @param path
     *      Path to check
     * @return
     *      Path index in the chunk, starting from 0
     */
    public int getPathIndexInChunk(final long path) {
        return getPathIndexInChunk(path, firstPath, depth);
    }

    /**
     * Returns an index, starting from 0, of the given path in a chunk that starts from the specified
     * first path. Paths are global, not relative to the chunk. Max index is {@link #getChunkLength()}.
     * If the path is not in the chunk, an {@link IllegalArgumentException} is thrown.
     *
     * @param path
     *      Path to check
     * @param firstPath
     *      First path in a chunk
     * @param chunkDepth
     *      Chunk depth
     * @return
     *      Path index in the chunk, starting from 0
     */
    public static int getPathIndexInChunk(final long path, final long firstPath, final int chunkDepth) {
        if (path < firstPath) {
            throw new IllegalArgumentException("Path is not in chunk: " + path);
        }
        int index = 0;
        long firstInLevel = firstPath;
        int pathsInLevel = 2; // first level in chunks of any depth is always 2
        while (firstInLevel + pathsInLevel - 1 < path) { // traverse to the right level
            index += pathsInLevel;
            if (index >= getChunkLength(chunkDepth)) {
                throw new IllegalArgumentException("Path is not in chunk: " + path);
            }
            firstInLevel = firstInLevel * 2 + 1;
            pathsInLevel = pathsInLevel * 2;
            if (path < firstInLevel) {
                throw new IllegalArgumentException("Path is not in chunk: " + path);
            }
        }
        index += path - firstInLevel; // now get the index in the level
        return index;
    }

    /**
     * Returns a hash that corresponds to the given path. The path is global, not relative
     * to the chunk. If the path isn't in the chunk, an {@link IllegalArgumentException}
     * is thrown.
     *
     * @param path
     *      Path to get hash for
     * @return
     *      Internal node hash for the path
     */
    public Hash getHash(final long path) {
        final int index = getPathIndexInChunk(path);
        final byte[] data = new byte[DIGEST_LENGTH];
        System.arraycopy(chunkData, index * DIGEST_LENGTH, data, 0, DIGEST_LENGTH);
        if (Arrays.equals(NULL_HASH_DATA, data)) {
            return null;
        }
        return new Hash(data, DigestType.SHA_384);
    }

    /**
     * Updates hash for the given path. The path is global, not relative to the chunk.
     * If the path isn't in the chunk, an {@link IllegalArgumentException} is thrown.
     *
     * @param path
     *      Path to update hash for
     * @param hash
     *      New hash value
     */
    public void updateHash(final long path, final Hash hash) {
        final int index = getPathIndexInChunk(path);
        if (hash == null) {
            Arrays.fill(chunkData, index * DIGEST_LENGTH, index * DIGEST_LENGTH + DIGEST_LENGTH, (byte) 0);
        } else {
            System.arraycopy(hash.getValue(), 0, chunkData, index * DIGEST_LENGTH, DIGEST_LENGTH);
        }
    }

    /**
     * Writes contents of this chunk to the specified output stream. It includes chunk
     * ID and all hashes stored in the chunk.
     *
     * @param out
     *      Output stream to write chunk data to
     * @throws IOException
     *      If an I/O error occurred
     */
    public void serialize(final SerializableDataOutputStream out) throws IOException {
        out.writeLong(chunkId);
        out.write(chunkData);
    }

    @Override
    public int hashCode() {
        return Objects.hash(depth, chunkId);
    }

    @Override
    public boolean equals(final Object obj) {
        if (!(obj instanceof VirtualInternalChunk that)) {
            return false;
        }
        return (depth == that.depth) && (chunkId == that.chunkId);
    }

    // static helper methods

    // Power
    public static long pow(final int base, final int p) {
        if (p == 0) {
            return 1;
        }
        long res = base;
        for (int i = 1; i < p; i++) {
            res *= base;
        }
        return res;
    }

    /**
     * Returns a number of hashes stored in the chunk.
     *
     * @return
     *      Number of hashes in the chunk
     */
    public int getChunkLength() {
        return getChunkLength(depth);
    }

    /**
     * Returns a number of hashes stored in a chunk of the given depth.
     *
     * @param chunkDepth
     *      Chunk depth
     * @return
     *      Number of hashes in a chunk
     */
    public static int getChunkLength(final int chunkDepth) {
        return (1 << (chunkDepth + 1)) - 2;
    }

    public static int getLevel(final long chunkId, final int chunkDepth) {
        int level = 1;
        long lastIdAtLevel = 1;
        final int childrenCount = getChunkChildrenCount(chunkDepth);
        long chunksAtNextLevel = childrenCount;
        while (lastIdAtLevel < chunkId) {
            level++;
            lastIdAtLevel += chunksAtNextLevel;
            chunksAtNextLevel *= childrenCount;
        }
        return level;
    }

    /**
     * Returns a number of child chunks. For example, for chunks of depth 2, number of child
     * chunks is 4.
     *
     * @return
     *      Number of child chunks
     */
    public int getChunkChildrenCount() {
        return getChunkChildrenCount(depth);
    }

    /**
     * Returns a number of chilc chunks for chunks of the given depth.
     *
     * @param chunkDepth
     *      Chunk depth
     * @return
     *      Number of child chunks
     */
    public static int getChunkChildrenCount(final int chunkDepth) {
        return 1 << chunkDepth;
    }

    // Right-most path in the given tree level. Root node level is 0
    private static long maxPathInLevel(final int level) {
        return (2L << level) - 2;
    }

    // Level in the tree of the given path
    // Is it the same as Path.getPathRank()?
    private static int pathDepth(final long path) {
        /*
        int depth = 0; // so root is depth 0; paths 1 and 2 are depth 1; and so on
        long p = path;
        while (p != 0) {
            depth++;
            p = (p - 1) / 2;
        }
        return depth;
        */

        if (path == 0) {
            return 0;
        }
        return (63 - Long.numberOfLeadingZeros(path + 1));
    }

    public static long pathToChunk(final long path) {
        return pathToChunk(path, DEFAULT_CHUNK_DEPTH);
    }

    /**
     * Returns ID of the chunk of the given depth, where the given path is located.
     *
     * @param path
     *      Path to check
     * @param chunkDepth
     *      Chunk depth
     * @return
     *      Chunk ID, starting from 1, where the path is located
     */
    public static long pathToChunk(final long path, final int chunkDepth) {
        /*
        if (path == 0) {
            throw new IllegalArgumentException("Root node is not a part of any chunk");
        }
        final int pDepth = pathDepth(path);
        long p = path;
        int l = (pDepth - 1) % chunkDepth;
        while (l > 0) { // go up in the tree to the top-most level in the chunk, if needed
            l--;
            p = (p - 1) / 2;
        }
        int cDepth = (pDepth - 1) / chunkDepth + 1; // depth of the chunk
        long chunksAbove =
                (pow(getChunkChildrenCount(chunkDepth), cDepth - 1) + 1) / (getChunkChildrenCount(chunkDepth) - 1);
        long pathsAbove = chunksAbove * getChunkLength(chunkDepth);
        return chunksAbove + (p - pathsAbove + 1) / 2; // each chunk has 2 nodes in the its top level
        */
        if (path == 0) {
            return 0;
        }
        long pp = path + 1;
        int z = Long.numberOfLeadingZeros(pp);
        int r = (Long.SIZE - z - 2) % chunkDepth + 1;
        long m = Long.MIN_VALUE >>> (z + r);
        return ((pp >>> r) ^ m) + (m - 1) / ((1L << chunkDepth) - 1) + 1;
    }

    /**
     * Returns the path of the first internal node in the specified chunk of the given depth.
     * For example, for depth 2, the first path in chunk 1 is 1, in chunk 2 is 7, in chunk 3 is 9, and so on.
     *
     * @param chunk
     *      Chunk ID
     * @param chunkDepth
     *      Chunk depth
     * @return
     *      Path of the first internal node in the chunk
     */
    public static long firstPathInChunk(final long chunk, final int chunkDepth) {
        final int chunkLength = getChunkLength(chunkDepth);
        final int chunkChildrenCount = getChunkChildrenCount(chunkDepth);
        int firstPathInLevel = 1; // start from chunk level 1
        int lastPathInLevel = getChunkLength(chunkDepth);
        int firstChunk = 1; // start from chunk 1
        int lastChunk = 1; // last chunk in a row
        while (lastChunk < chunk) { // traverse level by level until we reach the given chunk
            int chunkCount = (lastChunk - firstChunk + 1) * chunkChildrenCount; // chunks in the next level
            firstChunk = lastChunk + 1;
            firstPathInLevel = lastPathInLevel + 1;
            lastChunk = firstChunk + chunkCount - 1;
            lastPathInLevel = firstPathInLevel + chunkCount * chunkLength - 1;
        }
        return firstPathInLevel + (chunk - firstChunk) * 2;
    }

    /**
     * This one is interesting... This method is used by data source to calculate valid chunk ranges in
     * data files. It returns the highes chunk ID required to store all paths up to the given one.
     *
     * For example (depth is 2), to store paths up to 6, only one chunk is needed, with ID 1. To store paths
     * 7 or 8, chunk 2 is also needed. For 9 and 10, it's chunk 3, and so on up to chunk 5. Then it becomes
     * less trivial. For path 15, which is in chunk 2, we still need chunks 2, 3, 4, and 5, as some paths
     * lower than 15 are stored in these chunks.
     *
     * @param path
     *      Path
     * @param chunkDepth
     *      Chunk depth
     * @return
     *      Highest chunk ID to store all hashes up to the given path
     */
    public static long maxChunkBeforePath(final long path, final int chunkDepth) {
        if (path <= getChunkLength(chunkDepth)) {
            return 1;
        }
        final int pDepth = pathDepth(path);
        if (pDepth % chunkDepth == 1) {
            return pathToChunk(path, chunkDepth);
        } else {
            return pathToChunk(maxPathInLevel(pDepth), chunkDepth);
        }
    }

    public static int comparePaths(final long path1, final long path2) {
        return comparePaths(path1, path2, DEFAULT_CHUNK_DEPTH);
    }

    public static int comparePaths(final long path1, final long path2, final int chunkDepth) {
        final long chunk1 = path1 > 0 ? pathToChunk(path1, chunkDepth) : 0;
        final long chunk2 = path2 > 0 ? pathToChunk(path2, chunkDepth) : 0;
        return (chunk1 != chunk2) ? Long.compare(chunk1, chunk2) : Long.compare(path1, path2);
    }
}
