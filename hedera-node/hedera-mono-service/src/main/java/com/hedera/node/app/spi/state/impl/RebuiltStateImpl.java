package com.hedera.node.app.spi.state.impl;

import com.google.protobuf.ByteString;
import com.hedera.services.utils.EntityNum;
import com.swirlds.fchashmap.FCHashMap;

import javax.annotation.Nonnull;
import java.time.Instant;
import java.util.Map;
import java.util.Objects;

public class RebuiltStateImpl<K, V> extends StateBase<K, V> {
	private Map<ByteString, EntityNum> aliases;
	private final Instant lastModifiedTime;

	public RebuiltStateImpl(
			@Nonnull final String stateKey,
			@Nonnull Map<ByteString, EntityNum> aliases,
			@Nonnull final Instant lastModifiedTime) {
		super(stateKey);
		this.aliases = Objects.requireNonNull(aliases);
		this.lastModifiedTime = lastModifiedTime;
	}

	RebuiltStateImpl(
			@Nonnull final String stateKey,
			@Nonnull final Instant lastModifiedTime) {
		this(stateKey, new FCHashMap<>(), lastModifiedTime);
	}

	@Override
	public Instant getLastModifiedTime() {
		return lastModifiedTime;
	}

	@Override
	protected V read(final K key) {
		return (V) aliases.get(key);
	}
}
