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

import com.hedera.services.utils.TxnAccessor;
import com.hederahashgraph.api.proto.java.Transaction;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

public class ScopedSigBytesProvider implements PubKeyToSigBytesProvider {
	public static Logger log = LogManager.getLogger(ScopedSigBytesProvider.class);

	final PubKeyToSigBytes delegate;

	public ScopedSigBytesProvider(TxnAccessor accessor) {
		delegate = new SigMapPubKeyToSigBytes(accessor.getSigMap());
	}

	@Override
	public PubKeyToSigBytes payerSigBytesFor(Transaction ignore) {
		return delegate;
	}

	@Override
	public PubKeyToSigBytes otherPartiesSigBytesFor(Transaction ignore) {
		return delegate;
	}

	@Override
	public PubKeyToSigBytes allPartiesSigBytesFor(Transaction ignore) {
		return delegate;
	}
}
