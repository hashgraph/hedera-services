package com.hedera.services.utils.accessors;

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

import com.google.protobuf.InvalidProtocolBufferException;
import com.hedera.services.ledger.accounts.AliasManager;
import com.hedera.services.usage.crypto.CryptoAdjustAllowanceMeta;
import com.hedera.services.usage.crypto.CryptoApproveAllowanceMeta;
import com.hederahashgraph.api.proto.java.AccountID;
import com.hederahashgraph.api.proto.java.CryptoAllowance;
import com.hederahashgraph.api.proto.java.HederaFunctionality;
import com.hederahashgraph.api.proto.java.NftAllowance;
import com.hederahashgraph.api.proto.java.TokenAllowance;

import java.util.ArrayList;
import java.util.List;

import static com.hedera.services.utils.EntityIdUtils.unaliased;

public class CryptoAllowanceAccessor extends SignedTxnAccessor{
	private AliasManager aliasManager;

	public CryptoAllowanceAccessor(
			final byte[] txn,
			final AliasManager aliasManager) throws InvalidProtocolBufferException {
		super(txn);
		this.aliasManager = aliasManager;
		if (getFunction() == HederaFunctionality.CryptoApproveAllowance) {
			setCryptoApproveUsageMeta();
		} else {
			setCryptoAdjustUsageMeta();
		}
	}

	@Override
	public AccountID getPayer() {
		return unaliased(super.getPayer(), aliasManager).toGrpcAccountId();
	}

	public List<CryptoAllowance> getCryptoAllowances() {
		List<CryptoAllowance> allowances = new ArrayList<>();
		for (var allowance : getCryptoAllowancesList()) {
			allowances.add(
					allowance.toBuilder()
							.setSpender(unaliased(allowance.getSpender(), aliasManager).toGrpcAccountId())
							.build()
			);
		}
		return allowances;
	}

	public List<TokenAllowance> getTokenAllowances() {
		List<TokenAllowance> allowances = new ArrayList<>();
		for (var allowance : getTokenAllowancesList()) {
			allowances.add(
					allowance.toBuilder()
							.setSpender(unaliased(allowance.getSpender(), aliasManager).toGrpcAccountId())
							.build()
			);
		}
		return allowances;
	}

	public List<NftAllowance> getNftAllowances() {
		List<NftAllowance> allowances = new ArrayList<>();
		for (var allowance : getNftAllowancesList()) {
			allowances.add(
					allowance.toBuilder()
							.setSpender(unaliased(allowance.getSpender(), aliasManager).toGrpcAccountId())
							.build()
			);
		}
		return allowances;
	}

	private List<CryptoAllowance> getCryptoAllowancesList() {
		if (getFunction() == HederaFunctionality.CryptoApproveAllowance) {
			return getTxn().getCryptoApproveAllowance().getCryptoAllowancesList();
		} else {
			return getTxn().getCryptoAdjustAllowance().getCryptoAllowancesList();
		}
	}

	private List<TokenAllowance> getTokenAllowancesList() {
		if (getFunction() == HederaFunctionality.CryptoApproveAllowance) {
			return getTxn().getCryptoApproveAllowance().getTokenAllowancesList();
		} else {
			return getTxn().getCryptoAdjustAllowance().getTokenAllowancesList();
		}
	}

	private List<NftAllowance> getNftAllowancesList() {
		if (getFunction() == HederaFunctionality.CryptoApproveAllowance) {
			return getTxn().getCryptoApproveAllowance().getNftAllowancesList();
		} else {
			return getTxn().getCryptoAdjustAllowance().getNftAllowancesList();
		}
	}

	private void setCryptoApproveUsageMeta() {
		final var cryptoApproveMeta = new CryptoApproveAllowanceMeta(getTxn().getCryptoApproveAllowance(),
				getTxn().getTransactionID().getTransactionValidStart().getSeconds());
		SPAN_MAP_ACCESSOR.setCryptoApproveMeta(this, cryptoApproveMeta);
	}

	private void setCryptoAdjustUsageMeta() {
		final var cryptoAdjustMeta = new CryptoAdjustAllowanceMeta(getTxn().getCryptoAdjustAllowance(),
				getTxn().getTransactionID().getTransactionValidStart().getSeconds());
		SPAN_MAP_ACCESSOR.setCryptoAdjustMeta(this, cryptoAdjustMeta);
	}
}
