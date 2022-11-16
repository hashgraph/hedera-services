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

import static java.lang.Integer.min;
import static java.util.Arrays.copyOf;

import com.hedera.services.yahcli.commands.signedstate.evminfo.Assembly;
import com.hedera.services.yahcli.commands.signedstate.evminfo.Assembly.Line;
import com.hedera.services.yahcli.commands.signedstate.evminfo.Assembly.Options;
import com.hedera.services.yahcli.commands.signedstate.evminfo.CommentLine;
import com.hedera.services.yahcli.commands.signedstate.evminfo.DirectiveLine;
import com.hedera.services.yahcli.commands.signedstate.evminfo.LabelLine;
import com.hedera.services.yahcli.commands.signedstate.evminfo.Utility;
import java.util.ArrayList;
import java.util.Optional;
import java.util.concurrent.Callable;
import org.jetbrains.annotations.Contract;
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

    @Option(
            names = "--limit",
            paramLabel = "TRUNCATE_CONTRACT_BYTES",
            description = "Truncate contract to this limit (for testing)")
    Optional<Integer> truncationLimit;

    @Option(names = "--with-code-offset", description = "Display code offsets")
    boolean withCodeOffset;

    @Option(names = "--with-opcode", description = "Display opcode (in hex")
    boolean withOpcode;

    @Option(names = "--dump-partial", description = "Dump partial disassembly in case of exception")
    boolean dumpPartial;

    @Override
    public Integer call() throws Exception {
        disassembleContract();
        return 0;
    }

    @Contract(pure = true)
    void disassembleContract() {

        var options = new ArrayList<Assembly.Options>();
        if (withCodeOffset) options.add(Options.DISPLAY_CODE_OFFSET);
        if (withOpcode) options.add(Options.DISPLAY_OPCODE_HEX);

        final var asm = new Assembly(options.toArray(new Assembly.Options[0]));

        final int[] contract = truncatedContract();
        var prefixLines = new ArrayList<Line>();

        prefixLines.add(
                new CommentLine(
                        (truncationLimit.isPresent() ? "(truncated) " : "")
                                + "contract: "
                                + Utility.toHex(contract)));
        theContractId.ifPresentOrElse(
                aLong ->
                        prefixLines.add(
                                new DirectiveLine(
                                        "BEGIN", "", String.format("contract id: %d", aLong))),
                () -> prefixLines.add(new DirectiveLine("BEGIN")));
        prefixLines.add(new LabelLine("ENTRY"));

        final var lines = asm.getInstructions(prefixLines, contract, true, dumpPartial);

        for (var line : lines) System.out.printf("%s%s%n", prefix, line.formatLine());
    }

    @Contract(pure = true)
    int @NotNull [] truncatedContract() {
        int limit = theContract.contents.length;
        if (truncationLimit.isPresent()) limit = min(limit, truncationLimit.get());
        return copyOf(theContract.contents, limit);
    }
}
