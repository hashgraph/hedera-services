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

package com.hedera.services.cli.contracts;

import com.hedera.services.cli.utils.HexToBytesConverter;
import com.hedera.services.cli.utils.HexToBytesConverter.Bytes;
import com.swirlds.cli.PlatformCli;
import com.swirlds.cli.utility.AbstractCommand;
import com.swirlds.cli.utility.SubcommandOf;
import edu.umd.cs.findbugs.annotations.NonNull;
import edu.umd.cs.findbugs.annotations.Nullable;
import java.util.EnumSet;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import picocli.CommandLine;
import picocli.CommandLine.Command;
import picocli.CommandLine.Option;
import picocli.CommandLine.ParentCommand;

/** A subcommand of the `PlatformCLI`, for dealing with contract analysis */
@SuppressWarnings({"java:S106"}) // "use of system.out/system.err instead of logger" - not needed/desirable for CLI tool
@Command(name = "contract", mixinStandardHelpOptions = true, description = "Operations on contracts.")
@SubcommandOf(PlatformCli.class)
public class ContractCommand extends AbstractCommand {

    public static String REPORT_SEPARATOR = "===";

    @ParentCommand
    PlatformCli parent;

    enum Verbosity {
        SILENT,
        VERBOSE
    }

    @CommandLine.Option(
            names = {"-v", "--verbose"},
            arity = "0..1",
            defaultValue = "false",
            description = "Verbosity of command")
    private void setVerbosity(final boolean doVerbose) {
        verbosity = doVerbose ? Verbosity.VERBOSE : Verbosity.SILENT;
    }

    Verbosity verbosity;

    @SuppressWarnings("java:S115") // "Constant names should comply with a naming convention" - enum names used
    // directly as command line options by picocli
    enum ShortOption {
        b(DisassemblyOption.WITH_BYTECODE),
        d(DisassemblyOption.DO_NOT_DECODE_BEFORE_METADATA),
        f(DisassemblyOption.DISPLAY_CODE_OFFSET),
        l(DisassemblyOption.FETCH_SELECTOR_NAMES),
        m(DisassemblyOption.LIST_MACROS),
        p(DisassemblyOption.DISPLAY_OPCODE_HEX),
        q(DisassemblyOption.RECOGNIZE_CODE_SEQUENCES),
        r(DisassemblyOption.WITH_RAW_DISASSEMBLY),
        s(DisassemblyOption.DISPLAY_SELECTORS),
        t(DisassemblyOption.TRACE_RECOGNIZERS),
        x(DisassemblyOption.WITH_METRICS);

        ShortOption(@NonNull DisassemblyOption option) {
            this.option = option;
        }

        @NonNull
        DisassemblyOption option() {
            return option;
        }

        final DisassemblyOption option;
    }

    @Command(name = "disassemble", mixinStandardHelpOptions = true, description = "Disassembles contract bytecodes")
    void disassemble(
            @Option(
                            names = {"-i", "--id"},
                            paramLabel = "CONTRACT_ID",
                            description = "Contract Id (decimal, optional)")
                    @NonNull
                    Optional<Integer> theContractId,
            @Option(
                            names = {"-b", "--bytecode"},
                            required = true,
                            arity = "1",
                            converter = HexToBytesConverter.class,
                            paramLabel = "HEX",
                            description = "Contract bytecode as a hex string")
                    @Nullable
                    Bytes theContract,
            @Option(
                            names = {"-o", "--option", "--options"},
                            split = ",",
                            description = {
                                "different output options:",
                                "b    with contract bytecode header",
                                "d    do not decode before metadata",
                                "f    with code offsets in disassembly",
                                "l    with selector lookups (from internet) (requires q)",
                                "m    list macros",
                                "p    with opcode (in hex)",
                                "q    recognize and analyze code sequences",
                                "r    list raw disassembly (before analyzed disassembly",
                                "s    list selectors",
                                "t    dump trace of recognizers",
                                "x    with metrics"
                            })
                    @Nullable
                    List<ShortOption> shortOptions,
            @Option(
                            names = {"-p", "--prefix"},
                            description = "Prefix for each assembly line",
                            defaultValue = "")
                    @NonNull
                    String prefix) {
        Objects.requireNonNull(theContractId);
        Objects.requireNonNull(theContract);
        Objects.requireNonNull(prefix);
        final var options = EnumSet.noneOf(DisassemblyOption.class);
        if (null != shortOptions) shortOptions.stream().map(ShortOption::option).forEach(options::add);
        System.out.print(formatSeparatorLine(
                "Disassembly " + theContractId.map(Object::toString).orElse("")));
        DisassembleContractSubcommand.doit(theContractId, theContract, options, prefix, verbosity);
    }

    @NonNull
    public static String formatSeparatorLine(@NonNull final Object heading) {
        return "%s %s %s%n".formatted(REPORT_SEPARATOR, heading, REPORT_SEPARATOR);
    }

    private ContractCommand() {}
}
