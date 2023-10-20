package com.swirlds.platform.cli;

import com.swirlds.cli.commands.EventStreamCommand;
import com.swirlds.cli.utility.AbstractCommand;
import com.swirlds.cli.utility.SubcommandOf;
import com.swirlds.platform.event.EventStreamComparer;
import picocli.CommandLine;

@CommandLine.Command(
        name = "compare",
        mixinStandardHelpOptions = true,
        description = "blah blah this is not going to merge")
@SubcommandOf(EventStreamCommand.class)
public class EventStreamCompareCommand extends AbstractCommand {

    @Override
    public Integer call() throws Exception {
        EventStreamComparer.compare();
        return 0;
    }
}
