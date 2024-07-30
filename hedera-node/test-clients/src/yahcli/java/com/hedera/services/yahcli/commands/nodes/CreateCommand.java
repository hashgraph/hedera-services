package com.hedera.services.yahcli.commands.nodes;

import com.hedera.services.yahcli.Yahcli;
import com.hedera.services.yahcli.commands.accounts.BalanceCommand;
import com.hedera.services.yahcli.commands.accounts.InfoCommand;
import com.hedera.services.yahcli.commands.accounts.RekeyCommand;
import com.hedera.services.yahcli.commands.accounts.SendCommand;
import com.hedera.services.yahcli.commands.accounts.StakeCommand;
import com.hedera.services.yahcli.commands.accounts.UpdateCommand;
import java.util.concurrent.Callable;
import picocli.CommandLine;
import picocli.CommandLine.HelpCommand;
import picocli.CommandLine.ParentCommand;

@CommandLine.Command(
        name = "create",
        subcommands = {CommandLine.HelpCommand.class},
        description = "Creates a new node")
public class CreateCommand {

    public class AccountsCommand implements Callable<Integer> {
    }
}
