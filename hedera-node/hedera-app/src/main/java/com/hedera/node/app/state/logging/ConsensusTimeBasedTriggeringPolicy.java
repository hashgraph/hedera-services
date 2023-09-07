package com.hedera.node.app.state.logging;

import org.apache.logging.log4j.core.LogEvent;
import org.apache.logging.log4j.core.appender.rolling.RollingFileManager;
import org.apache.logging.log4j.core.appender.rolling.TriggeringPolicy;
import org.apache.logging.log4j.core.config.plugins.Plugin;
import org.apache.logging.log4j.core.config.plugins.PluginAttribute;
import org.apache.logging.log4j.core.config.plugins.PluginFactory;
import org.apache.logging.log4j.core.util.Integers;

/**
 * Triggering Policy that causes mandatory rollover at the end of every hour.
 */
@Plugin(name = "ConsensusTimeBasedTriggeringPolicy", category = "Core", printObject = true)
public class ConsensusTimeBasedTriggeringPolicy  implements TriggeringPolicy {
    private final int interval;

    private ConsensusTimeBasedTriggeringPolicy(final int interval) {
        this.interval = interval;
    }

    @Override
    public void initialize(RollingFileManager manager) {
        // Nothing to do here.
    }

    /**
     * Determine whether a rollover should occur.
     *
     * @param event A reference to the current event.
     * @return true if a rollover should occur.
     */
    @Override
    public boolean isTriggeringEvent(final LogEvent event) {
        System.out.println("ConsensusTimeBasedTriggeringPolicy.isTriggeringEvent --> "+event.getContextData().getValue("consensusTime"));
        if (event.getContextData().containsKey("consensusTime")) {
            long consensusTime = event.getContextData().getValue("consensusTime");
            return (consensusTime/1000) % interval == 0;
        }
        return false;
    }

    @Override
    public String toString() {
        return "ConsensusTimeBasedTriggeringPolicy";
    }

    /**
     * Create a ConsensusTimeBasedTriggeringPolicy
     *
     * @param interval The interval between rollover in ms of consensus time
     * @return a ConsensusTimeBasedTriggeringPolicy
     */
    @PluginFactory
    public static ConsensusTimeBasedTriggeringPolicy createPolicy(
            @PluginAttribute("interval") final String interval) {
        System.out.println("ConsensusTimeBasedTriggeringPolicy.createPolicy");
        System.out.println("interval = " + interval);
        final int parsedInterval = Integers.parseInt(interval, 10000);
        return new ConsensusTimeBasedTriggeringPolicy(parsedInterval);
    }
}