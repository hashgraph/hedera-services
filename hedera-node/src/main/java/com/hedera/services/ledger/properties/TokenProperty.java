package com.hedera.services.ledger.properties;

import com.hedera.services.state.merkle.MerkleAccount;
import com.hedera.services.state.merkle.MerkleToken;
import com.hedera.services.state.submerkle.EntityId;

import java.util.function.BiConsumer;
import java.util.function.Function;

public enum TokenProperty implements BeanProperty<MerkleToken> {
    IS_DELETED {
        @Override
        public BiConsumer<MerkleToken, Object> setter() {
            return (a, f) -> a.setDeleted((boolean) f);
        }

        @Override
        public Function<MerkleToken, Object> getter() {
            return MerkleToken::isDeleted;
        }
    },
    TREASURY {
        @Override
        public BiConsumer<MerkleToken, Object> setter() {
            return (a, f) -> a.setTreasury((EntityId) f);
        }

        @Override
        public Function<MerkleToken, Object> getter() {
            return MerkleToken::treasury;
        }
    }
}
