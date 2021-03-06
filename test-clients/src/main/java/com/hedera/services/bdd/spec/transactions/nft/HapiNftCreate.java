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
import com.hedera.services.bdd.spec.transactions.TxnUtils;
import com.hederahashgraph.api.proto.java.FeeComponents;
import com.hederahashgraph.api.proto.java.FeeData;
import com.hederahashgraph.api.proto.java.HederaFunctionality;
import com.hederahashgraph.api.proto.java.NftCreateTransactionBody;
import com.hederahashgraph.api.proto.java.Transaction;
import com.hederahashgraph.api.proto.java.TransactionBody;
import com.hederahashgraph.api.proto.java.TransactionResponse;
import com.hedera.services.bdd.spec.HapiApiSpec;
import com.hedera.services.bdd.spec.transactions.HapiTxnOp;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import java.util.Optional;
import java.util.OptionalInt;
import java.util.function.Consumer;
import java.util.function.Function;

import static com.hedera.services.bdd.spec.transactions.TxnFactory.bannerWith;
import static com.hederahashgraph.api.proto.java.ResponseCodeEnum.SUCCESS;
import static com.hedera.services.bdd.spec.transactions.TxnUtils.netOf;

public class HapiNftCreate extends HapiTxnOp<HapiNftCreate> {
	static final Logger log = LogManager.getLogger(HapiNftCreate.class);

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

	private boolean advertiseCreation = false;
	private String nftType;
	private OptionalInt serialNoCount = OptionalInt.empty();
	private Optional<String> treasury = Optional.empty();

	@Override
	public HederaFunctionality type() {
		return HederaFunctionality.NftCreate;
	}

	public HapiNftCreate advertisingCreation() {
		advertiseCreation = true;
		return this;
	}

	public HapiNftCreate(String nftType) {
		this.nftType = nftType;
	}

	public HapiNftCreate initialSerialNos(int count) {
		serialNoCount = OptionalInt.of(count);
		return this;
	}

	public HapiNftCreate treasury(String idLit) {
		treasury = Optional.of(idLit);
		return this;
	}

	@Override
	protected HapiNftCreate self() {
		return this;
	}

	@Override
	protected long feeFor(HapiApiSpec spec, Transaction txn, int numPayerKeys) throws Throwable {
		return spec.fees().forActivityBasedOp(
				HederaFunctionality.NftCreate, (_txn, sigUsage) -> MOCK_USAGE, txn, numPayerKeys);
	}

	@Override
	protected Consumer<TransactionBody.Builder> opBodyDef(HapiApiSpec spec) throws Throwable {
		var treasuryAccount = TxnUtils.asId(treasury.orElse(spec.setup().defaultPayerName()), spec);
		NftCreateTransactionBody opBody = spec
				.txns()
				.<NftCreateTransactionBody, NftCreateTransactionBody.Builder>body(
						NftCreateTransactionBody.class, b -> {
							b.setTreasury(treasuryAccount);
							serialNoCount.ifPresent(n -> b.setSerialNoCount(n));
						});
		return b -> b.setNftCreate(opBody);
	}

	@Override
	protected Function<Transaction, TransactionResponse> callToUse(HapiApiSpec spec) {
		return spec.clients().getNftSvcStub(targetNodeFor(spec), useTls)::createNft;
	}

	@Override
	protected void updateStateOf(HapiApiSpec spec) {
		if (actualStatus != SUCCESS) {
			return;
		}
		var registry = spec.registry();
		registry.saveNftId(nftType, lastReceipt.getNftID());
		registry.saveTreasury(nftType, treasury.orElse(spec.setup().defaultPayerName()));

		if (advertiseCreation) {
			String banner = "\n\n" + bannerWith(
					String.format(
							"Created nft '%s' with id '0.0.%d'.",
							nftType,
							lastReceipt.getNftID().getNftNum()));
			log.info(banner);
		}
	}

	@Override
	protected MoreObjects.ToStringHelper toStringHelper() {
		MoreObjects.ToStringHelper helper = super.toStringHelper()
				.add("nftType", nftType);
		Optional
				.ofNullable(lastReceipt)
				.ifPresent(receipt -> {
					long nftNum;
					if ((nftNum = receipt.getNftID().getNftNum()) != 0) {
						helper.add("created", nftNum);
					}
				});
		return helper;
	}
}
