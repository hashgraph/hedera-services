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

package com.hedera.services.yahcli.commands.contract.subcommands;

import static com.hedera.services.yahcli.commands.contract.evminfo.Assembly.UPPERCASE_HEX_FORMATTER;

import com.hedera.services.yahcli.commands.contract.ContractCommand;
import com.hedera.services.yahcli.commands.contract.evminfo.Assembly;
import com.hedera.services.yahcli.commands.contract.evminfo.Assembly.Line;
import com.hedera.services.yahcli.commands.contract.evminfo.Assembly.Variant;
import com.hedera.services.yahcli.commands.contract.evminfo.CommentLine;
import com.hedera.services.yahcli.commands.contract.evminfo.DirectiveLine;
import com.hedera.services.yahcli.commands.contract.evminfo.DirectiveLine.Kind;
import com.hedera.services.yahcli.commands.contract.evminfo.Editor;
import com.hedera.services.yahcli.commands.contract.evminfo.LabelLine;
import com.hedera.services.yahcli.commands.contract.evminfo.MacroLine;
import com.hedera.services.yahcli.commands.contract.evminfo.VTableEntryRecognizer;
import com.hedera.services.yahcli.commands.contract.evminfo.VTableEntryRecognizer.MethodEntry;
import com.hedera.services.yahcli.commands.contract.utils.HexToBytesConverter;
import com.hedera.services.yahcli.commands.contract.utils.HexToBytesConverter.Bytes;
import edu.umd.cs.findbugs.annotations.NonNull;
import java.io.PrintWriter;
import java.io.StringWriter;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.EnumSet;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
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
    @ParentCommand
    private ContractCommand contractCommand;

    @Option(
            names = {"-b", "--bytecode"},
            required = true,
            arity = "1",
            converter = HexToBytesConverter.class,
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

    @Option(names = "--with-contract-bytecode", description = "Put the contract bytecode in a comment")
    boolean withContractBytecode;

    @Option(names = "--do-not-decode-before-metadata")
    boolean withoutDecodeBeforeMetadata;

    @Option(names = "--recognize-sequences", description = "Recognize and analyze code sequences")
    boolean recognizeCodeSequences;

    @Option(names = "--signatures-file", description = "File containing selector/signature pairs (must be gzipped")
    Path signaturesFile;

    boolean withCannedSignatures;

    @Option(
            names = "--with-selector-lookups",
            description = "Fetch selector method names from internet (requires --recognize-sequences")
    boolean fetchSelectorNames;

    Set<Flag> flags = EnumSet.noneOf(Flag.class);

    enum Flag {
        MACROS,
        RAW_DISASSEMBLY,
        SELECTORS,
        TRACE
    };

    @Option(
            names = {"-f", "--flag"},
            description = {
                "flags controlling different output options:",
                "m    list macros",
                "r    list raw disassembly (before analyzed disassembly",
                "s    list selectors",
                "t    dump trace of recognizers"
            })
    ShortFlag[] shortFlags;

    enum ShortFlag {
        m(Flag.MACROS),
        r(Flag.RAW_DISASSEMBLY),
        s(Flag.SELECTORS),
        t(Flag.TRACE);

        ShortFlag(@NonNull Flag flag) {
            this.flag = flag;
        }

        final Flag flag;
    }

    @Override
    public Integer call() throws Exception {
        if (null != shortFlags) for (var sf : shortFlags) flags.add(sf.flag);
        disassembleContract();
        return 0;
    }

    void disassembleContract() throws Exception {

        withCannedSignatures = null != signaturesFile;

        try {
            var options = new ArrayList<Variant>();
            if (withCodeOffset) options.add(Variant.DISPLAY_CODE_OFFSET);
            if (withOpcode) options.add(Variant.DISPLAY_OPCODE_HEX);
            if (withoutDecodeBeforeMetadata) options.add(Variant.WITHOUT_DECODE_BEFORE_METADATA);
            if (recognizeCodeSequences) options.add(Variant.RECOGNIZE_CODE_SEQUENCES);
            if (fetchSelectorNames) options.add(Variant.FETCH_SELECTOR_NAMES);
            if (withCannedSignatures) options.add(Variant.WITH_CANNED_SIGNATURES);

            var metrics = new HashMap</*@NonNull*/ String, /*@NonNull*/ Object>();
            metrics.put(START_TIMESTAMP, System.nanoTime());

            // Do the disassembly here ...
            final var asm = new Assembly(metrics, options.toArray(new Variant[0]));
            final var prefixLines = getPrefixLines();
            final var lines = asm.getInstructions(prefixLines, theContract.contents);

            // TODO: Should just be embedded in Assembly class (since there's an option for it)
            final var analysisResults = Optional.ofNullable(recognizeCodeSequences ? asm.analyze(lines) : null);

            metrics.put(END_TIMESTAMP, System.nanoTime());

            if (withMetrics) {
                // replace existing `END` directive with one that has the metrics as a comment
                final var endDirective = new DirectiveLine(Kind.END, formatMetrics(metrics));
                if (lines.get(lines.size() - 1) instanceof DirectiveLine directive
                        && Kind.END.name().equals(directive.directive())) lines.remove(lines.size() - 1);
                lines.add(endDirective);
            }

            analysisResults.ifPresentOrElse(
                    results -> {
                        // TODO: Each section needs a command line argument to enable, also raw +/-
                        // analyzed listing.  Preferably as "POSIX clustered short options".
                        if (results.properties().containsKey(VTableEntryRecognizer.METHODS_PROPERTY)) {
                            if (flags.contains(Flag.TRACE)) {
                                @SuppressWarnings("unchecked")
                                final var traceLines =
                                        (List<String>) results.properties().get(VTableEntryRecognizer.METHODS_TRACE);
                                System.out.printf("Methods trace:%n");
                                for (final var t : traceLines) {
                                    System.out.printf("   %s%n", t);
                                }
                                System.out.printf("%n");
                            }

                            if (flags.contains(Flag.SELECTORS)) {
                                @SuppressWarnings("unchecked")
                                final var methodsTable = (List<VTableEntryRecognizer.MethodEntry>)
                                        results.properties().get(VTableEntryRecognizer.METHODS_PROPERTY);
                                methodsTable.sort(Comparator.comparing(MethodEntry::methodOffset));
                                System.out.printf("Selectors from contract vtable:%n");
                                for (final var me : methodsTable) {
                                    System.out.printf("%04X: %08X%n", me.methodOffset(), me.selector());
                                }
                                System.out.printf("%n");
                            }

                            if (flags.contains(Flag.MACROS)) {
                                System.out.printf("Macros from VTableEntryRecognizer:%n");
                                for (final var macro : results.codeLineReplacements()) {
                                    if (macro instanceof MacroLine macroLine) {
                                        System.out.printf("   %s%n", macroLine.formatLine());
                                    }
                                }
                                System.out.printf("%n");
                            }
                        }

                        if (flags.contains(Flag.RAW_DISASSEMBLY)) {
                            System.out.printf("Raw disassembly:%n");
                            for (var line : lines) System.out.printf("%s%s%n", prefix, line.formatLine());
                        }

                        {
                            // TODO: _This_ should _really_ be moved to the Assembly class
                            var editor = new Editor(lines);
                            results.codeLineReplacements().forEach(editor::add);
                            var analyzedLines = editor.merge();

                            System.out.printf("Analyzed disassembly:%n");
                            for (var line : analyzedLines) System.out.printf("%s%s%n", prefix, line.formatLine());
                        }
                    },
                    (/*orElse*/ ) -> {
                        System.out.printf("Raw disassembly:%n");
                        for (var line : lines) System.out.printf("%s%s%n", prefix, line.formatLine());
                    });
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
    @NonNull
    List<Line> getPrefixLines() {
        var lines = new ArrayList<Line>();
        if (withContractBytecode) {
            final var comment = "contract (%d bytes): %s"
                    .formatted(theContract.contents.length, UPPERCASE_HEX_FORMATTER.formatHex(theContract.contents));
            lines.add(new CommentLine(comment));
        }
        {
            final var comment =
                    theContractId.map(i -> "contract id: " + i.toString()).orElse("");
            lines.add(new DirectiveLine(Kind.BEGIN, comment));
        }
        lines.add(new LabelLine(0, "ENTRY"));
        return lines;
    }

    // metrics keys:
    static final String START_TIMESTAMP = "START_TIMESTAMP"; // long
    static final String END_TIMESTAMP = "END_TIMESTAMP"; // long

    /** Format metrics into a string suitable for a comment on the `END` directive */
    @NonNull
    String formatMetrics(@NonNull final Map</*@NonNull*/ String, /*@NonNull*/ Object> metrics) {
        var sb = new StringBuilder(80);

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
    @NonNull
    Exception printFormattedException(final Optional<Integer> theContractId, final Exception ex) {
        final var EXCEPTION_PREFIX = "***";

        var sw = new StringWriter(2000);
        var pw = new PrintWriter(sw, true /*auto-flush*/);
        ex.printStackTrace(pw);
        final var starredExceptionDump =
                sw.toString().lines().map(s -> EXCEPTION_PREFIX + s).collect(Collectors.joining("\n"));
        System.out.printf(
                "*** EXCEPTION CAUGHT (id %s): %s%n%s%n",
                theContractId.map(Object::toString).orElse("NOT-GIVEN"), ex.toString(), starredExceptionDump);
        return ex;
    }
}
