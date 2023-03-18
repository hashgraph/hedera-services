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

package com.hedera.services.yahcli.commands.contract.utils;

import edu.umd.cs.findbugs.annotations.NonNull;
import java.io.BufferedReader;
import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStreamReader;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HexFormat;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.function.BiConsumer;
import java.util.function.Supplier;
import java.util.stream.Collectors;
import java.util.stream.Stream;
import java.util.zip.GZIPInputStream;
import org.apache.commons.lang3.tuple.Pair;
import org.jetbrains.annotations.NotNull;

/**
 * Keeps a local map of selectors to method signatures, loaded from a canned file on disk.
 *
 * Once the database is read from disk it is read-only.  We're concerned with the space footprint: there are,
 * today (2023-03), ~1M selectors known.  Selectors are 4 bytes, the method signatures vary in size (some get
 * quite large: there are ~20 that are >500 characters, 5 of those >2000 characters) and add up to about 40Mb
 * of string data.  We don't want to have a standard Java map holding 1M boxed 4-byte keys and 1M separate strings.
 * Hence this data structure.
 *
 *
 * File has one line per known selector, each line has the format `xxxxxxxx selector(argtype,argtype,...)`, where the
 * selector is 8 characters long: the hexadecimal representation of the selector.  The lines are not sorted in any
 * particular order.
 *
 * Data structure consists of
 * - int[] selectors - selectors in sort order (32-bit signed values derived from selector via narrowing conversion in
 *       standard Java fashion)
 * - int[] str#/offset pair - encoded (str# * 1e6) + offset - offset has a max of 1e6, str# has a max of 1e3
 * - StringBuilder[] content strings - each string is (up to) 1e6 characters in size (in standard Java fashion this means it
 *       takes twice the space for these ASCII method signatures) holding method signatures separated by a delimiter
 *       character (and the string ends with a delimiter character) (the delimiter character might be `\n` or `|` or
 *       something else that doesn't appear in a method signature)
 *
 * To process a file:
 * 1) Read the file extracting the selectors from each line (throwing away the method signature) appending each selector
 *    to a selectors list.  When done, convert the list to an array.
 * 2) Sort the selectors array.
 * 3) Allocate the str#offset array to be the same length as the selectors array.
 * 3) Read the file again getting each selector/signature pair and adding the signatures as follows:
 *    3.1) Find the current highest str#.  Will the method signature + delimiter fit (string won't exceed 1e6 characters)?
 *         3.1.1) If not, create a new StringBuilder with capacity 1e6, add it to the array of content strings, now have
 *                a new highest str#
 *    3.2) Encode str# and current size of that str as: (str# * 10e6) + size
 *    3.3) Binary search the selectors array for the selector, and put the encoded str#/offset at that index in its
 *         array
 *
 * Getting the signature for a selector is obvious:  Binary search the selectors array for the signature.  If it is
 * there then get the corresponding str#/offset and then go to that string at that offset and the signature will be
 * there terminated by the delimiter character.  If the selector isn't found in the binary search, do something else,
 * such as: look it up at `4bytes.directory`.
 */
public class SelectorSignatureMapping {

    protected static final int STRING_HOLDER_LENGTH = 1_000_000;
    protected static final char SIGNATURES_DELIMITER = '\n';

    protected int[] selectors = null;
    protected int[] signatureLocator = null;
    protected List<StringBuilder> signatures;

    public SelectorSignatureMapping() {}

    /** Returns either a valid selector->signature mapping (in first element of pair) _or_ a list of
     * parsing errors (second element of pair).  (Of course it can also throw `IOException` if anything
     * is seriously wrong, like file not found.)
     */
    @NotNull
    public static Pair<SelectorSignatureMapping, List<String>> readMappingFromFile(@NonNull final Path path)
            throws IOException {
        return readMapping(path, SelectorSignatureMapping::new);
    }

    public int getCount() {
        return null != selectors ? selectors.length : 0;
    }

    @NonNull
    public Optional<String> getSignature(final long selector) {
        if (null == selectors) return Optional.empty();
        final var index = Arrays.binarySearch(selectors, 0, selectors.length, (int) selector);
        if (index < 0) return Optional.empty();
        return getSignature(Locator.from(signatureLocator[index]));
    }

    @NonNull
    protected SelectorSignatureMapping addSelectors(@NonNull final List<Integer> knownSelectors) {
        final var nSelectors = knownSelectors.size();
        selectors = new int[nSelectors];
        Arrays.setAll(selectors, knownSelectors::get);
        Arrays.sort(selectors);
        signatureLocator = new int[nSelectors];
        signatures = new ArrayList<>(50);
        return this;
    }

    @NonNull
    protected Optional<String> getSignature(final Locator locator) {
        final var sb = signatures.get(locator.strNum());
        for (int i = locator.strOffset(); ; i++) {
            if (sb.charAt(i) == SIGNATURES_DELIMITER) return Optional.of(sb.substring(locator.strOffset(), i));
        }
    }

    @NonNull
    protected SelSig getByIndex(final int i) {
        return SelSig.of(
                selectors[i], getSignature(Locator.from(signatureLocator[i])).orElse("<not found>"));
    }

    @NonNull
    protected static Pair<SelectorSignatureMapping, List<String>> readMapping(
            @NonNull final Path path, Supplier<SelectorSignatureMapping> factory) throws IOException {
        // read a gzipped file into a byte buffer - because we need to read it twice
        // on top of the byte buffer create an input stream that the gzip can use, that itself
        // is an inputstream which can be turned into a stream

        final var parseErrors = new ArrayList<String>(10);
        final var selectorsSignatureContentsCompressed = Files.readAllBytes(path); // ~13MB

        // First pass: just want the selectors
        final var mapping = factory.get();

        processSelSigBytes(
                (lines, errors) -> {
                    final var r = getSelectors(lines);
                    if (r.getRight().isEmpty()) mapping.addSelectors(r.getLeft());
                    else errors.addAll(r.getRight());
                },
                selectorsSignatureContentsCompressed,
                parseErrors);

        // Second pass: fill in the signatures
        if (parseErrors.isEmpty())
            processSelSigBytes(
                    (lines, errors) -> {
                        errors.addAll(mapping.addSignatures(lines));
                    },
                    selectorsSignatureContentsCompressed,
                    parseErrors);

        return Pair.of(mapping, parseErrors);
    }

    @NonNull
    protected static void processSelSigBytes(
            BiConsumer<Stream<String>, List<String>> doLine, byte[] compressedContent, List<String> errors)
            throws IOException {
        try (final var inputStreamReader = new BufferedReader(
                new InputStreamReader(new GZIPInputStream(new ByteArrayInputStream(compressedContent))))) {
            doLine.accept(inputStreamReader.lines(), errors);
        }
    }

    protected static final boolean PARSE_IN_PARALLEL = false;

    @NonNull
    protected static Pair<List<Integer>, List<String>> getSelectors(@NonNull Stream<String> lines) {
        final var parsingErrors = new ConcurrentLinkedQueue<String>();

        lines = PARSE_IN_PARALLEL ? lines.parallel() : lines.sequential();
        final var selectors = lines.map(line -> {
                    try {
                        return SelSig.from(line);
                    } catch (final IllegalArgumentException ex) {
                        parsingErrors.add(ex.getMessage());
                        return null;
                    }
                })
                .filter(Objects::nonNull)
                .mapToLong(SelSig::selector)
                .mapToInt(l -> (int) l)
                .boxed()
                .collect(Collectors.toCollection(ArrayList<Integer>::new));
        return Pair.of(selectors, new ArrayList<>(parsingErrors));
    }

    @NonNull
    protected List<String> addSignatures(@NonNull Stream<String> lines) {
        final var parsingErrors = new ConcurrentLinkedQueue<String>();

        signatures.add(new StringBuilder(STRING_HOLDER_LENGTH + 1)); // start with first stringbuilder

        lines = PARSE_IN_PARALLEL ? lines.parallel() : lines.sequential();
        lines.map(line -> {
                    try {
                        return SelSig.from(line);
                    } catch (final IllegalArgumentException ex) {
                        parsingErrors.add(ex.getMessage());
                        return null;
                    }
                })
                .filter(Objects::nonNull)
                .sequential()
                .forEach(selsig -> {
                    try {
                        addSelectorWithSignature(selsig);
                    } catch (final IllegalStateException ex) {
                        parsingErrors.add(ex.getMessage());
                    }
                });
        return new ArrayList<>(parsingErrors);
    }

    /** Each line of the input file contains a selector (as a hex string) and the method signature */
    protected record SelSig(long selector, String signature) {
        @NonNull
        public static SelSig of(final long selector, @NonNull final String signature) {
            return new SelSig(selector, signature);
        }

        @NonNull
        public static SelSig from(@NonNull final String line) {
            final var selsig = line.trim().split(" +");
            if (selsig.length != 2)
                throw new IllegalArgumentException("badly formed selector/signature line: '%s'".formatted(line));

            final var selector = HexFormat.fromHexDigitsToLong(selsig[0]);
            final var signature = selsig[1];
            final var limit = 0x1_0000_0000L; // 2³²

            if (selector == 0)
                throw new IllegalArgumentException("selector == 0, disallowed as probably wrong: '%s'".formatted(line));
            if (selector >= limit) throw new IllegalArgumentException("selector too large: '%s'".formatted(line));
            if (signature.isEmpty())
                throw new IllegalArgumentException("empty signature for selector: '%s'".formatted(line));
            return of(selector, signature);
        }

        @NonNull
        public String formattedSelector() {
            return HexFormat.of().toHexDigits(selector(), 8);
        }
    }

    protected void addSelectorWithSignature(@NonNull final SelSig selsig) {
        final var index = Arrays.binarySearch(selectors, 0, selectors.length, (int) selsig.selector());
        if (index < 0)
            throw new IllegalStateException(
                    "logic error: expected to find selector %08x but it is missing".formatted(selsig.selector()));
        final var locator = addSignatureToSignatureStore(selsig.signature());
        signatureLocator[index] = locator.encodedAsInt();
    }

    @NonNull
    protected Locator addSignatureToSignatureStore(@NonNull final String signature) {
        final var lastIndex = signatures.size() - 1;
        final var lastSb = signatures.get(lastIndex);
        final var lastSbLength = lastSb.length();
        if (lastSbLength + signature.length() + 1 >= STRING_HOLDER_LENGTH) {
            signatures.add(new StringBuilder(STRING_HOLDER_LENGTH));
            return addSignatureToSignatureStore(signature);
        }
        lastSb.append(signature);
        lastSb.append(SIGNATURES_DELIMITER);
        return Locator.of(lastIndex, lastSbLength);
    }

    protected record Locator(int strNum, int strOffset) {
        @NonNull
        public static Locator of(final int strNum, final int strOffset) {
            return new Locator(strNum, strOffset);
        }

        public int encodedAsInt() {
            return strNum() * STRING_HOLDER_LENGTH + strOffset();
        }

        @NonNull
        public static Locator from(final int locator) {
            return new Locator(locator / STRING_HOLDER_LENGTH, locator % STRING_HOLDER_LENGTH);
        }
    }
}
