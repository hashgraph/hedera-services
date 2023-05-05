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

import static com.hedera.hapi.streams.schema.RecordStreamFileSchema.HAPI_PROTO_VERSION;
import static com.hedera.hapi.streams.schema.RecordStreamFileSchema.START_OBJECT_RUNNING_HASH;
import static com.hedera.pbj.runtime.ProtoWriterTools.writeMessage;

import com.hedera.hapi.node.base.SemanticVersion;
import com.hedera.hapi.node.base.Transaction;
import com.hedera.hapi.node.transaction.TransactionRecord;
import com.hedera.hapi.streams.HashObject;
import com.hedera.hapi.streams.RecordStreamItem;
import com.hedera.hapi.streams.TransactionSidecarRecord;
import com.hedera.node.app.service.mono.stream.RecordStreamObject;
import com.hedera.node.app.spi.records.SingleTransactionRecord;
import com.hedera.pbj.runtime.io.buffer.Bytes;
import com.hedera.pbj.runtime.io.stream.WritableStreamingData;
import com.swirlds.common.crypto.DigestType;
import com.swirlds.common.crypto.Hash;
import com.swirlds.common.io.streams.SerializableDataOutputStream;
import edu.umd.cs.findbugs.annotations.NonNull;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.Arrays;
import java.util.HexFormat;
import java.util.List;

/**
 * RecordFileWriter for V6 record file format.
 */
public class RecordFileFormatV6 implements RecordFileFormat {

    /** The version of this format */
    public static final int VERSION = 6;
    /** The header bytes to hash before each item in stream */
    private static final byte[] RECORD_STREAM_OBJECT_HEADER;
    /** The header bytes to hash before each hash */
    static final byte[] HASH_HEADER;

    /** Singleton Instance */
    public static final RecordFileFormatV6 INSTANCE = new RecordFileFormatV6();

    /** Private constructor as singleton */
    private RecordFileFormatV6() {}

    static {
        try {
            // compute Hash object header, the hash header is not the usual SelfSerializable Hash object.
            // @see com.swirlds.common.crypto.engine.RunningHashProvider.updateForHash
            // @see com.swirlds.common.crypto.HashBuilder.update(long)
            // @see com.swirlds.common.crypto.HashBuilder.update(int)
            ByteBuffer buf = ByteBuffer.allocate(Long.BYTES + Integer.BYTES);
            buf.order(ByteOrder.LITTLE_ENDIAN);
            buf.putLong(Hash.CLASS_ID);
            buf.putInt(new Hash().getVersion());
            HASH_HEADER = buf.array();
            assert Arrays.equals(HASH_HEADER, HexFormat.of().parseHex("1e7451a283da22f401000000"))
                    : "Hash object header is not the expected 1e7451a283da22f401000000";
            // compute RecordStreamObject header
            ByteArrayOutputStream bout = new ByteArrayOutputStream();
            SerializableDataOutputStream sout = new SerializableDataOutputStream(bout);
            bout.reset();
            sout.writeLong(RecordStreamObject.CLASS_ID);
            sout.writeInt(RecordStreamObject.CLASS_VERSION);
            RECORD_STREAM_OBJECT_HEADER = bout.toByteArray();
            assert Arrays.equals(RECORD_STREAM_OBJECT_HEADER, HexFormat.of().parseHex("e370929ba5429d8b00000001"))
                    : "RecordStreamObject header is not the expected e370929ba5429d8b00000001";
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    /**
     * Serialized a record stream item to the intermediary format T that can be used for computing running hashes or writing
     * to the file. Using the two methods {@link RecordFileFormat#computeNewRunningHash(Bytes, List)} and
     * {@link RecordFileFormat#writeRecordStreamItem(WritableStreamingData, SerializedSingleTransactionRecord)}.
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
            // create RecordStreamItem
            final RecordStreamItem recordStreamItem =
                    new RecordStreamItem(singleTransactionRecord.transaction(), singleTransactionRecord.record());
            // serialize in format for hashing
            // the format is SelfSerializable header and then protobuf fields in reverse order with no protobuf tag
            final var bout = new ByteArrayOutputStream();
            bout.write(RECORD_STREAM_OBJECT_HEADER);
            WritableStreamingData out = new WritableStreamingData(bout);
            // [field 2] - record
            if (recordStreamItem.record() != null) {
                Bytes transactionRecordProtobufBytes = TransactionRecord.PROTOBUF.toBytes(recordStreamItem.record());
                out.writeInt((int) transactionRecordProtobufBytes.length());
                transactionRecordProtobufBytes.writeTo(out);
            }
            // [field 1] - transaction
            if (recordStreamItem.transaction() != null) {
                Bytes transactionProtobufBytes = Transaction.PROTOBUF.toBytes(recordStreamItem.transaction());
                out.writeInt((int) transactionProtobufBytes.length());
                transactionProtobufBytes.writeTo(out);
            }
            // serialize in protobuf format
            final Bytes protobufItemBytes = RecordStreamItem.PROTOBUF.toBytes(recordStreamItem);
            // serialize sidecar items to protobuf
            List<Bytes> sideCarItems = singleTransactionRecord.transactionSidecarRecords().stream()
                    .map(TransactionSidecarRecord.PROTOBUF::toBytes)
                    .toList();
            // return SerializedSingleTransactionRecord
            return new SerializedSingleTransactionRecord(
                    Bytes.wrap(bout.toByteArray()),
                    protobufItemBytes,
                    sideCarItems,
                    singleTransactionRecord.transactionSidecarRecords());
        } catch (IOException e) {
            throw new UncheckedIOException(e);
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
            // use for-i loop as it is faster
            //noinspection ForLoopReplaceableByForEach
            for (int i = 0; i < serializedItems.size(); i++) {
                final SerializedSingleTransactionRecord serializedItem = serializedItems.get(i);
                // first hash the item
                serializedItem.hashSerializedRecordStreamItem().writeTo(messageDigest);
                final byte[] serializedItemHash = messageDigest.digest();
                // now hash the previous hash and the item hash
                messageDigest.update(HASH_HEADER);
                messageDigest.update(previousHash);
                messageDigest.update(HASH_HEADER);
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
            // [1] - hapi_proto_version
            writeMessage(
                    outputStream,
                    HAPI_PROTO_VERSION,
                    hapiProtoVersion,
                    com.hedera.hapi.node.base.SemanticVersion.PROTOBUF::write,
                    com.hedera.hapi.node.base.SemanticVersion.PROTOBUF::measureRecord);
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
}
