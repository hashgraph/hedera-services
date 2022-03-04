package com.hedera.services.ledger;

import com.hedera.services.ledger.properties.BeanProperty;

import java.util.List;

/**
 * Implements an observer to a pending change set. The preview method can be used to
 * collect information for a record, or to assert validity of the pending changes.
 *
 * @param <K> the ledger id type
 * @param <A> the account type
 * @param <P> the enumerable family of account properties
 */
@FunctionalInterface
public interface CommitInterceptor<K, A, P extends Enum<P> & BeanProperty<A>> {
	/**
	 * Accepts a pending change set, including creations and removals.
	 *
	 * @throws IllegalStateException if these changes are invalid
	 */
	void preview(List<EntityChanges<K, A, P>> changesToCommit);
}