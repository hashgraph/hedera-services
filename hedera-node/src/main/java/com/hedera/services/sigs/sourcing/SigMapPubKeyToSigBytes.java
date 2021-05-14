package com.hedera.services.sigs.sourcing;

/*-
 * ‌
 * Hedera Services Node
 * ​
 * Copyright (C) 2018 - 2021 Hedera Hashgraph, LLC
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
import com.hederahashgraph.api.proto.java.SignatureMap;
import com.hederahashgraph.api.proto.java.SignaturePair;
import com.hedera.services.legacy.exception.KeyPrefixMismatchException;

import java.util.Arrays;

/**
 * A source of cryptographic signatures backed by a {@link SignatureMap} instance.
 *
 * <p><b>IMPORTANT:</b> If a public key does not match any prefix in the backing
 * {@code SignatureMap}, we simply return an empty {@code byte[]} for its
 * cryptographic signature. It might seem that we should instead fail fast
 * (since an empty signature can never be {@code VALID}).
 *
 * However, this would be a mistake, since with e.g. Hedera threshold keys it is quite
 * possible for a Hedera key to be active even if some number of its constituent
 * simple keys lack a valid signature.
 *
 * @author Michael Tinker
 */
public class SigMapPubKeyToSigBytes implements PubKeyToSigBytes {
	private final SignatureMap sigMap;

	SigMapPubKeyToSigBytes(SignatureMap sigMap) {
		this.sigMap = sigMap;
	}

	@Override
	public byte[] sigBytesFor(byte[] pubKey) throws KeyPrefixMismatchException {
		byte[] sigBytes = EMPTY_SIG;
		for (var sigPair : sigMap.getSigPairList()) {
			final var pubKeyPrefix = sigPair.getPubKeyPrefix().toByteArray();
			final var n = pubKeyPrefix.length;
			if (Arrays.equals(pubKeyPrefix, 0, n, pubKey, 0, n)) {
				if (sigBytes != EMPTY_SIG) {
					throw new KeyPrefixMismatchException("Source signature map is ambiguous for given public key!");
				}
				sigBytes = sigBytesFor(sigPair);
			}
		}
		return sigBytes;
	}

	private byte[] sigBytesFor(SignaturePair sp) {
		if (sp.getRSA3072() != ByteString.EMPTY) {
			return sp.getRSA3072().toByteArray();
		} else if (sp.getECDSA384() != ByteString.EMPTY) {
			return sp.getECDSA384().toByteArray();
		} else {
			return sp.getEd25519().toByteArray();
		}
	}

	public static boolean beginsWith(byte[] pubKey, byte[] prefix) {
		int n = prefix.length;
		return Arrays.equals(prefix, 0, n, pubKey, 0, n);
	}
}
