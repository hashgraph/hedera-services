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

package com.hedera.services.cli.contracts.assembly;

import static java.util.stream.Collectors.toMap;
import static java.util.stream.Collectors.toSet;

import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableSortedMap;
import edu.umd.cs.findbugs.annotations.NonNull;
import edu.umd.cs.findbugs.annotations.Nullable;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.EnumSet;
import java.util.Objects;
import java.util.Set;
import java.util.regex.Pattern;
import java.util.stream.IntStream;
import java.util.stream.Stream;

/**
 * Contains a type (Descr) describing an EVM opcode, and table lookup
 *
 * <p>Contained record type Descr describes an EVM opcode, and there is table lookup to get from a
 * byte to the opcode (mnemonic) or the other way around.
 */
public class Opcodes {

    /** Properties associated with EVM opcodes, categorizing them */
    public enum Properties {
        CALL,
        RETURN,
        JUMP1WAY,
        JUMP2WAY,
        JUMPDEST,
        TERMINAL,
        UNASSIGNED;

        public static @NonNull Set<Properties> parse(@Nullable final String s) {
            if (null == s || s.isEmpty()) return EnumSet.noneOf(Properties.class);
            final var values =
                    splitter.splitAsStream(s).map(Properties::valueOf).toList();
            return EnumSet.copyOf(values);
        }

        private static final Pattern splitter = Pattern.compile(",");
    }

    /**
     * Describes an EVM opcode
     *
     * @param opcode - value of the opcode, e.g., 0x62 for a "push 2 bytes" instruction
     * @param extraBytes - how many extra bytes go with this opcode, e.g., 2 for "push 2 bytes"
     * @param mnemonic - mnemonic for this opcode, e.g., "PUSH2" for "push 2 bytes" instruction
     */
    public record Descr(int opcode, int extraBytes, @NonNull String mnemonic, @NonNull Set<Properties> properties) {

        public Descr {
            Objects.requireNonNull(mnemonic);
            Objects.requireNonNull(properties);
        }

        public Descr(int opcode, final @NonNull String mnemonic) {
            this(opcode, 0, mnemonic, EMPTY_PROPERTIES);
        }

        public Descr(int opcode, final @NonNull String mnemonic, @NonNull Set<Properties> properties) {
            this(opcode, 0, mnemonic, properties);
        }

        public Descr(int opcode, int extraBytes, final @NonNull String mnemonic) {
            this(opcode, extraBytes, mnemonic, EMPTY_PROPERTIES);
        }

        public boolean isAssigned() {
            return !properties.contains(Properties.UNASSIGNED);
        }

        public boolean isBasicBlockStart() {
            return properties.contains(Properties.JUMPDEST);
        }

        public boolean isBasicBlockEnd() {
            return properties.contains(Properties.JUMP1WAY)
                    || properties.contains(Properties.JUMP2WAY)
                    || properties.contains(Properties.RETURN)
                    || properties.contains(Properties.TERMINAL);
            // The definition of the _end_ of a basic block includes _call_ instructions that
            // _can not return_.  But this predicate isn't strong enough to determine that (since
            // it requires context of the _called_ basic block).  So, for a _call_ instruction,
            // this will always return _false_.
        }

        public boolean isTerminal() {
            return properties.contains(Properties.TERMINAL);
        }

        @Override
        public String toString() {
            final var extra = extraBytes > 0 ? "+%d".formatted(extraBytes) : "";
            final var props = properties.isEmpty() ? "" : trimMatched("[]", properties.toString());
            var sb = new StringBuilder();
            sb.append(extra);
            if (!extra.isEmpty() && !props.isEmpty()) sb.append(", ");
            sb.append(props);
            return "%s(%#02X)[%s]".formatted(mnemonic, opcode, sb);
        }

        static final Set<Properties> EMPTY_PROPERTIES = Set.of();
    }

    /** Table of opcodes, indexed by opcode */
    @NonNull
    private static final ImmutableList<Descr> byOpcode;

    /** Get an opcode Descr, given opcode value */
    public static @NonNull Descr getOpcode(int opcode) {
        return byOpcode.get(opcode & 0xFF);
    }

    /** Table of opcodes, indexed by mnemonic */
    @NonNull
    private static final ImmutableSortedMap<String, Descr> byMnemonic;

    /** Get an opcode Descr, given mnemonic */
    public static @NonNull Descr getDescrFor(@NonNull final String mnemonic) {
        Objects.requireNonNull(mnemonic);
        final var descr = Opcodes.byMnemonic.get(mnemonic.toUpperCase());
        if (null == descr) throw new IllegalArgumentException("opcode '%s' not found in table".formatted(mnemonic));
        return descr;
    }

    static {
        // Create the opcode tables ... see https://ethereum.org/en/developers/docs/evm/opcodes/

        var descrs = new ArrayList<Descr>(256);

        // Start with all the unique/ordinary opcodes
        """
          00 STOP           TERMINAL
          01 ADD
          02 MUL
          03 SUB
          04 DIV
          05 SDIV
          06 MOD
          07 SMOD
          08 ADDMOD
          09 MULMOD
          0A EXP
          0B SIGNEXTEND
          10 LT
          11 GT
          12 SLT
          13 SGT
          14 EQ
          15 ISZERO
          16 AND
          17 OR
          18 XOR
          19 NOT
          1A BYTE
          1B SHL
          1C SHR
          1D SAR
          20 KECCAK256
          30 ADDRESS
          31 BALANCE
          32 ORIGIN
          33 CALLER
          34 CALLVALUE
          35 CALLDATALOAD
          36 CALLDATASIZE
          37 CALLDATACOPY
          38 CODESIZE
          39 CODECOPY
          3A GASPRICE
          3B EXTCODESIZE
          3C EXTCODECOPY
          3D RETURNDATASIZE
          3E RETURNDATACOPY
          3F EXTCODEHASH
          40 BLOCKHASH
          41 COINBASE
          42 TIMESTAMP
          43 NUMBER
          44 PREVRANDAO
          45 GASLIMIT
          46 CHAIND
          47 SELFBALANCE
          48 BASEFEE
          50 POP
          51 MLOAD
          52 MSTORE
          53 MSTORE8
          54 SLOAD
          55 SSTORE
          56 JUMP           JUMP1WAY
          57 JUMPI          JUMP2WAY
          58 PC
          59 MSIZE
          5A GAS
          5B JUMPDEST       JUMPDEST
          F0 CREATE
          F1 CALL           CALL
          F2 CALLCODE       CALL
          F3 RETURN         RETURN
          F4 DELEGATECALL   CALL
          F5 CREATE2
          FA STATICCALL     CALL
          FD REVERT         TERMINAL
          FE INVALID        TERMINAL
          FF SELFDESTRUCT   TERMINAL
          """
                .lines()
                .forEach(line -> {
                    final var no = line.split(" +");
                    final var op = Integer.parseUnsignedInt(no[0], 16);
                    final var properties = Properties.parse(no.length > 2 ? no[2] : "");
                    descrs.add(new Descr(op, no[1], properties));
                });

        // Add all the "multiple" opcodes
        for (int i = 1; i <= 32; i++) {
            descrs.add(new Descr(0x60 + i - 1, i, "PUSH" + Integer.toString(i)));
        }
        for (int i = 1; i <= 16; i++) {
            descrs.add(new Descr(0x80 + i - 1, "DUP" + Integer.toString(i)));
        }
        for (int i = 1; i <= 16; i++) {
            descrs.add(new Descr(0x90 + i - 1, "SWAP" + Integer.toString(i)));
        }
        for (int i = 0; i <= 4; i++) {
            descrs.add(new Descr(0xA0 + i, "LOG" + Integer.toString(i)));
        }
        // Add all the unassigned (thus invalid) opcodes
        concatStreams(
                        intRange(0x0C, 0x0F),
                        intRange(0x1E, 0x1F),
                        intRange(0x21, 0x2F),
                        intRange(0x49, 0x4F),
                        intRange(0x5C, 0x5F),
                        intRange(0xA5, 0xEF),
                        intRange(0xF6, 0xF9),
                        intRange(0xFB, 0xFC))
                // (Say, you'd think Guava's `RangeSet` would be perfect for this ... except you
                // can't iterate it.  A set which you can't iterate over its members ... it's a good
                // idea, because ... I dunno ...)
                .forEach(i -> {
                    final var n = "%02X".formatted(i);
                    descrs.add(new Descr(i, "UNASSIGNED-" + n, EnumSet.of(Properties.UNASSIGNED)));
                });

        // Validate we've got all 256 opcodes defined
        final var allOpcodes = descrs.stream().map(Descr::opcode).collect(toSet());

        // sanity check this data constructor ...
        if (256 != allOpcodes.size()) {
            throw new IllegalStateException(
                    "EVM opcode table incomplete, only %s opcodes present".formatted(allOpcodes.size()));
        }

        descrs.sort(Comparator.comparingInt(Descr::opcode));
        byOpcode = ImmutableList.copyOf(descrs);
        byMnemonic = ImmutableSortedMap.copyOf(descrs.stream().collect(toMap(Descr::mnemonic, d -> d)));
    }

    /** Returns a Stream&lt;Integer&gt; of a range of integers, inclusive of both ends */
    private static Stream<Integer> intRange(int low, int high) {
        return IntStream.rangeClosed(low, high).boxed();
    }

    /** Variadic Stream concatenation (why isn't this part of Java Streams class?) */
    @SafeVarargs
    private static @NonNull Stream<Integer> concatStreams(@NonNull final Stream<Integer>... sis) {
        Objects.requireNonNull(sis);
        Stream<Integer> s = Stream.empty();
        for (var str : sis) s = Stream.concat(s, str);
        return s;
    }

    /** Utility method to trim matched "brackets" from the ends of a string */
    private static @NonNull String trimMatched(@NonNull final String brackets, @NonNull final String s) {
        Objects.requireNonNull(brackets);
        Objects.requireNonNull(s);
        if (brackets.length() < 2) throw new IllegalArgumentException("brackets too short");
        if (s.length() < 2) return s;
        if (s.charAt(0) != brackets.charAt(0)) return s;
        if (s.charAt(s.length() - 1) != brackets.charAt(1)) return s;
        return s.substring(1, s.length() - 1);
    }

    /** Provide a formatted EVM opcode table (as a multi-line string) */
    public static @NonNull String formatOpcodeTable() {
        final var LARGE_BLUE_CIRCLE = "\uD83D\uDD35";
        var sb = new StringBuilder();
        for (int i = 0; i < 256; i++) {
            final var opcode = getOpcode(i);
            final var bbstart = opcode.isBasicBlockStart();
            final var bbend = opcode.isBasicBlockEnd();
            final var lineStart = sb.length();
            sb.append("%3d: ".formatted(i));
            sb.append(opcode);
            if (bbstart || bbend) {
                sb.append("                                        ");
                sb.setLength(lineStart + 40);
                sb.append(LARGE_BLUE_CIRCLE + " basic block");
                if (bbstart) sb.append(" START");
                if (bbend) sb.append(" END");
            }
            sb.append('\n');
        }
        return sb.toString();
    }

    private Opcodes() {
        throw new UnsupportedOperationException("Utility class");
    }
}
