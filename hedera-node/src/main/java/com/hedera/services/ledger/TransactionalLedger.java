package com.hedera.services.ledger;

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

import com.hedera.services.exceptions.MissingAccountException;
import com.hedera.services.ledger.backing.BackingStore;
import com.hedera.services.ledger.properties.BeanProperty;
import com.hedera.services.ledger.properties.ChangeSummaryManager;
import com.hedera.services.utils.EntityIdUtils;
import com.hederahashgraph.api.proto.java.ResponseCodeEnum;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import java.util.ArrayList;
import java.util.EnumMap;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.function.Function;
import java.util.function.Supplier;

import static com.hedera.services.utils.EntityIdUtils.readableId;
import static com.hedera.services.utils.MiscUtils.readableProperty;
import static com.hederahashgraph.api.proto.java.ResponseCodeEnum.INVALID_ACCOUNT_ID;
import static java.util.stream.Collectors.joining;

/**
 * Provides a ledger with transactional semantics. Changes during a transaction
 * are summarized in per-account changesets, which are then either saved to a
 * backing store when the transaction is committed; or dropped with no effects
 * upon a rollback.
 *
 * @param <K>
 * 		the type of id used by the ledger
 * @param <P>
 * 		the family of properties associated to entities in the ledger
 * @param <A>
 * 		the type of a ledger entity
 */
public class TransactionalLedger<K, P extends Enum<P> & BeanProperty<A>, A>
		implements Ledger<K, P, A>, BackingStore<K, A> {
	private static final int MAX_ENTITIES_LIKELY_TOUCHED_IN_LEDGER_TXN = 42;

	private static final Logger log = LogManager.getLogger(TransactionalLedger.class);

	private final P[] allProps;
	private final Set<K> deadEntities = new HashSet<>();
	private final List<K> createdKeys = new ArrayList<>(MAX_ENTITIES_LIKELY_TOUCHED_IN_LEDGER_TXN);
	private final List<K> changedKeys = new ArrayList<>(MAX_ENTITIES_LIKELY_TOUCHED_IN_LEDGER_TXN);
	private final List<K> perishedKeys = new ArrayList<>(MAX_ENTITIES_LIKELY_TOUCHED_IN_LEDGER_TXN);
	private final Class<P> propertyType;
	private final Supplier<A> newEntity;
	private final BackingStore<K, A> entities;
	private final TransactionalLedger<K, P, A> entitiesLedger;
	private final ChangeSummaryManager<A, P> changeManager;
	private final Function<K, EnumMap<P, Object>> changeFactory;

	final Map<K, EnumMap<P, Object>> changes = new HashMap<>();

	private boolean isInTransaction = false;
	private PropertyChangeObserver<K, P> commitInterceptor = null;
	private Optional<Function<K, String>> keyToString = Optional.empty();

	public TransactionalLedger(
			Class<P> propertyType,
			Supplier<A> newEntity,
			BackingStore<K, A> entities,
			ChangeSummaryManager<A, P> changeManager
	) {
		this.entities = entities;
		this.allProps = propertyType.getEnumConstants();
		this.newEntity = newEntity;
		this.propertyType = propertyType;
		this.changeManager = changeManager;
		this.changeFactory = ignore -> new EnumMap<>(propertyType);

		if (entities instanceof TransactionalLedger) {
			this.entitiesLedger = (TransactionalLedger<K, P, A>) entities;
		} else {
			this.entitiesLedger = null;
		}
	}

	/**
	 * Constructs an active ledger backed by the given source ledger.
	 *
	 * @param sourceLedger
	 * 		the ledger to wrap
	 * @param <K>
	 * 		the type of id used by the ledger
	 * @param <P>
	 * 		the family of properties associated to entities in the ledger
	 * @param <A>
	 * 		the type of a ledger entity
	 * @return an active ledger wrapping the source
	 */
	public static <K, P extends Enum<P> & BeanProperty<A>, A> TransactionalLedger<K, P, A> activeLedgerWrapping(
			final TransactionalLedger<K, P, A> sourceLedger
	) {
		Objects.requireNonNull(sourceLedger);

		final var wrapper = new TransactionalLedger<>(
				sourceLedger.getPropertyType(),
				sourceLedger.getNewEntity(),
				sourceLedger,
				sourceLedger.getChangeManager());
		wrapper.begin();
		return wrapper;
	}

	public void setKeyToString(final Function<K, String> keyToString) {
		this.keyToString = Optional.of(keyToString);
	}

	public void setCommitInterceptor(final PropertyChangeObserver<K, P> commitInterceptor) {
		this.commitInterceptor = commitInterceptor;
	}

	public void begin() {
		if (isInTransaction) {
			throw new IllegalStateException("A transaction is already active!");
		}
		isInTransaction = true;
	}

	public void undoChangesOfType(List<P> properties) {
		if (!isInTransaction) {
			throw new IllegalStateException("Cannot undo changes, no transaction is active");
		}
		if (!changedKeys.isEmpty()) {
			for (var key : changedKeys) {
				final var delta = changes.get(key);
				if (delta != null) {
					for (final var property : properties) {
						delta.remove(property);
					}
				}
			}
		}
	}

	public void undoCreations() {
		if (!isInTransaction) {
			throw new IllegalStateException("Cannot undo created keys, no transaction is active");
		}
		createdKeys.clear();
	}

	public void rollback() {
		if (!isInTransaction) {
			throw new IllegalStateException("Cannot perform rollback, no transaction is active!");
		}

		changes.clear();
		deadEntities.clear();
		changedKeys.clear();
		createdKeys.clear();
		perishedKeys.clear();

		isInTransaction = false;
	}

	public void commit() {
		if (!isInTransaction) {
			throw new IllegalStateException("Cannot perform commit, no transaction is active!");
		}

		try {
			flushListed(changedKeys);
			flushListed(createdKeys);
			changes.clear();

			if (!deadEntities.isEmpty()) {
				perishedKeys.forEach(entities::remove);
				deadEntities.clear();
				perishedKeys.clear();
			}

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

		changeManager.update(changes.computeIfAbsent(id, ignore -> {
			changedKeys.add(id);
			return changeFactory.apply(id);
		}), property, value);
	}

	@Override
	public A getFinalized(K id) {
		throwIfMissing(id);

		final EnumMap<P, Object> changeSet = changes.get(id);
		final boolean hasPendingChanges = changeSet != null;
		final A entity = entities.contains(id) ? entities.getRef(id) : newEntity.get();
		if (hasPendingChanges) {
			if (commitInterceptor != null) {
				changeManager.persistWithObserver(id, changeSet, entity, commitInterceptor);
			} else {
				changeManager.persist(changeSet, entity);
			}
		}

		return entity;
	}

	@Override
	public Object get(K id, P property) {
		throwIfMissing(id);

		var changeSet = changes.get(id);
		if (changeSet != null && changeSet.containsKey(property)) {
			return changeSet.get(property);
		} else {
			if (entitiesLedger == null) {
				return property.getter().apply(toGetterTarget(id));
			} else {
				/* If we are backed by a ledger, it is far more efficient to source properties
				 * by using get(), since each call to a ledger's getRef() creates a new entity
				 * and sets all its properties. */
				return entitiesLedger.contains(id)
						? entitiesLedger.get(id, property)
						: property.getter().apply(newEntity.get());
			}
		}
	}

	@Override
	public void create(K id) {
		assertIsCreatable(id);

		changes.put(id, new EnumMap<>(propertyType));
		createdKeys.add(id);
	}

	@Override
	public void destroy(K id) {
		throwIfNotInTxn();

		deadEntities.add(id);
		perishedKeys.add(id);
	}

	/* --- BackingStore --- */

	/**
	 * {@inheritDoc}
	 */
	@Override
	public A getRef(K id) {
		if (!exists(id)) {
			return null;
		}

		/* Create a new entity, then set its properties based on the combination of
		 * the existing (or default) entity's properties, plus the current change set. */
		final var entity = newEntity.get();
		if (entitiesLedger == null) {
			final var getterTarget = toGetterTarget(id);
			setPropsWithSource(id, entity, prop -> prop.getter().apply(getterTarget));
		} else {
			setPropsWithSource(id, entity, extantLedgerPropsFor(id));
		}
		return entity;
	}

	/**
	 * {@inheritDoc}
	 */
	@Override
	public A getImmutableRef(K id) {
		return getRef(id);
	}

	/**
	 * {@inheritDoc}
	 */
	@Override
	public void put(K id, A entity) {
		throwIfNotInTxn();

		if (isZombie(id)) {
			deadEntities.remove(id);
		}
		/* The ledger wrapping us may have created an entity we don't have; so catch up on that if necessary. */
		if (!exists(id)) {
			create(id);
		}
		/* Now accumulate the entire change-set represented by the received entity. */
		for (final var prop : allProps) {
			set(id, prop, prop.getter().apply(entity));
		}
	}

	/**
	 * {@inheritDoc}
	 */
	@Override
	public void remove(K id) {
		destroy(id);
	}

	/**
	 * {@inheritDoc}
	 */
	@Override
	public boolean contains(K id) {
		return exists(id);
	}

	/**
	 * {@inheritDoc}
	 */
	@Override
	public Set<K> idSet() {
		return entities.idSet();
	}

	@Override
	public long size() {
		return entities.size();
	}

	/* --- Helpers --- */
	public ResponseCodeEnum validate(final K id, final LedgerCheck<A, P> ledgerCheck) {
		if (!exists(id)) {
			return INVALID_ACCOUNT_ID;
		}
		var changeSet = changes.get(id);
		if (entitiesLedger == null) {
			final var getterTarget = toGetterTarget(id);
			return ledgerCheck.checkUsing(getterTarget, changeSet);
		} else {
			/* If we are backed by a ledger, it is far more efficient to source properties
			 * by using get(), since each call to a ledger's getRef() creates a new entity
			 * and sets all its properties. */
			final var extantProps = extantLedgerPropsFor(id);
			return ledgerCheck.checkUsing(extantProps, changeSet);
		}
	}

	private void setPropsWithSource(final K id, final A entity, final Function<P, Object> extantProps) {
		final var changeSet = changes.get(id);
		for (final var prop : allProps) {
			if (changeSet != null && changeSet.containsKey(prop)) {
				prop.setter().accept(entity, changeSet.get(prop));
			} else {
				prop.setter().accept(entity, extantProps.apply(prop));
			}
		}
	}

	private Function<P, Object> extantLedgerPropsFor(final K id) {
		Objects.requireNonNull(entitiesLedger);
		return entitiesLedger.contains(id)
				? prop -> entitiesLedger.get(id, prop)
				: newDefaultPropertySource();
	}

	public boolean isInTransaction() {
		return isInTransaction;
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
					.append(keyToString.orElse(EntityIdUtils::readableId).apply(id))
					.append(": [");
			desc.append(
					change.getValue().entrySet().stream()
							.map(entry -> String.format("%s -> %s", entry.getKey(), readableProperty(entry.getValue())))
							.collect(joining(", ")));
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

	void throwIfNotInTxn() {
		if (!isInTransaction) {
			throw new IllegalStateException("No active transaction!");
		}
	}

	Map<K, EnumMap<P, Object>> getChanges() {
		return changes;
	}

	List<K> getCreations() {
		return createdKeys;
	}

	private void flushListed(final List<K> l) {
		if (!l.isEmpty()) {
			for (final var key : l) {
				if (!deadEntities.contains(key)) {
					entities.put(key, getFinalized(key));
				}
			}
			l.clear();
		}
	}

	private A toGetterTarget(K id) {
		return isPendingCreation(id) ? newEntity.get() : entities.getImmutableRef(id);
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
			throw new IllegalArgumentException("An entity already exists with key '" + id + "'!");
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

	private Function<P, Object> newDefaultPropertySource() {
		final var defaultEntity = newEntity.get();
		return prop -> prop.getter().apply(defaultEntity);
	}

	ChangeSummaryManager<A, P> getChangeManager() {
		return changeManager;
	}

	Supplier<A> getNewEntity() {
		return newEntity;
	}

	Class<P> getPropertyType() {
		return propertyType;
	}

	/* --- Only used by unit tests --- */
	public TransactionalLedger<K, P, A> getEntitiesLedger() {
		return entitiesLedger;
	}
}
