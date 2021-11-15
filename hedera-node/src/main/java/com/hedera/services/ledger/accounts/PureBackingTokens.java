package com.hedera.services.ledger.accounts;

import com.hedera.services.state.merkle.MerkleToken;
import com.hedera.services.utils.EntityNum;
import com.hederahashgraph.api.proto.java.TokenID;
import com.swirlds.merkle.map.MerkleMap;

import java.util.Set;
import java.util.function.Supplier;

import static com.hedera.services.utils.EntityNum.fromTokenId;
import static java.util.stream.Collectors.toSet;

public class PureBackingTokens implements BackingStore<TokenID, MerkleToken> {
    private final Supplier<MerkleMap<EntityNum, MerkleToken>> delegate;

    public PureBackingTokens(Supplier<MerkleMap<EntityNum, MerkleToken>> delegate) {
        this.delegate = delegate;
    }

    @Override
    public MerkleToken getRef(TokenID id) {
        return delegate.get().get(fromTokenId(id));
    }

    @Override
    public MerkleToken getImmutableRef(TokenID id) {
        return delegate.get().get(fromTokenId(id));
    }

    @Override
    public void put(TokenID id, MerkleToken Token) {
        throw new UnsupportedOperationException();
    }

    @Override
    public void remove(TokenID id) {
        throw new UnsupportedOperationException();
    }

    @Override
    public boolean contains(TokenID id) {
        return delegate.get().containsKey(fromTokenId(id));
    }

    @Override
    public Set<TokenID> idSet() {
        return delegate.get().keySet().stream().map(EntityNum::toGrpcTokenId).collect(toSet());
    }
}
