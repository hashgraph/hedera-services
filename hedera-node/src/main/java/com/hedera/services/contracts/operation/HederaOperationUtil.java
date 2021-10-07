package com.hedera.services.contracts.operation;

/*
 * -
 * ‌
 * Hedera Services Node
 * ​
 * Copyright (C) 2018 - 2021 Hedera Hashgraph, LLC
 * ​
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *       http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 * ‍
 *
 */

import com.hedera.services.store.contracts.HederaWorldState;
import com.hedera.services.store.contracts.HederaWorldUpdater;
import org.hyperledger.besu.evm.frame.MessageFrame;

import java.util.Iterator;
import java.util.Optional;

/**
 * Utility methods used by Hedera adapted {@link org.hyperledger.besu.evm.operation.Operation}
 */
public final class HederaOperationUtil {

	private HederaOperationUtil() {
		throw new UnsupportedOperationException("Utility Class");
	}

	/**
	 * Returns the expiry to be used for a new contract. Climbs the {@link MessageFrame} and searches for the parent
	 * {@link com.hedera.services.store.contracts.HederaWorldState.WorldStateAccount}. The expiry to be used is
	 * the expiry of the first account found in the stack
	 * @param frame Current message frame
	 * @return Expiry to be used for new contracts
	 */
	public static long computeExpiryForNewContract(MessageFrame frame) {
		long expiry = 0;
		HederaWorldState.WorldStateAccount hederaAccount;
		Iterator<MessageFrame> framesIterator = frame.getMessageFrameStack().iterator();
		MessageFrame messageFrame;
		while (framesIterator.hasNext()) {
			messageFrame = framesIterator.next();
			/* if this is the initial frame from the deque, check context vars first */
			if (!framesIterator.hasNext()) {
				Optional<Long> expiryOptional = messageFrame.getContextVariable("expiry");
				if (expiryOptional.isPresent()) {
					expiry = expiryOptional.get();
					break;
				}
			}
			/* check if this messageFrame's sender account can be retrieved from state */
			hederaAccount = ((HederaWorldUpdater) messageFrame.getWorldUpdater()).getHederaAccount(frame.getSenderAddress());
			if (hederaAccount != null) {
				expiry = hederaAccount.getExpiry();
				break;
			}
		}
		return expiry;
	}
}
