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
import com.hedera.services.utils.MiscUtils;
import com.hederahashgraph.api.proto.java.AccountID;
import com.hederahashgraph.api.proto.java.ScheduleID;
import com.hederahashgraph.api.proto.java.Transaction;

import javax.inject.Inject;

import static com.hedera.services.legacy.proto.utils.CommonUtils.extractTransactionBody;

public class AccessorFactory {
	final AliasManager aliasManager;

	@Inject
	public AccessorFactory(final AliasManager aliasManager) {
		this.aliasManager = aliasManager;
	}

	public SignedTxnAccessor nonTriggeredTxn(byte[] signedTxnWrapperBytes) throws InvalidProtocolBufferException {
		final var subtype = constructSpecializedAccessor(signedTxnWrapperBytes);
		subtype.setTriggered(false);
		subtype.setScheduleRef(null);
		return subtype;
	}

	public SignedTxnAccessor triggeredTxn(byte[] signedTxnWrapperBytes, final AccountID payer,
			ScheduleID parent) throws InvalidProtocolBufferException {
		final var subtype = constructSpecializedAccessor(signedTxnWrapperBytes);
		subtype.setTriggered(true);
		subtype.setScheduleRef(parent);
		subtype.setPayer(payer);
		return subtype;
	}

	/**
	 * parse the signedTxnWrapperBytes, figure out what specialized implementation to use
	 * construct the subtype instance
	 *
	 * @param signedTxnWrapperBytes
	 * @return
	 * @throws InvalidProtocolBufferException
	 */
	private SignedTxnAccessor constructSpecializedAccessor(byte[] signedTxnWrapperBytes)
			throws InvalidProtocolBufferException {
		final var body = extractTransactionBody(Transaction.parseFrom(signedTxnWrapperBytes));
		final var function = MiscUtils.functionExtractor.apply(body);
		return switch (function) {
			case TokenAccountWipe -> new TokenWipeAccessor(signedTxnWrapperBytes, aliasManager);
			case CryptoCreate -> new CryptoCreateAccessor(signedTxnWrapperBytes, aliasManager);
			case CryptoUpdate -> new CryptoUpdateAccessor(signedTxnWrapperBytes, aliasManager);
			case CryptoDelete -> new CryptoDeleteAccessor(signedTxnWrapperBytes, aliasManager);
			case CryptoApproveAllowance, CryptoAdjustAllowance -> new CryptoAllowanceAccessor(signedTxnWrapperBytes, aliasManager);
			default -> SignedTxnAccessor.from(signedTxnWrapperBytes);
		};
	}
}
