package com.hedera.node.app.spi.keys;

import com.swirlds.virtualmap.VirtualValue;

/**
 * Temporary key needed to not break existing code.
 * This will be removed after legacy {@link com.hedera.services.legacy.core.jproto.JKey}
 * is replaced with {@link HederaKey}. Once this is removed {@link HederaKey} will extend
 * {@link VirtualValue}
 */
public interface ReplHederaKey extends VirtualValue, HederaKey{
}
