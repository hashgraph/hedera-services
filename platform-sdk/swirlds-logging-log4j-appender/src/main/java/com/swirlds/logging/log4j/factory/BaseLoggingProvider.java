package com.swirlds.logging.log4j.factory;

import com.google.auto.service.AutoService;
import org.apache.logging.log4j.spi.Provider;

@AutoService(Provider.class)
public class BaseLoggingProvider extends Provider {
    public BaseLoggingProvider() {
        super(15, "2.6.0", BaseLoggerContextFactory.class);
    }
}
