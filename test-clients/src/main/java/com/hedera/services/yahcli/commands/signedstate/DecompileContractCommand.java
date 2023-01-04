/*
 * Copyright (C) 2022-2023 Hedera Hashgraph, LLC
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

import static com.hedera.services.yahcli.commands.signedstate.evminfo.Assembly.UPPERCASE_HEX_FORMATTER;

import com.hedera.services.yahcli.commands.signedstate.HexStringConverter.Bytes;
import com.hedera.services.yahcli.commands.signedstate.evminfo.Assembly;
import com.hedera.services.yahcli.commands.signedstate.evminfo.Assembly.Line;
import com.hedera.services.yahcli.commands.signedstate.evminfo.Assembly.Variant;
import com.hedera.services.yahcli.commands.signedstate.evminfo.CommentLine;
import com.hedera.services.yahcli.commands.signedstate.evminfo.DirectiveLine;
import com.hedera.services.yahcli.commands.signedstate.evminfo.DirectiveLine.Kind;
import com.hedera.services.yahcli.commands.signedstate.evminfo.LabelLine;
import edu.umd.cs.findbugs.annotations.NonNull;
import java.io.PrintWriter;
import java.io.StringWriter;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.Callable;
import java.util.stream.Collectors;
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
            paramLabel = "HEX",
            description = "Contract bytecode as a hex string")
    Bytes theContract;

    @Option(
            names = {"-p", "--prefix"},
            description = "Prefix for each assembly line")
    String prefix = "";

    @Option(
            names = {"-i", "--id"},
            paramLabel = "CONTRACT_ID",
            description = "Contract Id (decimal, optional)")
    Optional<Integer> theContractId;

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

    @Option(names = "--do-not-decode-before-metadata")
    boolean withoutDecodeBeforeMetadata;

    @Override
    public Integer call() throws Exception {
        disassembleContract();
        return 0;
    }

    void disassembleContract() throws Exception {

        try {
            var options = new ArrayList<Variant>();
            if (withCodeOffset) options.add(Variant.DISPLAY_CODE_OFFSET);
            if (withOpcode) options.add(Variant.DISPLAY_OPCODE_HEX);
            if (withoutDecodeBeforeMetadata) options.add(Variant.WITHOUT_DECODE_BEFORE_METADATA);

            var metrics = new HashMap</*@NonNull*/ String, /*@NonNull*/ Object>();
            metrics.put(START_TIMESTAMP, System.nanoTime());

            // Do the disassembly here ...
            final var asm = new Assembly(metrics, options.toArray(new Variant[0]));
            final var prefixLines = getPrefixLines();
            final var lines = asm.getInstructions(prefixLines, theContract.contents);

            metrics.put(END_TIMESTAMP, System.nanoTime());

            if (withMetrics) {
                // replace existing `END` directive with one that has the metrics as a comment
                final var endDirective = new DirectiveLine(Kind.END, formatMetrics(metrics));
                if (lines.get(lines.size() - 1) instanceof DirectiveLine directive
                        && Kind.END.name().equals(directive.directive()))
                    lines.remove(lines.size() - 1);
                lines.add(endDirective);
            }

            for (var line : lines) System.out.printf("%s%s%n", prefix, line.formatLine());
        } catch (Exception ex) {
            throw printFormattedException(theContractId, ex);
        }
    }

    /**
     * Create "prefix" lines to be put ahead of disassembly
     *
     * <p>Put some interesting data in the form of comments and directives to be placed as a prefix
     * in front of the disassembly.
     */
    List<Line> getPrefixLines() {
        var lines = new ArrayList<Line>();
        if (withContractBytecode) {
            final var comment =
                    String.format(
                            "contract (%d bytes): %s",
                            theContract.contents.length,
                            UPPERCASE_HEX_FORMATTER.formatHex(theContract.contents));
            lines.add(new CommentLine(comment));
        }
        {
            final var comment = theContractId.map(i -> "contract id: " + i.toString()).orElse("");
            lines.add(new DirectiveLine(Kind.BEGIN, comment));
        }
        lines.add(new LabelLine("ENTRY"));
        return lines;
    }

    // metrics keys:
    static final String START_TIMESTAMP = "START_TIMESTAMP"; // long
    static final String END_TIMESTAMP = "END_TIMESTAMP"; // long

    /** Format metrics into a string suitable for a comment on the `END` directive */
    String formatMetrics(@NonNull final Map</*@NonNull*/ String, /*@NonNull*/ Object> metrics) {
        var sb = new StringBuilder();

        // elapsed time computation
        final var nanosElapsed =
                (long) metrics.get(END_TIMESTAMP) - (long) metrics.get(START_TIMESTAMP);
        final float msElapsed = nanosElapsed / 1.e6f;
        sb.append(String.format("%.3f ms elapsed", msElapsed));

        return sb.toString();
    }

    /**
     * Dump the contract id + exception + stacktrace to stdout
     *
     * <p>Do it here so each line can be formatted with a known prefix so that you can easily grep
     * for problems in a directory full of disassembled contracts.
     */
    Exception printFormattedException(final Optional<Integer> theContractId, final Exception ex) {
        final var EXCEPTION_PREFIX = "***";

        var sw = new StringWriter(2000);
        var pw = new PrintWriter(sw, true /*auto-flush*/);
        ex.printStackTrace(pw);
        final var starredExceptionDump =
                sw.toString()
                        .lines()
                        .map(s -> EXCEPTION_PREFIX + s)
                        .collect(Collectors.joining("\n"));
        System.out.printf(
                "*** EXCEPTION CAUGHT (id %s): %s%n%s%n",
                theContractId.map(Object::toString).orElse("NOT-GIVEN"),
                ex.toString(),
                starredExceptionDump);
        return ex;
    }
}
