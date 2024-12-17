/*
 * Copyright (C) 2021-2024 Hedera Hashgraph, LLC
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.hedera.services.yahcli.commands.accounts;

import static com.hedera.services.yahcli.output.CommonMessages.COMMON_MESSAGES;

import com.hedera.services.bdd.spec.HapiSpec;
import com.hedera.services.yahcli.Yahcli;
import com.hedera.services.yahcli.config.ConfigUtils;
import com.hedera.services.yahcli.suites.SendSuite;
import com.hedera.services.yahcli.suites.Utils;
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

    @ParentCommand
    AccountsCommand accountsCommand;

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

    @CommandLine.Parameters(paramLabel = "<amount_to_send>", description = "how many units of the denomination to send")
    String amountRepr;

    @CommandLine.Option(
            names = {"-d", "--denomination"},
            paramLabel = "denomination",
            description = "{ tinybar | hbar | kilobar | <HTS token num> }",
            defaultValue = "hbar")
    String denomination;

    @CommandLine.Option(
            names = {"--decimals"},
            paramLabel = "<decimals>",
            defaultValue = "6",
            description = "for an HTS token denomination, the number of decimals")
    Integer decimals;

    @Override
    public Integer call() throws Exception {
        var config = ConfigUtils.configFrom(accountsCommand.getYahcli());

        if (!config.isAllowListEmptyOrContainsAccount(Long.parseLong(beneficiary))) {
            throw new CommandLine.ParameterException(
                    accountsCommand.getYahcli().getSpec().commandLine(),
                    "Beneficiary " + beneficiary + " supposed to be in allow list");
        }

        long amount;
        String originalDenomination = denomination;
        if (isHbarDenomination(denomination)) {
            amount = validatedTinybars(accountsCommand.getYahcli(), amountRepr, denomination);
            denomination = null;
        } else {
            denomination = Utils.extractAccount(denomination);
            amount = validatedUnits(amountRepr, decimals);
        }
        final var effectiveMemo = memo != null ? memo : "";
        var delegate = new SendSuite(
                config.asSpecConfig(),
                beneficiary,
                amount,
                effectiveMemo,
                denomination,
                accountsCommand.getYahcli().isScheduled());
        delegate.runSuiteSync();

        if (delegate.getFinalSpecs().get(0).getStatus() == HapiSpec.SpecStatus.PASSED) {
            COMMON_MESSAGES.info("SUCCESS - "
                    + "sent "
                    + amountRepr
                    + " "
                    + originalDenomination
                    + " to account "
                    + beneficiary
                    + " with memo: '"
                    + memo
                    + "'");
        } else {
            COMMON_MESSAGES.info("FAILED - "
                    + "could not send "
                    + amountRepr
                    + " "
                    + originalDenomination
                    + " to account "
                    + beneficiary
                    + " with memo: '"
                    + memo
                    + "'");
            return 1;
        }

        return 0;
    }

    private boolean isHbarDenomination(final String denomination) {
        return "tinybar".equals(denomination) || "hbar".equals(denomination) || "kilobar".equals(denomination);
    }

    public static long validatedTinybars(final Yahcli yahcli, final String amountRepr, final String denomination) {
        final var amount = Long.parseLong(amountRepr.replaceAll("_", ""));
        return switch (denomination) {
            default -> throw new CommandLine.ParameterException(
                    yahcli.getSpec().commandLine(), "Denomination must be one of { tinybar | hbar | kilobar }");
            case "tinybar" -> amount;
            case "hbar" -> amount * TINYBARS_PER_HBAR;
            case "kilobar" -> amount * TINYBARS_PER_KILOBAR;
        };
    }

    public static long validatedUnits(final String amountRepr, Integer decimals) {
        long integral;
        if (amountRepr.contains(".")) {
            final var parts = amountRepr.split("[.]");
            integral = Long.parseLong(parts[0]);
            var n = decimals;
            while (n-- > 0) {
                integral *= 10;
            }
            if (parts.length > 1) {
                var rightPadded = parts[1];
                while (rightPadded.length() < decimals) {
                    rightPadded += "0";
                }
                long m = 0;
                for (var c : rightPadded.toCharArray()) {
                    final var v = Long.parseLong("" + c);
                    m *= 10;
                    m += v;
                }
                integral += m;
            }
        } else {
            integral = Long.parseLong(amountRepr);
            while (decimals-- > 0) {
                integral *= 10;
            }
            return integral;
        }
        return integral;
    }
}
