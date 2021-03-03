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
import com.hedera.services.state.serdes.DomainSerdes;
import com.hederahashgraph.api.proto.java.TransactionID;

import java.io.IOException;
import java.util.Arrays;
import java.util.Objects;

import com.swirlds.common.io.SelfSerializable;
import com.swirlds.common.io.SerializableDataInputStream;
import com.swirlds.common.io.SerializableDataOutputStream;
import org.apache.commons.codec.binary.Hex;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import static com.hedera.services.state.submerkle.EntityId.MISSING_ENTITY_ID;
import static com.hedera.services.state.submerkle.EntityId.ofNullableAccountId;
import static com.hedera.services.state.submerkle.RichInstant.MISSING_INSTANT;
import static com.hedera.services.utils.EntityIdUtils.asAccount;

public class TxnId implements SelfSerializable {
	private static final Logger log = LogManager.getLogger(TxnId.class);

	private static final byte[] ABSENT_NONCE = null;

	static final int MAX_CONCEIVABLE_NONCE_BYTES = Integer.MAX_VALUE;

	static final int PRE_RELEASE_0120_VERSION = 1;
	static final int RELEASE_0120_VERSION = 2;
	public static final int MERKLE_VERSION = RELEASE_0120_VERSION;

	public static final long RUNTIME_CONSTRUCTABLE_ID = 0x61a52dfb3a18d9bL;

	static DomainSerdes serdes = new DomainSerdes();

	private byte[] nonce = ABSENT_NONCE;
	private boolean scheduled = false;
	private EntityId payerAccount = MISSING_ENTITY_ID;
	private RichInstant validStart = MISSING_INSTANT;

	public TxnId() { }

	public TxnId(
			EntityId payerAccount,
			RichInstant validStart,
			boolean scheduled,
			byte[] nonce
	) {
		this.scheduled = scheduled;
		this.validStart = validStart;
		this.payerAccount = payerAccount;
		this.nonce = nonce;
	}

	public EntityId getPayerAccount() {
		return payerAccount;
	}

	public RichInstant getValidStart() {
		return validStart;
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
		payerAccount = in.readSerializable(true, EntityId::new);
		validStart = serdes.deserializeTimestamp(in);
		if (version >= RELEASE_0120_VERSION) {
			scheduled = in.readBoolean();
			var hasNonce = in.readBoolean();
			if (hasNonce) {
				nonce = in.readByteArray(MAX_CONCEIVABLE_NONCE_BYTES);
			}
		}
	}

	@Override
	public void serialize(SerializableDataOutputStream out) throws IOException {
		out.writeSerializable(payerAccount, true);
		serdes.serializeTimestamp(validStart, out);
		out.writeBoolean(scheduled);
		if (nonce == ABSENT_NONCE) {
			out.writeBoolean(false);
		} else {
			out.writeBoolean(true);
			out.writeByteArray(nonce);
		}
	}

	/* --- Objects --- */
	@Override
	public boolean equals(Object o) {
		if (this == o) {
			return true;
		}
		if (o == null || TxnId.class != o.getClass()) {
			return false;
		}
		var that = (TxnId)o;
		return this.scheduled == that.scheduled &&
				Objects.equals(payerAccount, that.payerAccount) &&
				Objects.equals(validStart, that.validStart) &&
				Arrays.equals(this.nonce, that.nonce);
	}

	@Override
	public int hashCode() {
		return Objects.hash(
				MERKLE_VERSION,
				RUNTIME_CONSTRUCTABLE_ID,
				payerAccount,
				validStart,
				scheduled,
				nonce);
	}

	@Override
	public String toString() {
		var helper = MoreObjects.toStringHelper(this)
				.add("payer", payerAccount)
				.add("validStart", validStart)
				.add("scheduled", scheduled);
		if (nonce != ABSENT_NONCE) {
			helper.add("nonce", Hex.encodeHexString(nonce));
		}
		return helper.toString();
	}

	/* --- Helpers --- */
	public static TxnId fromGrpc(final TransactionID grpc) {
		var grpcNonce = grpc.getNonce();
		byte[] nonce = grpcNonce.isEmpty() ? ABSENT_NONCE : grpcNonce.toByteArray();
		return new TxnId(
				ofNullableAccountId(grpc.getAccountID()),
				RichInstant.fromGrpc(grpc.getTransactionValidStart()),
				grpc.getScheduled(),
				nonce);
	}

	public TransactionID toGrpc() {
		var grpc = TransactionID.newBuilder().setAccountID(asAccount(payerAccount));

		if (!validStart.isMissing()) {
			grpc.setTransactionValidStart(validStart.toGrpc());
		}
		grpc.setScheduled(scheduled);
		if (nonce != ABSENT_NONCE) {
			grpc.setNonce(ByteString.copyFrom(nonce));
		}

		return grpc.build();
	}
}
