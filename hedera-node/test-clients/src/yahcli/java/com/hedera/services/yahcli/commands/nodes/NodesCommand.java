package com.hedera.services.yahcli.commands.nodes;

import com.hedera.services.yahcli.Yahcli;
import java.util.concurrent.Callable;
import picocli.CommandLine;
import picocli.CommandLine.ParentCommand;

    @CommandLine.Command(
            name = "nodes",
            subcommands = {
                    UpdateCommand.class,
                    CreateCommand.class,
                    DeleteCommand.class
            },
            description = "Performs nodes operations")
    public class NodesCommand implements Callable<Integer> {
        @ParentCommand
        Yahcli yahcli;

        @Override
        public Integer call() throws Exception {
            throw new CommandLine.ParameterException(
                    yahcli.getSpec().commandLine(), "Please specify an nodes subcommand!");
        }

        public Yahcli getYahcli() {
            return yahcli;
        }
}
