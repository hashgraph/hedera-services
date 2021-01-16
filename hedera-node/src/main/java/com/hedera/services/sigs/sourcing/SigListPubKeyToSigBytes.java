package com.hedera.services.sigs.sourcing;

/*-
 * ‌
 * Hedera Services Node
 * ​
 * Copyright (C) 2018 - 2020 Hedera Hashgraph, LLC
 * ​
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 * 
 *      http://www.apache.org/licenses/LICENSE-2.0
 * 
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 * ‍
 */

import com.google.protobuf.ByteString;
import com.hederahashgraph.api.proto.java.Signature;
import com.hedera.services.legacy.exception.KeySignatureCountMismatchException;

import java.util.List;
import java.util.stream.Stream;

import static java.util.stream.Collectors.toList;

/**
 * A source of cryptographic signatures backed by a list of Hedera {@link Signature} instances.
 *
 * <b>IMPORTANT:</b> The (deprecated) {@link com.hederahashgraph.api.proto.java.SignatureList}
 * approach to signing a gRPC transaction does not explicitly map from public keys to their raw
 * signatures. Instead, it uses the convention that a left-to-right DFS traversal of the raw
 * signatures in the {@link com.hederahashgraph.api.proto.java.SignatureList} will encounter
 * them in exactly the order that their public keys appear when doing a left-to-right
 * traversal of the Hedera keys required to sign the gRPC transaction (as ordered by
 * {@link com.hedera.services.sigs.order.HederaSigningOrder}).
 *
 * This fragile, unenforceable, thinly documented protocol is much inferior to the
 * explicit public-key-to-cryptographic-signature mapping given by a
 * {@link com.hederahashgraph.api.proto.java.SignatureMap}.
 *
 * @author Michael Tinker
 */
public class SigListPubKeyToSigBytes implements PubKeyToSigBytes {
	private List<Signature> simpleSigs;
	private int i = 0;

	public SigListPubKeyToSigBytes(List<Signature> hederaSigs) {
		simpleSigs = hederaSigs.stream().flatMap(this::flattened).collect(toList());
	}
	private Stream<Signature> flattened(Signature sig) {
		if (sig.hasThresholdSignature()) {
			return sig.getThresholdSignature().getSigs().getSigsList().stream().flatMap(this::flattened);
		} else if (sig.hasSignatureList()) {
			return sig.getSignatureList().getSigsList().stream().flatMap(this::flattened);
		} else {
			return Stream.of(sig);
		}
	}

	@Override
	public byte[] sigBytesFor(byte[] pubKey) throws KeySignatureCountMismatchException {
		if (i == simpleSigs.size()) {
			throw new KeySignatureCountMismatchException("No more signatures available in the list!");
		}
		return sigBytesFor(simpleSigs.get(i++));
	}

	private byte[] sigBytesFor(Signature sig) {
		if (sig.getRSA3072() != ByteString.EMPTY) {
			return sig.getRSA3072().toByteArray();
		} else if (sig.getECDSA384() != ByteString.EMPTY) {
			return sig.getECDSA384().toByteArray();
		} else {
			return sig.getEd25519().toByteArray();
		}
	}
}
