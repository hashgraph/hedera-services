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

package com.hedera.services.yahcli.commands.contract.evminfo;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatIllegalArgumentException;
import static org.assertj.core.api.Assertions.assertThatIndexOutOfBoundsException;
import static org.assertj.core.api.Assumptions.assumeThat;
import static org.junit.jupiter.api.Assertions.assertAll;

import com.google.common.collect.Collections2;
import com.hedera.services.yahcli.commands.contract.evminfo.Assembly.Code;
import com.hedera.services.yahcli.commands.contract.evminfo.Assembly.Line;
import com.hedera.services.yahcli.commands.contract.evminfo.Editor.Edit;
import com.hedera.services.yahcli.commands.contract.utils.Range;
import edu.umd.cs.findbugs.annotations.NonNull;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.List;
import java.util.function.BiFunction;
import java.util.stream.IntStream;
import java.util.stream.Stream;
import org.assertj.core.description.LazyTextDescription;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.function.Executable;

class EditorTest {

    // Note to the reader from the author:
    //
    // At first glance this set of tests would look both 1) way overdone for the module under
    // test and 2) a strange complicated mish-mosh of test writing styles.  Second glance too.
    // And both glances would be correct.  The facts are that I wanted to experiment with different
    // styles of "data driven tests" and that beyond which JUnit jupiter provided, plus I happened
    // to need tests for this module.  So this is my test bed for "test style" for different
    // approaches.  And, of course, having written valid working tests this way, I then felt no
    // need to go back and rewrite them for consistency, limit them to only the tests actually
    // necessary, etc. etc.  Just left them the way they are.  And I'm still considering if any of
    // these approaches are stylistically better for the test _reader_ than standard JUnit
    // parameterized or dynamic tests (which I find kind of either limited, or bulky, or both).

    record LinesWithRange(List<Line> lines, Range<Line> range) {}

    @Test
    void getByteRangeFromLinesTest() {
        {
            final var lr = getSomeLines(0);
            final var sut = new Editor(lr.lines());
            assertAll("empty lines", () -> assertThat(sut.getByteRangeFromLines(lr.range()))
                    .isEqualTo(Range.empty()));
        }
        {
            final var lr = get4LinesWithNoCode();
            final var sut = new Editor(lr.lines());
            assertAll(
                    "lines but no code lines",
                    () -> assertThat(sut.getByteRangeFromLines(lr.range())).isEqualTo(Range.empty()),
                    () -> assertThat(sut.getByteRangeFromLines(new Range<Line>(1, lr.lines.size() - 2)))
                            .isEqualTo((Range.empty())));
        }
        {
            final var lr = getExactly1LineOfCode();
            final var sut = new Editor(lr.lines());
            assertAll("exactly 1 line of code", () -> assertThat(sut.getByteRangeFromLines(lr.range()))
                    .isEqualTo((new Range<Byte>(0, 1 /*exclusive at this end*/))));
        }
        {
            final int at = 3;
            final var lr = get1LineOfCodeOfAt(8, at);
            final var rangeIfFound = new Range<Byte>(0, 1 /*exclusive at this end*/);
            final var rangeIfNotFound = Range.empty();
            final var sut = new Editor(lr.lines());
            final var tests = new ArrayList<Executable>(100);
            for (int i = 0; i < lr.lines.size(); i++) {
                final int ii = i;
                final var inRangeHead = new Range<Line>(0, ii);
                final var inRangeTail = new Range<Line>(ii, lr.lines.size());
                tests.add(() -> assertThat(sut.getByteRangeFromLines(inRangeHead))
                        .as(
                                "head (%s) %s find code line at position %d",
                                inRangeHead, ii <= at ? "does NOT" : "does", at)
                        .isEqualTo(ii <= at ? rangeIfNotFound : rangeIfFound));
                tests.add(() -> assertThat(sut.getByteRangeFromLines(inRangeTail))
                        .as("tail (%s) always finds code line at position %d", inRangeTail, at)
                        .isEqualTo(rangeIfFound));
            }
            assertAll("1 line of code with various ranges of lines", tests);
        }
        {
            final int n = 5;
            final int[][] ats = {
                {0, 2},
                {0, 4},
                {1, 3},
                {1, 2, 3},
                {0, 2, 3, 4},
                {0, 1, 2, 3, 4}
            };
            for (final int[] at : ats) {
                final var codeSize = IntStream.of(at).map(i -> i + 1).sum();
                final var lr = getLinesOfCodeOfAt(n, WithExtraBytes.YES, at);
                final var sut = new Editor(lr.lines());
                assertAll(
                        String.format("%d lines of code at %s of %d lines", at.length, Arrays.toString(at), n),
                        () -> assertThat(sut.getByteRangeFromLines(lr.range()))
                                .isEqualTo(new Range<Byte>(0, codeSize)));
            }
        }
    }

    @Test
    void getCodeRangeFromLinesTest() {
        {
            final var lr = getSomeLines(0);
            final var sut = new Editor(lr.lines());
            assertThat(sut.getCodeRangeFromLines(lr.range())).as("empty lines").containsExactly(null, null);
        }
        {
            final var lr = get4LinesWithNoCode();
            final var sut = new Editor(lr.lines());
            assertThat(sut.getCodeRangeFromLines(lr.range()))
                    .as("lines but no code lines")
                    .containsExactly(null, null);
        }
        {
            final var lr = getExactly1LineOfCode();
            final var codeLine = (Code) lr.lines().get(0);
            final var sut = new Editor(lr.lines());
            assertThat(sut.getCodeRangeFromLines(lr.range()))
                    .as("exactly 1 line of code")
                    .containsExactly(codeLine, codeLine);
        }
        {
            final int n = 5;
            for (int i = 0; i < n; i++) {
                final var lr = get1LineOfCodeOfAt(n, i);
                final var codeLine = (Code) lr.lines().get(i);
                final var sut = new Editor(lr.lines());
                assertThat(sut.getCodeRangeFromLines(lr.range()))
                        .as("1 line of code at %d of %d lines".formatted(i, n))
                        .containsExactly(codeLine, codeLine);
            }
        }
        {
            final int n = 5;
            final int[][] ats = {
                {0, 2},
                {0, 4},
                {1, 3},
                {1, 2, 3},
                {0, 2, 3, 4},
                {0, 1, 2, 3, 4}
            };

            for (final int[] at : ats) {
                final var lr = getLinesOfCodeOfAt(n, WithExtraBytes.NO, at);
                final var firstCodeLine = (Code) lr.lines().get(at[0]);
                final var lastCodeLine = (Code) lr.lines().get(at[at.length - 1]);
                final var sut = new Editor(lr.lines());
                final var description = new LazyTextDescription(
                        () -> "%d lines of code at %s of %d lines".formatted(at.length, Arrays.toString(at), n));
                assertThat(sut.getCodeRangeFromLines(lr.range()))
                        .as(description)
                        .containsExactly(firstCodeLine, lastCodeLine);
            }
        }
    }

    @Test
    void isValidLineRangeTest() {
        {
            final var sut = new Editor(getSomeLines(0).lines());
            assertAll(
                    "ranges on empty lines",
                    () -> assertThat(sut.isValidLineRange(new Range<Line>(0, 0)))
                            .isTrue(),
                    () -> assertThat(sut.isValidLineRange(new Range<Line>(0, 1)))
                            .isFalse(),
                    () -> assertThat(sut.isValidLineRange(new Range<Line>(0, 2)))
                            .isFalse(),
                    () -> assertThat(sut.isValidLineRange(new Range<Line>(1, 1)))
                            .isFalse());
        }
        {
            final var n = 3;
            final var sut = new Editor(getSomeLines(n).lines());
            final var tests = new ArrayList<Executable>(100);
            tests.add(() ->
                    assertThat(sut.isValidLineRange(new Range<Line>(-1, 0))).isFalse());
            tests.add(() -> assertThat(sut.isValidLineRange(new Range<Line>(n + 1, n + 1)))
                    .isFalse());
            for (var from = 0; from < n + 1; from++)
                for (var to = 0; to < n + 2; to++) {
                    final int finalFrom = from; // stupidity: for-each allows loop var to be `final`, why not
                    // ordinary for loop?
                    final int finalTo = to; // for that matter: why aren't java lambdas true closures?
                    Executable positiveTest =
                            () -> assertThat(sut.isValidLineRange(new Range<Line>(finalFrom, finalTo)))
                                    .isTrue();
                    Executable negativeTest =
                            () -> assertThat(sut.isValidLineRange(new Range<Line>(finalFrom, finalTo)))
                                    .isFalse();
                    if (to > n) tests.add(negativeTest);
                    else if (to < from) tests.add(negativeTest);
                    else tests.add(positiveTest);
                }
            assertAll("validity against 3 lines", tests);
        }
    }

    @Test
    void validateLineRangeTest() {
        BiFunction<Editor, Range<Line>, Executable> genPosTest = (final Editor sut, final Range<Line> range) -> {
            return () -> assertThat(sut.validateLineRange(range)).isEqualTo(range);
        };
        BiFunction<Editor, Range<Line>, Executable> genNegTest = (final Editor sut, final Range<Line> range) -> {
            return () -> assertThatIllegalArgumentException().isThrownBy(() -> sut.validateLineRange(range));
        };

        {
            final var sut = new Editor(getSomeLines(0).lines());
            assertAll(
                    "ranges on empty lines",
                    genPosTest.apply(sut, new Range<Line>(0, 0)),
                    genNegTest.apply(sut, new Range<Line>(0, 1)),
                    genNegTest.apply(sut, new Range<Line>(0, 2)),
                    genNegTest.apply(sut, new Range<Line>(1, 1)));
        }
        {
            final var n = 3;
            final var sut = new Editor(getSomeLines(n).lines());
            final var tests = new ArrayList<Executable>(100);
            tests.add(genNegTest.apply(sut, new Range<Line>(-1, 0)));
            tests.add(genNegTest.apply(sut, new Range<Line>(n + 1, n + 1)));
            for (var from = 0; from < n + 1; from++)
                for (var to = 0; to < n + 2; to++) {
                    Executable positiveTest = genPosTest.apply(sut, new Range<Line>(from, to));
                    Executable negativeTest = genNegTest.apply(sut, new Range<Line>(from, to));
                    if (to > n) tests.add(negativeTest);
                    else if (to < from) tests.add(negativeTest);
                    else tests.add(positiveTest);
                }
            assertAll("validate against 3 lines", tests);
        }
    }

    @Test
    void isValidByteRangeAndValidateByteRangeTest() {
        final int n = 5;
        final int[] at = {1, 2, 3};
        final var codeSize = IntStream.of(at).map(x -> x + 1).sum();
        final var lr = getLinesOfCodeOfAt(n, WithExtraBytes.YES, at);
        final var sut = new Editor(lr.lines());
        //        System.out.printf(
        //                "isValidByteRangeAndValidateByteRangeTest codeSize %d, byteRange %s
        // equals? %s",
        //                codeSize,
        //                sut.getByteRangeFromLines(lr.range()),
        //                sut.getByteRangeFromLines(lr.range()).equals(new Range<Byte>(0,
        // codeSize)));
        assertThat(sut.getByteRangeFromLines(lr.range())).isEqualTo(new Range<Byte>(0, codeSize));

        record TC(@NonNull Range<Line> lRange, @NonNull Range<Byte> bRange, boolean expected) {
            public TC(int lineFrom, int lineTo, int byteFrom, int byteTo, boolean expected) {
                this(new Range<Line>(lineFrom, lineTo), new Range<Byte>(byteFrom, byteTo), expected);
            }
        }

        final TC[] testCases = {
            new TC(0, 5, 3, 2, false),
            new TC(0, 5, -1, 5, false),
            new TC(0, 5, 0, 9, true),
            new TC(0, 5, 0, 10, false),
            new TC(1, 5, 0, 9, true),
            new TC(2, 5, 0, 9, false),
            new TC(2, 5, 1, 9, false),
            new TC(2, 5, 2, 9, true),
            new TC(2, 5, 3, 9, true),
        };

        final var isValidAsserts = new ArrayList<Executable>(100);
        final var validateAsserts = new ArrayList<Executable>(100);

        int nn = 0;
        for (final var testCase : testCases) {
            final var lRange = testCase.lRange();
            final var bRange = testCase.bRange();
            final var expected = testCase.expected();
            final int nnn = nn;
            isValidAsserts.add(() -> assertThat(sut.isValidByteRange(lRange, bRange))
                    .as("isValidByteRange %d: lines %s bytes %s expected %s", nnn, lRange, bRange, expected)
                    .isEqualTo(testCase.expected));
            validateAsserts.add(
                    testCase.expected
                            ? () -> assertThat(sut.validateByteRange(lRange, bRange))
                                    .as("validateByteRange valid %d: lines %s bytes %s", nnn, lRange, bRange)
                                    .isEqualTo(bRange)
                            : () -> assertThatIllegalArgumentException()
                                    .as("validateByteRange throws %d: lines %s bytes" + " %s", nnn, lRange, bRange)
                                    .isThrownBy(() -> sut.validateByteRange(lRange, bRange)));
            nn++;
        }
        assertAll("isValidByteRange tests", isValidAsserts);
        assertAll("validateByteRange tests", validateAsserts);

        assertThatIllegalArgumentException()
                .as("throws when Range<Byte> 2nd arg is null")
                .isThrownBy(() -> sut.validateByteRange(new Range<Line>(0, 0), null));
    }

    private static List<Edit> makeEdits(int[] fromToPairs) {
        if (0 != fromToPairs.length % 2)
            throw new IllegalArgumentException("must have even number of ints to make from-to pairs");

        final var r = new ArrayList<Edit>(fromToPairs.length / 2);
        for (int i = 0; i < fromToPairs.length; i += 2) {
            var edit = new Edit(new Range<Line>(fromToPairs[i], fromToPairs[i + 1]), List.of(), Range.empty());
            r.add(edit);
        }
        return r;
    }

    @Test
    void hasNoOverlappingEditsTest() {

        record TC(@NonNull List<Edit> lines, boolean expected) {
            TC(boolean expected, int... fromToPairs) {
                this(makeEdits(fromToPairs), expected);
            }
        }

        final TC[] testCases = {
            new TC(true),
            new TC(true, 0, 10),
            new TC(true, 0, 5, 10, 15),
            new TC(true, 0, 5, 5, 15),
            new TC(true, 2, 5, 5, 15, 20, 25),
            new TC(true, 2, 5, 6, 15, 15, 25),
            new TC(true, 0, 5, 5, 6),
            new TC(true, 0, 5, 10, 15, 6, 7),
            new TC(true, 12, 13, 13, 14),
            new TC(false, 0, 1, 0, 10),
            new TC(false, 0, 10, 0, 1),
            new TC(false, 0, 10, 9, 10),
            new TC(false, 0, 10, 9, 20),
            new TC(false, 0, 10, 2, 3),
            new TC(false, 0, 10, 2, 4),
            new TC(false, 12, 12, 12, 12),
            new TC(false, 12, 13, 12, 13),
            new TC(false, 12, 12, 10, 14),
            new TC(true, 12, 12, 12, 13),
            new TC(true, 12, 12, 10, 13),
            new TC(true, 12, 12, 10, 12),
        };

        final var hasNoOverlappingEditsAsserts = new ArrayList<Executable>(100);
        int n = 0;
        for (final var tc : testCases) {
            final int nn = n;
            hasNoOverlappingEditsAsserts.add(() -> assertThat(Editor.hasNoOverlappingEdits(tc.lines))
                    .as("expected %s case %d: %s".formatted(tc.expected, nn, tc.lines))
                    .isEqualTo(tc.expected));

            n++;
        }
        assertAll("hasNoOverlappingEdits tests", hasNoOverlappingEditsAsserts);
    }

    @SuppressWarnings("UnstableApiUsage")
    private Collection<List<Edit>> permutations(@NonNull final List<Edit> elements) {
        // Guava supplies an iterator over all permutations of a given collection
        return Collections2.permutations(elements);
    }

    @Test
    void arrangeEditsForMergeTest() {

        record TC(@NonNull List<Edit> lines, boolean expected) {
            TC(boolean expected, int... fromToPairs) {
                this(makeEdits(fromToPairs), expected);
            }
        }

        final TC[] testCases = {
            // In each test case edits are _in  order_ for ease of readability - but the true
            // _expected_ value is the _reverse order_!
            new TC(true),
            new TC(true, 0, 10),
            new TC(true, 0, 5, 10, 15),
            new TC(true, 0, 5, 5, 10),
            new TC(true, 2, 5, 5, 15, 20, 25),
            new TC(true, 2, 5, 6, 15, 15, 25),
            new TC(true, 12, 13, 13, 14, 14, 15, 20, 25, 30, 35),
        };

        for (final var tc : testCases) {
            final var expected = new ArrayList<Edit>(tc.lines);
            Collections.reverse(expected);
            for (final var perm : permutations(tc.lines)) {
                final var actual = new ArrayList<Edit>(perm);
                Editor.arrangeEditsForMerge(actual);
                assertThat(actual).isEqualTo(expected);
            }
        }

        final var badCase = new TC(true, 0, 5, 3, 10);
        assertThatIndexOutOfBoundsException().isThrownBy(() -> {
            Editor.arrangeEditsForMerge(badCase.lines);
        });
    }

    @Test
    void editGetKindTest() {
        final int n = 5;
        final int[] at = {1, 2, 3};
        final var codeSize = IntStream.of(at).map(x -> x + 1).sum();
        final var lr = getLinesOfCodeOfAt(n, WithExtraBytes.YES, at);
        final var sut = new Editor(lr.lines());
        assumeThat(sut.getByteRangeFromLines(lr.range())).isEqualTo(new Range<Byte>(0, codeSize + 1));

        // TODO
    }

    private @NonNull List<Line> gatherLines(@NonNull List<Line> input, @NonNull int[] elements) {
        var r = new ArrayList<Line>(elements.length);
        for (int i : elements) r.add(input.get(i));
        return r;
    }

    private @NonNull List<Line> selectLines(
            @NonNull List<Line> input1, @NonNull List<Line> input2, @NonNull float[] elements) {
        final var r = new ArrayList<Line>(elements.length);
        for (final var f : elements) r.add(Math.copySign(1.0f, f) >= 0 ? input1.get((int) f) : input2.get((int) -f));
        return r;
    }

    @Test
    void mergeTestCodeOnly() {
        final var baseLines =
                getLinesOfCodeOfAt(4, WithExtraBytes.NO, 0, 1, 2, 3).lines();
        final var insertLines =
                getLinesOfCodeOfAt(4, WithExtraBytes.NO, 0, 1, 3, 3).lines();

        {
            // DELETE
            record TCD(int from, int to, float... mergeLines) {}
            final TCD[] deleteTestCases = {
                new TCD(0, 1, 1, 2, 3),
                new TCD(0, 2, 2, 3),
                new TCD(0, 3, 3),
                new TCD(0, 4),
                new TCD(1, 2, 0, 2, 3),
                new TCD(1, 3, 0, 3),
                new TCD(1, 4, 0),
                new TCD(2, 3, 0, 1, 3),
                new TCD(2, 4, 0, 1),
                new TCD(3, 4, 0, 1, 2)
            };

            for (int n = 0; n < deleteTestCases.length; n++) {
                final var tcd = deleteTestCases[n];
                final var expectedLines = selectLines(baseLines, Collections.emptyList(), tcd.mergeLines);

                final var deleteRange = new Range<Line>(tcd.from, tcd.to);
                final var sut = new Editor(new ArrayList<>(baseLines));
                sut.add(deleteRange, Collections.emptyList());
                final var actual = sut.merge();
                assertThat(actual)
                        .as("delete %d %s".formatted(n, deleteRange))
                        .isEqualTo(expectedLines); // test _input lines_ are in actual (by `equals`)
            }
        }

        {
            // INSERT
            record TCI(int at, int nToInsert, float... mergeLines) {}
            final TCI[] insertTestCases = {
                new TCI(0, 1, -0.0f, 0, 1, 2, 3),
                new TCI(0, 2, -0.0f, -1, 0, 1, 2, 3),
                new TCI(0, 3, -0.0f, -1, -2, 0, 1, 2, 3),
                new TCI(1, 1, 0, -0.0f, 1, 2, 3),
                new TCI(1, 2, 0, -0.0f, -1, 1, 2, 3),
                new TCI(1, 3, 0, -0.0f, -1, -2, 1, 2, 3),
                new TCI(2, 1, 0, 1, -0.0f, 2, 3),
                new TCI(2, 2, 0, 1, -0.0f, -1, 2, 3),
                new TCI(2, 3, 0, 1, -0.0f, -1, -2, 2, 3),
                new TCI(3, 1, 0, 1, 2, -0.0f, 3),
                new TCI(4, 1, 0, 1, 2, 3, -0.0f),
                new TCI(4, 2, 0, 1, 2, 3, -0.0f, -1)
            };

            for (int n = 0; n < insertTestCases.length; n++) {
                final var tci = insertTestCases[n];
                final var expectedLines = selectLines(baseLines, insertLines, tci.mergeLines);

                final var insertRange = new Range<Line>(tci.at, tci.at);
                final var sut = new Editor(new ArrayList<>(baseLines));
                sut.add(insertRange, new ArrayList<>(insertLines.subList(0, tci.nToInsert)));
                final var actual = sut.merge();
                assertThat(actual).as("insert %d %s".formatted(n, insertRange)).isEqualTo(expectedLines);
            }
        }

        {
            // REPLACE
            record TCR(int at, int nToReplace, int nToInsert, float... mergeLines) {}
            final TCR[] replaceTestCases = {
                new TCR(0, 1, 1, -0.0f, 1, 2, 3),
                new TCR(0, 2, 1, -0.0f, 2, 3),
                new TCR(0, 4, 1, -0.0f),
                new TCR(0, 1, 2, -0.0f, -1, 1, 2, 3),
                new TCR(0, 1, 3, -0.0f, -1, -2, 1, 2, 3),
                new TCR(1, 1, 1, 0, -0.0f, 2, 3),
                new TCR(1, 1, 2, 0, -0.0f, -1, 2, 3),
                new TCR(3, 1, 1, 0, 1, 2, -0.0f),
            };

            for (int n = 0; n < replaceTestCases.length; n++) {
                final var tcr = replaceTestCases[n];
                final var expectedLines = selectLines(baseLines, insertLines, tcr.mergeLines);

                final var insertRange = new Range<Line>(tcr.at, tcr.at + tcr.nToReplace);
                final var sut = new Editor(new ArrayList<>(baseLines));
                sut.add(insertRange, new ArrayList<>(insertLines.subList(0, tcr.nToInsert)));
                final var actual = sut.merge();
                assertThat(actual).as("replace %d %s".formatted(n, insertRange)).isEqualTo(expectedLines);
            }
        }
    }

    @Test
    void editorToStringTest() {
        {
            final var lr = getSomeLines(0);
            final var sut = new Editor(lr.lines());
            assertThat(sut.toString(Editor.Verbose.NO))
                    .as("0 lines no edits")
                    .isEqualTo("Editor[0 baselines, 0 edits {DELETE=0, INSERT=0, REPLACE=0}]");
            assertThat(sut.toString(Editor.Verbose.YES))
                    .as("0 lines no edits, verbose")
                    .isEqualTo(
                            """
                        Editor[0 baselines, 0 edits {DELETE=0, INSERT=0, REPLACE=0} \
                        baselines: [] edits: []]""");
        }

        final int n = 4;
        final int[] at = {1, 2, 3};
        final var lr = getLinesOfCodeOfAt(n, WithExtraBytes.YES, at);
        final var linesDisplay = lr.lines().toString();

        {
            final var sut = new Editor(List.copyOf(lr.lines()));
            assertThat(sut.toString(Editor.Verbose.NO))
                    .as("4 lines no edits")
                    .isEqualTo("Editor[4 baselines, 0 edits {DELETE=0, INSERT=0, REPLACE=0}]");
            // spotless:off
            assertThat(sut.toString(Editor.Verbose.YES))
                    .as("4 lines no edits, verbose")
                    .isEqualTo(
                            """
                        Editor[4 baselines, 0 edits {DELETE=0, INSERT=0, REPLACE=0} \
                        baselines: %s \
                        edits: []]""".formatted(linesDisplay));
            // spotless:on
        }

        {
            final var sut = new Editor(List.copyOf(lr.lines()));
            sut.add(new Range<Line>(0, 0), List.<Line>of(), new Range<Byte>(0, 0));
            assertThat(sut.toString(Editor.Verbose.NO))
                    .as("4 lines adding empty everything therefore null edit")
                    .isEqualTo("Editor[4 baselines, 0 edits {DELETE=0, INSERT=0, REPLACE=0}]");
            // spotless:off
            assertThat(sut.toString(Editor.Verbose.YES))
                    .as("4 lines adding empty everything therefore null edit, verbose")
                    .isEqualTo(
                            """
                        Editor[4 baselines, 0 edits {DELETE=0, INSERT=0, REPLACE=0} \
                        baselines: %s \
                        edits: []]""".formatted(linesDisplay));
            // spotless:on
        }

        {
            final var sut = new Editor(List.copyOf(lr.lines()));
            sut.add(new Range<Line>(1, 1), List.<Line>of(new CommentLine("inserted")));
            assertThat(sut.toString(Editor.Verbose.NO))
                    .as("4 lines adding comment @1")
                    .isEqualTo("Editor[4 baselines, 1 edits {DELETE=0, INSERT=1, REPLACE=0}]");
            // spotless:off
            assertThat(sut.toString(Editor.Verbose.YES))
                    .as("4 lines adding comment @1, verbose")
                    .isEqualTo(
                            """
                        Editor[4 baselines, 1 edits {DELETE=0, INSERT=1, REPLACE=0} \
                        baselines: %s \
                        edits: [Edit{lineRange=Range[0x0001,0x0001), byteRange=Range[0x0000,0x0000), \
                        newLines=[CommentLine[comment=inserted]]}]]""".formatted(linesDisplay));
            // spotless:on
        }

        {
            final var sut = new Editor(List.copyOf(lr.lines()));
            sut.add(new Range<Line>(1, 2), List.<Line>of());
            assertThat(sut.toString(Editor.Verbose.NO))
                    .as("4 lines deleting codeline @1")
                    .isEqualTo("Editor[4 baselines, 1 edits {DELETE=1, INSERT=0, REPLACE=0}]");
            // spotless:off
            assertThat(sut.toString(Editor.Verbose.YES))
                    .as("4 lines deleting codeline @1, verbose")
                    .isEqualTo(
                            """
                            Editor[4 baselines, 1 edits {DELETE=1, INSERT=0, REPLACE=0} \
                            baselines: %s \
                            edits: [Edit{lineRange=Range[0x0001,0x0002), byteRange=Range[0x0000,0x0002), \
                            newLines=[]}]]""".formatted(linesDisplay));
            // spotless:on
        }

        {
            final var sut = new Editor(List.copyOf(lr.lines()));
            sut.add(new Range<Line>(1, 2), List.<Line>of(new CommentLine("replaced")));
            assertThat(sut.toString(Editor.Verbose.NO))
                    .as("4 lines replacing codeline @1")
                    .isEqualTo("Editor[4 baselines, 1 edits {DELETE=0, INSERT=0, REPLACE=1}]");
            // spotless:off
            assertThat(sut.toString(Editor.Verbose.YES))
                    .as("4 lines replacing codeline @1, verbose")
                    .isEqualTo(
                            """
                        Editor[4 baselines, 1 edits {DELETE=0, INSERT=0, REPLACE=1} \
                        baselines: %s \
                        edits: [Edit{lineRange=Range[0x0001,0x0002), byteRange=Range[0x0000,0x0002), \
                        newLines=[CommentLine[comment=replaced]]}]]""".formatted(linesDisplay));
        }
        // spotless:on
    }

    @NonNull
    LinesWithRange asLinesWithRange(@NonNull final List<Line> lines) {
        return new LinesWithRange(lines, new Range<Line>(0, lines.size()));
    }

    @NonNull
    LinesWithRange getSomeLines(final int n) {
        return asLinesWithRange(Stream.generate(() -> new CommentLine("lorem ipsum"))
                .map(Line.class::cast)
                .limit(n)
                .toList());
    }

    @NonNull
    LinesWithRange get4LinesWithNoCode() {
        return asLinesWithRange(List.<Line>of(
                new CommentLine("a comment"),
                new DirectiveLine("FOO", "Bar", "eh?"),
                new DirectiveLine("FOO", "Zebra", "NVM"),
                new CommentLine("another comment")));
    }

    @NonNull
    LinesWithRange getExactly1LineOfCode() {
        return asLinesWithRange(List.<Line>of(new CodeLine(0, 0, null, "")));
    }

    @NonNull
    LinesWithRange get1LineOfCodeOfAt(final int of, final int at) {
        final var lines = new ArrayList<Line>(of);
        for (int i = 0; i < of; i++) lines.add(i, new CommentLine("comment " + Integer.toString(i)));
        lines.set(at, new CodeLine(0, 0, null, null));
        return asLinesWithRange(lines);
    }

    enum WithExtraBytes {
        NO,
        YES
    };

    private static final byte[] NO_BYTES = new byte[0];

    @NonNull
    LinesWithRange getLinesOfCodeOfAt(final int of, WithExtraBytes withExtraBytes, final int... at) {
        final var lines = new ArrayList<Line>(of);
        // Fill with nulls (there's no "set size to n")
        for (int i = 0; i < of; i++) lines.add(null);

        // Set our known code lines
        int offset = 0;
        int n = 0;
        for (final int j : at) {
            final int extraBytes = withExtraBytes == WithExtraBytes.YES ? j : 0;
            lines.set(
                    j,
                    new CodeLine(offset, 0, new byte[extraBytes], "original code # at index [%d,%d]".formatted(n, j)));
            offset += 1 + extraBytes;
            n++;
        }

        // Fill in the remaining slots with comment lines
        for (int k = 0; k < of; k++)
            if (null == lines.get(k)) lines.set(k, new CommentLine("original comment at index [%d]".formatted(k)));

        return asLinesWithRange(lines);
    }
}
