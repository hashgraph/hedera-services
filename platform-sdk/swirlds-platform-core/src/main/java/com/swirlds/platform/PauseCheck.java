package com.swirlds.platform;

import static com.swirlds.logging.LogMarker.EXCEPTION;

import com.swirlds.common.utility.Startable;
import java.util.Timer;
import java.util.TimerTask;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

/**
 * This class periodically checks whether the java application has been paused for a long period.
 * If so, it will log an error message.
 */
public class PauseCheck implements Startable {

    private static final Logger logger = LogManager.getLogger(PauseCheck.class);

    /** alert threshold for java app pause */
    private static final long PAUSE_ALERT_INTERVAL = 5000;

    /** last time stamp when pause check timer is active */
    private long pauseCheckTimeStamp;

    public PauseCheck() {

    }

    /**
     * Start the pause check timer.
     */
    @Override
    public void start() {
        final Timer pauseCheckTimer = new Timer("pause check", true);
        pauseCheckTimer.schedule(
                new TimerTask() {
                    @Override
                    public void run() {
                        final long currentTimeStamp = System.currentTimeMillis();
                        if ((currentTimeStamp - pauseCheckTimeStamp) > PAUSE_ALERT_INTERVAL
                                && pauseCheckTimeStamp != 0) {
                            logger.error(
                                    EXCEPTION.getMarker(),
                                    "ERROR, a pause larger than {} is detected ",
                                    PAUSE_ALERT_INTERVAL);
                        }
                        pauseCheckTimeStamp = currentTimeStamp;
                    }
                },
                0,
                PAUSE_ALERT_INTERVAL / 2);
    }
}
