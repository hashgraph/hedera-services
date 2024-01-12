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

package com.hedera.services.cli.contracts.analysis;

import static java.util.stream.Collectors.toCollection;

import com.hedera.services.cli.contracts.assembly.Code;
import com.hedera.services.cli.contracts.assembly.CodeLine;
import com.hedera.services.cli.contracts.assembly.DataPseudoOpLine;
import com.hedera.services.cli.contracts.assembly.LabelLine;
import com.hedera.services.cli.contracts.assembly.MacroLine;
import edu.umd.cs.findbugs.annotations.NonNull;
import edu.umd.cs.findbugs.annotations.Nullable;
import java.math.BigInteger;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.stream.Stream;

public class VTableEntryRecognizer extends CodeRecognizer {

    // This is an example of a recognizer for "macroizing" EVM contract assembly code.  Where
    // "macroizing" means recognizing (reasonably) short sequences of opcodes with their operands
    // and replacing them with a short pseudo-op with meaningful operands.

    public VTableEntryRecognizer() {
        super();
    }

    public static final String METHODS_PROPERTY = "Methods";
    public static final String METHODS_TRACE = "Methods-Trace";

    @SuppressWarnings("java:S1192") // "String literals should not be duplicated" - would impair readability here
    public enum State {

        // States named "HAVE_<mnemonic>" are (automatically) the destination states for
        // transitions for the given opcode (by mnemonic)

        START(null, "DUP1", "PUSH1", "PUSH2" /*, "JUMPDEST" */) {
            @Override
            @NonNull
            public Optional<State> acceptCodeLine(@NonNull VTableEntryRecognizer recognizer, @NonNull CodeLine code) {
                recognizer.setStartOffset(code.codeOffset());
                return Optional.of(
                        switch (code.mnemonic()) {
                            case "DUP1" -> State.HAVE_DUP1;
                            case "PUSH1" -> {
                                recognizer.setJumpOffset(intFromBytes(code.operandBytes()));
                                yield State.HAVE_PUSH1;
                            }
                            case "PUSH2" -> {
                                if (recognizer.lastLineWasSelectorNode) {
                                    recognizer.setJumpOffset(intFromBytes(code.operandBytes()));
                                    yield State.HAVE_PUSH2_FAIL;
                                } else yield State.START;
                            }
                            default -> State.START;
                        });
            }
        },
        HAVE_DUP1("PUSH4") {
            @Override
            @NonNull
            public Optional<State> acceptCodeLine(@NonNull VTableEntryRecognizer recognizer, @NonNull CodeLine code) {
                recognizer.setSelector(longFromBytes(code.operandBytes()));
                return next();
            }
        },
        HAVE_PUSH4(new Next("HAVE_EQ_OR_GT"), "EQ", "GT") {
            @Override
            @NonNull
            public Optional<State> acceptCodeLine(@NonNull VTableEntryRecognizer recognizer, @NonNull CodeLine code) {
                recognizer.setNodeKind(code.mnemonic());
                return next();
            }
        },
        HAVE_EQ_OR_GT("PUSH2") {
            @Override
            @NonNull
            public Optional<State> acceptCodeLine(@NonNull VTableEntryRecognizer recognizer, @NonNull CodeLine code) {
                recognizer.setJumpOffset(intFromBytes(code.operandBytes()));
                return next();
            }
        },
        HAVE_PUSH2(new Next("START"), "JUMPI") {
            @Override
            @NonNull
            public Optional<State> acceptCodeLine(@NonNull VTableEntryRecognizer recognizer, @NonNull CodeLine code) {
                recognizer.setNextOffset(code.getNextCodeOffset());
                recognizer.gotSelectorNodeMatch();
                return next();
            }
        },
        HAVE_PUSH1(new Next("START"), "SHR") {
            @Override
            @NonNull
            public Optional<State> acceptCodeLine(@NonNull VTableEntryRecognizer recognizer, @NonNull CodeLine code) {
                if (recognizer.jumpOffset != 0xE0) return Optional.empty();
                recognizer.setNextOffset(code.getNextCodeOffset());
                recognizer.gotVTableStart();
                return next();
            }
        },
        HAVE_PUSH2_FAIL(new Next("START"), "JUMP") {
            @Override
            @NonNull
            public Optional<State> acceptCodeLine(@NonNull VTableEntryRecognizer recognizer, @NonNull CodeLine code) {
                recognizer.setNextOffset(code.getNextCodeOffset());
                recognizer.gotVTableFailJump();
                return next();
            }
        };

        @NonNull
        Optional<State> next() {
            if (null == nextState)
                throw new IllegalStateException(
                        "attempting to go to default next state where it must be computed, %s".formatted(this));
            return Optional.of(State.valueOf(nextState));
        }

        int intFromBytes(@NonNull final byte[] operand) {
            return new BigInteger(1, operand).intValue();
        }

        long longFromBytes(@NonNull final byte[] operand) {
            return new BigInteger(1, operand).longValue();
        }

        // State mechanism
        final String nextState;
        final @NonNull List<String> expectingMnemonic;

        record Next(@NonNull String next) {}

        State(@Nullable Next nextState, @NonNull final String... expectingMnemonic) {
            Objects.requireNonNull(expectingMnemonic);
            if (expectingMnemonic.length == 0)
                throw new IllegalArgumentException("State must have at least one mnemonic expected");
            this.nextState = null != nextState ? nextState.next : null;
            this.expectingMnemonic = Arrays.asList(expectingMnemonic);
        }

        /**
         * Compute the next state for this transition directly from the mnemonic (by prepending
         * "HAVE_")
         */
        State(@NonNull String expectingMnemonic) {
            // (Tried to "bind" the string to an enum instance by `State.valueOf` but _cannot do
            // that because `valueOf` doesn't recognize enum instances that have not yet been
            // constructed!_  A consequence of these being actual _instances_ rather than just
            // "fancy" constants. So have to bind later, at "run time".  But it isn't expensive,
            // so, whatever.)
            this(new Next("HAVE_" + expectingMnemonic), expectingMnemonic);
        }

        public abstract @NonNull Optional<State> acceptCodeLine(
                @NonNull VTableEntryRecognizer recognizer, @NonNull CodeLine code);

        public void transition(@NonNull final VTableEntryRecognizer recognizer, @NonNull final CodeLine code) {
            Objects.requireNonNull(recognizer);
            Objects.requireNonNull(code);

            // Tracing:
            Optional<State> nextState;
            if (expectingMnemonic.contains(code.mnemonic())
                    && (nextState = acceptCodeLine(recognizer, code)).isPresent()) {

                recognizer.trace("@%s: found %s, expected set %s, moving to %s"
                        .formatted(this, code.mnemonic(), expectingMnemonic, nextState));
                recognizer.advance(nextState.get());
            } else {
                recognizer.trace("@%s: found %s, did not find expected set %s, resetting"
                        .formatted(this, code.mnemonic(), expectingMnemonic));
                recognizer.reset();
            }
        }
    }

    State state;

    void advance(@NonNull final State next) {
        Objects.requireNonNull(next);
        trace("advance: moving to %s".formatted(next));
        state = next;
    }

    @Override
    protected void reset() {
        state = State.START;
        startByteOffset = nextByteOffset = 0;
        selector = jumpOffset = 0;
        lastLineWasSelectorNode = false;
        nodeKind = Kind.NONE;
        // N.B.: Do _not_ reset failOffset - it needs to persist to end of run
        trace("reset: state is START");
    }

    int startByteOffset;

    void setStartOffset(int offset) {
        trace("setStartOffset: %04X".formatted(offset));
        startByteOffset = offset;
    }

    int nextByteOffset;

    void setNextOffset(int offset) {
        trace("setNextOffset: %04X".formatted(offset));
        nextByteOffset = offset;
    }

    long selector;

    void setSelector(long selector) {
        trace("setSelector: %08X".formatted(selector));
        this.selector = selector;
    }

    enum Kind {
        NONE,
        INTERNAL,
        LEAF
    };

    Kind nodeKind;

    void setNodeKind(@NonNull final String mnemonic) {
        Objects.requireNonNull(mnemonic);
        nodeKind = switch (mnemonic) {
            case "EQ" -> Kind.LEAF;
            case "GT" -> Kind.INTERNAL;
            default -> Kind.NONE;};
        trace("setNodeKind: %s -> %s".formatted(mnemonic, nodeKind));
    }

    int jumpOffset;

    void setJumpOffset(int jumpOffset) {
        trace("setJumpOffset: %04X".formatted(jumpOffset));
        this.jumpOffset = jumpOffset;
    }

    // globals over entire run

    boolean lastLineWasSelectorNode;
    int failOffset;

    void setFailOffset(int failOffset) {
        trace("setFailOffset: %04X".formatted(failOffset));
        this.failOffset = failOffset;
    }

    @NonNull
    List<MacroLine> macros = new ArrayList<>();

    @NonNull
    List<String> trace = new ArrayList<>();

    void trace(@NonNull final String t) {
        Objects.requireNonNull(t);
        this.trace.add(t);
    }

    void gotSelectorNodeMatch() {
        trace("gotSelectorNodeMatch: startByteOffset %04X nextByteOffset %04X selector %08X jumpOffset %04X"
                .formatted(startByteOffset, nextByteOffset, selector, jumpOffset));

        lastLineWasSelectorNode = true;

        final var macro =
                switch (nodeKind) {
                    case LEAF -> new VTSelectorLine(
                            startByteOffset, nextByteOffset - startByteOffset, selector, jumpOffset);
                    case INTERNAL -> new VTSelectorTreeInternalNode(
                            startByteOffset, nextByteOffset - startByteOffset, selector, jumpOffset);
                    case NONE -> throw new IllegalStateException(
                            "Must have had EQ or GT to get here - but it's missing");
                };
        trace("gotSelectorNodeMatch: adding macro %s".formatted(macro.formatLine()));
        macros.add(macro);
    }

    void gotVTableStart() {
        trace("gotVTableStart: startByteOffset %04X nextByteOffset %04X".formatted(startByteOffset, nextByteOffset));

        final var macro = new VTSelectorTableStart(startByteOffset, nextByteOffset - startByteOffset);
        trace("gotVTableStart: adding macro %s".formatted(macro.formatLine()));
        macros.add(macro);
    }

    void gotVTableFailJump() {
        setFailOffset(jumpOffset);

        trace("gotVTableFailJump: startByteOffset %04X nextByteOffset %04X failOffset %04X"
                .formatted(startByteOffset, nextByteOffset, failOffset));

        final var macro = new VTSelectorLeafNodeEnd(startByteOffset, nextByteOffset - startByteOffset, failOffset);
        trace("gotVTableFailJump: adding macro %s".formatted(macro.formatLine()));
        macros.add(macro);
    }

    @Override
    public void begin() {
        trace("begin");
        setFailOffset(0);
        reset();
    }

    @Override
    public void acceptCodeLine(@NonNull final CodeLine code) {
        Objects.requireNonNull(code);
        trace("acceptCodeLine: %s".formatted(code));
        state.transition(this, code);
    }

    @Override
    public void acceptDataLine(@NonNull final DataPseudoOpLine data) {
        Objects.requireNonNull(data);
        trace("acceptDataLine: %s".formatted(data));
        reset();
    }

    public record MethodEntry(long selector, int methodOffset) {}

    @Override
    public @NonNull CodeRecognizer.Results end() {
        var vtLeafNodes = macros.stream()
                .filter(VTSelectorLine.class::isInstance)
                .map(VTSelectorLine.class::cast)
                .toList();
        var vtInternalNodes = macros.stream()
                .filter(VTSelectorTreeInternalNode.class::isInstance)
                .map(VTSelectorTreeInternalNode.class::cast)
                .toList();

        // Following idiom (stream-of-streams into "identity" flatmap) is hand-brewed stream concat
        var replacements = Stream.of(
                        macros.stream(),
                        vtLeafNodes.stream().map(VTSelectorLine::asLabel),
                        vtInternalNodes.stream().map(VTSelectorTreeInternalNode::asLabel),
                        Stream.of(new LabelLine(
                                failOffset, VTSelectorLeafNodeEnd.FAIL_DESTINATION, "selector not found/implemented")))
                .flatMap(s -> s)
                .map(Code.class::cast)
                .toList();

        var selectors = vtLeafNodes.stream()
                .map(vtsl -> new MethodEntry(vtsl.selector, vtsl.methodOffset))
                .collect(toCollection(ArrayList::new));
        trace("end: %d selectors found".formatted(selectors.size()));

        return new Results()
                .withReplacements(replacements)
                .withProperty(METHODS_PROPERTY, selectors)
                .withProperty(METHODS_TRACE, trace);
    }
}
