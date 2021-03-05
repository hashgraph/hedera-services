package com.hedera.services.ledger.properties;

import com.hedera.services.state.merkle.MerkleEntityId;
import com.hedera.services.state.merkle.MerklePlaceholder;
import com.hederahashgraph.api.proto.java.AccountID;

import java.util.function.BiConsumer;
import java.util.function.Function;

public enum NftOwningAccountProperty implements BeanProperty<MerkleEntityId> {
	IDENTITY {
		@Override
		public BiConsumer<MerkleEntityId, Object> setter() {
			return (entity, owner) -> {
				var merkleOwner = (MerkleEntityId) owner;
				entity.setShard(merkleOwner.getShard());
				entity.setRealm(merkleOwner.getRealm());
				entity.setNum(merkleOwner.getNum());
			};
		}

		@Override
		public Function<MerkleEntityId, Object> getter() {
			return entity -> entity;
		}
	}
}
