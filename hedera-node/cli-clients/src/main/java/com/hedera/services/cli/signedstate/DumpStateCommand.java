/*
 * Copyright (C) 2023-2024 Hedera Hashgraph, LLC
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
import java.util.EnumSet;
import java.util.Objects;
import java.util.Set;
import picocli.CommandLine;
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

    enum WithFeeSummary {
        NO,
        YES
    }

    enum WithMigration {
        NO,
        YES
    }

    enum WithValidation {
        NO,
        YES
    }

    enum OmitContents {
        NO,
        YES
    }

    enum Format {
        CSV,
        ELIDED_DEFAULT_FIELDS
    }

    enum KeyDetails {
        STRUCTURE,
        STRUCTURE_WITH_IDS,
        NONE
    }

    // We want to open the signed state file only once but run a bunch of dumps against it
    // (because it takes a long time to open the signed state file).  So we can specify
    // more than one of these subcommands on the single command line.  But we don't get
    // any hook at the beginning of the entire set of subcommands or after either.  So
    // that we have to track ourselves, via `init` and `finish` methods that _each_
    // subcommand is responsible for calling.

    @Command(name = "contract-bytecodes", description = "Dump all contract (runtime) bytecodes")
    void contractBytecodes(
            @Option(
                            names = {"-o", "--bytecode", "--contract-bytecode"},
                            required = true,
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
        System.out.println("=== contract bytecodes ===");
        DumpContractBytecodesSubcommand.doit(
                parent.signedState,
                bytecodePath,
                emitSummary ? EmitSummary.YES : EmitSummary.NO,
                uniqify ? Uniqify.YES : Uniqify.NO,
                withIds ? WithIds.YES : WithIds.NO,
                parent.verbosity);
        finish();
    }

    @Command(name = "contract-stores", description = "Dump all contract stores (all of their slots)")
    void contractStores(
            @Option(
                            names = {"-o", "--store"},
                            required = true,
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
                    final boolean withSlots,
            @Option(
                            names = {"--migrate"},
                            description =
                                    "migrate from mono-service representation to modular-service representation (before dump)")
                    final boolean withMigration,
            @Option(
                            names = {"--validate-migration"},
                            description = "validate the migrated contract store")
                    final boolean withValidationOfMigration) {
        Objects.requireNonNull(storePath);
        init();
        System.out.println("=== contract stores ===");
        DumpContractStoresSubcommand.doit(
                parent.signedState,
                storePath,
                emitSummary ? EmitSummary.YES : EmitSummary.NO,
                withSlots ? WithSlots.YES : WithSlots.NO,
                withMigration ? WithMigration.YES : WithMigration.NO,
                withValidationOfMigration ? WithValidation.YES : WithValidation.NO,
                parent.verbosity);
        finish();
    }

    @Command(name = "accounts", description = "Dump all accounts (EOA + smart contract)")
    void accounts(
            @Option(
                            names = {"--account"},
                            required = true,
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
                            names = {"--low"},
                            arity = "0..1",
                            defaultValue = "0",
                            description = "Lowest account number (inclusive) to dump")
                    int lowLimit,
            @Option(
                            names = {"--high"},
                            arity = "0..1",
                            defaultValue = "2147483647",
                            description = "highest account number (inclusive) to dump")
                    int highLimit,
            @Option(
                            names = {"--keys"},
                            arity = "0..*",
                            description = "How to dump key summaries")
                    final EnumSet<KeyDetails> keyDetails) {
        Objects.requireNonNull(accountPath);
        if (lowLimit > highLimit)
            throw new CommandLine.ParameterException(
                    parent.getSpec().commandLine(), "--highLimit must be >= --lowLimit");

        init();
        System.out.println("=== accounts ===");
        DumpAccountsSubcommand.doit(
                parent.signedState,
                accountPath,
                lowLimit,
                highLimit,
                null != keyDetails ? keyDetails : EnumSet.noneOf(KeyDetails.class),
                doCsv ? Format.CSV : Format.ELIDED_DEFAULT_FIELDS,
                parent.verbosity);
        finish();
    }

    @Command(name = "files", description = "Dump data files (no special files, no contracts)")
    void files(
            @Option(
                            names = {"--file"},
                            required = true,
                            arity = "1",
                            description = "Output file for files dump")
                    @NonNull
                    final Path filesPath,
            @Option(
                            names = {"--keys"},
                            arity = "0..*",
                            description = "How to dump key summaries")
                    final EnumSet<KeyDetails> keyDetails,
            @Option(
                            names = {"--omit-contents"},
                            description = "Omit the contents (bytes) of the file")
                    final boolean omitContents,
            @Option(
                            names = {"-s", "--summary"},
                            description = "Emit summary information")
                    final boolean emitSummary) {
        Objects.requireNonNull(filesPath);
        init();
        System.out.println("=== files ===");
        DumpFilesSubcommand.doit(
                parent.signedState,
                filesPath,
                null != keyDetails ? keyDetails : EnumSet.noneOf(KeyDetails.class),
                emitSummary ? EmitSummary.YES : EmitSummary.NO,
                omitContents ? OmitContents.YES : OmitContents.NO,
                parent.verbosity);
        finish();
    }

    @Command(name = "tokens", description = "Dump fungible token types")
    void tokens(
            @Option(
                            names = {"--token"},
                            required = true,
                            arity = "1",
                            description = "Output file for fungibles dump")
                    @NonNull
                    final Path tokensPath,
            @Option(
                            names = {"--keys"},
                            arity = "0..*",
                            description = "How to dump key summaries")
                    final EnumSet<KeyDetails> keyDetails,
            @Option(
                            names = {"--fees"},
                            description = "Emit a summary of the types of fees")
                    final boolean doFeeSummary,
            @Option(
                            names = {"-s", "--summary"},
                            description = "Emit summary information")
                    final boolean emitSummary) {
        Objects.requireNonNull(tokensPath);
        init();
        System.out.println("=== tokens ===");
        DumpTokensSubcommand.doit(
                parent.signedState,
                tokensPath,
                null != keyDetails ? keyDetails : EnumSet.noneOf(KeyDetails.class),
                doFeeSummary ? WithFeeSummary.YES : WithFeeSummary.NO,
                emitSummary ? EmitSummary.YES : EmitSummary.NO,
                parent.verbosity);
        finish();
    }

    @Command(name = "uniques", description = "Dump unique (serial-numbered) tokens")
    void uniques(
            @Option(
                            names = {"--unique"},
                            required = true,
                            arity = "1",
                            description = "Output file for unique tokens dump")
                    @NonNull
                    final Path uniquesPath,
            @Option(
                            names = {"-s", "--summary"},
                            description = "Emit a summary information")
                    final boolean emitSummary) {
        Objects.requireNonNull(uniquesPath);
        init();
        System.out.println("=== unique NFTs ===");
        DumpUniqueTokensSubcommand.doit(
                parent.signedState, uniquesPath, emitSummary ? EmitSummary.YES : EmitSummary.NO, parent.verbosity);
        finish();
    }

    @Command(name = "associations", description = "Dump token associations (tokenrels)")
    void associations(
            @Option(
                            names = {"--associations"},
                            required = true,
                            arity = "1",
                            description = "Output file for token associations dump")
                    @NonNull
                    final Path associationsPath,
            @Option(
                            names = {"-s", "--summary"},
                            description = "Emit summary information")
                    final boolean emitSummary) {
        Objects.requireNonNull(associationsPath);
        init();
        System.out.println("=== token associations ===");
        DumpTokenAssociationsSubcommand.doit(
                parent.signedState, associationsPath, emitSummary ? EmitSummary.YES : EmitSummary.NO, parent.verbosity);
        finish();
    }

    @Command(name = "block-info", description = "Dump block info")
    void blockInfo(
            @Option(
                            names = {"--block-info"},
                            required = true,
                            arity = "1",
                            description = "Output file for block info dump")
                    @NonNull
                    final Path blockInfoPath) {
        Objects.requireNonNull(blockInfoPath);
        init();
        System.out.println("=== Block info ===");
        DumpBlockInfoSubcommand.doit(parent.signedState, blockInfoPath);
        finish();
    }

    @Command(name = "staking-info", description = "Dump staking info")
    void stakingInfo(
            @Option(
                            names = {"--staking-info"},
                            required = true,
                            arity = "1",
                            description = "Output file for staking info dump")
                    @NonNull
                    final Path stakingInfoPath,
            @Option(
                            names = {"-s", "--summary"},
                            description = "Emit summary information")
                    final boolean emitSummary) {
        Objects.requireNonNull(stakingInfoPath);
        init();
        System.out.println("=== Staking info ===");
        DumpStakingInfoSubcommand.doit(
                parent.signedState, stakingInfoPath, emitSummary ? EmitSummary.YES : EmitSummary.NO, parent.verbosity);
        finish();
    }

    @Command(name = "staking-rewards", description = "Dump staking rewards")
    void stakingRewards(
            @Option(
                            names = {"--staking-rewards"},
                            required = true,
                            arity = "1",
                            description = "Output file for staking rewards dump")
                    @NonNull
                    final Path stakingRewardsPath) {
        Objects.requireNonNull(stakingRewardsPath);
        init();
        System.out.println("=== Staking rewards ===");
        DumpStakingRewardsSubcommand.doit(parent.signedState, stakingRewardsPath);
        finish();
    }

    @Command(name = "payer-records", description = "Dump payer records")
    void payerRecords(
            @Option(
                            names = {"--payer-records"},
                            required = true,
                            arity = "1",
                            description = "Output file for payer records dump")
                    @NonNull
                    final Path payerRecordsPath) {
        Objects.requireNonNull(payerRecordsPath);
        init();
        System.out.println("=== Payer records ===");
        DumpPayerRecordsSubcommand.doit(parent.signedState, payerRecordsPath);
        finish();
    }

    @Command(name = "congestion", description = "Dump congestion")
    void congestion(
            @Option(
                            names = {"--congestion"},
                            required = true,
                            arity = "1",
                            description = "Output file for congestion dump")
                    @NonNull
                    final Path congestionPath) {
        Objects.requireNonNull(congestionPath);
        init();
        System.out.println("=== Congestion ===");
        DumpCongestionSubcommand.doit(parent.signedState, congestionPath);
        finish();
    }

    @Command(name = "topics", description = "Dump topics")
    void topics(
            @Option(
                            names = {"--topic"},
                            required = true,
                            arity = "1",
                            description = "Output file for topics dump")
                    @NonNull
                    final Path topicsPath,
            @Option(
                            names = {"-s", "--summary"},
                            description = "Emit summary information")
                    final boolean emitSummary) {
        Objects.requireNonNull(topicsPath);
        init();
        System.out.println("=== Topics ===");
        DumpTopicsSubcommand.doit(
                parent.signedState, topicsPath, emitSummary ? EmitSummary.YES : EmitSummary.NO, parent.verbosity);
        finish();
    }

    @Command(name = "scheduled-transactions", description = "Dump scheduled transactions")
    void scheduledTransactions(
            @Option(
                            names = {"--scheduled-transaction"},
                            required = true,
                            arity = "1",
                            description = "Output file for scheduled transactions dump")
                    @NonNull
                    final Path scheduledTxsPath,
            @Option(
                            names = {"-s", "--summary"},
                            description = "Emit summary information")
                    final boolean emitSummary) {
        Objects.requireNonNull(scheduledTxsPath);
        init();
        System.out.println("=== scheduled transactions ===");
        DumpScheduledTransactionsSubcommand.doit(
                parent.signedState, scheduledTxsPath, emitSummary ? EmitSummary.YES : EmitSummary.NO, parent.verbosity);
        finish();
    }

    /** Setup to run a dump subcommand: If _first_ subcommand being run then open signed state file */
    void init() {
        if (thisSubcommandsNumber == null) {
            thisSubcommandsNumber = 0;
            subcommandsToRun = getSubcommandsToRun();
            parent.openSignedState();
        } else {
            thisSubcommandsNumber++;
        }
    }

    /** Cleanup after running a dump subcommand: If _last_ subcommand being run then close signed state file */
    void finish() {
        if (thisSubcommandsNumber == subcommandsToRun.size() - 1) {
            parent.closeSignedState();
        }
    }

    Integer thisSubcommandsNumber;
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
