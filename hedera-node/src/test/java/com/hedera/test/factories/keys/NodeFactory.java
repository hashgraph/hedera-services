package com.hedera.test.factories.keys;

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

import com.swirlds.common.crypto.SignatureType;

import java.util.List;

public interface NodeFactory {
	static NodeFactory rsa3072(boolean usedToSign) {
		return new LeafFactory(null, usedToSign, SignatureType.RSA);
	}

	static NodeFactory ecdsa384(boolean usedToSign) {
		return new LeafFactory(null, usedToSign, SignatureType.ECDSA);
	}

	static NodeFactory ed25519() {
		return LeafFactory.DEFAULT_FACTORY;
	}

	static NodeFactory ed25519(boolean usedToSign) {
		return new LeafFactory(null, usedToSign, SignatureType.ED25519);
	}

	static NodeFactory ed25519(String label) {
		return new LeafFactory(label, true, SignatureType.ED25519);
	}

	static NodeFactory ed25519(String label, boolean usedToSign) {
		return new LeafFactory(label, usedToSign, SignatureType.ED25519);
	}

	static NodeFactory list(NodeFactory... childFactories) {
		return new ListFactory(List.of(childFactories));
	}

	static NodeFactory threshold(int M, NodeFactory... childFactories) {
		return new ThresholdFactory(List.of(childFactories), M);
	}
}
