package com.swirlds.logging.console;

import com.swirlds.config.api.Configuration;
import com.swirlds.logging.api.extensions.event.LogEvent;
import com.swirlds.logging.api.extensions.handler.AbstractSyncedHandler;
import com.swirlds.logging.api.internal.format.LineBasedFormat;

import java.io.PrintWriter;

public class ConsoleHandler extends AbstractSyncedHandler {

    private final LineBasedFormat lineBasedFormat;

    private final PrintWriter printWriter = new PrintWriter(System.out, true);

    public ConsoleHandler (final Configuration configuration) {
        super("console", configuration);
        lineBasedFormat = new LineBasedFormat(printWriter);
    }

    @Override
    protected void handleEvent (final LogEvent event) {
        lineBasedFormat.print(event);
    }

    @Override
    protected void handleStopAndFinalize() {
        super.handleStopAndFinalize();
        printWriter.close();
    }
}
