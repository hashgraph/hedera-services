package com.hedera.services.legacy.core.jproto;

/*-
 * ‌
 * Hedera Services Node
 * ​
 * Copyright (C) 2018 - 2020 Hedera Hashgraph, LLC
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
import com.hedera.services.state.submerkle.EntityId;
import com.hedera.services.state.submerkle.RichInstant;
import com.hederahashgraph.api.proto.java.TransactionID;

import java.io.DataInputStream;
import java.io.IOException;
import java.util.Objects;

import com.swirlds.common.io.SelfSerializable;
import com.swirlds.common.io.SerializableDataInputStream;
import com.swirlds.common.io.SerializableDataOutputStream;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import static com.hedera.services.legacy.logic.ApplicationConstants.P;
import static com.hedera.services.state.submerkle.EntityId.MISSING_ENTITY_ID;
import static com.hedera.services.state.submerkle.EntityId.ofNullableAccountId;
import static com.hedera.services.state.submerkle.RichInstant.MISSING_INSTANT;
import static com.hedera.services.utils.EntityIdUtils.asAccount;

public class TxnId implements SelfSerializable {
	private static final Logger log = LogManager.getLogger(TxnId.class);

	public static final int MERKLE_VERSION = 1;
	public static final long RUNTIME_CONSTRUCTABLE_ID = 0x61a52dfb3a18d9bL;

	static EntityId.Provider legacyIdProvider = EntityId.LEGACY_PROVIDER;
	static RichInstant.Provider legacyInstantProvider = RichInstant.LEGACY_PROVIDER;

	public static final TxnId.Provider LEGACY_PROVIDER = new TxnId.Provider();

	@Deprecated
	public static class Provider {
		public TxnId deserialize(DataInputStream in) throws IOException {
			var txnId = new TxnId();

			in.readLong();
			in.readLong();

			if (in.readChar() == P) {
				txnId.payerAccount = legacyIdProvider.deserialize(in);
			}
			if (in.readBoolean()) {
				txnId.validStart = legacyInstantProvider.deserialize(in);
			}

			return txnId;
		}
	}

	private EntityId payerAccount = MISSING_ENTITY_ID;
	private RichInstant validStart = MISSING_INSTANT;

	public TxnId() { }

	public TxnId(EntityId payerAccount, RichInstant validStart) {
		this.validStart = validStart;
		this.payerAccount = payerAccount;
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
		validStart = RichInstant.from(in);
	}

	@Override
	public void serialize(SerializableDataOutputStream out) throws IOException {
		out.writeSerializable(payerAccount, true);
		validStart.serialize(out);
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
		return Objects.equals(payerAccount, that.payerAccount) && Objects.equals(validStart, that.validStart);
	}

	@Override
	public int hashCode() {
		return Objects.hash(MERKLE_VERSION, RUNTIME_CONSTRUCTABLE_ID, payerAccount, validStart);
	}

	@Override
	public String toString() {
		return MoreObjects.toStringHelper(this)
				.add("payer", payerAccount)
				.add("validStart", validStart)
				.toString();
	}

	/* --- Helpers --- */

	public static TxnId fromGrpc(final TransactionID grpc) {
		return new TxnId(
				ofNullableAccountId(grpc.getAccountID()),
				RichInstant.fromGrpc(grpc.getTransactionValidStart()));
	}

	public TransactionID toGrpc() {
		var grpc = TransactionID.newBuilder()
				.setAccountID(asAccount(payerAccount));
		if (!validStart.isMissing()) {
			grpc.setTransactionValidStart(validStart.toGrpc());
		}
		return grpc.build();
	}
}
