package com.hedera.services.ledger;

import com.hedera.services.ledger.properties.BeanProperty;

import java.util.Map;

/**
 * Represents a set of changes to an entity in a {@link TransactionalLedger}.
 *
 * <b>IMPORTANT:</b>
 * <ul>
 *     <li>If the target entity is null, represents creation of a new entity with the given id and
 *     customizing changes.</li>
 *     <li>If the changes {@code Map} is null, represents removal of the entity with the given id.</li>
 * </ul>
 *
 * @param <K>
 * 		the ledger id type
 * @param <A>
 * 		the ledger entity type
 * @param <P>
 * 		the enumerable family of properties
 */
public record EntityChanges<K, A, P extends Enum<P> & BeanProperty<A>>(K id, A entity, Map<P, Object> changes) {
}