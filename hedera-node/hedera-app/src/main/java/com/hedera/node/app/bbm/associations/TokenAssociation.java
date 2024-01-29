package com.hedera.node.app.bbm.associations;

import com.hedera.hapi.node.state.token.TokenRelation;
import com.hedera.node.app.service.mono.state.submerkle.EntityId;
import com.hedera.node.app.service.mono.state.virtual.entities.OnDiskTokenRel;
import com.hedera.node.app.service.mono.utils.EntityNumPair;
import com.hedera.node.app.state.merkle.disk.OnDiskValue;
import com.hederahashgraph.api.proto.java.AccountID;
import com.hederahashgraph.api.proto.java.TokenID;
import com.swirlds.base.utility.Pair;
import edu.umd.cs.findbugs.annotations.NonNull;
import edu.umd.cs.findbugs.annotations.Nullable;

record TokenAssociation(
        EntityId account,
        EntityId tokenId,
        long balance,
        boolean isFrozen,
        boolean isKycGranted,
        boolean isAutomaticAssociation,
        EntityId prev,
        EntityId next
) {

    @NonNull
    static TokenAssociation fromMono(OnDiskTokenRel tokenRel) {
        final var at = toLongsPair(toPair(tokenRel.getKey()));

        return new TokenAssociation(
                entityIdFrom(at.left()),
                entityIdFrom(at.right()),
                tokenRel.getBalance(),
                tokenRel.isFrozen(),
                tokenRel.isKycGranted(),
                tokenRel.isAutomaticAssociation(),
                entityIdFrom(tokenRel.getPrev()),
                entityIdFrom(tokenRel.getNext()));
    }

    static TokenAssociation fromMod(@NonNull final OnDiskValue<TokenRelation> wrapper) {
        final var value = wrapper.getValue();
        return new TokenAssociation(
                accountIdFromMod(value.accountId()),
                tokenIdFromMod(value.tokenId()),
                value.balance(),
                value.frozen(),
                value.kycGranted(),
                value.automaticAssociation(),
                tokenIdFromMod(value.previousToken()),
                tokenIdFromMod(value.previousToken()));
    }

    @NonNull
    static Pair<AccountID, TokenID> toPair(@NonNull final EntityNumPair enp) {
        final var at = enp.asAccountTokenRel();
        return Pair.of(at.getLeft(), at.getRight());
    }

    @NonNull
    static Pair<Long, Long> toLongsPair(@NonNull final Pair<AccountID, TokenID> pat) {
        return Pair.of(pat.left().getAccountNum(), pat.right().getTokenNum());
    }

    private static EntityId accountIdFromMod(@Nullable final com.hedera.hapi.node.base.AccountID accountId) {
        return null == accountId ? EntityId.MISSING_ENTITY_ID : new EntityId(0L, 0L, accountId.accountNumOrThrow());
    }

    private static EntityId tokenIdFromMod(@Nullable final com.hedera.hapi.node.base.TokenID tokenId) {
        return null == tokenId ? EntityId.MISSING_ENTITY_ID : new EntityId(0L, 0L, tokenId.tokenNum());
    }

    private static EntityId entityIdFrom(long num) {
        return new EntityId(0L, 0L, num);
    }
}
