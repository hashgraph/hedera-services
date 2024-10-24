/*
 * Copyright (C) 2023-2024 Hedera Hashgraph, LLC
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

package com.hedera.node.app.records.impl.producers.formats.v6;

import static com.hedera.hapi.streams.schema.SidecarFileSchema.SIDECAR_RECORDS;
import static com.hedera.node.app.records.impl.producers.BlockRecordFormat.TAG_TYPE_BITS;
import static com.hedera.node.app.records.impl.producers.BlockRecordFormat.WIRE_TYPE_DELIMITED;

import com.hedera.hapi.streams.SidecarType;
import com.hedera.hapi.streams.TransactionSidecarRecord;
import com.hedera.pbj.runtime.io.buffer.Bytes;
import com.hedera.pbj.runtime.io.stream.WritableStreamingData;
import com.swirlds.common.crypto.HashingOutputStream;
import edu.umd.cs.findbugs.annotations.NonNull;
import java.io.BufferedOutputStream;
import java.io.IOException;
import java.io.OutputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.EnumSet;
import java.util.List;
import java.util.zip.GZIPOutputStream;

/**
 * An incremental sidecar file writer that writes a single {@link TransactionSidecarRecord} at a time. It also maintains
 * a hash as it is going that can be fetched at the end after closing. A single {@link SidecarWriterV6} represents one
 * sidecar file and has a life-span of writing a single file.
 */
final class SidecarWriterV6 implements AutoCloseable {
    /** The maximum size of a sidecar file in bytes */
    private final int maxSideCarSizeInBytes;
    /** The HashingOutputStream does not propagate close() to its delegate,
     * so we need to manually call close() on the stream it wraps. */
    private final OutputStream hashingDelegateStream;
    /** HashingOutputStream for hashing the file contents */
    private final HashingOutputStream hashingOutputStream;
    /** WritableStreamingData we are data writing to, that goes into the file */
    private final WritableStreamingData outputStream;
    /** Set of the types of sidecar records that are in this file */
    private final EnumSet<SidecarType> sidecarTypes = EnumSet.noneOf(SidecarType.class);
    /** The hash of the file contents, computed in close */
    private Bytes hash = null;
    /** The number of uncompressed bytes written to this file */
    private long bytesWritten;

    private final int id;

    /**
     * Creates a new incremental sidecar file writer on a new file.
     *
     * @param file path to the file to write
     * @param compressFile true if the file should be gzip compressed
     * @param maxSideCarSizeInBytes the maximum size of a sidecar file in bytes before compression
     * @throws IOException If there was a problem creating the file
     */
    SidecarWriterV6(@NonNull final Path file, final boolean compressFile, final int maxSideCarSizeInBytes, final int id)
            throws IOException {
        this.id = id;
        this.maxSideCarSizeInBytes = maxSideCarSizeInBytes;
        // create parent directories if needed
        Files.createDirectories(file.getParent());
        // create digest for hashing the file contents
        MessageDigest wholeFileDigest;
        try {
            wholeFileDigest = MessageDigest.getInstance("SHA-384");
        } catch (NoSuchAlgorithmException e) {
            throw new RuntimeException(e);
        }
        // create streams
        final var fout = Files.newOutputStream(file);
        if (compressFile) {
            GZIPOutputStream gout = new GZIPOutputStream(fout);
            hashingDelegateStream = gout;
            hashingOutputStream = new HashingOutputStream(wholeFileDigest, gout);
            BufferedOutputStream bout = new BufferedOutputStream(hashingOutputStream);
            outputStream = new WritableStreamingData(bout);
        } else {
            hashingDelegateStream = fout;
            hashingOutputStream = new HashingOutputStream(wholeFileDigest, fout);
            BufferedOutputStream bout = new BufferedOutputStream(hashingOutputStream);
            outputStream = new WritableStreamingData(bout);
        }
    }

    public int id() {
        return id;
    }

    /**
     * Write a single TransactionSidecarRecord to sidecar file.
     *
     * @param sidecarType the type of sidecar record
     * @param transactionSidecarRecord the TransactionSidecarRecord to write to file
     * @return true if the record was written, false if it was not written as it would cause the file to exceed the maximum size
     */
    boolean writeTransactionSidecarRecord(
            @NonNull final TransactionSidecarRecord.SidecarRecordsOneOfType sidecarType,
            @NonNull final Bytes transactionSidecarRecord) {
        if ((bytesWritten + transactionSidecarRecord.length()) > maxSideCarSizeInBytes) {
            // return false as writing the record was not possible as it would cause the file to exceed the maximum size
            return false;
        }
        // add type to set
        switch (sidecarType) {
            case ACTIONS -> sidecarTypes.add(SidecarType.CONTRACT_ACTION);
            case BYTECODE -> sidecarTypes.add(SidecarType.CONTRACT_BYTECODE);
            case STATE_CHANGES -> sidecarTypes.add(SidecarType.CONTRACT_STATE_CHANGE);
            default -> throw new IllegalStateException("Unexpected value: " + sidecarType);
        }
        // update bytes written counter
        bytesWritten += transactionSidecarRecord.length();
        // write protobuf format to file
        // FUTURE can change once https://github.com/hashgraph/pbj/issues/44 is fixed to:
        // ProtoWriterTools.writeTag(outputStream, SIDECAR_RECORDS, WIRE_TYPE_DELIMITED);
        outputStream.writeVarInt((SIDECAR_RECORDS.number() << TAG_TYPE_BITS) | WIRE_TYPE_DELIMITED, false);
        outputStream.writeVarInt((int) transactionSidecarRecord.length(), false);
        outputStream.writeBytes(transactionSidecarRecord);
        // return true as we have written the record
        return true;
    }

    /**
     * Get the hash for the written file, will return null before file is finished writing and has been closed.
     *
     * @return the hash bytes
     */
    Bytes fileHash() {
        return hash;
    }

    /**
     * Get list of all sidecar transaction types written to this file.
     *
     * @return the list of sidecar transaction types
     */
    List<SidecarType> types() {
        return List.copyOf(sidecarTypes);
    }

    /**
     * Closes this resource
     */
    @Override
    public void close() throws IOException {
        outputStream.close();
        hashingDelegateStream.close();
        hash = Bytes.wrap(hashingOutputStream.getDigest());
    }
}
