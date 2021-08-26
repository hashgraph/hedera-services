package com.hedera.services.utils;

import com.hedera.services.store.tokens.views.internals.PermHashInteger;
import com.swirlds.common.merkle.utility.Keyed;
import com.swirlds.common.merkle.utility.MerkleLong;

public class KeyedMerkleLong extends MerkleLong implements Keyed<PermHashInteger> {
	public KeyedMerkleLong() {
	}

	public KeyedMerkleLong(long value) {
		super(value);
	}

	public KeyedMerkleLong(MerkleLong that) {
		super(that);
	}

	@Override
	public PermHashInteger getKey() {
		return new PermHashInteger((int) getValue());
	}

	@Override
	public void setKey(PermHashInteger permHashInteger) {
		throw new UnsupportedOperationException();
	}
}
