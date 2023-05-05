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

import static com.hedera.hapi.streams.schema.RecordStreamFileSchema.START_OBJECT_RUNNING_HASH;
import static com.hedera.pbj.runtime.ProtoWriterTools.writeMessage;

import com.hedera.hapi.node.base.SemanticVersion;
import com.hedera.hapi.node.base.Transaction;
import com.hedera.hapi.node.transaction.TransactionRecord;
import com.hedera.hapi.streams.HashObject;
import com.hedera.hapi.streams.TransactionSidecarRecord;
import com.hedera.node.app.spi.records.SingleTransactionRecord;
import com.hedera.pbj.runtime.Codec;
import com.hedera.pbj.runtime.io.ReadableSequentialData;
import com.hedera.pbj.runtime.io.WritableSequentialData;
import com.hedera.pbj.runtime.io.buffer.Bytes;
import com.hedera.pbj.runtime.io.stream.WritableStreamingData;
import com.swirlds.common.crypto.DigestType;
import edu.umd.cs.findbugs.annotations.NonNull;
import edu.umd.cs.findbugs.annotations.Nullable;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.List;

/**
 * This is a prototype for a RecordFileWriter for a cleaned up version of V6 format, going to true protobuf and
 * no SelfSerializable formatting.
 * <p>
 * It is only here so that the APIs further up can be designed in a way that is compatible with the new format.
 */
public final class RecordFileFormatV7 implements RecordFileFormat {
    /** The version of this format */
    public static final int VERSION = 7;

    /** Singleton Instance */
    public static final RecordFileFormatV7 INSTANCE = new RecordFileFormatV7();

    /**
     * True once the first item has been created
     * TODO remove this, find another way. Because this is not a singleton format it can not have state, so
     * TODO file writer or StreamFileProducer will need to maintain state.
     */
    private boolean hasFirstItemBeenSerializedYet = false;

    /** Private constructor as singleton */
    private RecordFileFormatV7() {}

    /**
     * Serialized a record stream item to the intermediary format T that can be used for computing running hashes or writing
     * to the file. Using the two methods {@link this.computeNewRunningHash()} and {@link this.writeRecordStreamItem()}.
     * <p>
     *     This method is expected to be stateless so it can be called on other threads.
     * </p>
     *
     * @param singleTransactionRecord The records of a single transaction to serialize
     * @param blockNumber The current block number this transaction will be written into
     * @param hapiVersion The hapi protobuf version
     * @return the serialized intermediary format item
     */
    @Override
    public SerializedSingleTransactionRecord serialize(
            @NonNull final SingleTransactionRecord singleTransactionRecord,
            final long blockNumber,
            @NonNull final SemanticVersion hapiVersion) {
        try {
            // first serialize sidecar items to protobuf
            final List<Bytes> sideCarItemsBytes = singleTransactionRecord.transactionSidecarRecords().stream()
                    .map(TransactionSidecarRecord.PROTOBUF::toBytes)
                    .toList();
            // now hash sidecar items
            Bytes sidecarHash = null;
            if (!sideCarItemsBytes.isEmpty()) {
                final MessageDigest sidecarMessageDigest =
                        MessageDigest.getInstance(DigestType.SHA_384.algorithmName());
                for (Bytes sideCarItem : sideCarItemsBytes) {
                    sideCarItem.writeTo(sidecarMessageDigest);
                }
                sidecarHash = Bytes.wrap(sidecarMessageDigest.digest());
            }
            // now create a RecordStreamItemV7
            RecordStreamItemV7 recordStreamItemV7 = hasFirstItemBeenSerializedYet
                    ? new RecordStreamItemV7(
                            singleTransactionRecord.transaction(),
                            singleTransactionRecord.record(),
                            sidecarHash,
                            null,
                            0,
                            0)
                    : new RecordStreamItemV7(
                            singleTransactionRecord.transaction(),
                            singleTransactionRecord.record(),
                            sidecarHash,
                            hapiVersion,
                            blockNumber,
                            VERSION);
            // we have created an item so hasFirstItemBeenSerializedYet is now true
            hasFirstItemBeenSerializedYet = true;
            return new SerializedSingleTransactionRecord(
                    null,
                    RecordStreamItemV7.PROTOBUF.toBytes(recordStreamItemV7),
                    sideCarItemsBytes,
                    singleTransactionRecord.transactionSidecarRecords());
        } catch (NoSuchAlgorithmException e) {
            throw new RuntimeException(e);
        }
    }

    /**
     * Given the starting running hash and a stream of serialized record stream items, compute the new running hash by adding the items to
     * the hash one at a time.
     * <p>
     *     This method is expected to be stateless so it can be called on other threads.
     * </p>
     *
     * @param startRunningHash the starting current running hash of all record stream items up to this point in time.
     * @param serializedItems the stream of intermediary format serialized record stream items to add to the running hash
     * @return the new running hash, or startRunningHash if there were no items to add
     */
    @Override
    public Bytes computeNewRunningHash(
            @NonNull final Bytes startRunningHash,
            @NonNull final List<SerializedSingleTransactionRecord> serializedItems) {
        try {
            byte[] previousHash = startRunningHash.toByteArray();
            final MessageDigest messageDigest = MessageDigest.getInstance(DigestType.SHA_384.algorithmName());
            // Use for-i loop because it is faster, and we know List is a ArrayList
            //noinspection ForLoopReplaceableByForEach
            for (int i = 0; i < serializedItems.size(); i++) {
                final Bytes serializedItem = serializedItems.get(i).protobufSerializedRecordStreamItem();
                // first hash the item
                serializedItem.writeTo(messageDigest);
                final byte[] serializedItemHash = messageDigest.digest();
                // now hash the previous hash and the item hash
                messageDigest.update(previousHash);
                messageDigest.update(serializedItemHash);
                previousHash = messageDigest.digest();
            }
            return Bytes.wrap(previousHash);
        } catch (NoSuchAlgorithmException e) {
            throw new RuntimeException(e);
        }
    }

    /**
     * Write header to file, the header must include at least the first 4 byte integer version
     *
     * @param outputStream the output stream to write to
     * @param hapiProtoVersion       the HAPI version of protobuf
     * @param startObjectRunningHash the starting running hash at the end of previous record file
     */
    @Override
    public void writeHeader(
            @NonNull final WritableStreamingData outputStream,
            @NonNull final SemanticVersion hapiProtoVersion,
            @NonNull final HashObject startObjectRunningHash) {
        try {
            // Write the record file version int first to start of file
            outputStream.writeInt(VERSION);
            // [2] - start_object_running_hash
            writeMessage(
                    outputStream,
                    START_OBJECT_RUNNING_HASH,
                    startObjectRunningHash,
                    com.hedera.hapi.streams.HashObject.PROTOBUF::write,
                    com.hedera.hapi.streams.HashObject.PROTOBUF::measureRecord);
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    // =================================================================================================================

    /** Temporary fake record stream item format for V7 */
    public record RecordStreamItemV7(
            @Nullable Transaction transaction,
            @Nullable TransactionRecord record,
            @Nullable Bytes hashOfSidecarItems,
            @Nullable SemanticVersion hapiVersion,
            long blockNumber,
            int recordFileVersion) {
        /** Protobuf codec for reading and writing in protobuf format */
        public static final Codec<RecordStreamItemV7> PROTOBUF = new RecordStreamItemV7ProtoCodec();
    }

    public static final class RecordStreamItemV7ProtoCodec implements Codec<RecordStreamItemV7> {
        public @NonNull RecordStreamItemV7 parse(@NonNull ReadableSequentialData input) {
            return new RecordStreamItemV7(null, null, null, null, 0, 0);
        }

        public @NonNull RecordStreamItemV7 parseStrict(@NonNull ReadableSequentialData input) {
            return new RecordStreamItemV7(null, null, null, null, 0, 0);
        }

        public void write(@NonNull RecordStreamItemV7 item, @NonNull WritableSequentialData output) {}

        public int measure(@NonNull ReadableSequentialData input) {
            return 0;
        }

        public int measureRecord(RecordStreamItemV7 item) {
            return 0;
        }

        public boolean fastEquals(@NonNull RecordStreamItemV7 item, @NonNull ReadableSequentialData input) {
            return false;
        }
    }
}
