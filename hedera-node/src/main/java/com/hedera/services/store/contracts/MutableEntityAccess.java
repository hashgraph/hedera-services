package com.hedera.services.store.contracts;

/*
 * -
 * ‌
 * Hedera Services Node
 * ​
 * Copyright (C) 2018 - 2021 Hedera Hashgraph, LLC
 * ​
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *       http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 * ‍
 *
 */

import com.hedera.services.context.TransactionContext;
import com.hedera.services.ledger.HederaLedger;
import com.hedera.services.ledger.TransactionalLedger;
import com.hedera.services.ledger.accounts.HederaAccountCustomizer;
import com.hedera.services.ledger.properties.AccountProperty;
import com.hedera.services.ledger.properties.TokenProperty;
import com.hedera.services.legacy.core.jproto.JKey;
import com.hedera.services.state.merkle.MerkleAccount;
import com.hedera.services.state.merkle.MerkleToken;
import com.hedera.services.state.submerkle.EntityId;
import com.hedera.services.state.virtual.VirtualBlobKey;
import com.hedera.services.state.virtual.VirtualBlobValue;
import com.hederahashgraph.api.proto.java.AccountID;
import com.hederahashgraph.api.proto.java.TokenID;
import com.swirlds.virtualmap.VirtualMap;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.apache.tuweni.bytes.Bytes;
import org.apache.tuweni.units.bigints.UInt256;

import javax.inject.Inject;
import javax.inject.Singleton;
import java.util.function.Supplier;

import static com.hedera.services.store.contracts.StaticEntityAccess.explicitCodeFetch;
import static com.hederahashgraph.api.proto.java.HederaFunctionality.ContractCall;
import static com.hederahashgraph.api.proto.java.HederaFunctionality.ContractCreate;

@Singleton
public class MutableEntityAccess implements EntityAccess {
	private static final Logger log = LogManager.getLogger(MutableEntityAccess.class);

	private final HederaLedger ledger;
	private final WorldLedgers worldLedgers;
	private final TransactionContext txnCtx;
	private final SizeLimitedStorage sizeLimitedStorage;
	private final Supplier<VirtualMap<VirtualBlobKey, VirtualBlobValue>> bytecode;
	private final TransactionalLedger<TokenID, TokenProperty, MerkleToken> tokensLedger;

	@Inject
	public MutableEntityAccess(
			final HederaLedger ledger,
			final TransactionContext txnCtx,
			final SizeLimitedStorage sizeLimitedStorage,
			final TransactionalLedger<TokenID, TokenProperty, MerkleToken> tokensLedger,
			final Supplier<VirtualMap<VirtualBlobKey, VirtualBlobValue>> bytecode
	) {
		this.txnCtx = txnCtx;
		this.ledger = ledger;
		this.bytecode = bytecode;
		this.tokensLedger = tokensLedger;
		this.sizeLimitedStorage = sizeLimitedStorage;

		this.worldLedgers = new WorldLedgers(
				ledger.getTokenRelsLedger(),
				ledger.getAccountsLedger(),
				ledger.getNftsLedger(),
				tokensLedger);

		ledger.setMutableEntityAccess(this);
	}

	@Override
	public WorldLedgers worldLedgers() {
		return worldLedgers;
	}

	@Override
	public void begin() {
		if (isActiveContractOp()) {
			sizeLimitedStorage.beginSession();
			if (tokensLedger.isInTransaction()) {
				tokensLedger.rollback();
				log.warn("Tokens ledger had to be rolled back before beginning contract op; " +
						"full transaction is {}", txnCtx.accessor().getSignedTxnWrapper());
			}
			tokensLedger.begin();
		}
	}

	@Override
	public void commit() {
		if (isActiveContractOp()) {
			tokensLedger.commit();
		}
	}

	@Override
	public void rollback() {
		if (isActiveContractOp()) {
			tokensLedger.rollback();
		}
	}

	@Override
	public String currentManagedChangeSet() {
		return tokensLedger.changeSetSoFar();
	}

	@Override
	public void spawn(final AccountID id, final long balance, final HederaAccountCustomizer customizer) {
		ledger.spawn(id, balance, customizer);
	}

	@Override
	public void customize(final AccountID id, final HederaAccountCustomizer customizer) {
		ledger.customizePotentiallyDeleted(id, customizer);
	}

	@Override
	public void adjustBalance(final AccountID id, final long adjustment) {
		ledger.adjustBalance(id, adjustment);
	}

	@Override
	public long getAutoRenew(final AccountID id) {
		return ledger.autoRenewPeriod(id);
	}

	@Override
	public long getBalance(final AccountID id) {
		return ledger.getBalance(id);
	}

	@Override
	public long getExpiry(final AccountID id) {
		return ledger.expiry(id);
	}

	@Override
	public JKey getKey(final AccountID id) {
		return ledger.key(id);
	}

	@Override
	public String getMemo(final AccountID id) {
		return ledger.memo(id);
	}

	@Override
	public EntityId getProxy(final AccountID id) {
		return ledger.proxy(id);
	}

	@Override
	public boolean isDeleted(final AccountID id) {
		return ledger.isDeleted(id);
	}

	@Override
	public boolean isDetached(final AccountID id) {
		return ledger.isDetached(id);
	}

	@Override
	public boolean isExtant(final AccountID id) {
		return ledger.exists(id);
	}

	@Override
	public void putStorage(final AccountID id, final UInt256 key, final UInt256 value) {
		sizeLimitedStorage.putStorage(id, key, value);
	}

	@Override
	public UInt256 getStorage(final AccountID id, final UInt256 key) {
		return sizeLimitedStorage.getStorage(id, key);
	}

	@Override
	public void flushStorage() {
		sizeLimitedStorage.validateAndCommit();
	}

	@Override
	public void storeCode(final AccountID id, final Bytes code) {
		final var key = new VirtualBlobKey(VirtualBlobKey.Type.CONTRACT_BYTECODE, (int) id.getAccountNum());
		final var value = new VirtualBlobValue(code.toArray());
		bytecode.get().put(key, value);
	}

	@Override
	public Bytes fetchCodeIfPresent(final AccountID id) {
		return explicitCodeFetch(bytecode.get(), id);
	}

	@Override
	public void recordNewKvUsageTo(final TransactionalLedger<AccountID, AccountProperty, MerkleAccount> accountsLedger) {
		sizeLimitedStorage.recordNewKvUsageTo(accountsLedger);
	}

	private boolean isActiveContractOp() {
		final var accessor = txnCtx.accessor();
		final var activeFunction = accessor.getFunction();
		return activeFunction == ContractCreate || activeFunction == ContractCall;
	}
}
