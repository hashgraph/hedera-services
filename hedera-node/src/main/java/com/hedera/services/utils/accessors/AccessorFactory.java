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
import com.hederahashgraph.api.proto.java.Transaction;
import com.swirlds.common.SwirldTransaction;

import javax.inject.Inject;

import static com.hedera.services.legacy.proto.utils.CommonUtils.extractTransactionBody;
import static com.hedera.services.utils.accessors.SignedTxnAccessor.functionExtractor;

public class AccessorFactory {
	final AliasManager aliasManager;

	@Inject
	public AccessorFactory(final AliasManager aliasManager) {
		this.aliasManager = aliasManager;
	}

	public PlatformTxnAccessor constructFrom(SwirldTransaction transaction) throws InvalidProtocolBufferException {
		return constructFrom(Transaction.parseFrom(transaction.getContents()), aliasManager);
	}

	public PlatformTxnAccessor constructFrom(Transaction validSignedTxn) throws InvalidProtocolBufferException {
		final var platformTxn = new SwirldTransaction(validSignedTxn.toByteArray());
		return constructFrom(platformTxn);
	}

	public static PlatformTxnAccessor constructFrom(Transaction validSignedTxn, AliasManager aliasManager) throws InvalidProtocolBufferException {
		final var platformTxn = new SwirldTransaction(validSignedTxn.toByteArray());
		final var body = extractTransactionBody(validSignedTxn);
		final var function = functionExtractor.apply(body);
		switch (function) {
			case TokenAccountWipe -> {
				return new TokenWipeAccessor(platformTxn, aliasManager);
			}
			case CryptoCreate -> {
				return new CryptoCreateAccessor(platformTxn, aliasManager);
			}
			case CryptoUpdate -> {
				return new CryptoUpdateAccessor(platformTxn, aliasManager);
			}
			default -> {
				return new PlatformTxnAccessor(platformTxn, aliasManager);
			}
		}
	}
}
