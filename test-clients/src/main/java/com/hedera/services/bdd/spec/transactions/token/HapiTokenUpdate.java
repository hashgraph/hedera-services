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
import com.hederahashgraph.api.proto.java.FeeComponents;
import com.hederahashgraph.api.proto.java.FeeData;
import com.hederahashgraph.api.proto.java.HederaFunctionality;
import com.hederahashgraph.api.proto.java.Key;
import com.hederahashgraph.api.proto.java.TokenCreation;
import com.hederahashgraph.api.proto.java.TokenManagement;
import com.hederahashgraph.api.proto.java.Transaction;
import com.hederahashgraph.api.proto.java.TransactionBody;
import com.hederahashgraph.api.proto.java.TransactionResponse;
import com.hederahashgraph.fee.SigValueObj;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.apache.logging.log4j.util.ProcessIdUtil;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.OptionalLong;
import java.util.function.Consumer;
import java.util.function.Function;

import static com.hedera.services.bdd.spec.transactions.TxnUtils.netOf;
import static com.hederahashgraph.api.proto.java.ResponseCodeEnum.SUCCESS;

public class HapiTokenUpdate extends HapiTxnOp<HapiTokenUpdate> {
	static final Logger log = LogManager.getLogger(HapiTokenUpdate.class);

	private String token;

	private OptionalLong expiry = OptionalLong.empty();
	private OptionalLong autoRenewPeriod = OptionalLong.empty();
	private Optional<String> newAdminKey = Optional.empty();
	private Optional<String> newKycKey = Optional.empty();
	private Optional<String> newWipeKey = Optional.empty();
	private Optional<String> newSupplyKey = Optional.empty();
	private Optional<String> newFreezeKey = Optional.empty();
	private Optional<String> newSymbol = Optional.empty();
	private Optional<String> newTreasury = Optional.empty();
	private Optional<String> autoRenewAccount = Optional.empty();
	private Optional<Function<HapiApiSpec, String>> newSymbolFn = Optional.empty();

	@Override
	public HederaFunctionality type() {
		return HederaFunctionality.TokenUpdate;
	}

	public HapiTokenUpdate(String token) {
		this.token = token;
	}


	public HapiTokenUpdate freezeKey(String name) {
		newFreezeKey = Optional.of(name);
		return this;
	}

	public HapiTokenUpdate kycKey(String name) {
		newKycKey = Optional.of(name);
		return this;
	}

	public HapiTokenUpdate wipeKey(String name) {
		newWipeKey = Optional.of(name);
		return this;
	}

	public HapiTokenUpdate supplyKey(String name) {
		newSupplyKey = Optional.of(name);
		return this;
	}

	public HapiTokenUpdate symbol(String symbol) {
		this.newSymbol = Optional.of(symbol);
		return this;
	}

	public HapiTokenUpdate symbol(Function<HapiApiSpec, String> symbolFn) {
		this.newSymbolFn = Optional.of(symbolFn);
		return this;
	}

	public HapiTokenUpdate adminKey(String name) {
		newAdminKey = Optional.of(name);
		return this;
	}

	public HapiTokenUpdate treasury(String treasury) {
		this.newTreasury = Optional.of(treasury);
		return this;
	}

	public HapiTokenUpdate autoRenewAccount(String account) {
		this.autoRenewAccount = Optional.of(account);
		return this;
	}

	public HapiTokenUpdate autoRenewPeriod(long secs) {
		this.autoRenewPeriod = OptionalLong.of(secs);
		return this;
	}

	public HapiTokenUpdate expiry(long at) {
		this.expiry = OptionalLong.of(at);
		return this;
	}

	@Override
	protected HapiTokenUpdate self() {
		return this;
	}

	@Override
	protected long feeFor(HapiApiSpec spec, Transaction txn, int numPayerKeys) throws Throwable {
		return spec.fees().forActivityBasedOp(
				HederaFunctionality.TokenUpdate, this::mockTokenUpdateUsage, txn, numPayerKeys);
	}

	private FeeData mockTokenUpdateUsage(TransactionBody ignoredTxn, SigValueObj ignoredSigUsage) {
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
		var id = TxnUtils.asTokenId(token, spec);
		if (newSymbolFn.isPresent()) {
			newSymbol = Optional.of(newSymbolFn.get().apply(spec));
		}
		TokenManagement opBody = spec
				.txns()
				.<TokenManagement, TokenManagement.Builder>body(
						TokenManagement.class, b -> {
							b.setToken(TxnUtils.asRef(id));
							newSymbol.ifPresent(b::setSymbol);
							newAdminKey.ifPresent(a -> b.setAdminKey(spec.registry().getKey(a)));
							newTreasury.ifPresent(a -> b.setTreasury(spec.registry().getAccountID(a)));
							newSupplyKey.ifPresent(k -> b.setSupplyKey(spec.registry().getKey(k)));
							newWipeKey.ifPresent(k -> b.setWipeKey(spec.registry().getKey(k)));
							newKycKey.ifPresent(k -> b.setKycKey(spec.registry().getKey(k)));
							newFreezeKey.ifPresent(k -> b.setFreezeKey(spec.registry().getKey(k)));
							if (autoRenewAccount.isPresent()) {
								var autoRenewId = TxnUtils.asId(autoRenewAccount.get(), spec);
								b.setAutoRenewAccount(autoRenewId);
							}
							expiry.ifPresent(b::setExpiry);
							autoRenewPeriod.ifPresent(b::setAutoRenewPeriod);
						});
		return b -> b.setTokenUpdate(opBody);
	}

	@Override
	protected List<Function<HapiApiSpec, Key>> defaultSigners() {
		List<Function<HapiApiSpec, Key>> signers = new ArrayList<>();
		signers.add(spec -> spec.registry().getKey(effectivePayer(spec)));
		signers.add(spec -> {
			try {
				return spec.registry().getAdminKey(token);
			} catch (Exception ignore) {
				return Key.getDefaultInstance();
			}
		});
		newAdminKey.ifPresent(n -> signers.add(spec -> spec.registry().getKey(n)));
		autoRenewAccount.ifPresent(a -> signers.add(spec -> spec.registry().getKey(a)));
		return signers;
	}

	@Override
	protected Function<Transaction, TransactionResponse> callToUse(HapiApiSpec spec) {
		return spec.clients().getTokenSvcStub(targetNodeFor(spec), useTls)::updateToken;
	}

	@Override
	protected void updateStateOf(HapiApiSpec spec) {
		if (actualStatus != SUCCESS) {
			return;
		}
		var registry = spec.registry();
		newAdminKey.ifPresent(n -> registry.saveKey(token, registry.getKey(n)));
		newSymbol.ifPresent(s -> registry.saveSymbol(token, s));
		newFreezeKey.ifPresent(n -> registry.saveFreezeKey(token, registry.getKey(n)));
		newSupplyKey.ifPresent(n -> registry.saveSupplyKey(token, registry.getKey(n)));
		newWipeKey.ifPresent(n -> registry.saveWipeKey(token, registry.getKey(n)));
		newKycKey.ifPresent(n -> registry.saveKycKey(token, registry.getKey(n)));
	}

	@Override
	protected MoreObjects.ToStringHelper toStringHelper() {
		MoreObjects.ToStringHelper helper = super.toStringHelper()
				.add("token", token);
		return helper;
	}
}
