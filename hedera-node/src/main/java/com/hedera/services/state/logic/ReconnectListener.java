package com.hedera.services.state.logic;

import com.hedera.services.ServicesState;
import com.hedera.services.stream.RecordStreamManager;
import com.swirlds.common.notification.listeners.ReconnectCompleteListener;
import com.swirlds.common.notification.listeners.ReconnectCompleteNotification;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import javax.inject.Inject;
import javax.inject.Singleton;

@Singleton
public class ReconnectListener implements ReconnectCompleteListener {
	private static final Logger log = LogManager.getLogger(ReconnectListener.class);

	private final RecordStreamManager recordStreamManager;

	@Inject
	public ReconnectListener(RecordStreamManager recordStreamManager) {
		this.recordStreamManager = recordStreamManager;
	}

	@Override
	public void notify(ReconnectCompleteNotification notification) {
		log.info(
				"Notification Received: Reconnect Finished. " +
						"consensusTimestamp: {}, roundNumber: {}, sequence: {}",
				notification.getConsensusTimestamp(),
				notification.getRoundNumber(),
				notification.getSequence());
		ServicesState state = (ServicesState) notification.getState();
		state.logSummary();
		recordStreamManager.setStartWriteAtCompleteWindow(true);
	}
}
