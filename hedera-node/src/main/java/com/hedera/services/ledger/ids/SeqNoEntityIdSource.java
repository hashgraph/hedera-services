package com.hedera.services.ledger.ids;

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

import com.hederahashgraph.api.proto.java.AccountID;
import com.hederahashgraph.api.proto.java.FileID;
import com.hedera.services.state.submerkle.SequenceNumber;

public class SeqNoEntityIdSource implements EntityIdSource {
	private final SequenceNumber seqNo;

	public SeqNoEntityIdSource(SequenceNumber seqNo) {
		this.seqNo = seqNo;
	}

	@Override
	public AccountID newAccountId(AccountID sponsor) {
		return AccountID.newBuilder()
				.setRealmNum(sponsor.getRealmNum())
				.setShardNum(sponsor.getShardNum())
				.setAccountNum(seqNo.getAndIncrement())
				.build();
	}

	@Override
	public FileID newFileId(AccountID sponsor) {
		return FileID.newBuilder()
				.setRealmNum(sponsor.getRealmNum())
				.setShardNum(sponsor.getShardNum())
				.setFileNum(seqNo.getAndIncrement())
				.build();
	}
}
