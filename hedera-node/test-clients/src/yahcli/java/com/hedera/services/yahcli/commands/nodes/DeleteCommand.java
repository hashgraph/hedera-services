package com.hedera.services.yahcli.commands.nodes;

import static com.hedera.services.yahcli.output.CommonMessages.COMMON_MESSAGES;
import static com.hedera.services.yahcli.suites.CreateSuite.NOVELTY;

import com.hedera.services.bdd.spec.HapiSpec;
import com.hedera.services.bdd.suites.HapiSuite;
import com.hedera.services.yahcli.commands.accounts.SendCommand;
import com.hedera.services.yahcli.config.ConfigUtils;
import com.hedera.services.yahcli.suites.CreateSuite;
import com.hedera.services.yahcli.suites.DeleteNodeSuite;
import java.io.File;
import java.util.concurrent.Callable;
import picocli.CommandLine;

@CommandLine.Command(
        name = "delete",
        subcommands = {CommandLine.HelpCommand.class},
        description = "Delete a node")
public class DeleteCommand implements Callable<Integer> {
    private static final int DEFAULT_NUM_RETRIES = 5;

    @CommandLine.ParentCommand
    NodesCommand nodesCommand;

    @CommandLine.Option(
            names = {"-n", "--nodeId"},
            paramLabel = "node Id for deletion")
    String nodeId;

    @CommandLine.Option(
            names = {"-r", "--retries"},
            paramLabel = "Number of times to retry on BUSY")
    Integer boxedRetries;

    @Override
    public Integer call() throws Exception {
        final var yahcli = nodesCommand.getYahcli();
        var config = ConfigUtils.configFrom(yahcli);

        final var noveltyLoc = config.keysLoc() + File.separator + NOVELTY + ".pem";
        final var effectiveNodeId = nodeId != null ? nodeId : "";
        final var retries = boxedRetries != null ? boxedRetries.intValue() : DEFAULT_NUM_RETRIES;

        final var delegate = new DeleteNodeSuite(
                config.asSpecConfig(), effectiveNodeId, noveltyLoc, retries);
        delegate.runSuiteSync();

        if (delegate.getFinalSpecs().get(0).getStatus() == HapiSpec.SpecStatus.PASSED) {
            COMMON_MESSAGES.info("SUCCESS - node Id "
                    + effectiveNodeId
                    + " has been deleted");
        } else {
            COMMON_MESSAGES.warn("FAILED to delete node Id "
                    + effectiveNodeId);
            return 1;
        }

        return 0;
    }

}
