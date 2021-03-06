package com.hedera.services.state.submerkle;

/*-
 * ‌
 * Hedera Services Node
 * ​
 * Copyright (C) 2018 - 2021 Hedera Hashgraph, LLC
 * ​
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
 * ‍
 */

import com.google.common.base.MoreObjects;
import com.google.protobuf.ByteString;
import com.hedera.services.state.merkle.MerkleNftOwnership;
import com.hedera.services.utils.EntityIdUtils;
import com.hederahashgraph.api.proto.java.AccountAmount;
import com.hederahashgraph.api.proto.java.NftTransfer;
import com.hederahashgraph.api.proto.java.NftTransferList;
import com.hederahashgraph.api.proto.java.TransferList;
import com.swirlds.common.io.SelfSerializable;
import com.swirlds.common.io.SerializableDataInputStream;
import com.swirlds.common.io.SerializableDataOutputStream;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.stream.IntStream;

import static com.hedera.services.state.submerkle.EntityId.fromGrpcAccount;
import static com.hedera.services.utils.MiscUtils.readableOwnershipChanges;
import static com.hedera.services.utils.MiscUtils.readableTransferList;
import static java.util.stream.Collectors.toList;

public class OwnershipChanges implements SelfSerializable {
	private static final Logger log = LogManager.getLogger(OwnershipChanges.class);

	static final int MERKLE_VERSION = 1;
	static final long RUNTIME_CONSTRUCTABLE_ID = 0xfa8373a45e02fff4L;

	static final int NUM_SERIAL_NO_BYTES = MerkleNftOwnership.NUM_NFT_SERIAL_NO_BYTES;

	static final byte[] NO_SERIAL_NOS = new byte[0];

	public static final int MAX_NUM_CHANGES = 25;

	byte[] serialNos = NO_SERIAL_NOS;
	List<EntityId> fromToIds = Collections.emptyList();

	public OwnershipChanges() {
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
		fromToIds = in.readSerializableList(MAX_NUM_CHANGES, true, EntityId::new);
		serialNos = in.readByteArray(MAX_NUM_CHANGES * NUM_SERIAL_NO_BYTES);
	}

	@Override
	public void serialize(SerializableDataOutputStream out) throws IOException {
		out.writeSerializableList(fromToIds, true, true);
		out.writeByteArray(serialNos);
	}

	/* ---- Object --- */

	@Override
	public boolean equals(Object o) {
		if (this == o) {
			return true;
		}
		if (o == null || OwnershipChanges.class != o.getClass()) {
			return false;
		}

		OwnershipChanges that = (OwnershipChanges) o;
		return fromToIds.equals(that.fromToIds) && Arrays.equals(serialNos, that.serialNos);
	}

	@Override
	public int hashCode() {
		int result = Long.hashCode(RUNTIME_CONSTRUCTABLE_ID);
		result = result * 31 + Integer.hashCode(MERKLE_VERSION);
		result = result * 31 + fromToIds.hashCode();
		return result * 31 + Arrays.hashCode(serialNos);
	}

	@Override
	public String toString() {
		return MoreObjects.toStringHelper(this)
				.add("readable", readableOwnershipChanges(toGrpc()))
				.toString();
	}

	/* --- Helpers --- */

	public NftTransferList toGrpc() {
		var grpc = NftTransferList.newBuilder();
		for (int i = 0, n = fromToIds.size() / 2; i < n; i++) {
			var changes = NftTransfer.newBuilder()
					.setFromAccount(fromToIds.get(i * 2).toGrpcAccountId())
					.setToAccount(fromToIds.get(i * 2 + 1).toGrpcAccountId())
					.setSerialNo(ByteString.copyFrom(serialNos, i * NUM_SERIAL_NO_BYTES, NUM_SERIAL_NO_BYTES));
			grpc.addTransfer(changes);
		}
		return grpc.build();
	}

	public static OwnershipChanges fromGrpc(NftTransferList grpc) {
		return fromGrpc(grpc.getTransferList());
	}

	public static OwnershipChanges fromGrpc(List<NftTransfer> grpc) {
		var pojo = new OwnershipChanges();
		int n = grpc.size();
		if (n > 0) {
			pojo.fromToIds = new ArrayList<>(2 * n);
			pojo.serialNos = new byte[n * NUM_SERIAL_NO_BYTES];
			for (int i = 0; i < n; i++) {
				var grpcChange = grpc.get(i);
				pojo.fromToIds.add(fromGrpcAccount(grpcChange.getFromAccount()));
				pojo.fromToIds.add(fromGrpcAccount(grpcChange.getToAccount()));
				System.arraycopy(
						grpcChange.getSerialNo().toByteArray(),
						0,
						pojo.serialNos,
						i * NUM_SERIAL_NO_BYTES,
						NUM_SERIAL_NO_BYTES);
			}
		}
		return pojo;
	}
}
