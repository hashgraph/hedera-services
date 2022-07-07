package com.hedera.services.utils.accessors;

/*-
 * ‌
 * Hedera Services Node
 * ​
 * Copyright (C) 2018 - 2022 Hedera Hashgraph, LLC
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

import com.google.protobuf.InvalidProtocolBufferException;
import com.hedera.services.ledger.accounts.AliasManager;
import com.hederahashgraph.api.proto.java.AccountID;
import com.hederahashgraph.api.proto.java.ScheduleID;

import javax.inject.Inject;

public class AccessorFactory {
	final AliasManager aliasManager;

	@Inject
	public AccessorFactory(final AliasManager aliasManager) {
		this.aliasManager = aliasManager;
	}

	public TxnAccessor nonTriggeredTxn(byte[] signedTxnWrapperBytes) throws InvalidProtocolBufferException {
		final var subtype = constructSpecializedAccessor(signedTxnWrapperBytes);
		subtype.setScheduleRef(null);
		return subtype;
	}

	public TxnAccessor triggeredTxn(byte[] signedTxnWrapperBytes, final AccountID payer,
			ScheduleID parent, boolean markThrottleExempt, boolean markCongestionExempt) throws InvalidProtocolBufferException {
		final var subtype = constructSpecializedAccessor(signedTxnWrapperBytes);
		subtype.setScheduleRef(parent);
		subtype.setPayer(payer);
		if (markThrottleExempt) {
			subtype.markThrottleExempt();
		}
		if (markCongestionExempt) {
			subtype.markCongestionExempt();
		}
		return subtype;
	}

	/**
	 * parse the signedTxnWrapperBytes, figure out what specialized implementation to use
	 * construct the subtype instance
	 *
	 * @param signedTxnWrapperBytes
	 * @return
	 */
	private SignedTxnAccessor constructSpecializedAccessor(byte[] signedTxnWrapperBytes) throws InvalidProtocolBufferException {
		// custom accessors will be defined here in future PR based on the function from functionExtractor
		return SignedTxnAccessor.from(signedTxnWrapperBytes);
	}
}

