package com.hedera.node.app.spi.fixtures;

import com.hedera.hapi.node.base.Key;
import com.hedera.pbj.runtime.io.buffer.Bytes;

public record TestKeyInfo(Bytes privateKey, Key publicKey, Bytes alias) {
}
