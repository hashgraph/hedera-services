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
import com.hedera.services.ledger.accounts.BackingStore;
import com.hedera.services.ledger.properties.BeanProperty;
import com.hedera.services.ledger.properties.ChangeSummaryManager;
import com.hedera.services.ledger.properties.TokenScopedPropertyValue;
import com.hedera.services.tokens.TokenScope;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

/**
 * Provides a ledger with transactional semantics. Changes during a transaction
 * are summarized in per-account changesets, which are then either saved to a
 * backing store when the transaction is committed; or dropped with no effects
 * upon a rollback.
 *
 * @param <K> the type of id used by the ledger.
 * @param <P> the family of properties associated to entities in the ledger.
 * @param <A> the type of a ledger entity.
 *
 * @author Michael Tinker
 */
public class TransactionalLedger<
		K,
		P extends Enum<P> & BeanProperty<A>,
		A extends TokenViewMergeable<A>> implements Ledger<K, P, A> {

	private static final Logger log = LogManager.getLogger(TransactionalLedger.class);

	private final Set<K> deadEntities = new HashSet<>();
	private final Class<P> propertyType;
	private final Supplier<A> newEntity;
	private final BackingStore<K, A> entities;
	private final ChangeSummaryManager<A, P> changeManager;
	private final Function<K, EnumMap<P, Object>> changeFactory;

	final Map<K, A> tokenRefs = new HashMap<>();
	final Map<K, EnumMap<P, Object>> changes = new HashMap<>();

	private boolean isInTransaction = false;
	private Optional<Comparator<K>> keyComparator = Optional.empty();

	public TransactionalLedger(
			Class<P> propertyType,
			Supplier<A> newEntity,
			BackingStore<K, A> accounts,
			ChangeSummaryManager<A, P> changeManager
	) {
		this.propertyType = propertyType;
		this.newEntity = newEntity;
		this.entities = accounts;
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
		entities.flushMutableRefs();

		changes.clear();
		deadEntities.clear();
		tokenRefs.clear();

		isInTransaction = false;
	}

	void dropPendingTokenChanges() {
		tokenRefs.clear();
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
					.filter(id -> !deadEntities.contains(id))
					.forEach(id -> entities.put(id, get(id)));
			changes.clear();
			tokenRefs.clear();

			Stream<K> deadKeys = keyComparator.isPresent()
					? deadEntities.stream().sorted(keyComparator.get())
					: deadEntities.stream();
			deadKeys.forEach(entities::remove);
			deadEntities.clear();

			entities.flushMutableRefs();

			isInTransaction = false;
		} catch (Exception e) {
			String changeDesc = "<N/A>";
			try {
				changeDesc = changeSetSoFar();
			} catch (Exception f) {
				log.warn("Unable to describe pending change set!", f);
			}
			log.error("Catastrophic failure during commit of {}!", changeDesc);
			throw e;
		}
	}

	public String changeSetSoFar() {
		StringBuilder desc = new StringBuilder("{");
		AtomicBoolean isFirstChange = new AtomicBoolean(true);
		changes.entrySet().forEach(change -> {
			if (!isFirstChange.get()) {
				desc.append(", ");
			}
			K id = change.getKey();
			var accountInDeadAccounts = deadEntities.contains(id) ? "*DEAD* " : "";
			var accountNotInDeadAccounts = deadEntities.contains(id) ? "*NEW -> DEAD* " : "*NEW* ";
			var prefix = entities.contains(id)
					? accountInDeadAccounts
					: accountNotInDeadAccounts;
			desc.append(prefix)
					.append(readableId(id))
					.append(": [");
			desc.append(
					change.getValue().entrySet().stream()
							.map(entry -> String.format("%s -> %s", entry.getKey(), readableProperty(entry.getValue())))
							.collect(joining(", ")));
			if (tokenRefs.containsKey(id)) {
				if (change.getValue().size() > 0) {
					desc.append(", ");
				}
				desc.append("TOKENS -> ");
				var view = tokenRefs.get(id);
				desc.append(view.readableTokenRelationships());
			}
			desc.append("]");
			isFirstChange.set(false);
		});
		deadEntities.stream()
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

		if (value instanceof TokenScopedPropertyValue) {
			var viewSoFar = tokenRefs.computeIfAbsent(id, ignore -> toTokenTarget(id));
			property.setter().accept(viewSoFar, value);
			changes.computeIfAbsent(id, changeFactory);
		} else {
			changeManager.update(changes.computeIfAbsent(id, changeFactory), property, value);
		}
	}

	@Override
	public A get(K id) {
		throwIfMissing(id);

		EnumMap<P, Object> changeSet = changes.get(id);
		boolean hasPendingChanges = changeSet != null;
		A account = entities.contains(id) ? entities.getRef(id) : newEntity.get();
		if (hasPendingChanges) {
			changeManager.persist(changeSet, account);
		}

		var viewSoFar = tokenRefs.get(id);
		if (viewSoFar != null) {
			account.mergeTokenPropertiesFrom(viewSoFar);
		}

		return account;
	}

	@Override
	public A getTokenRef(K id) {
		throwIfMissing(id);

		return tokenRefs.computeIfAbsent(id, ignore -> toTokenTarget(id));
	}

	public void markForMerge(K id) {
		changes.computeIfAbsent(id, changeFactory);
	}

	@Override
	public Object get(K id, P property) {
		throwIfMissing(id);

		var changeSet = changes.get(id);
		if (changeSet != null && changeSet.containsKey(property)) {
			return changeSet.get(property);
		} else {
			return property.getter().apply(toGetterTarget(id));
		}
	}

	@Override
	public Object get(K id, P property, TokenScope scope) {
		throwIfMissing(id);

		var viewSoFar = tokenRefs.get(id);
		viewSoFar = (viewSoFar != null) ? viewSoFar : toGetterTarget(id);
		return property.scopedGetter().apply(viewSoFar, scope);
	}

	@Override
	public void create(K id) {
		assertIsCreatable(id);

		changes.put(id, new EnumMap<>(propertyType));
	}

	@Override
	public void destroy(K id) {
		throwIfNotInTxn();

		deadEntities.add(id);
	}

	boolean isInTransaction() {
		return isInTransaction;
	}

	private A toGetterTarget(K id) {
		return isPendingCreation(id) ? newEntity.get() : entities.getRef(id);
	}

	private A toTokenTarget(K id) {
		return isPendingCreation(id) ? newEntity.get() : entities.getTokenCopy(id);
	}

	private boolean isPendingCreation(K id) {
		return !entities.contains(id) && changes.containsKey(id);
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

	private void throwIfMissing(K id) {
		if (!exists(id)) {
			throw new MissingAccountException(id);
		}
	}

	private boolean existsOrIsPendingCreation(K id) {
		return entities.contains(id) || changes.containsKey(id);
	}

	private boolean isZombie(K id) {
		return deadEntities.contains(id);
	}
}
