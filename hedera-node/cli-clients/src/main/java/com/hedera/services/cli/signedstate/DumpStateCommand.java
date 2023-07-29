/*
 * Copyright (C) 2023 Hedera Hashgraph, LLC
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

package com.hedera.services.cli.signedstate;

import com.swirlds.cli.PlatformCli;
import com.swirlds.cli.utility.AbstractCommand;
import com.swirlds.cli.utility.SubcommandOf;
import edu.umd.cs.findbugs.annotations.NonNull;
import java.nio.file.Path;
import java.util.Objects;
import java.util.Set;
import picocli.CommandLine.Command;
import picocli.CommandLine.Option;
import picocli.CommandLine.ParentCommand;

@SuppressWarnings("java:S106") // "use of system.out/system.err instead of logger" - not needed/desirable for CLI tool
@Command(
        name = "dump",
        subcommandsRepeatable = true,
        mixinStandardHelpOptions = true,
        description = "Dump state from signed state file (to stdout)")
@SubcommandOf(SignedStateCommand.class)
public class DumpStateCommand extends AbstractCommand {

    @ParentCommand
    SignedStateCommand parent;

    enum EmitSummary {
        NO,
        YES
    }

    enum Uniqify {
        NO,
        YES
    }

    enum WithIds {
        NO,
        YES
    }

    enum WithSlots {
        NO,
        YES
    }

    enum Format {
        CSV,
        ELIDED_DEFAULT_FIELDS
    }

    // We want to open the signed state file only once but run a bunch of dumps against it
    // (because it takes a long time to open the signed state file).  So we can specify
    // more than one of these subcommands on the single command line.  But we don't get
    // any hook at the beginning of the entire set of subcommands or after either.  So
    // that we have to track ourselves, via `init` and `finish` methods that _each_
    // subcommand is responsible for calling.

    @Command(name = "contract-bytecodes")
    void contractBytecodes(
            @Option(
                            names = {"-o", "--bytecode", "--contract-bytecode"},
                            arity = "1",
                            description = "Output file for contracts bytecode dump")
                    @NonNull
                    final Path bytecodePath,
            @Option(
                            names = {"-s", "--summary"},
                            description = "Emit a summary line")
                    final boolean emitSummary,
            @Option(
                            names = {"-u", "--unique"},
                            description = "Emit each unique contract bytecode only once")
                    final boolean uniqify,
            @Option(
                            names = {"-#", "--with-ids"},
                            description = "Emit contract ids")
                    final boolean withIds) {
        Objects.requireNonNull(bytecodePath);
        init();
        System.out.println("=== bytecodes ===");
        DumpContractBytecodesSubcommand.doit(
                parent.signedState,
                bytecodePath,
                emitSummary ? EmitSummary.YES : EmitSummary.NO,
                uniqify ? Uniqify.YES : Uniqify.NO,
                withIds ? WithIds.YES : WithIds.NO,
                parent.verbosity);
        finish();
    }

    @Command(name = "contract-stores")
    void contractStores(
            @Option(
                            names = {"-o", "--contract-store"},
                            arity = "1",
                            description = "Output file for contracts store dump")
                    @NonNull
                    final Path storePath,
            @Option(
                            names = {"-s", "--summary"},
                            description = "Emit a summary line")
                    final boolean emitSummary,
            @Option(
                            names = {"-k", "--slots"},
                            description = "Emit the slot/value pairs for each contract's store")
                    final boolean withSlots) {
        Objects.requireNonNull(storePath);
        init();
        System.out.println("=== stores ===");
        DumpContractStoresSubcommand.doit(
                parent.signedState,
                storePath,
                emitSummary ? EmitSummary.YES : EmitSummary.NO,
                withSlots ? WithSlots.YES : WithSlots.NO,
                parent.verbosity);
        finish();
    }

    @Command(name = "accounts")
    void accounts(
            @Option(
                            names = {"--account"},
                            arity = "1",
                            description = "Output file for accounts dump")
                    @NonNull
                    final Path accountPath,
            @Option(
                            names = {"--csv"},
                            arity = "0..1",
                            description = "If present output is in pure csv form")
                    final boolean doCsv,
            @Option(
                            names = {"--limit"},
                            arity = "0..1",
                            defaultValue = "-1",
                            description = "Limit #of accounts processed (default: all)")
                    int limit) {
        Objects.requireNonNull(accountPath);
        init();
        System.out.println("=== accounts ===");
        DumpAccountsSubcommand.doit(
                parent.signedState,
                accountPath,
                limit,
                doCsv ? Format.CSV : Format.ELIDED_DEFAULT_FIELDS,
                parent.verbosity);
        finish();
    }

    /** Setup to run a dump subcommand: If _first_ subcommand being run then open signed state file */
    void init() {
        if (thisSubcommandisNumber == null) {
            thisSubcommandisNumber = 0;
            subcommandsToRun = getSubcommandsToRun();
            parent.openSignedState();
        } else {
            thisSubcommandisNumber++;
        }
    }

    /** Cleanup after running a dump subcommand: If _last_ subcommand being run then close signed state file */
    void finish() {
        if (thisSubcommandisNumber == subcommandsToRun.size() - 1) {
            parent.closeSignedState();
        }
    }

    Integer thisSubcommandisNumber;
    Set<String> subcommandsToRun;

    /** We need to find out how many subcommands we're going to run, so that we can manage the signed state file.
     * Here we grovel around in picocli's parsed command line to find that information, returning the set of subcommand
     * names.
     *
     * Rather fragile if the subcommands are ever reorganized ... but maybe that won't happen soon ...
     */
    @SuppressWarnings("java:S1488") // "immediately return expr" - Sonar doesn't understand value of parallel constructs
    @NonNull
    Set<String> getSubcommandsToRun() {
        final SignedStateCommand signedStateCommand = this.parent;
        final PlatformCli platformCli = signedStateCommand.parent;
        final var pcliCommandSpec = platformCli.getSpec();
        final var signedStateCommandLine = pcliCommandSpec.subcommands().get("signed-state");
        final var dumpCommandLine = signedStateCommandLine.getSubcommands().get("dump");
        final var dumpSubcommands = dumpCommandLine.getSubcommands().keySet();
        return dumpSubcommands;
    }

    private DumpStateCommand() {}
}
