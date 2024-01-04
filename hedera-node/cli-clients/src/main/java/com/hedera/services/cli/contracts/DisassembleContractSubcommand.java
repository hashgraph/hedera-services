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

import static com.hedera.services.cli.contracts.ContractCommand.formatSeparatorLine;
import static com.hedera.services.cli.contracts.assembly.Constants.UPPERCASE_HEX_FORMATTER;

import com.hedera.services.cli.contracts.ContractCommand.Verbosity;
import com.hedera.services.cli.contracts.analysis.MethodAnalysis;
import com.hedera.services.cli.contracts.analysis.VTableEntryRecognizer;
import com.hedera.services.cli.contracts.analysis.VTableEntryRecognizer.MethodEntry;
import com.hedera.services.cli.contracts.assembly.Code;
import com.hedera.services.cli.contracts.assembly.CommentLine;
import com.hedera.services.cli.contracts.assembly.DirectiveLine;
import com.hedera.services.cli.contracts.assembly.Disassembler;
import com.hedera.services.cli.contracts.assembly.Editor;
import com.hedera.services.cli.contracts.assembly.LabelLine;
import com.hedera.services.cli.contracts.assembly.Line;
import com.hedera.services.cli.contracts.assembly.MacroLine;
import com.hedera.services.cli.utils.HexToBytesConverter.Bytes;
import edu.umd.cs.findbugs.annotations.NonNull;
import java.io.PrintWriter;
import java.io.StringWriter;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;

@SuppressWarnings("java:S106") // "use of system.out/system.err instead of logger" - not needed/desirable for CLI tool
public class DisassembleContractSubcommand {

    static void doit(
            @NonNull final Optional<Integer> theContractId,
            @NonNull final Bytes theContract,
            @NonNull final Set<DisassemblyOption> theOptions,
            @NonNull final String prefix,
            @NonNull final Verbosity verbosity) {
        new DisassembleContractSubcommand(theContractId, theContract, theOptions, prefix, verbosity)
                .disassembleContract();
    }

    @NonNull
    final Optional<Integer> theContractId;

    @NonNull
    final Bytes theContract;

    @NonNull
    final String prefix;

    @NonNull
    final Verbosity verbosity;

    DisassembleContractSubcommand(
            @NonNull final Optional<Integer> theContractId,
            @NonNull final Bytes theContract,
            @NonNull final Set<DisassemblyOption> theOptions,
            @NonNull final String prefix,
            @NonNull final Verbosity verbosity) {
        this.theContract = theContract;
        this.theContractId = theContractId;
        this.prefix = prefix;
        this.verbosity = verbosity;

        DisassemblyOption.setOptions(theOptions);
    }

    @SuppressWarnings("java:S1066") // "Collapsible "if" statements should be merged" to "increase the code's
    // readability" - it doesn't always, and doesn't here
    void disassembleContract() {

        try {

            var metrics = new HashMap</*@NonNull*/ String, /*@NonNull*/ Object>();
            metrics.put(START_TIMESTAMP, System.nanoTime());

            // Do the disassembly here ...
            final var asm = new Disassembler(metrics);
            final var prefixLines = getPrefixLines();
            final var lines = asm.getInstructions(prefixLines, theContract.contents);

            // Do the analysis here (if requested by command line option)
            final var analysisResults = Optional.ofNullable(
                    DisassemblyOption.isOn(DisassemblyOption.RECOGNIZE_CODE_SEQUENCES)
                            ? new MethodAnalysis(lines).analyze()
                            : null);

            metrics.put(END_TIMESTAMP, System.nanoTime());

            if (DisassemblyOption.isOn(DisassemblyOption.WITH_METRICS)) addMetricsToEnd(lines, metrics);

            analysisResults.ifPresentOrElse(
                    results -> {
                        // analyzed listing
                        if (DisassemblyOption.isOn(DisassemblyOption.TRACE_RECOGNIZERS)) {
                            if (results.properties().containsKey(VTableEntryRecognizer.METHODS_PROPERTY)) {
                                @SuppressWarnings("unchecked")
                                final var traceLines =
                                        (List<String>) results.properties().get(VTableEntryRecognizer.METHODS_TRACE);
                                System.out.print(formatSeparatorLine("Methods trace"));
                                for (final var t : traceLines) {
                                    System.out.printf("   %s%n", t);
                                }
                                System.out.printf("%n");
                            }
                        }

                        if (DisassemblyOption.isOn(DisassemblyOption.DISPLAY_SELECTORS)) {
                            if (results.properties().containsKey(VTableEntryRecognizer.METHODS_PROPERTY)) {
                                @SuppressWarnings("unchecked")
                                final var methodsTable = (List<VTableEntryRecognizer.MethodEntry>)
                                        results.properties().get(VTableEntryRecognizer.METHODS_PROPERTY);
                                methodsTable.sort(Comparator.comparing(MethodEntry::methodOffset));
                                System.out.print(formatSeparatorLine("Selectors from contract vtable"));
                                for (final var me : methodsTable) {
                                    System.out.printf("   %04X: %08X%n", me.methodOffset(), me.selector());
                                }
                                System.out.printf("%n");
                            }
                        }

                        if (DisassemblyOption.isOn(DisassemblyOption.LIST_MACROS)) {
                            spewFormattedLines(
                                    "Macros from VTableEntryRecognizer",
                                    results.codeLineReplacements().stream()
                                            .filter(MacroLine.class::isInstance)
                                            .map(Line.class::cast)
                                            .toList(),
                                    3);
                            System.out.printf("%n");
                        }

                        if (DisassemblyOption.isOn(DisassemblyOption.WITH_RAW_DISASSEMBLY)) {
                            spewDisassembly("Raw disassembly", lines);
                            System.out.printf("%n");
                        }

                        spewDisassembly(
                                "Analyzed disassembly", mergeAnalyzedLines(lines, results.codeLineReplacements()));
                    },
                    (/*orElse*/ ) -> spewDisassembly("Raw disassembly", lines));
        } catch (Exception ex) {
            throw printFormattedException(theContractId, ex);
        }
    }

    void addMetricsToEnd(@NonNull final List<Line> lines, @NonNull final Map<String, Object> metrics) {
        // replace existing `END` directive with one that has the metrics as a comment (if there _is_ no existing
        // `END` directive that's fine: just add one)
        final var endDirective = new DirectiveLine(DirectiveLine.Kind.END, formatMetrics(metrics));
        if (lines.get(lines.size() - 1) instanceof DirectiveLine directive
                && DirectiveLine.Kind.END.name().equals(directive.directive())) lines.remove(lines.size() - 1);
        lines.add(endDirective);
    }

    @NonNull
    List<Line> mergeAnalyzedLines(@NonNull final List<Line> lines, @NonNull final List<Code> replacementLines) {
        final var editor = new Editor(lines);
        replacementLines.forEach(editor::add);
        return editor.merge();
    }

    void spewFormattedLines(@NonNull final String heading, @NonNull final List<Line> lines, final int indent) {
        System.out.printf(formatSeparatorLine(heading));
        for (final var l : lines) System.out.print(l.formatLine().indent(indent));
    }

    void spewDisassembly(@NonNull final String heading, @NonNull final List<Line> lines) {
        spewFormattedLines(heading, lines, 0);
    }

    /**
     * Create "prefix" lines to be put ahead of disassembly
     *
     * <p>Put some interesting data in the form of comments and directives to be placed as a prefix
     * in front of the disassembly.
     */
    List<Line> getPrefixLines() {
        var lines = new ArrayList<Line>();
        if (DisassemblyOption.isOn(DisassemblyOption.DISPLAY_OPCODE_HEX)) {
            final var comment = "contract (%d bytes): %s"
                    .formatted(theContract.contents.length, UPPERCASE_HEX_FORMATTER.formatHex(theContract.contents));
            lines.add(new CommentLine(comment));
        }

        final var comment = theContractId.map(i -> "contract id: " + i).orElse("");
        lines.add(new DirectiveLine(DirectiveLine.Kind.BEGIN, comment));

        lines.add(new LabelLine(0, "ENTRY"));
        return lines;
    }

    // metrics keys:
    static final String START_TIMESTAMP = "START_TIMESTAMP"; // long
    static final String END_TIMESTAMP = "END_TIMESTAMP"; // long

    /** Format metrics into a string suitable for a comment on the `END` directive */
    String formatMetrics(@NonNull final Map</*@NonNull*/ String, /*@NonNull*/ Object> metrics) {
        var sb = new StringBuilder();

        // elapsed time computation
        final var nanosElapsed = (long) metrics.get(END_TIMESTAMP) - (long) metrics.get(START_TIMESTAMP);
        final float msElapsed = nanosElapsed / 1.e6f;
        sb.append("%.3f ms elapsed".formatted(msElapsed));

        return sb.toString();
    }

    /**
     * Dump the contract id + exception + stacktrace to stdout
     *
     * <p>Do it here so each line can be formatted with a known prefix so that you can easily grep
     * for problems in a directory full of disassembled contracts.
     */
    RuntimeException printFormattedException(final Optional<Integer> theContractId, final Exception ex) {
        final var EXCEPTION_PREFIX = "***";

        var sw = new StringWriter(2000);
        var pw = new PrintWriter(sw, true /*auto-flush*/);
        ex.printStackTrace(pw);
        final var starredExceptionDump =
                sw.toString().lines().map(s -> EXCEPTION_PREFIX + s).collect(Collectors.joining("\n"));
        System.out.printf(
                "*** EXCEPTION CAUGHT (id %s): %s%n%s%n",
                theContractId.map(Object::toString).orElse("NOT-GIVEN"), ex, starredExceptionDump);
        return ex instanceof RuntimeException rex ? rex : new RuntimeException(ex);
    }
}
