package com.hedera.services.ledger.properties;

import com.hedera.services.state.merkle.MerklePlaceholder;

import java.util.function.BiConsumer;
import java.util.function.Function;

public enum NftOwnershipProperty implements BeanProperty<MerklePlaceholder> {
	GRANT {
		@Override
		public BiConsumer<MerklePlaceholder, Object> setter() {
			return (placeholder, any) -> {};
		}

		@Override
		public Function<MerklePlaceholder, Object> getter() {
			return placeholder -> Boolean.TRUE;
		}
	}
}
