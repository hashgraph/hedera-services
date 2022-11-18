/*
 * Copyright (C) 2022 Hedera Hashgraph, LLC
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
package com.hedera.services.yahcli.commands.signedstate;

import com.hedera.services.yahcli.commands.signedstate.evminfo.Assembly;
import com.hedera.services.yahcli.commands.signedstate.evminfo.Assembly.Line;
import com.hedera.services.yahcli.commands.signedstate.evminfo.Assembly.Options;
import com.hedera.services.yahcli.commands.signedstate.evminfo.CommentLine;
import com.hedera.services.yahcli.commands.signedstate.evminfo.DirectiveLine;
import com.hedera.services.yahcli.commands.signedstate.evminfo.LabelLine;
import com.hedera.services.yahcli.commands.signedstate.evminfo.Utility;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.Callable;
import org.jetbrains.annotations.NotNull;
import picocli.CommandLine.Command;
import picocli.CommandLine.Option;
import picocli.CommandLine.ParentCommand;

@Command(
        name = "decompilecontract",
        subcommands = {picocli.CommandLine.HelpCommand.class},
        description = "Decompiles contract bytecodes")
public class DecompileContractCommand implements Callable<Integer> {
    @ParentCommand private SignedStateCommand signedStateCommand;

    @Option(
            names = {"-b", "--bytecode"},
            required = true,
            arity = "1",
            converter = HexStringConverter.class,
            paramLabel = "BYTECODE_HEX",
            description = "Contract bytecode as a hex string")
    HexStringConverter.Bytes theContract;

    @Option(
            names = {"-p", "--prefix"},
            description = "Prefix for each assembly line")
    String prefix = "";

    @Option(
            names = {"-i", "--id"},
            paramLabel = "CONTRACT_ID",
            description = "Contract Id (decimal, optional)")
    Optional<Long> theContractId;

    @Option(names = "--with-code-offset", description = "Display code offsets")
    boolean withCodeOffset;

    @Option(names = "--with-opcode", description = "Display opcode (in hex")
    boolean withOpcode;

    @Option(names = "--with-metrics", description = "Record metrics in generated assembly")
    boolean withMetrics;

    @Option(
            names = "--with-contract-bytecode",
            description = "Put the contract bytecode in a comment")
    boolean withContractBytecode;

    @Override
    public Integer call() throws Exception {
        disassembleContract();
        return 0;
    }

    void disassembleContract() {

        var options = new ArrayList<Assembly.Options>();
        if (withCodeOffset) options.add(Options.DISPLAY_CODE_OFFSET);
        if (withOpcode) options.add(Options.DISPLAY_OPCODE_HEX);

        @NotNull Map<@NotNull String, @NotNull Object> metrics = new HashMap<>();
        metrics.put(START_TIMESTAMP, System.nanoTime());

        final var asm = new Assembly(metrics, options.toArray(new Assembly.Options[0]));

        var prefixLines = new ArrayList<Line>();

        if (withContractBytecode) {
            prefixLines.add(new CommentLine("contract: " + Utility.toHex(theContract.contents)));
        }
        theContractId.ifPresentOrElse(
                aLong ->
                        prefixLines.add(
                                new DirectiveLine(
                                        "BEGIN", "", String.format("contract id: %d", aLong))),
                () -> prefixLines.add(new DirectiveLine("BEGIN")));
        prefixLines.add(new LabelLine("ENTRY"));

        final var lines = asm.getInstructions(prefixLines, theContract.contents);

        metrics.put(END_TIMESTAMP, System.nanoTime());

        var endDirective = new DirectiveLine("END", "", withMetrics ? formatMetrics(metrics) : "");
        lines.add(endDirective);

        for (var line : lines) System.out.printf("%s%s%n", prefix, line.formatLine());
    }

    // metrics keys:
    static final String START_TIMESTAMP = "START_TIMESTAMP"; // long
    static final String END_TIMESTAMP = "END_TIMESTAMP"; // long

    String formatMetrics(@NotNull Map<@NotNull String, @NotNull Object> metrics) {
        var sb = new StringBuilder();

        // elapsed time computation
        final var nanosElapsed =
                (long) metrics.get(END_TIMESTAMP) - (long) metrics.get(START_TIMESTAMP);
        final float msElapsed = nanosElapsed / 1.e6f;
        sb.append(String.format("%.3f ms elapsed", msElapsed));

        return sb.toString();
    }
}
