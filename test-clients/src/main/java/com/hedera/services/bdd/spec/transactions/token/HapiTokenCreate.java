package com.hedera.services.bdd.spec.transactions.token;

/*-
 * ‌
 * Hedera Services Test Clients
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

import com.google.common.base.MoreObjects;
import com.hedera.services.bdd.spec.HapiApiSpec;
import com.hedera.services.bdd.spec.transactions.HapiTxnOp;
import com.hedera.services.bdd.spec.transactions.TxnUtils;
import com.hedera.services.bdd.suites.HapiApiSuite;
import com.hederahashgraph.api.proto.java.FeeComponents;
import com.hederahashgraph.api.proto.java.FeeData;
import com.hederahashgraph.api.proto.java.HederaFunctionality;
import com.hederahashgraph.api.proto.java.Key;
import com.hederahashgraph.api.proto.java.TokenCreation;
import com.hederahashgraph.api.proto.java.Transaction;
import com.hederahashgraph.api.proto.java.TransactionBody;
import com.hederahashgraph.api.proto.java.TransactionResponse;
import com.hederahashgraph.fee.SigValueObj;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.OptionalInt;
import java.util.OptionalLong;
import java.util.function.Consumer;
import java.util.function.Function;

import static com.hedera.services.bdd.spec.transactions.TxnUtils.netOf;
import static com.hederahashgraph.api.proto.java.ResponseCodeEnum.SUCCESS;

public class HapiTokenCreate extends HapiTxnOp<HapiTokenCreate> {
	static final Logger log = LogManager.getLogger(HapiTokenCreate.class);

	private String token;

	private OptionalInt divisibility = OptionalInt.empty();
	private OptionalLong expiry = OptionalLong.empty();
	private OptionalLong initialFloat = OptionalLong.empty();
	private OptionalLong autoRenewPeriod = OptionalLong.empty();
	private Optional<String> freezeKey = Optional.empty();
	private Optional<String> kycKey = Optional.empty();
	private Optional<String> wipeKey = Optional.empty();
	private Optional<String> supplyKey = Optional.empty();
	private Optional<String> symbol = Optional.empty();
	private Optional<String> name = Optional.empty();
	private Optional<String> treasury = Optional.empty();
	private Optional<String> adminKey = Optional.empty();
	private Optional<Boolean> freezeDefault = Optional.empty();
	private Optional<Boolean> kycDefault = Optional.empty();
	private Optional<String> autoRenewAccount = Optional.empty();
	private Optional<Function<HapiApiSpec, String>> symbolFn = Optional.empty();
	private Optional<Function<HapiApiSpec, String>> nameFn = Optional.empty();

	@Override
	public HederaFunctionality type() {
		return HederaFunctionality.TokenCreate;
	}

	public HapiTokenCreate(String token) {
		this.token = token;
	}

	public HapiTokenCreate initialFloat(long initialFloat) {
		this.initialFloat = OptionalLong.of(initialFloat);
		return this;
	}

	public HapiTokenCreate divisibility(int divisibility) {
		this.divisibility = OptionalInt.of(divisibility);
		return this;
	}

	public HapiTokenCreate kycDefault(boolean knownByDefault) {
		kycDefault = Optional.of(knownByDefault);
		return this;
	}

	public HapiTokenCreate freezeDefault(boolean frozenByDefault) {
		freezeDefault = Optional.of(frozenByDefault);
		return this;
	}

	public HapiTokenCreate freezeKey(String name) {
		freezeKey = Optional.of(name);
		return this;
	}

	public HapiTokenCreate expiry(long at) {
		expiry = OptionalLong.of(at);
		return this;
	}

	public HapiTokenCreate kycKey(String name) {
		kycKey = Optional.of(name);
		return this;
	}

	public HapiTokenCreate wipeKey(String name) {
		wipeKey = Optional.of(name);
		return this;
	}

	public HapiTokenCreate supplyKey(String name) {
		supplyKey = Optional.of(name);
		return this;
	}

	public HapiTokenCreate symbol(String symbol) {
		this.symbol = Optional.of(symbol);
		return this;
	}

	public HapiTokenCreate symbol(Function<HapiApiSpec, String> symbolFn) {
		this.symbolFn = Optional.of(symbolFn);
		return this;
	}

	public HapiTokenCreate name(String name) {
		this.name = Optional.of(name);
		return this;
	}

	public HapiTokenCreate name(Function<HapiApiSpec, String> nameFn) {
		this.nameFn = Optional.of(nameFn);
		return this;
	}

	public HapiTokenCreate adminKey(String adminKeyName) {
		this.adminKey = Optional.of(adminKeyName);
		return this;
	}

	public HapiTokenCreate treasury(String treasury) {
		this.treasury = Optional.of(treasury);
		return this;
	}

	public HapiTokenCreate autoRenewAccount(String account) {
		this.autoRenewAccount = Optional.of(account);
		return this;
	}

	public HapiTokenCreate autoRenewPeriod(long secs) {
		this.autoRenewPeriod = OptionalLong.of(secs);
		return this;
	}

	@Override
	protected HapiTokenCreate self() {
		return this;
	}

	@Override
	protected long feeFor(HapiApiSpec spec, Transaction txn, int numPayerKeys) throws Throwable {
		return spec.fees().forActivityBasedOp(
				HederaFunctionality.TokenCreate, this::mockTokenCreateUsage, txn, numPayerKeys);
	}

	private FeeData mockTokenCreateUsage(TransactionBody ignoredTxn, SigValueObj ignoredSigUsage) {
		return TxnUtils.defaultPartitioning(
				FeeComponents.newBuilder()
						.setMin(1)
						.setMax(1_000_000)
						.setConstant(3)
						.setBpt(3)
						.setVpt(3)
						.setRbh(3)
						.setSbh(3)
						.setGas(3)
						.setTv(3)
						.setBpr(3)
						.setSbpr(3)
						.build(), 3);
	}

	@Override
	protected Consumer<TransactionBody.Builder> opBodyDef(HapiApiSpec spec) throws Throwable {
		if (symbolFn.isPresent()) {
			symbol = Optional.of(symbolFn.get().apply(spec));
		}
		if (nameFn.isPresent()) {
			name = Optional.of(nameFn.get().apply(spec));
		}
		TokenCreation opBody = spec
				.txns()
				.<TokenCreation, TokenCreation.Builder>body(
						TokenCreation.class, b -> {
							symbol.ifPresent(b::setSymbol);
							name.ifPresent(b::setName);
							initialFloat.ifPresent(b::setFloat);
							divisibility.ifPresent(b::setDivisibility);
							freezeDefault.ifPresent(b::setFreezeDefault);
							kycDefault.ifPresent(b::setKycDefault);
							adminKey.ifPresent(k -> b.setAdminKey(spec.registry().getKey(k)));
							freezeKey.ifPresent(k -> b.setFreezeKey(spec.registry().getKey(k)));
							supplyKey.ifPresent(k -> b.setSupplyKey(spec.registry().getKey(k)));
							if (autoRenewAccount.isPresent()) {
								var id = TxnUtils.asId(autoRenewAccount.get(), spec);
								b.setAutoRenewAccount(id);
								b.setAutoRenewPeriod(autoRenewPeriod
										.orElse(spec.setup().defaultAutoRenewPeriod().getSeconds()));
							}
							expiry.ifPresent(b::setExpiry);
							wipeKey.ifPresent(k -> b.setWipeKey(spec.registry().getKey(k)));
							kycKey.ifPresent(k -> b.setKycKey(spec.registry().getKey(k)));
							treasury.ifPresent(a -> b.setTreasury(spec.registry().getAccountID(a)));
						});
		return b -> b.setTokenCreation(opBody);
	}

	@Override
	protected List<Function<HapiApiSpec, Key>> defaultSigners() {
		List<Function<HapiApiSpec, Key>> signers = new ArrayList<>(List.of(
				spec -> spec.registry().getKey(effectivePayer(spec))));
		adminKey.ifPresent(k -> signers.add(spec -> spec.registry().getKey(k)));
		freezeKey.ifPresent(k -> signers.add(spec -> spec.registry().getKey(k)));
		autoRenewAccount.ifPresent(k -> signers.add(spec -> spec.registry().getKey(k)));
		return signers;
	}

	@Override
	protected Function<Transaction, TransactionResponse> callToUse(HapiApiSpec spec) {
		return spec.clients().getTokenSvcStub(targetNodeFor(spec), useTls)::createToken;
	}

	@Override
	protected void updateStateOf(HapiApiSpec spec) {
		if (actualStatus != SUCCESS) {
			return;
		}
		var registry = spec.registry();
		registry.saveSymbol(token, symbol.orElse(token));
		registry.saveName(token, name.orElse(token));
		registry.saveTokenId(token, lastReceipt.getTokenId());

		adminKey.ifPresent(k -> registry.saveAdminKey(token, registry.getKey(k)));
		kycKey.ifPresent(k -> registry.saveKycKey(token, registry.getKey(k)));
		wipeKey.ifPresent(k -> registry.saveWipeKey(token, registry.getKey(k)));
		supplyKey.ifPresent(k -> registry.saveSupplyKey(token, registry.getKey(k)));
		freezeKey.ifPresent(k -> registry.saveFreezeKey(token, registry.getKey(k)));
	}

	@Override
	protected MoreObjects.ToStringHelper toStringHelper() {
		MoreObjects.ToStringHelper helper = super.toStringHelper()
				.add("token", token);
		Optional
				.ofNullable(lastReceipt)
				.ifPresent(receipt -> {
					if (receipt.getTokenId().getTokenNum() != 0) {
						helper.add("created", receipt.getTokenId().getTokenNum());
					}
				});
		return helper;
	}
}
