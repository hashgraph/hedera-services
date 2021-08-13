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

import com.hedera.services.state.submerkle.FcCustomFee;
import com.hedera.services.statecreation.creationtxns.utils.KeyFactory;
import com.hederahashgraph.api.proto.java.AccountID;
import com.hederahashgraph.api.proto.java.Duration;
import com.hederahashgraph.api.proto.java.Key;
import com.hederahashgraph.api.proto.java.Timestamp;
import com.hederahashgraph.api.proto.java.TokenCreateTransactionBody;
import com.hederahashgraph.api.proto.java.Transaction;
import com.hederahashgraph.api.proto.java.TransactionBody;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

import static com.hedera.services.statecreation.creationtxns.utils.TempUtils.asAccount;

public class TokenCreateTxnFactory extends CreateTxnFactory<TokenCreateTxnFactory> {
	private boolean frozen = false;
	private boolean omitAdmin = false;
	private boolean omitTreasury = false;
	private AccountID treasury = null;
	private Optional<AccountID> autoRenew = Optional.empty();
	private List<FcCustomFee> customFees = new ArrayList<>();

	public static final String DEFAULT_TREASURE_ID = "0.0.2";
	private static AccountID DEFAULT_TREASURY = asAccount(DEFAULT_TREASURE_ID);
	private static Key adminKey = KeyFactory.getKey();
	private static Key freezeKey = KeyFactory.getKey();
	private static Key kycKey = KeyFactory.getKey();
	private static Key wipeKey = KeyFactory.getKey();
	private static Key feeScheduleKey = KeyFactory.getKey();

	private String name;
	private String symbol;

	private TokenCreateTxnFactory() {}

	public static TokenCreateTxnFactory newSignedTokenCreate() {
		return new TokenCreateTxnFactory();
	}

	public TokenCreateTxnFactory frozen() {
		frozen = true;
		return this;
	}

	public TokenCreateTxnFactory symbol(String symbol) {
		this.symbol = symbol;
		return this;
	}

	public TokenCreateTxnFactory name(String name) {
		this.name = name;
		return this;
	}

	public TokenCreateTxnFactory treasury(AccountID treasury) {
		this.treasury = treasury;
		return this;
	}

	public TokenCreateTxnFactory autoRenew(AccountID account) {
		autoRenew = Optional.of(account);
		return this;
	}

	public TokenCreateTxnFactory missingAdmin() {
		omitAdmin = true;
		return this;
	}

	public TokenCreateTxnFactory plusCustomFee(FcCustomFee customFee) {
		customFees.add(customFee);
		return this;
	}

	@Override
	protected TokenCreateTxnFactory self() {
		return this;
	}

	@Override
	protected long feeFor(Transaction signedTxn, int numPayerKeys) {
		return 0;
	}

	@Override
	protected void customizeTxn(TransactionBody.Builder txn) {
		var op = TokenCreateTransactionBody.newBuilder();
		op.setName(name);
		op.setSymbol(symbol);
		Timestamp.Builder expiry = Timestamp.newBuilder()
				.setSeconds(Instant.now().getEpochSecond() + 1000000)
				.setNanos(0);
		Duration.Builder duration = Duration.newBuilder()
				.setSeconds(30000);
		op.setExpiry(expiry);
		op.setAutoRenewPeriod(duration);
		if (!omitAdmin) {
			op.setAdminKey(adminKey);
		}
		if (!omitTreasury) {
			if (treasury != null) {
				op.setTreasury(treasury);
			} else {
				op.setTreasury(DEFAULT_TREASURY);
			}
		}
		if (frozen) {
			op.setFreezeKey(freezeKey);
		}
		for (var fee : customFees) {
			op.addCustomFees(fee.asGrpc());
		}
		autoRenew.ifPresent(op::setAutoRenewAccount);
		txn.setTokenCreation(op);
	}
}
