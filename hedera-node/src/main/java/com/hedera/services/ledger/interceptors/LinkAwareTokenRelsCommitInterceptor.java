package com.hedera.services.ledger.interceptors;

import com.hedera.services.context.SideEffectsTracker;
import com.hedera.services.context.TransactionContext;
import com.hedera.services.ledger.EntityChangeSet;
import com.hedera.services.ledger.properties.TokenRelProperty;
import com.hedera.services.state.merkle.MerkleTokenRelStatus;
import com.hedera.services.utils.EntityNum;
import com.hederahashgraph.api.proto.java.AccountID;
import com.hederahashgraph.api.proto.java.TokenID;
import org.apache.commons.lang3.tuple.Pair;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;
import java.util.TreeSet;

import static com.hedera.services.state.merkle.internals.BitPackUtils.packedNums;

/**
 * Derived interceptor that maintains the token association linked list for any account being
 * associated or dissociated from a token in this transaction.
 *
 * <p>Note that for HAPI {@code tokenAssociate} or {@code tokenDissociate}, the {@link TreeMap}s
 * and {@link TreeSet} used in implementation will have a single entry, with very low overhead.
 *
 * <p>For a HAPI contract operation, the maps could be somewhat larger, but compared to the overall
 * cost of an EVM transaction, this overhead will still be negligible.
 */
public class LinkAwareTokenRelsCommitInterceptor extends AutoAssocTokenRelsCommitInterceptor {
	// The entity numbers of all the accounts whose relationships were touched in this transaction
	private final Set<EntityNum> touched = new TreeSet<>();
	// Map from touched account number to all dissociated token numbers in this transaction
	private final Map<EntityNum, List<EntityNum>> removedNums = new TreeMap<>();
	// Map from touched account number to all new relationships created in this transaction
	private final Map<EntityNum, List<MerkleTokenRelStatus>> addedRels = new TreeMap<>();

	private final TokenRelsLinkManager relsLinkManager;

	public LinkAwareTokenRelsCommitInterceptor(
			final TransactionContext txnCtx,
			final SideEffectsTracker sideEffectsTracker,
			final TokenRelsLinkManager relsLinkManager
	) {
		super(txnCtx, sideEffectsTracker);
		this.relsLinkManager = relsLinkManager;
	}

	@Override
	public void preview(
			final EntityChangeSet<Pair<AccountID, TokenID>, MerkleTokenRelStatus, TokenRelProperty> pendingChanges
	) {
		final var n = pendingChanges.size();
		if (n == 0) {
			return;
		}
		super.preview(pendingChanges);
		touched.clear();
		addedRels.clear();
		removedNums.clear();
		for (int i = 0; i < n; i++) {
			final var entity = pendingChanges.entity(i);
			if (entity != null && pendingChanges.changes(i) != null) {
				// Simply changing the properties of an existing relationship doesn't affect links
				continue;
			}
			final var id = pendingChanges.id(i);
			final var accountNum = EntityNum.fromLong(id.getLeft().getAccountNum());
			touched.add(accountNum);
			if (entity == null) {
				// A null current entity means this is a new association
				final var number = packedNums(accountNum.longValue(), id.getRight().getTokenNum());
				final var newRel = new MerkleTokenRelStatus(number);
				addedRels.computeIfAbsent(accountNum, ignore -> new ArrayList<>())
						.add(newRel);
			} else {
				// A null change set means the relationship is ending
				final var tbdTokenNum = EntityNum.fromLong(entity.getRelatedTokenNum());
				removedNums.computeIfAbsent(accountNum, ignore -> new ArrayList<>()).add(tbdTokenNum);
			}
		}
		touched.forEach(accountNum ->
				relsLinkManager.updateLinks(accountNum, removedNums.get(accountNum), addedRels.get(accountNum)));
	}
}
