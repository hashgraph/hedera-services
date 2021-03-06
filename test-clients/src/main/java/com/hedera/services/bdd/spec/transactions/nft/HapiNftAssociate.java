package com.hedera.services.bdd.spec.transactions.nft;

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
import com.hederahashgraph.api.proto.java.NftAssociateTransactionBody;
import com.hederahashgraph.api.proto.java.Transaction;
import com.hederahashgraph.api.proto.java.TransactionBody;
import com.hederahashgraph.api.proto.java.TransactionResponse;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Consumer;
import java.util.function.Function;

import static com.hedera.services.bdd.spec.queries.QueryVerbs.getContractInfo;
import static com.hederahashgraph.api.proto.java.ResponseCodeEnum.SUCCESS;
import static java.util.stream.Collectors.toList;

public class HapiNftAssociate extends HapiTxnOp<HapiNftAssociate> {
	static final Logger log = LogManager.getLogger(HapiNftAssociate.class);

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

	private String account;
	private List<String> nftTypes = new ArrayList<>();

	@Override
	public HederaFunctionality type() {
		return HederaFunctionality.NftAssociate;
	}

	public HapiNftAssociate(String account, String... nftTypes) {
		this.account = account;
		this.nftTypes.addAll(List.of(nftTypes));
	}
	public HapiNftAssociate(String account, List<String> nftTypes) {
		this.account = account;
		this.nftTypes.addAll(nftTypes);
	}

	@Override
	protected HapiNftAssociate self() {
		return this;
	}

	@Override
	protected long feeFor(HapiApiSpec spec, Transaction txn, int numPayerKeys) throws Throwable {
		return spec.fees().forActivityBasedOp(
				HederaFunctionality.NftAssociate, (_txn, sigUsage) -> MOCK_USAGE, txn, numPayerKeys);
	}

	@Override
	protected Consumer<TransactionBody.Builder> opBodyDef(HapiApiSpec spec) throws Throwable {
		var aId = TxnUtils.asId(account, spec);
		NftAssociateTransactionBody opBody = spec
				.txns()
				.<NftAssociateTransactionBody, NftAssociateTransactionBody.Builder>body(
						NftAssociateTransactionBody.class, b -> {
							b.setAccount(aId);
							b.addAllNftTypes(nftTypes.stream()
									.map(lit -> TxnUtils.asNftId(lit, spec))
									.collect(toList()));
						});
		return b -> b.setNftAssociate(opBody);
	}

	@Override
	protected List<Function<HapiApiSpec, Key>> defaultSigners() {
		return List.of(
				spec -> spec.registry().getKey(effectivePayer(spec)),
				spec -> spec.registry().getKey(account));
	}

	@Override
	protected Function<Transaction, TransactionResponse> callToUse(HapiApiSpec spec) {
		return spec.clients().getNftSvcStub(targetNodeFor(spec), useTls)::associateNfts;
	}

	@Override
	protected void updateStateOf(HapiApiSpec spec) {
		if (actualStatus != SUCCESS) {
			return;
		}
		var registry = spec.registry();
		/* TODO - save NFT relationships */
	}

	@Override
	protected MoreObjects.ToStringHelper toStringHelper() {
		MoreObjects.ToStringHelper helper = super.toStringHelper()
				.add("account", account)
				.add("nftTypes", nftTypes);
		return helper;
	}
}
