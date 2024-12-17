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

package com.hedera.services.yahcli;

import com.hedera.services.yahcli.commands.accounts.AccountsCommand;
import com.hedera.services.yahcli.commands.accounts.SetupStakeCommand;
import com.hedera.services.yahcli.commands.fees.FeesCommand;
import com.hedera.services.yahcli.commands.files.SysFilesCommand;
import com.hedera.services.yahcli.commands.keys.KeysCommand;
import com.hedera.services.yahcli.commands.nodes.NodesCommand;
import com.hedera.services.yahcli.commands.schedules.ScheduleCommand;
import com.hedera.services.yahcli.commands.system.FreezeAbortCommand;
import com.hedera.services.yahcli.commands.system.FreezeOnlyCommand;
import com.hedera.services.yahcli.commands.system.FreezeUpgradeCommand;
import com.hedera.services.yahcli.commands.system.PrepareUpgradeCommand;
import com.hedera.services.yahcli.commands.system.TelemetryUpgradeCommand;
import com.hedera.services.yahcli.commands.system.VersionInfoCommand;
import java.util.concurrent.Callable;
import org.apache.logging.log4j.Level;
import picocli.CommandLine;
import picocli.CommandLine.Command;
import picocli.CommandLine.HelpCommand;
import picocli.CommandLine.Model.CommandSpec;
import picocli.CommandLine.Option;
import picocli.CommandLine.ParameterException;
import picocli.CommandLine.Spec;

@Command(
        name = "yahcli",
        subcommands = {
            HelpCommand.class,
            KeysCommand.class,
            AccountsCommand.class,
            ScheduleCommand.class,
            SysFilesCommand.class,
            FeesCommand.class,
            FreezeAbortCommand.class,
            FreezeOnlyCommand.class,
            PrepareUpgradeCommand.class,
            FreezeUpgradeCommand.class,
            TelemetryUpgradeCommand.class,
            VersionInfoCommand.class,
            SetupStakeCommand.class,
            NodesCommand.class
        },
        description = "Performs DevOps-type actions against a Hedera Services network")
public class Yahcli implements Callable<Integer> {
    public static final long NO_FIXED_FEE = Long.MIN_VALUE;
    public static final String DEFAULT_LOG_LEVEL = "WARN";

    @Spec
    CommandSpec spec;

    @Option(
            names = {"-f", "--fixed-fee"},
            paramLabel = "fee",
            defaultValue = "" + NO_FIXED_FEE)
    Long fixedFee;

    @Option(
            names = {"-n", "--network"},
            paramLabel = "network")
    String net;

    @Option(
            names = {"-a", "--node-account"},
            paramLabel = "node account")
    String nodeAccount;

    @Option(
            names = {"-i", "--node-ip"},
            paramLabel = "node IPv4 address")
    String nodeIpv4Addr;

    @Option(
            names = {"-p", "--payer"},
            paramLabel = "payer")
    String payer;

    @Option(
            names = {"-s", "--schedule"},
            paramLabel = "schedule",
            description = "true if the transaction should be scheduled, false otherwise")
    boolean schedule;

    @Option(
            names = {"-c", "--config"},
            paramLabel = "config YAML",
            defaultValue = "config.yml")
    String configLoc;

    @Option(
            names = {"-v", "--verbose"},
            paramLabel = "log level",
            description = "one of : WARN, INFO and DEBUG",
            defaultValue = DEFAULT_LOG_LEVEL)
    String loglevel;

    @Override
    public Integer call() throws Exception {
        throw new ParameterException(spec.commandLine(), "Please specify a subcommand!");
    }

    public static void main(String... args) {
        int rc = new CommandLine(new Yahcli()).execute(args);
        System.exit(rc);
    }

    public String getNet() {
        return net;
    }

    public String getPayer() {
        return payer;
    }

    public boolean isScheduled() {
        return schedule;
    }

    public String getConfigLoc() {
        return configLoc;
    }

    public CommandSpec getSpec() {
        return spec;
    }

    public Long getFixedFee() {
        return fixedFee;
    }

    public String getNodeAccount() {
        return nodeAccount == null ? nodeAccount : ("0.0." + nodeAccount);
    }

    public Level getLogLevel() {
        Level level = Level.getLevel(loglevel);
        return level == null ? Level.WARN : level;
    }

    public String getNodeIpv4Addr() {
        return nodeIpv4Addr;
    }
}
