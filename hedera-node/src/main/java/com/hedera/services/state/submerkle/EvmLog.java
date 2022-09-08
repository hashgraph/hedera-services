/*
 * Copyright (C) 2020-2022 Hedera Hashgraph, LLC
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
package com.hedera.services.state.submerkle;

import static com.hedera.services.state.serdes.IoUtils.readNullableSerializable;
import static com.hedera.services.state.serdes.IoUtils.writeNullableSerializable;

import com.google.common.base.MoreObjects;
import com.hedera.services.legacy.proto.utils.ByteStringUtils;
import com.hederahashgraph.api.proto.java.ContractLoginfo;
import com.swirlds.common.io.SelfSerializable;
import com.swirlds.common.io.streams.SerializableDataInputStream;
import com.swirlds.common.io.streams.SerializableDataOutputStream;
import com.swirlds.common.utility.CommonUtils;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.LinkedList;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import org.hyperledger.besu.evm.log.Log;
import org.hyperledger.besu.evm.log.LogsBloomFilter;

public class EvmLog implements SelfSerializable {
    static final int MERKLE_VERSION = 1;
    static final long RUNTIME_CONSTRUCTABLE_ID = 0x2af05aa9c7ff917L;

    public static final byte[] MISSING_BYTES = new byte[0];

    private byte[] data = MISSING_BYTES;
    private byte[] bloom = MISSING_BYTES;
    private EntityId contractId;
    private List<byte[]> topics = Collections.emptyList();

    public static final int MAX_DATA_BYTES = Integer.MAX_VALUE;
    public static final int MAX_BLOOM_BYTES = 256;
    public static final int MAX_TOPIC_BYTES = 1024;

    public EvmLog() {
        // RuntimeConstructable
    }

    public EvmLog(
            final EntityId contractId,
            final byte[] bloom,
            final List<byte[]> topics,
            final byte[] data) {
        this.contractId = contractId;
        this.bloom = bloom;
        this.topics = topics;
        this.data = data;
    }

    public static EvmLog fromBesu(final Log log) {
        return new EvmLog(
                // The HederaLogOperation ensures this will be a mirror address
                EntityId.fromAddress(log.getLogger()),
                LogsBloomFilter.builder().insertLog(log).build().toArray(),
                topicsOf(log),
                log.getData().toArrayUnsafe());
    }

    public static List<EvmLog> fromBesu(final List<Log> logs) {
        if (logs.isEmpty()) {
            return Collections.emptyList();
        } else {
            final int n = logs.size();
            final List<EvmLog> ans = new ArrayList<>(n);
            for (int i = 0; i < n; i++) {
                ans.add(fromBesu(logs.get(i)));
            }
            return ans;
        }
    }

    /* --- SelfSerializable --- */
    @Override
    public long getClassId() {
        return RUNTIME_CONSTRUCTABLE_ID;
    }

    @Override
    public int getVersion() {
        return MERKLE_VERSION;
    }

    @Override
    public void deserialize(SerializableDataInputStream in, int version) throws IOException {
        data = in.readByteArray(MAX_DATA_BYTES);
        bloom = in.readByteArray(MAX_BLOOM_BYTES);
        contractId = readNullableSerializable(in);
        int numTopics = in.readInt();
        if (numTopics > 0) {
            topics = new LinkedList<>();
            for (int i = 0; i < numTopics; i++) {
                topics.add(in.readByteArray(MAX_TOPIC_BYTES));
            }
        }
    }

    @Override
    public void serialize(SerializableDataOutputStream out) throws IOException {
        out.writeByteArray(data);
        out.writeByteArray(bloom);
        writeNullableSerializable(contractId, out);
        out.writeInt(topics.size());
        for (byte[] topic : topics) {
            out.writeByteArray(topic);
        }
    }

    /* --- Object --- */
    @Override
    public boolean equals(final Object o) {
        if (this == o) {
            return true;
        }
        if (o == null || EvmLog.class != o.getClass()) {
            return false;
        }
        EvmLog that = (EvmLog) o;
        return Objects.equals(contractId, that.contractId)
                && Arrays.equals(bloom, that.bloom)
                && Arrays.equals(data, that.data)
                && areSameTopics(topics, that.topics);
    }

    private static boolean areSameTopics(List<byte[]> a, List<byte[]> b) {
        int aLen = Optional.ofNullable(a).map(List::size).orElse(-1);
        int bLen = Optional.ofNullable(b).map(List::size).orElse(-1);
        if (aLen != bLen) {
            return false;
        } else {
            for (int i = 0; i < aLen; i++) {
                if (!Arrays.equals(a.get(i), b.get(i))) {
                    return false;
                }
            }
            return true;
        }
    }

    @Override
    public int hashCode() {
        int result = Objects.hash(contractId, topics);
        result = 31 * result + Arrays.hashCode(bloom);
        return 31 * result + Arrays.hashCode(data);
    }

    @Override
    public String toString() {
        return MoreObjects.toStringHelper(this)
                .add("data", CommonUtils.hex(data))
                .add("bloom", CommonUtils.hex(bloom))
                .add("contractId", contractId)
                .add("topics", topics.stream().map(CommonUtils::hex).toList())
                .toString();
    }

    /* --- Bean --- */
    public EntityId getContractId() {
        return contractId;
    }

    public byte[] getBloom() {
        return bloom;
    }

    public List<byte[]> getTopics() {
        return topics;
    }

    public byte[] getData() {
        return data;
    }

    public void setBloom(byte[] bloom) {
        this.bloom = bloom;
    }

    public ContractLoginfo toGrpc() {
        var grpc = ContractLoginfo.newBuilder();
        if (contractId != null) {
            grpc.setContractID(contractId.toGrpcContractId());
        }
        grpc.setBloom(ByteStringUtils.wrapUnsafely(bloom));
        grpc.setData(ByteStringUtils.wrapUnsafely(data));
        for (final var topic : topics) {
            grpc.addTopic(ByteStringUtils.wrapUnsafely(topic));
        }
        return grpc.build();
    }

    private static List<byte[]> topicsOf(final Log log) {
        final var topics = log.getTopics();
        if (topics.isEmpty()) {
            return Collections.emptyList();
        } else {
            final List<byte[]> rawTopics = new ArrayList<>();
            for (int i = 0, n = topics.size(); i < n; i++) {
                rawTopics.add(topics.get(i).toArrayUnsafe());
            }
            return rawTopics;
        }
    }
}
