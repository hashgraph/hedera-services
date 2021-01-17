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

public class ScheduledPubKeyToSigBytes implements PubKeyToSigBytes {
	private final PubKeyToSigBytes scopedDelegate;
	private final PubKeyToSigBytes scheduledDelegate;

	public ScheduledPubKeyToSigBytes(
			PubKeyToSigBytes scopedDelegate,
			PubKeyToSigBytes scheduledDelegate
	) {
		this.scopedDelegate = scopedDelegate;
		this.scheduledDelegate = scheduledDelegate;
	}

	@Override
	public byte[] sigBytesFor(byte[] pubKey) throws Exception {
		return scopedDelegate.sigBytesFor(pubKey);
	}

	@Override
	public byte[] sigBytesForScheduled(byte[] pubKey) {
		try {
			return scheduledDelegate.sigBytesFor(pubKey);
		} catch (Exception ignore) {
			/* Since not all required keys might have been used to sign
			the scheduled transaction in this scope, it's permissible
			to have a prefix that's ambiguous for one of them. */
			return SigMapPubKeyToSigBytes.EMPTY_SIG;
		}
	}
}
