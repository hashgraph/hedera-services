package com.hedera.services.ledger;

import com.google.common.annotations.VisibleForTesting;
import com.hedera.services.ledger.properties.BeanProperty;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import static com.hedera.services.ledger.TransactionalLedger.MAX_ENTITIES_LIKELY_TOUCHED_IN_LEDGER_TXN;

/**
 * Represents a set of changes to one or more entities in a {@link TransactionalLedger}.
 *
 * <b>IMPORTANT: </b> the only changes represented are creations and mutations; we do not
 * currently have a use case that requires intercepting removals from a ledger.
 *
 * @param <K>
 * 		the ledger id type
 * @param <A>
 * 		the ledger entity type
 * @param <P>
 * 		the enumerable family of properties
 */
public class EntityChangeSet<K, A, P extends Enum<P> & BeanProperty<A>> {
	// The change set is stored in three parallel lists to reduce object allocations; if the entities
	// list has a null value at an index, it means the change at that index was a creation
	private final List<K> keys = new ArrayList<>(MAX_ENTITIES_LIKELY_TOUCHED_IN_LEDGER_TXN);
	private final List<A> entities = new ArrayList<>(MAX_ENTITIES_LIKELY_TOUCHED_IN_LEDGER_TXN);
	private final List<Map<P, Object>> changes = new ArrayList<>(MAX_ENTITIES_LIKELY_TOUCHED_IN_LEDGER_TXN);

	public K key(final int i) {
		return keys.get(i);
	}

	public A entity(final int i) {
		return entities.get(i);
	}

	public Map<P, Object> changes(final int i) {
		return changes.get(i);
	}

	public void clear() {
		keys.clear();
		changes.clear();
		entities.clear();
	}

	public int size() {
		return keys.size();
	}

	public void include(final K key, final A entity, final Map<P, Object> entityChanges) {
		keys.add(key);
		entities.add(entity);
		changes.add(entityChanges);
	}

	@VisibleForTesting
	List<K> getKeys() {
		return keys;
	}

	List<A> getEntities() {
		return entities;
	}

	List<Map<P, Object>> getChanges() {
		return changes;
	}
}
