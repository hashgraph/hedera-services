package com.hedera.services.bdd.spec.transactions.token;

/*-
 * ‌
 * Hedera Services Test Clients
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
import com.hedera.services.bdd.spec.HapiApiSpec;
import com.hedera.services.bdd.spec.transactions.HapiTxnOp;
import com.hedera.services.bdd.spec.transactions.TxnUtils;
import com.hederahashgraph.api.proto.java.FeeComponents;
import com.hederahashgraph.api.proto.java.FeeData;
import com.hederahashgraph.api.proto.java.HederaFunctionality;
import com.hederahashgraph.api.proto.java.Key;
import com.hederahashgraph.api.proto.java.NftMintTransactionBody;
import com.hederahashgraph.api.proto.java.Transaction;
import com.hederahashgraph.api.proto.java.TransactionBody;
import com.hederahashgraph.api.proto.java.TransactionResponse;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import java.util.List;
import java.util.function.Consumer;
import java.util.function.Function;

public class HapiNftMint extends HapiTxnOp<HapiNftMint> {
	static final Logger log = LogManager.getLogger(HapiNftMint.class);

	private static final FeeData MOCK_USAGE;
	static {
		var usagesBuilder = FeeData.newBuilder();
		usagesBuilder.setNetworkdata(FeeComponents.newBuilder()
				.setConstant(1).setBpt(256).setVpt(2).setRbh(2160));
		usagesBuilder.setNodedata(FeeComponents.newBuilder()
				.setConstant(1).setBpt(256).setVpt(1).setBpr(32));
		usagesBuilder.setServicedata(FeeComponents.newBuilder()
				.setConstant(1).setRbh(2160));
		MOCK_USAGE = usagesBuilder.build();
	}

	private int newSerialNos;
	private String nftType;

	@Override
	public HederaFunctionality type() {
		return HederaFunctionality.NftMint;
	}

	public HapiNftMint(String nftType, int newSerialNos) {
		this.nftType = nftType;
		this.newSerialNos = newSerialNos;
	}

	@Override
	protected HapiNftMint self() {
		return this;
	}

	@Override
	protected long feeFor(HapiApiSpec spec, Transaction txn, int numPayerKeys) throws Throwable {
		return spec.fees().forActivityBasedOp(
				HederaFunctionality.NftMint, (_txn, sigUsage) -> MOCK_USAGE, txn, numPayerKeys);
	}

	@Override
	protected Consumer<TransactionBody.Builder> opBodyDef(HapiApiSpec spec) throws Throwable {
		var nId = TxnUtils.asNftId(nftType, spec);
		NftMintTransactionBody opBody = spec
				.txns()
				.<NftMintTransactionBody, NftMintTransactionBody.Builder>body(
						NftMintTransactionBody.class, b -> {
							b.setNftType(nId);
							b.setNumSerialNos(newSerialNos);
						});
		return b -> b.setNftMint(opBody);
	}

	@Override
	protected Function<Transaction, TransactionResponse> callToUse(HapiApiSpec spec) {
		return spec.clients().getNftSvcStub(targetNodeFor(spec), useTls)::mintNfts;
	}

	@Override
	protected void updateStateOf(HapiApiSpec spec) {
	}

	@Override
	protected MoreObjects.ToStringHelper toStringHelper() {
		MoreObjects.ToStringHelper helper = super.toStringHelper()
				.add("nftType", nftType)
				.add("newSerialNos", newSerialNos);
		return helper;
	}
}
