package com.hedera.services.ledger;

import com.hedera.services.ledger.properties.BeanProperty;

import java.util.Map;

/**
 * Represents a set of changes to an account.
 *
 * <b>IMPORTANT:</b>
 *.   - If the target account is null, represents creation of account with the given id.
 *.   - If the changes {@code Map} is null, represents removal of account with the given id.
 *
 * @param <K> the ledger id type
 * @param <A> the account type
 * @param <P> the enumerable family of account properties
 */
record AccountChanges<K, A, P extends Enum<P> & BeanProperty<A>>(K id, A account, Map<P, Object> changes) {
}