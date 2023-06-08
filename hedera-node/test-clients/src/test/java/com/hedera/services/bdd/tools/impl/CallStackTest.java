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

package com.hedera.services.bdd.tools.impl;

import static org.assertj.core.api.Assertions.assertThat;

import com.hedera.services.bdd.tools.impl.CallStack.WithLineNumbers;
import edu.umd.cs.findbugs.annotations.NonNull;
import java.lang.StackWalker.StackFrame;
import java.util.List;
import java.util.function.Predicate;
import java.util.stream.IntStream;
import java.util.stream.Stream;
import org.assertj.core.api.SoftAssertions;
import org.assertj.core.api.junit.jupiter.InjectSoftAssertions;
import org.assertj.core.api.junit.jupiter.SoftAssertionsExtension;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;

@ExtendWith(SoftAssertionsExtension.class)
class CallStackTest {

    @InjectSoftAssertions
    private SoftAssertions softly;

    @Test
    void dumpTest() {
        final var expected =
                """
            stack (depth captured: 9)
             0: com.hedera.services.bdd.tools.impl.CallStack.grabFrames
             1: com.hedera.services.bdd.tools.impl.CallStackTest$L5.knownLevel6
             2: com.hedera.services.bdd.tools.impl.CallStackTest$L3.knownLevel5
             3: com.hedera.services.bdd.tools.impl.CallStackTest$L4.knownLevel4
             4: com.hedera.services.bdd.tools.impl.CallStackTest$L3.knownLevel3
             5: com.hedera.services.bdd.tools.impl.CallStackTest.knownLevel2
             6: com.hedera.services.bdd.tools.impl.CallStackTest.knownLevel1
             7: com.hedera.services.bdd.tools.impl.CallStackTest.getKnownTestStackLimitedTo
             8: com.hedera.services.bdd.tools.impl.CallStackTest.dumpTest
             """;
        final var sut = getKnownTestStackLimitedTo(9);
        final var actual = sut.dump(WithLineNumbers.NO); // Not easy to test with `WithLineNumbers.YES`
        assertThat(actual).isEqualTo(expected);
    }

    @Test
    void integerLimitRespected() {
        softly.assertThat(getKnownTestStackLimitedTo(5).size()).isEqualTo(5);
        softly.assertThat(getKnownTestStackLimitedTo(15).size()).isEqualTo(15);
        softly.assertThat(getFullKnownTestStack().size()).isLessThanOrEqualTo(1000);
    }

    @Test
    void klassLimitRespected() {
        softly.assertThat(getKnownTestStackLimitedTo(L5.class).size()).isEqualTo(1);
        softly.assertThat(getKnownTestStackLimitedTo(L4.class).size()).isEqualTo(3);
        softly.assertThat(getKnownTestStackLimitedTo(L3.class).size()).isEqualTo(2);
        softly.assertThat(getKnownTestStackLimitedTo(Throwable.class).size()).isLessThanOrEqualTo(1000);
    }

    @Test
    void getTest() {
        final var sut = getKnownTestStackLimitedTo(5);
        softly.assertThat(CallStack.dump(sut.get(0), WithLineNumbers.NO))
                .isEqualTo("com.hedera.services.bdd.tools.impl.CallStack.grabFrames");
        softly.assertThat(CallStack.dump(sut.get(4), WithLineNumbers.NO))
                .isEqualTo("com.hedera.services.bdd.tools.impl.CallStackTest$L3.knownLevel3");

        softly.assertThatExceptionOfType(IndexOutOfBoundsException.class).isThrownBy(() -> sut.get(-1));
        softly.assertThatExceptionOfType(IndexOutOfBoundsException.class).isThrownBy(() -> sut.get(5));
    }

    @Test
    void framesTest() {
        final var sut = getKnownTestStackLimitedTo(5);
        for (int i = 0; i < 5; i++) {
            softly.assertThat(sut.frames().get(i)).isEqualTo(sut.get(i));
        }
    }

    @Test
    void getDeclaringClassOfFrameTest() {
        final var sut = getKnownTestStackLimitedTo(5);
        softly.assertThat(sut.getDeclaringClassOfFrame(0)).isEqualTo(CallStack.class);
        softly.assertThat(sut.getDeclaringClassOfFrame(3)).isEqualTo(L4.class);

        softly.assertThatExceptionOfType(IndexOutOfBoundsException.class)
                .isThrownBy(() -> sut.getDeclaringClassOfFrame(-1));
        softly.assertThatExceptionOfType(IndexOutOfBoundsException.class)
                .isThrownBy(() -> sut.getDeclaringClassOfFrame(5));
    }

    @Test
    void getMethodFromFrameTest() throws NoSuchMethodException {
        final var sut = getKnownTestStackLimitedTo(10);
        softly.assertThat(sut.getMethodFromFrame(0))
                .isEqualTo(CallStack.class.getDeclaredMethod("grabFrames", Integer.TYPE));
        softly.assertThat(sut.getMethodFromFrame(6))
                .isEqualTo(CallStackTest.class.getDeclaredMethod("knownLevel1", String.class));
    }

    @NonNull
    static Predicate<StackFrame> getPartialMethodNameMatcher(@NonNull final String name) {
        return sf -> sf.getMethodName().contains(name);
    }

    @Test
    void getTopmostFrameSatisfyingTest() {
        final var sut = getKnownTestStackLimitedTo(9);

        softly.assertThat(sut.getTopmostFrameSatisfying(getPartialMethodNameMatcher("Level5")))
                .isPresent()
                .contains(sut.get(2));
        softly.assertThat(sut.getTopmostFrameSatisfying(getPartialMethodNameMatcher("knownL")))
                .isPresent()
                .contains(sut.get(1));
        softly.assertThat(sut.getTopmostFrameSatisfying(getPartialMethodNameMatcher("TestStackLimited")))
                .isPresent()
                .contains(sut.get(7));
        softly.assertThat(sut.getTopmostFrameSatisfying(getPartialMethodNameMatcher("f00bar")))
                .isEmpty();
    }

    @Test
    void getTopmostFrameOfClassTest() {
        final var sut = getKnownTestStackLimitedTo(9);

        softly.assertThat(sut.getTopmostFrameOfClass(CallStack.class))
                .isPresent()
                .contains(sut.get(0));
        softly.assertThat(sut.getTopmostFrameOfClass(CallStackTest.L3.class))
                .isPresent()
                .contains(sut.get(2));
        softly.assertThat(sut.getTopmostFrameOfClass(CallStackTest.class))
                .isPresent()
                .contains(sut.get(5));
        softly.assertThat(sut.getTopmostFrameOfClass(Throwable.class)).isEmpty();
    }

    @Test
    void getTopmostFrameOfClassSatisfyingTest() {
        final var sut = getKnownTestStackLimitedTo(9);

        softly.assertThat(sut.getTopmostFrameOfClassSatisfying(
                        CallStackTest.class, getPartialMethodNameMatcher("Level2")))
                .isPresent()
                .contains(sut.get(5));
        softly.assertThat(sut.getTopmostFrameOfClassSatisfying(
                        CallStackTest.class, getPartialMethodNameMatcher("Level1")))
                .isPresent()
                .contains(sut.get(6));
        softly.assertThat(sut.getTopmostFrameOfClassSatisfying(
                        CallStackTest.class, getPartialMethodNameMatcher("LevelX")))
                .isEmpty();
        softly.assertThat(sut.getTopmostFrameOfClassSatisfying(CallStack.class, getPartialMethodNameMatcher("Level1")))
                .isEmpty();
    }

    // All following code is test scaffolding: Create a known stack to test with - we want different declaring classes,
    // different methods (at least)

    @NonNull
    CallStack getFullKnownTestStack() {
        knownTestStackRequestedLimit = 1000;
        knownTestStackRequestKlassLimit = Error.class; // will never be found on stack
        knownLevel1("xray");
        return knownTestStackLimitedToN;
    }

    @NonNull
    CallStack getKnownTestStackLimitedTo(final int n) {
        knownTestStackRequestedLimit = n;
        knownTestStackRequestKlassLimit = Error.class; // will never be found on stack
        knownLevel1("xray");
        return knownTestStackLimitedToN;
    }

    @NonNull
    CallStack getKnownTestStackLimitedTo(@NonNull final Class<?> klass) {
        knownTestStackRequestedLimit = 1000;
        knownTestStackRequestKlassLimit = klass;
        knownLevel1("xray");
        return knownTestStackLimitedToKlass;
    }

    int knownTestStackRequestedLimit;
    Class<?> knownTestStackRequestKlassLimit;
    CallStack knownTestStackLimitedToN;
    CallStack knownTestStackLimitedToKlass;

    String knownLevel1(@NonNull final String sl1) {
        return knownLevel2().toString() + sl1;
    }

    Integer knownLevel2() {
        final var l3 = new L3();
        return l3.knownLevel3("xray");
    }

    class L3 {
        Integer knownLevel3(@NonNull final String s) {
            final var l4 = new L4(this);
            return l4.knownLevel4(s).size();
        }

        Stream<Integer> knownLevel5(@NonNull final IntStream stream) {
            return new L5().knownLevel6(stream);
        }
    }

    class L4 {
        final L3 l3;

        L4(@NonNull final L3 l3) {
            this.l3 = l3;
        }

        List<Integer> knownLevel4(@NonNull final String s) {
            return l3.knownLevel5(s.chars()).toList();
        }
    }

    class L5 {
        Stream<Integer> knownLevel6(@NonNull final IntStream stream) {
            // Deepest frame of known test stack - grab callstacks
            // (Grab callstack each way because: why not?)
            knownTestStackLimitedToN = CallStack.grabFrames(knownTestStackRequestedLimit);
            knownTestStackLimitedToKlass = CallStack.grabFramesUpToButNotIncluding(knownTestStackRequestKlassLimit);
            return stream.boxed();
        }
    }
}
