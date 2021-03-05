package com.hedera.services.ledger.properties;

import com.hedera.services.state.merkle.MerkleEntityId;
import com.hederahashgraph.api.proto.java.AccountID;

import java.util.function.BiConsumer;
import java.util.function.Function;

public enum NftOwningAccountProperty implements BeanProperty<MerkleEntityId> {
	OWNER {
		@Override
		public BiConsumer<MerkleEntityId, Object> setter() {
			return (entity, owner) -> {
				var grpcOwner = (AccountID) owner;
				entity.setShard(grpcOwner.getShardNum());
				entity.setRealm(grpcOwner.getRealmNum());
				entity.setNum(grpcOwner.getAccountNum());
			};
		}

		@Override
		public Function<MerkleEntityId, Object> getter() {
			return entity -> entity;
		}
	}
}
