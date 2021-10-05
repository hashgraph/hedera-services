package com.hedera.services.txns.contract.operation;

import com.hedera.services.store.contracts.HederaWorldState;
import com.hedera.services.store.contracts.HederaWorldUpdater;
import org.hyperledger.besu.evm.frame.MessageFrame;

import java.util.Iterator;
import java.util.Optional;

public class HederaOperationUtil {

	public static long getExpiry(MessageFrame frame) {
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
