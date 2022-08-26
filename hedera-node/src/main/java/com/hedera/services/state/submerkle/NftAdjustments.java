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

import static com.hedera.services.utils.MiscUtils.readableNftTransferList;

import com.google.common.base.MoreObjects;
import com.hedera.services.utils.EntityIdUtils;
import com.hederahashgraph.api.proto.java.NftTransfer;
import com.hederahashgraph.api.proto.java.TokenTransferList;
import com.swirlds.common.io.SelfSerializable;
import com.swirlds.common.io.streams.SerializableDataInputStream;
import com.swirlds.common.io.streams.SerializableDataOutputStream;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.stream.IntStream;

public class NftAdjustments implements SelfSerializable {
    private static final int MERKLE_VERSION = 1;
    private static final long RUNTIME_CONSTRUCTABLE_ID = 0xd7a02bf45e103466L;
    private static final long[] NO_ADJUSTMENTS = new long[0];

    static final int MAX_NUM_ADJUSTMENTS = 1024;

    private long[] serialNums = NO_ADJUSTMENTS;
    private List<EntityId> senderAccIds = Collections.emptyList();
    private List<EntityId> receiverAccIds = Collections.emptyList();

    public NftAdjustments() {
        // RuntimeConstructable
    }

    public NftAdjustments(
            final long[] serialNums,
            final List<EntityId> senderAccIds,
            final List<EntityId> receiverAccIds) {
        this.serialNums = serialNums;
        this.senderAccIds = senderAccIds;
        this.receiverAccIds = receiverAccIds;
    }

    public void appendAdjust(
            final EntityId senderId, final EntityId receiverId, final long serialNo) {
        final var newSerialNums = new long[serialNums.length + 1];
        System.arraycopy(serialNums, 0, newSerialNums, 0, serialNums.length);
        newSerialNums[serialNums.length] = serialNo;
        serialNums = newSerialNums;

        senderAccIds = new ArrayList<>(senderAccIds);
        senderAccIds.add(senderId);
        receiverAccIds = new ArrayList<>(receiverAccIds);
        receiverAccIds.add(receiverId);
    }

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
        serialNums = in.readLongArray(MAX_NUM_ADJUSTMENTS);
        senderAccIds = in.readSerializableList(MAX_NUM_ADJUSTMENTS, true, EntityId::new);
        receiverAccIds = in.readSerializableList(MAX_NUM_ADJUSTMENTS, true, EntityId::new);
    }

    @Override
    public void serialize(SerializableDataOutputStream out) throws IOException {
        out.writeLongArray(serialNums);
        out.writeSerializableList(senderAccIds, true, true);
        out.writeSerializableList(receiverAccIds, true, true);
    }

    /* ---- Object --- */
    @Override
    public boolean equals(Object o) {
        if (this == o) {
            return true;
        }
        if (o == null || getClass() != o.getClass()) {
            return false;
        }
        NftAdjustments that = (NftAdjustments) o;
        return Arrays.equals(serialNums, that.serialNums)
                && senderAccIds.equals(that.senderAccIds)
                && receiverAccIds.equals(that.receiverAccIds);
    }

    @Override
    public int hashCode() {
        int result = Long.hashCode(RUNTIME_CONSTRUCTABLE_ID);
        result = result * 31 + senderAccIds.hashCode();
        result = result * 31 + receiverAccIds.hashCode();
        return result * 31 + Arrays.hashCode(serialNums);
    }

    @Override
    public String toString() {
        return MoreObjects.toStringHelper(this)
                .add("readable", readableNftTransferList(toGrpc()))
                .toString();
    }

    /* --- Helpers --- */
    public TokenTransferList toGrpc() {
        var grpc = TokenTransferList.newBuilder();
        IntStream.range(0, serialNums.length)
                .mapToObj(this::transferBuilderFor)
                .forEach(grpc::addNftTransfers);

        return grpc.build();
    }

    public void addToGrpc(final TokenTransferList.Builder builder) {
        for (int i = 0; i < serialNums.length; i++) {
            builder.addNftTransfers(transferBuilderFor(i));
        }
    }

    public static NftAdjustments fromGrpc(List<NftTransfer> grpc) {
        var pojo = new NftAdjustments();

        pojo.serialNums = grpc.stream().mapToLong(NftTransfer::getSerialNumber).toArray();
        pojo.senderAccIds =
                grpc.stream()
                        .filter(nftTransfer -> nftTransfer.getSenderAccountID() != null)
                        .map(NftTransfer::getSenderAccountID)
                        .map(EntityId::fromGrpcAccountId)
                        .toList();
        pojo.receiverAccIds =
                grpc.stream()
                        .map(NftTransfer::getReceiverAccountID)
                        .map(EntityId::fromGrpcAccountId)
                        .toList();

        return pojo;
    }

    private NftTransfer.Builder transferBuilderFor(final int i) {
        return NftTransfer.newBuilder()
                .setSerialNumber(serialNums[i])
                .setSenderAccountID(EntityIdUtils.asAccount(senderAccIds.get(i)))
                .setReceiverAccountID(EntityIdUtils.asAccount(receiverAccIds.get(i)));
    }
}
