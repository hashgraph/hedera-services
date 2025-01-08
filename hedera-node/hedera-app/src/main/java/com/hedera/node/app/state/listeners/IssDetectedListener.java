package com.hedera.node.app.state.listeners;

import com.swirlds.platform.system.state.notifications.AsyncIssListener;
import com.swirlds.platform.system.state.notifications.IssNotification;
import javax.inject.Inject;
import javax.inject.Singleton;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

@Singleton
public class IssDetectedListener implements AsyncIssListener {

    private static final Logger log = LogManager.getLogger(IssDetectedListener.class);

    @Inject
    public IssDetectedListener()  {
        // no-op
    }

    @Override
    public void notify(final IssNotification data) {
        log.warn("ISS detected (type={}, round={})", data.getIssType(), data.getRound());
    }
}
