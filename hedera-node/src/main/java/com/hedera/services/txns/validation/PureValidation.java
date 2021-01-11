package com.hedera.services.txns.validation;

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

import com.hedera.services.legacy.core.jproto.JKey;
import com.hedera.services.state.merkle.MerkleAccount;
import com.hedera.services.context.primitives.StateView;
import com.hederahashgraph.api.proto.java.AccountID;
import com.hederahashgraph.api.proto.java.ContractID;
import com.hederahashgraph.api.proto.java.FileGetInfoResponse;
import com.hederahashgraph.api.proto.java.FileID;
import com.hederahashgraph.api.proto.java.Key;
import com.hederahashgraph.api.proto.java.ResponseCodeEnum;
import com.hederahashgraph.api.proto.java.Timestamp;
import com.hedera.services.state.merkle.MerkleEntityId;
import com.swirlds.fcmap.FCMap;

import java.time.Instant;
import java.util.Optional;

import static com.hederahashgraph.api.proto.java.ResponseCodeEnum.ACCOUNT_DELETED;
import static com.hederahashgraph.api.proto.java.ResponseCodeEnum.CONTRACT_DELETED;
import static com.hederahashgraph.api.proto.java.ResponseCodeEnum.INVALID_ACCOUNT_ID;
import static com.hederahashgraph.api.proto.java.ResponseCodeEnum.INVALID_CONTRACT_ID;
import static com.hederahashgraph.api.proto.java.ResponseCodeEnum.INVALID_FILE_ID;
import static com.hederahashgraph.api.proto.java.ResponseCodeEnum.INVALID_TRANSACTION_START;
import static com.hederahashgraph.api.proto.java.ResponseCodeEnum.OK;
import static com.hederahashgraph.api.proto.java.ResponseCodeEnum.TRANSACTION_EXPIRED;
import static com.hedera.services.state.merkle.MerkleEntityId.fromContractId;

public class PureValidation {
	public static ResponseCodeEnum queryableFileStatus(FileID id, StateView view) {
		Optional<FileGetInfoResponse.FileInfo> info = view.infoForFile(id);
		if (info.isEmpty()) {
			return INVALID_FILE_ID;
		} else {
			return  OK;
		}
	}

	public static ResponseCodeEnum queryableAccountStatus(AccountID id, FCMap<MerkleEntityId, MerkleAccount> accounts) {
		MerkleAccount account = accounts.get(MerkleEntityId.fromAccountId(id));

		return Optional.ofNullable(account)
				.map(v -> v.isDeleted()
						? ACCOUNT_DELETED
						: (v.isSmartContract() ? INVALID_ACCOUNT_ID : OK))
				.orElse(INVALID_ACCOUNT_ID);
	}

	public static ResponseCodeEnum queryableContractStatus(ContractID cid, FCMap<MerkleEntityId, MerkleAccount> contracts) {
		MerkleAccount contract = contracts.get(fromContractId(cid));

		return Optional.ofNullable(contract)
				.map(v -> v.isDeleted()
						? CONTRACT_DELETED
						: (!v.isSmartContract() ? INVALID_CONTRACT_ID : OK))
				.orElse(INVALID_CONTRACT_ID);
	}

	public static ResponseCodeEnum chronologyStatus(Instant consensusTime, Instant validStart, long validDuration) {
		validDuration = Math.min(validDuration, Instant.MAX.getEpochSecond() - validStart.getEpochSecond());
		if (validStart.plusSeconds(validDuration).isBefore(consensusTime)) {
			return TRANSACTION_EXPIRED;
		} else if (!validStart.isBefore(consensusTime)) {
			return INVALID_TRANSACTION_START;
		} else {
			return OK;
		}
	}

	public static Instant asCoercedInstant(Timestamp when) {
		return Instant.ofEpochSecond(
			Math.min(Math.max(Instant.MIN.getEpochSecond(), when.getSeconds()), Instant.MAX.getEpochSecond()),
			Math.min(Math.max(Instant.MIN.getNano(), when.getNanos()), Instant.MAX.getNano()));
	}

	public static ResponseCodeEnum checkKey(Key key, ResponseCodeEnum failure) {
		try {
			var fcKey = JKey.mapKey(key);
			if (!fcKey.isValid()) {
				return failure;
			}
			return OK;
		} catch (Exception ignore) {
			return failure;
		}
	}
}
