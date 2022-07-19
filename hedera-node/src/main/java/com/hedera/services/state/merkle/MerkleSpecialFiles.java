/*
 * Copyright (C) 2021-2022 Hedera Hashgraph, LLC
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
package com.hedera.services.state.merkle;

import static com.hedera.services.context.properties.StaticPropertiesHolder.STATIC_PROPERTIES;
import static com.hedera.services.legacy.proto.utils.CommonUtils.noThrowSha384HashOf;
import static com.hedera.services.utils.EntityIdUtils.readableId;

import com.google.common.base.MoreObjects;
import com.google.common.primitives.Longs;
import com.hedera.services.state.merkle.internals.BytesElement;
import com.hederahashgraph.api.proto.java.FileID;
import com.swirlds.common.crypto.CryptoFactory;
import com.swirlds.common.crypto.DigestType;
import com.swirlds.common.crypto.Hash;
import com.swirlds.common.io.streams.SerializableDataInputStream;
import com.swirlds.common.io.streams.SerializableDataOutputStream;
import com.swirlds.common.merkle.MerkleLeaf;
import com.swirlds.common.merkle.MerkleNode;
import com.swirlds.common.merkle.impl.PartialMerkleLeaf;
import com.swirlds.fcqueue.FCQueue;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.util.Arrays;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;
import java.util.function.Supplier;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

/**
 * A key-value store with {@link FileID} keys and {@code byte[]} values. Used to accumulate the
 * contents of special files used in a network software upgrade.
 *
 * <p>Because this leaf will change only during very small and infrequent windows, we can get away
 * with a very naive implementation of the {@link MerkleNode#copy()} contract. Each copy keeps its
 * own map of file contents; and when a file's bytes change in the mutable copy, it updates that map
 * with a completely new {@code byte[]}.
 */
public class MerkleSpecialFiles extends PartialMerkleLeaf implements MerkleLeaf {
    private static final Logger log = LogManager.getLogger(MerkleSpecialFiles.class);

    private static final byte[] NO_CONTENTS = new byte[0];

    public static final long CLASS_ID = 0x1608d4b49c28983aL;
    public static final int MEMCOPY_VERSION = 1;
    public static final int CURRENT_VERSION = 2;

    private final Map<FileID, byte[]> hashCache;
    private final Map<FileID, FCQueue<BytesElement>> fileContents;

    private static Supplier<ByteArrayOutputStream> baosSupplier = ByteArrayOutputStream::new;

    public MerkleSpecialFiles() {
        this.hashCache = new LinkedHashMap<>();
        this.fileContents = new LinkedHashMap<>();
    }

    public MerkleSpecialFiles(MerkleSpecialFiles that) {
        hashCache = new HashMap<>(that.hashCache);
        fileContents = new LinkedHashMap<>();
        for (final var entry : that.getFileContents().entrySet()) {
            fileContents.put(entry.getKey(), entry.getValue().copy());
        }
    }

    /** {@inheritDoc} */
    @Override
    public synchronized MerkleSpecialFiles copy() {
        setImmutable(true);
        return new MerkleSpecialFiles(this);
    }

    @Override
    public boolean isSelfHashing() {
        return true;
    }

    /**
     * Checks if the current contents of the given file match the given SHA-384 hash.
     *
     * @param fid the id of the file to check
     * @param sha384Hash the candidate hash
     * @return if the given file's contents match the given hash
     */
    public synchronized boolean hashMatches(final FileID fid, final byte[] sha384Hash) {
        if (!fileContents.containsKey(fid)) {
            return false;
        }
        return Arrays.equals(sha384Hash, hashOfKnown(fid));
    }

    /**
     * Gets the contents of the given file.
     *
     * @param fid the id of the file to get
     * @return the file's contents
     */
    public synchronized byte[] get(FileID fid) {
        final var fileByParts = fileContents.get(fid);
        if (fileByParts == null) {
            return NO_CONTENTS;
        }
        final var baos = baosSupplier.get();
        for (final BytesElement part : fileByParts) {
            try {
                baos.write(part.getData());
            } catch (IOException e) {
                log.error("Special file concatenation failed for {}", readableId(fid), e);
                throw new UncheckedIOException(e);
            }
        }
        return baos.toByteArray();
    }

    /**
     * Checks if the given file exists.
     *
     * @param fid the id of a file to check existence of
     * @return if the file exixts
     */
    public synchronized boolean contains(FileID fid) {
        return fileContents.containsKey(fid);
    }

    /**
     * Appends the given bytes to the contents of the requested file. (Or, if the file does not yet
     * exist, creates it with the given contents.)
     *
     * @param fid the id of the file to append to
     * @param extraContents the contents to append
     */
    public synchronized void append(FileID fid, byte[] extraContents) {
        throwIfImmutable();
        final var fileByParts = fileContents.get(fid);
        if (fileByParts == null) {
            update(fid, extraContents);
            return;
        }
        fileByParts.add(new BytesElement(extraContents));
        hashCache.remove(fid);
    }

    /**
     * Sets the contents of the requested file to the given bytes.
     *
     * @param fid the id of the file to set contents of
     * @param newContents the new contents
     */
    public synchronized void update(FileID fid, byte[] newContents) {
        throwIfImmutable();
        fileContents.put(fid, newFcqWith(newContents));
        hashCache.remove(fid);
    }

    /** {@inheritDoc} */
    @Override
    public synchronized void deserialize(final SerializableDataInputStream in, final int version)
            throws IOException {
        var numFiles = in.readInt();
        while (numFiles-- > 0) {
            final var fidNum = in.readLong();
            if (version == MEMCOPY_VERSION) {
                final var contents = in.readByteArray(Integer.MAX_VALUE);
                fileContents.put(STATIC_PROPERTIES.scopedFileWith(fidNum), newFcqWith(contents));
            } else {
                final FCQueue<BytesElement> fileByParts = in.readSerializable();
                fileContents.put(STATIC_PROPERTIES.scopedFileWith(fidNum), fileByParts);
            }
        }
    }

    /** {@inheritDoc} */
    @Override
    public synchronized void serialize(final SerializableDataOutputStream out) throws IOException {
        out.writeInt(fileContents.size());
        for (final var entry : fileContents.entrySet()) {
            out.writeLong(entry.getKey().getFileNum());
            out.writeSerializable(entry.getValue(), true);
        }
    }

    /** {@inheritDoc} */
    @Override
    public long getClassId() {
        return CLASS_ID;
    }

    /** {@inheritDoc} */
    @Override
    public int getVersion() {
        return CURRENT_VERSION;
    }

    @Override
    public Hash getHash() {
        final var baos = baosSupplier.get();
        for (final var entry : fileContents.entrySet()) {
            try {
                baos.write(Longs.toByteArray(entry.getKey().getFileNum()));
                baos.write(entry.getValue().getHash().getValue());
            } catch (IOException e) {
                log.error("Hash concatenation failed", e);
                throw new UncheckedIOException(e);
            }
        }
        return new Hash(noThrowSha384HashOf(baos.toByteArray()), DigestType.SHA_384);
    }

    @Override
    public boolean equals(final Object obj) {
        if (this == obj) {
            return true;
        }
        if (obj == null || MerkleSpecialFiles.class != obj.getClass()) {
            return false;
        }

        var that = (MerkleSpecialFiles) obj;
        return Objects.equals(this.fileContents, that.fileContents);
    }

    @Override
    public String toString() {
        return MoreObjects.toStringHelper(MerkleSpecialFiles.class)
                .add("fileContents", this.fileContents)
                .toString();
    }

    @Override
    public int hashCode() {
        return Objects.hash(this.fileContents);
    }

    private byte[] hashOfKnown(FileID fid) {
        return hashCache.computeIfAbsent(
                fid,
                missingFid -> CryptoFactory.getInstance().digestSync(get(missingFid)).getValue());
    }

    private FCQueue<BytesElement> newFcqWith(byte[] initialContents) {
        final var fileByParts = new FCQueue<BytesElement>();
        fileByParts.add(new BytesElement(initialContents));
        return fileByParts;
    }

    /* --- Only used by unit tests --- */
    Map<FileID, FCQueue<BytesElement>> getFileContents() {
        return fileContents;
    }

    public Map<FileID, byte[]> getHashCache() {
        return hashCache;
    }

    static void setBaosSupplier(Supplier<ByteArrayOutputStream> baosSupplier) {
        MerkleSpecialFiles.baosSupplier = baosSupplier;
    }
}
