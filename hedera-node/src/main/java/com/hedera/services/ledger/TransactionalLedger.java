package com.hedera.services.ledger;

/*-
 * ‌
 * Hedera Services Node
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

import java.util.Comparator;
import java.util.EnumMap;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.function.Function;
import java.util.function.Supplier;
import java.util.stream.Stream;

import static com.hedera.services.utils.EntityIdUtils.readableId;
import static com.hedera.services.utils.MiscUtils.readableProperty;
import static java.util.stream.Collectors.joining;
import com.hedera.services.exceptions.MissingAccountException;
import com.hedera.services.ledger.accounts.BackingAccounts;
import com.hedera.services.ledger.properties.BeanProperty;
import com.hedera.services.ledger.properties.ChangeSummaryManager;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

/**
 * Provides a ledger with transactional semantics. Changes during a transaction
 * are summarized in per-account changesets, which are then either saved to a
 * backing store when the transaction is committed; or dropped with no effects
 * upon a rollback.
 *
 * @param <K> the type of id used by the ledger.
 * @param <P> the family of properties associated to accounts in the ledger.
 * @param <A> the type of a ledger account.
 *
 * @author Michael Tinker
 */
public class TransactionalLedger<K, P extends Enum<P> & BeanProperty<A>, A> implements Ledger<K, P, A> {
	private static final Logger log = LogManager.getLogger(TransactionalLedger.class);

	private final Set<K> deadAccounts = new HashSet<>();
	private final Class<P> propertyType;
	private final Supplier<A> newAccount;
	private final BackingAccounts<K, A> accounts;
	private final ChangeSummaryManager<A, P> changeManager;
	private final Function<K, EnumMap<P, Object>> changeFactory;

	final Map<K, A> mutableRefs = new HashMap<>();
	final Map<K, EnumMap<P, Object>> changes = new HashMap<>();

	private boolean isInTransaction = false;
	private Optional<Comparator<K>> keyComparator = Optional.empty();

	public TransactionalLedger(
			Class<P> propertyType,
			Supplier<A> newAccount,
			BackingAccounts<K, A> accounts,
			ChangeSummaryManager<A, P> changeManager
	) {
		this.propertyType = propertyType;
		this.newAccount = newAccount;
		this.accounts = accounts;
		this.changeManager = changeManager;
		this.changeFactory = ignore -> new EnumMap<>(propertyType);
	}

	public void setKeyComparator(Comparator<K> keyComparator) {
		this.keyComparator = Optional.of(keyComparator);
	}

	void begin() {
		if (isInTransaction) {
			throw new IllegalStateException("A transaction is already active!");
		}
		isInTransaction = true;
	}

	void rollback() {
		if (!isInTransaction) {
			throw new IllegalStateException("Cannot perform rollback, no transaction is active!");
		}
		changes.clear();
		mutableRefs.clear();
		deadAccounts.clear();
		isInTransaction = false;
	}

	void commit() {
		if (!isInTransaction) {
			throw new IllegalStateException("Cannot perform commit, no transaction is active!");
		}

		log.debug("Changes to be committed: {}", this::changeSetSoFar);

		try {
			Stream<K> changedKeys = keyComparator.isPresent()
					? changes.keySet().stream().sorted(keyComparator.get())
					: changes.keySet().stream();
			changedKeys
					.filter(id -> !deadAccounts.contains(id))
					.forEach(id -> accounts.replace(id, get(id)));
			changes.clear();
			mutableRefs.clear();

			Stream<K> deadKeys = keyComparator.isPresent()
					? deadAccounts.stream().sorted(keyComparator.get())
					: deadAccounts.stream();
			deadKeys.forEach(accounts::remove);
			deadAccounts.clear();

			isInTransaction = false;
		} catch (Exception e) {
			String changeDesc = "<N/A>";
			try {
				changeDesc = changeSetSoFar();
			} catch (Exception ignore) {
				log.warn(ignore.getMessage());
			}
			log.error("Catastrophic failure during commit of {}!", changeDesc);
			throw e;
		}
	}

	String changeSetSoFar() {
		StringBuilder desc = new StringBuilder("{");
		AtomicBoolean isFirstChange = new AtomicBoolean(true);
		changes.entrySet().forEach(change -> {
			if (!isFirstChange.get()) {
				desc.append(", ");
			}
			K id = change.getKey();
			var accountInDeadAccounts = deadAccounts.contains(id) ? "*DEAD* " : "";
			var accountNotInDeadAccounts = deadAccounts.contains(id) ? "*NEW -> DEAD* " : "*NEW* ";
			var prefix = accounts.contains(id)
					? accountInDeadAccounts
					: accountNotInDeadAccounts;
			desc.append(prefix)
					.append(readableId(id))
					.append(": [");
			desc.append(
					change.getValue().entrySet().stream()
							.map(entry -> String.format("%s -> %s", entry.getKey(), readableProperty(entry.getValue())))
							.collect(joining(", ")));
			desc.append("]");
			isFirstChange.set(false);
		});
		deadAccounts.stream()
				.filter(id -> !changes.containsKey(id))
				.forEach(id -> {
					if (!isFirstChange.get()) {
						desc.append(", ");
					}
					desc.append("*DEAD* ").append(readableId(id));
					isFirstChange.set(false);
				});
		return desc.append("}").toString();
	}

	@Override
	public boolean exists(K id) {
		return existsOrIsPendingCreation(id) && !isZombie(id);
	}

	@Override
	public boolean existsPending(K id) {
		return isPendingCreation(id);
	}

	@Override
	public void set(K id, P property, Object value) {
		assertIsSettable(id);
		changeManager.update(changes.computeIfAbsent(id, changeFactory), property, value);
	}

	@Override
	public A get(K id) {
		throwIfMissing(id);

		EnumMap<P, Object> changeSet = changes.get(id);
		boolean hasPendingChanges = changeSet != null;
		A account;
		if (!accounts.contains(id)) {
			account = newAccount.get();
		} else {
			account = hasPendingChanges ? mutableRefTo(id) : accounts.getUnsafeRef(id);
		}

		if (hasPendingChanges) {
			changeManager.persist(changeSet, account);
		}
		return account;
	}

	@Override
	public Object get(K id, P property) {
		throwIfMissing(id);
		if (hasPendingChange(id, property)) {
			EnumMap<P, Object> changeSet = changes.get(id);
			if (changeSet != null) {
				return changeSet.get(property);
			}
		}

		var value = property.getter().apply(
				isPendingCreation(id)
						? newAccount.get()
						: property.requiresMutableRef() ? mutableRefTo(id) : accounts.getUnsafeRef(id));
		if (property.requiresMutableRef()) {
			set(id, property, value);
		}
		return value;
	}

	private A mutableRefTo(K id) {
		return mutableRefs.computeIfAbsent(id, accounts::getMutableRef);
	}

	@Override
	public void create(K id) {
		assertIsCreatable(id);
		changes.put(id, new EnumMap<>(propertyType));
	}

	@Override
	public void destroy(K id) {
		throwIfNotInTxn();
		deadAccounts.add(id);
	}

	boolean isInTransaction() {
		return isInTransaction;
	}

	private boolean isPendingCreation(K id) {
		return !accounts.contains(id) && changes.containsKey(id);
	}

	private boolean hasPendingChange(K id, P property) {
		EnumMap<P, Object> changeSet = changes.get(id);
		return (changeSet != null) && changeSet.containsKey(property);
	}

	private void assertIsSettable(K id) {
		throwIfNotInTxn();
		throwIfMissing(id);
	}

	private void assertIsCreatable(K id) {
		if (!isInTransaction) {
			throw new IllegalStateException("No active transaction!");
		}
		if (existsOrIsPendingCreation(id)) {
			throw new IllegalArgumentException("An account already exists with key '" + id + "'!");
		}
	}

	void throwIfNotInTxn() {
		if (!isInTransaction) {
			throw new IllegalStateException("No active transaction!");
		}
	}

	private void throwIfUnsaved(K id) {
		if (isZombie(id) || !accounts.contains(id)) {
			throw new MissingAccountException(id);
		}
	}

	private void throwIfMissing(K id) {
		if (!exists(id)) {
			throw new MissingAccountException(id);
		}
	}

	private boolean existsOrIsPendingCreation(K id) {
		return accounts.contains(id) || changes.containsKey(id);
	}

	private boolean isZombie(K id) {
		return deadAccounts.contains(id);
	}
}
