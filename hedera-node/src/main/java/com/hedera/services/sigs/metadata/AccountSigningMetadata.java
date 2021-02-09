package com.hedera.services.sigs.metadata;

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

import com.hedera.services.legacy.core.jproto.JKey;

/**
 * Represents metadata about the signing activities of a Hedera cryptocurrency account.
 *
 * @author Michael Tinker
 */
public class AccountSigningMetadata {
	private final JKey key;
	private final boolean receiverSigRequired;

	public AccountSigningMetadata(JKey key, boolean receiverSigRequired) {
		this.key = key;
		this.receiverSigRequired = receiverSigRequired;
	}

	public JKey getKey() {
		return key;
	}

	public boolean isReceiverSigRequired() {
		return receiverSigRequired;
	}
}
