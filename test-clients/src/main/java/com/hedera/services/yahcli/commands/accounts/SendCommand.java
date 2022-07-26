/*
 * Copyright (C) 2021-2022 Hedera Hashgraph, LLC
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *    http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.hedera.services.yahcli.commands.accounts;

import static com.hedera.services.bdd.spec.HapiApiSpec.SpecStatus.PASSED;
import static com.hedera.services.yahcli.config.ConfigUtils.configFrom;
import static com.hedera.services.yahcli.output.CommonMessages.COMMON_MESSAGES;

import com.hedera.services.yahcli.Yahcli;
import com.hedera.services.yahcli.suites.SendSuite;
import java.util.concurrent.Callable;
import picocli.CommandLine;
import picocli.CommandLine.Command;
import picocli.CommandLine.HelpCommand;
import picocli.CommandLine.ParentCommand;

@Command(
        name = "send",
        subcommands = {HelpCommand.class},
        description = "Transfers funds from the payer to a target account")
public class SendCommand implements Callable<Integer> {
    private static final long TINYBARS_PER_HBAR = 100_000_000L;
    private static final long TINYBARS_PER_KILOBAR = 1_000 * TINYBARS_PER_HBAR;

    @ParentCommand AccountsCommand accountsCommand;

    @CommandLine.Option(
            names = {"--to"},
            paramLabel = "<beneficiary>",
            description = "account to receive the funds")
    String beneficiary;

    @CommandLine.Option(
            names = {"--memo"},
            paramLabel = "<memo>",
            description = "memo to use for the CryptoTransfer")
    String memo;

    @CommandLine.Parameters(
            paramLabel = "<amount_to_send>",
            description = "how many units of the denomination to send")
    String amountRepr;

    @CommandLine.Option(
            names = {"-d", "--denomination"},
            paramLabel = "denomination",
            description = "{ tinybar | hbar | kilobar }",
            defaultValue = "hbar")
    String denomination;

    @Override
    public Integer call() throws Exception {
        var config = configFrom(accountsCommand.getYahcli());

        long amount = validatedTinybars(accountsCommand.getYahcli(), amountRepr, denomination);
        final var effectiveMemo = memo != null ? memo : "";
        var delegate = new SendSuite(config.asSpecConfig(), beneficiary, amount, effectiveMemo);
        delegate.runSuiteSync();

        if (delegate.getFinalSpecs().get(0).getStatus() == PASSED) {
            COMMON_MESSAGES.info(
                    "SUCCESS - "
                            + "sent "
                            + amountRepr
                            + " "
                            + denomination
                            + " to account "
                            + beneficiary
                            + " with memo: '"
                            + memo
                            + "'");
        } else {
            COMMON_MESSAGES.info(
                    "FAILED - "
                            + "could not send "
                            + amountRepr
                            + " "
                            + denomination
                            + " to account "
                            + beneficiary
                            + " with memo: '"
                            + memo
                            + "'");
            return 1;
        }

        return 0;
    }

    public static long validatedTinybars(
            final Yahcli yahcli, final String amountRepr, final String denomination) {
        final var amount = Long.parseLong(amountRepr.replaceAll("_", ""));
        return switch (denomination) {
            default -> throw new CommandLine.ParameterException(
                    yahcli.getSpec().commandLine(),
                    "Denomination must be one of { tinybar | hbar | kilobar }");
            case "tinybar" -> amount;
            case "hbar" -> amount * TINYBARS_PER_HBAR;
            case "kilobar" -> amount * TINYBARS_PER_KILOBAR;
        };
    }
}
