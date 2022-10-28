package com.hedera.services.ledger.accounts;

import com.google.protobuf.ByteString;
import com.hedera.services.legacy.core.jproto.JKey;
import com.hedera.services.utils.EntityNum;

// think of a better name please
public interface HederaAliasManager {

  void unlink(final ByteString alias);

  void link(final ByteString alias, final EntityNum num);

  boolean maybeLinkEvmAddress(@org.jetbrains.annotations.Nullable final JKey key, final EntityNum num);

  void forgetEvmAddress(final ByteString alias);
}
