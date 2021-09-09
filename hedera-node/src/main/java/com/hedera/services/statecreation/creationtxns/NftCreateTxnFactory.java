package com.hedera.services.statecreation.creationtxns;

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

import com.google.protobuf.ByteString;
import com.hedera.services.statecreation.creationtxns.utils.SimpleUtils;
import com.hederahashgraph.api.proto.java.TokenID;
import com.hederahashgraph.api.proto.java.TokenMintTransactionBody;
import com.hederahashgraph.api.proto.java.Transaction;
import com.hederahashgraph.api.proto.java.TransactionBody;

import java.security.SecureRandom;
import java.util.ArrayList;
import java.util.List;
import java.util.OptionalInt;


public class NftCreateTxnFactory extends CreateTxnFactory<NftCreateTxnFactory> {
	private final SecureRandom random = new SecureRandom();
	private static final int META_DATA_BASE_LENGTH = 50;
	private static final int META_DATA_MAX_VAR =50;
	private OptionalInt totalNftsPerToken = OptionalInt.of(1000);
	private TokenID tokenID;

	private NftCreateTxnFactory() {}

	public static NftCreateTxnFactory newSignedNftCreate() {
		return new NftCreateTxnFactory();
	}

	public NftCreateTxnFactory metaDataPer(final int totalNftsPer) {
		this.totalNftsPerToken = OptionalInt.of(totalNftsPer);
		return this;
	}

	public NftCreateTxnFactory forUniqToken(final long tokenNum) {
		tokenID = TokenID.newBuilder()
				.setTokenNum(tokenNum).setRealmNum(0L).setShardNum(0L).build();
		return this;
	}


	@Override
	protected NftCreateTxnFactory self() {
		return this;
	}

	@Override
	protected long feeFor(Transaction signedTxn, int numPayerKeys) {
		return 0;
	}

	@Override
	protected void customizeTxn(TransactionBody.Builder txn) {
		var op = TokenMintTransactionBody.newBuilder();

		final List<ByteString> allMeta = new ArrayList<>();

		for (int i = 0; i < totalNftsPerToken.getAsInt() ; i++) {
			var aMeta = SimpleUtils.randomUtf8ByteString(META_DATA_BASE_LENGTH + random.nextInt(META_DATA_MAX_VAR));
			allMeta.add(aMeta);
		}

		op.setToken(tokenID);
		op.addAllMetadata(allMeta);
		txn.setTokenMint(op.build());
	}

}
