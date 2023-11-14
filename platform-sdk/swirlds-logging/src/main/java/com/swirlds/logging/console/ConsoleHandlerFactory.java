package com.swirlds.logging.console;

import com.swirlds.config.api.Configuration;
import com.swirlds.logging.api.extensions.handler.LogHandler;
import com.swirlds.logging.api.extensions.handler.LogHandlerFactory;

public class ConsoleHandlerFactory implements LogHandlerFactory {

    @Override
    public LogHandler create (final Configuration configuration) {
        return new ConsoleHandler(configuration);
    }
}