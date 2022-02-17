package com.hedera.services.ledger;

import com.hedera.services.ledger.properties.BeanProperty;

import java.util.Map;

/**
 * Represents a set of changes to a merkle leaf.
 *
 * <b>IMPORTANT:</b>
 *.   - If the target merkle leaf is null, represents creation of a merkle leaf with the given id.
 *.   - If the changes {@code Map} is null, represents removal of merkle leaf with the given id.
 *
 * @param <K> the ledger id type
 * @param <A> the merkle leaf type
 * @param <P> the enumerable family of properties
 */
public record MerkleLeafChanges<K, A, P extends Enum<P> & BeanProperty<A>>(K id, A merkleLeaf, Map<P, Object> changes) {
}