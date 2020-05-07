package com.hedera.services.context.domain.haccount;

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

import com.hedera.services.context.domain.serdes.DomainSerdes;
import com.swirlds.common.io.FCDataOutputStream;

import java.io.IOException;
import java.util.Optional;

import static com.hedera.services.legacy.logic.ApplicationConstants.N;
import static com.hedera.services.legacy.logic.ApplicationConstants.P;

public enum HederaAccountSerializer {
	HEDERA_ACCOUNT_SERIALIZER;

	public static final long OBJECT_ID = 15487001L;
	public static final long SERIALIZED_VERSION = 5L;

	DomainSerdes serdes = new DomainSerdes();

	public void serializeExceptRecords(HederaAccount account, FCDataOutputStream out) throws IOException {
		out.writeLong(SERIALIZED_VERSION);
		out.writeLong(OBJECT_ID);
		out.writeLong(account.balance);
		out.writeLong(account.senderThreshold);
		out.writeLong(account.receiverThreshold);
		out.writeByte((byte)(account.receiverSigRequired ? 1 : 0));
		serdes.serializeKey(account.accountKeys, out);
		if (account.proxyAccount != null) {
			out.writeChar(P);
			serdes.serializeId(account.proxyAccount, out);
		} else {
			out.writeChar(N);
		}
		out.writeLong(account.autoRenewPeriod);
		out.writeByte((byte)(account.deleted ? 1 : 0));
		out.writeLong(account.expirationTime);
		out.writeUTF(Optional.ofNullable(account.memo).orElse(""));
		out.writeByte((byte)(account.isSmartContract ? 1 : 0));
	}
}
