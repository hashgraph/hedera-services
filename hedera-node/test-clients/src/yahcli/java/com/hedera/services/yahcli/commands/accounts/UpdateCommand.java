// SPDX-License-Identifier: Apache-2.0
package com.hedera.services.yahcli.commands.accounts;

import static com.hedera.services.yahcli.output.CommonMessages.COMMON_MESSAGES;

import com.google.common.collect.Lists;
import com.google.common.io.Files;
import com.google.protobuf.ByteString;
import com.hedera.services.bdd.spec.HapiSpec;
import com.hedera.services.yahcli.config.ConfigUtils;
import com.hedera.services.yahcli.suites.UpdateSuite;
import com.hederahashgraph.api.proto.java.Key;
import com.swirlds.common.utility.CommonUtils;
import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.concurrent.Callable;
import picocli.CommandLine;
import picocli.CommandLine.Command;
import picocli.CommandLine.HelpCommand;
import picocli.CommandLine.ParentCommand;

@Command(
        name = "update",
        subcommands = {HelpCommand.class},
        description = "scheduling update account key list option")
public class UpdateCommand implements Callable<Integer> {

    @ParentCommand
    AccountsCommand accountsCommand;

    @CommandLine.Option(
            names = {"--pathKeys"},
            paramLabel = "<pathKeys>",
            description = "update path keys for the account")
    String pathKeys;

    @CommandLine.Option(
            names = {"--memo"},
            paramLabel = "<memo>",
            description = "memo to use for the update command")
    String memo;

    @CommandLine.Option(
            names = {"--targetAccount"},
            paramLabel = "<targetAccount>",
            description = "target account to update")
    String targetAccount;

    @Override
    public Integer call() throws Exception {
        var config = ConfigUtils.configFrom(accountsCommand.getYahcli());

        final var effectiveMemo = memo != null ? memo : "";
        final var effectiveTargetAccount = targetAccount != null ? targetAccount : "";
        final var keysPath = pathKeys != null ? pathKeys : "";

        final var effectivePublicKeys = unHexListOfKeys(readPublicKeyFromFile(keysPath));

        if (!accountsCommand.getYahcli().isScheduled()) {
            throw new CommandLine.PicocliException("you have to schedule the update command");
        }

        final var delegate = new UpdateSuite(
                config.asSpecConfig(),
                effectiveMemo,
                effectivePublicKeys,
                effectiveTargetAccount,
                accountsCommand.getYahcli().isScheduled());
        delegate.runSuiteSync();

        if (delegate.getFinalSpecs().get(0).getStatus() == HapiSpec.SpecStatus.PASSED) {
            COMMON_MESSAGES.info("SUCCESS - "
                    + "Scheduled update account "
                    + effectiveTargetAccount
                    + " keys "
                    + effectivePublicKeys
                    + " with memo: '"
                    + memo
                    + "'");
        } else {
            COMMON_MESSAGES.warn("FAILED - "
                    + "Schedule update account "
                    + effectiveTargetAccount
                    + " keys "
                    + effectivePublicKeys
                    + " with memo: '"
                    + memo
                    + "'");
            return 1;
        }

        return 0;
    }

    private List<String> readPublicKeyFromFile(final String fileKeyPath) throws IOException {
        File file = new File(fileKeyPath);
        return Files.readLines(file, StandardCharsets.UTF_8);
    }

    private List<Key> unHexListOfKeys(final List<String> hexedKeys) {
        List<Key> unHexedKeys = Lists.newArrayList();
        for (String hexedKey : hexedKeys) {
            ByteString byteString = ByteString.copyFrom(CommonUtils.unhex(hexedKey));
            Key key = Key.newBuilder().setEd25519(byteString).build();
            unHexedKeys.add(key);
        }
        return unHexedKeys;
    }
}
