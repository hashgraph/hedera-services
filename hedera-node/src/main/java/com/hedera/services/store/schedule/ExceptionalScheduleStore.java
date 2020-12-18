package com.hedera.services.store.schedule;

/*
 * ‌
 * Hedera Services Node
 * ​
 * Copyright (C) 2018 - 2020 Hedera Hashgraph, LLC
 * ​
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 * ‍
 */

import com.hedera.services.ledger.HederaLedger;
import com.hedera.services.ledger.TransactionalLedger;
import com.hedera.services.ledger.properties.AccountProperty;
import com.hedera.services.legacy.core.jproto.JKey;
import com.hedera.services.state.merkle.MerkleAccount;
import com.hedera.services.state.merkle.MerkleSchedule;
import com.hedera.services.state.submerkle.RichInstant;
import com.hedera.services.store.CreationResult;
import com.hederahashgraph.api.proto.java.AccountID;
import com.hederahashgraph.api.proto.java.ResponseCodeEnum;
import com.hederahashgraph.api.proto.java.ScheduleID;

import java.util.Optional;
import java.util.Set;
import java.util.function.Consumer;

public enum ExceptionalScheduleStore implements ScheduleStore {
	NOOP_SCHEDULE_STORE;

	@Override
	public MerkleSchedule get(ScheduleID sID) { throw new UnsupportedOperationException(); }

	@Override
	public boolean exists(ScheduleID id) { throw new UnsupportedOperationException(); }

	@Override
	public void setHederaLedger(HederaLedger ledger) {
		/* No-op */
	}

	@Override
	public void setAccountsLedger(TransactionalLedger<AccountID, AccountProperty, MerkleAccount> accountsLedger) {
		/* No-op */
	}

	@Override
	public void apply(ScheduleID id, Consumer<MerkleSchedule> change) {
		throw new UnsupportedOperationException();
	}

	@Override
	public CreationResult<ScheduleID> createProvisionally(byte[] bodyBytes, Optional<AccountID> payer, AccountID schedulingAccount, RichInstant schedulingTXValidStart, Optional<JKey> adminKey) {
		throw new UnsupportedOperationException();
	}

	@Override
	public ResponseCodeEnum addSigners(ScheduleID sID, Set<JKey> key) {
		throw new UnsupportedOperationException();
	}

	@Override
	public Optional<ScheduleID> getScheduleIDByTransactionBody(byte[] bodyBytes) {
		throw new UnsupportedOperationException();
	}

	@Override
	public ResponseCodeEnum delete(ScheduleID id) { throw new UnsupportedOperationException(); }

	@Override
	public void commitCreation() { throw new UnsupportedOperationException(); }

	@Override
	public void rollbackCreation() { throw new UnsupportedOperationException(); }

	@Override
	public boolean isCreationPending() { throw new UnsupportedOperationException(); }
}
