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

package com.hedera.node.app.records.files;

import static com.hedera.hapi.streams.schema.RecordStreamFileSchema.*;
import static com.hedera.pbj.runtime.ProtoWriterTools.writeLong;
import static com.hedera.pbj.runtime.ProtoWriterTools.writeMessage;

import com.hedera.hapi.node.base.SemanticVersion;
import com.hedera.hapi.streams.HashObject;
import com.hedera.hapi.streams.SidecarMetadata;
import com.hedera.node.app.spi.records.SingleTransactionRecord;
import com.hedera.pbj.runtime.ProtoWriterTools;
import com.hedera.pbj.runtime.io.buffer.Bytes;
import com.hedera.pbj.runtime.io.stream.WritableStreamingData;
import edu.umd.cs.findbugs.annotations.NonNull;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.util.List;

/**
 * Methods for working with a record file of a given format.
 *
 * <p><b>All implementations and methods are expected to be stateless as if they were static.</b></p>
 */
public interface RecordFileFormat {
    /** TODO remove once <a href="https://github.com/hashgraph/pbj/issues/44">PBJ Issue 44</a> is fixed */
    int WIRE_TYPE_DELIMITED = 2;
    /** TODO remove once <a href="https://github.com/hashgraph/pbj/issues/44">PBJ Issue 44</a> is fixed */
    int TAG_TYPE_BITS = 3;

    /**
     * Serialized a record stream item to the intermediary format T that can be used for computing running hashes or writing
     * to the file. Using the two methods {@link RecordFileFormat#computeNewRunningHash(Bytes, List)} and
     * {@link RecordFileFormat#writeRecordStreamItem(WritableStreamingData, SerializedSingleTransactionRecord)}.
     *
     * @param singleTransactionRecord The records of a single transaction to serialize
     * @param blockNumber The current block number this transaction will be written into
     * @param hapiVersion The hapi protobuf version
     * @return the serialized intermediary format item
     */
    SerializedSingleTransactionRecord serialize(
            @NonNull final SingleTransactionRecord singleTransactionRecord,
            final long blockNumber,
            @NonNull final SemanticVersion hapiVersion);

    /**
     * Given the starting running hash and a stream of serialized record stream items, compute the new running hash by adding the items to
     * the hash one at a time.
     *
     * @param startRunningHash the starting current running hash of all record stream items up to this point in time.
     * @param serializedItems the list of intermediary format serialized record stream items to add to the running hash
     * @return the new running hash, or startRunningHash if there were no items to add
     */
    Bytes computeNewRunningHash(
            @NonNull final Bytes startRunningHash,
            @NonNull final List<SerializedSingleTransactionRecord> serializedItems);

    /**
     * Write header to file, the header must include at least the first 4 byte integer version
     *
     * @param outputStream the output stream to write to
     * @param hapiProtoVersion the HAPI version of protobuf
     * @param startObjectRunningHash the starting running hash at the end of previous record file
     */
    void writeHeader(
            @NonNull final WritableStreamingData outputStream,
            @NonNull final SemanticVersion hapiProtoVersion,
            @NonNull final HashObject startObjectRunningHash);

    /**
     * Write a serialized record stream item of type T.
     *
     * @param outputStream the output stream to write to
     * @param item the item to extract/convert
     */
    default void writeRecordStreamItem(
            @NonNull final WritableStreamingData outputStream, @NonNull final SerializedSingleTransactionRecord item) {
        final Bytes itemBytes = item.protobufSerializedRecordStreamItem();
        // [3] - record_stream_items
        // FUTURE can change once https://github.com/hashgraph/pbj/issues/44 is fixed to:
        // ProtoWriterTools.writeTag(outputStream, RECORD_STREAM_ITEMS, ProtoConstants.WIRE_TYPE_DELIMITED);
        outputStream.writeVarInt((RECORD_STREAM_ITEMS.number() << TAG_TYPE_BITS) | WIRE_TYPE_DELIMITED, false);
        outputStream.writeVarInt((int) itemBytes.length(), false);
        outputStream.writeBytes(itemBytes);
    }

    /**
     * Write the footer to the file
     *
     * @param endRunningHash the ending running hash after the last record stream item
     * @param blockNumber the block number of this file
     * @param sidecarMetadata The sidecar metadata to write
     */
    default void writeFooter(
            @NonNull final WritableStreamingData outputStream,
            @NonNull final HashObject endRunningHash,
            final long blockNumber,
            @NonNull final List<SidecarMetadata> sidecarMetadata) {
        try {
            // [4] - end_object_running_hash
            writeMessage(
                    outputStream,
                    END_OBJECT_RUNNING_HASH,
                    endRunningHash,
                    HashObject.PROTOBUF::write,
                    HashObject.PROTOBUF::measureRecord);
            // [5] - block_number
            writeLong(outputStream, BLOCK_NUMBER, blockNumber);
            // [6] - sidecars
            ProtoWriterTools.writeMessageList(
                    outputStream,
                    SIDECARS,
                    sidecarMetadata,
                    SidecarMetadata.PROTOBUF::write,
                    SidecarMetadata.PROTOBUF::measureRecord);
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }
}
