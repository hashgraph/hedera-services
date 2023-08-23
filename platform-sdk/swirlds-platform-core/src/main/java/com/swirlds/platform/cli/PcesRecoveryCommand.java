package com.swirlds.platform.cli;

import com.swirlds.cli.PlatformCli;
import com.swirlds.cli.utility.AbstractCommand;
import com.swirlds.cli.utility.SubcommandOf;
import com.swirlds.common.startup.CommandLineArgs;
import com.swirlds.common.system.NodeId;
import com.swirlds.platform.Browser;
import picocli.CommandLine;

import java.io.IOException;
import java.util.Set;

@CommandLine.Command(
		name = "pces-recovery",
		mixinStandardHelpOptions = true,
		description = "Start the platform in PCES recovery mode. The platform will replay events from disk, "
				+ "save a state, then shut down."
)
@SubcommandOf(PlatformCli.class)
public class PcesRecoveryCommand extends AbstractCommand {
	@CommandLine.Parameters(description = "The node ID to run in recovery mode", index = "0")
	private long nodeId;

	@Override
	public Integer call() throws IOException, InterruptedException {
		Browser.launch(new CommandLineArgs(Set.of(new NodeId(nodeId)), true));
		return null;
	}
}
