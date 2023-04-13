package com.hedera.node.app.signature;

import com.hedera.hapi.node.base.SignaturePair;
import com.hedera.node.app.spi.key.HederaKey;
import com.hedera.pbj.runtime.io.buffer.Bytes;
import java.util.List;
import java.util.concurrent.Future;

/**
 * Asynchronously verifies signatures on a transaction given a set of keys.
 */
public interface SignatureVerifier {
    Future<Boolean> verifySignatures(Bytes signedBytes, List<SignaturePair> sigPairs, HederaKey key);
}
