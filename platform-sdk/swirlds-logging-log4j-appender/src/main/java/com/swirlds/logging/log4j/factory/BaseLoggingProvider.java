package com.swirlds.logging.log4j.factory;

import com.google.auto.service.AutoService;
import com.swirlds.logging.api.Logger;
import com.swirlds.logging.api.Loggers;
import org.apache.logging.log4j.spi.Provider;

/**
 * This class is a factory registering the {@link BaseLoggerContextFactory} as the provider for the Log4J2 logging system.
 * <p>
 * This will allow the {@link BaseLoggerContextFactory} to be used as backend for the slf4j api
 */
@AutoService(Provider.class)
public class BaseLoggingProvider extends Provider {
    /**
     * This is a base logger to force the initialisation of the logging system.
     */
    private static final Logger logger = Loggers.getLogger(BaseLoggingProvider.class);

    /**
     * Creates a new logging provider and logs to force the initialisation of the base logging system
     */
    public BaseLoggingProvider() {
        super(15, "2.6.0", BaseLoggerContextFactory.class);
        logger.trace("Initialised the BaseLoggingProvider");
    }
}
