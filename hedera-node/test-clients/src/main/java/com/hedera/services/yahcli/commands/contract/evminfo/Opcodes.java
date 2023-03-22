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

import static java.util.stream.Collectors.toMap;
import static java.util.stream.Collectors.toSet;

import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableSortedMap;
import edu.umd.cs.findbugs.annotations.NonNull;
import edu.umd.cs.findbugs.annotations.Nullable;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Comparator;
import java.util.EnumSet;
import java.util.Set;
import java.util.regex.Pattern;
import java.util.stream.Collectors;
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
        // Blocks
        CALL,
        RETURN,
        JUMP1WAY,
        JUMP2WAY,
        JUMPDEST,
        TERMINAL,

        // Stack manipulation: consumers
        VOID,
        EATS1,
        EATS2,
        EATS3,
        EATS4,
        EATS5,
        EATS6,

        // Stack manipulation: functions
        NULLARY,
        UNARY,
        BINARY,
        TERNARY,
        QUATERNARY,
        QUINARY,
        SENARY,
        SEPTENARY,
        OCTARY,
        NONARY,

        // Stack manipulation: special
        STACK_FIDDLE,

        // Opcodes with variants
        PUSH,
        DUP,
        SWAP,
        LOG,

        // Specific return types pushed on stack
        ADDRESS_RETURNED,
        BOOL_RETURNED,

        UNASSIGNED;

        @NonNull
        public static Set<Properties> parse(@Nullable final String s) {
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
     * @param variant - the nth opcode of a set, like 2 for "PUSH2" or 3 for "LOG3"
     * @param extraBytes - how many extra bytes go with this opcode, e.g., 2 for "push 2 bytes"
     * @param mnemonic - mnemonic for this opcode, e.g., "PUSH2" for "push 2 bytes" instruction
     */
    public record Descr(int opcode, int variant, int extraBytes, String mnemonic, Set<Properties> properties) {

        public Descr {
            properties = null != properties ? properties : EnumSet.noneOf(Properties.class);
        }

        public Descr(final int opcode, final @NonNull String mnemonic) {
            this(opcode, 0, 0, mnemonic, null);
        }

        public Descr(final int opcode, final @NonNull String mnemonic, @NonNull Set<Properties> properties) {
            this(opcode, 0, 0, mnemonic, properties);
        }

        public Descr(
                final int opcode,
                final @NonNull String mnemonic,
                @NonNull Set<Properties> properties,
                final int variant) {
            this(opcode, variant, 0, mnemonic, properties);
        }

        public Descr(final int opcode, final int extraBytes, final @NonNull String mnemonic) {
            this(opcode, 0, extraBytes, mnemonic, null);
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
            final var sb = new StringBuilder(30);
            sb.append(extra);
            if (!extra.isEmpty() && !props.isEmpty()) sb.append(", ");
            sb.append(props);
            return "%s(%#02X)[%s]".formatted(mnemonic, opcode, sb);
        }
    }

    /** Table of opcodes, indexed by opcode */
    @NonNull
    private static final ImmutableList<Descr> byOpcode;

    /** Get an opcode Descr, given opcode value */
    @NonNull
    public static Descr getOpcode(final int opcode) {
        return byOpcode.get(opcode & 0xFF);
    }

    /** Table of opcodes, indexed by mnemonic */
    private static final ImmutableSortedMap<String, Descr> byMnemonic;

    /** Get an opcode Descr, given mnemonic */
    @NonNull
    public static Descr getDescrFor(@NonNull final String mnemonic) {
        final var descr = Opcodes.byMnemonic.get(mnemonic.toUpperCase());
        if (null == descr) throw new IllegalArgumentException("opcode '%s' not found in table".formatted(mnemonic));
        return descr;
    }

    static {
        // Create the opcode tables ... see https://ethereum.org/en/developers/docs/evm/opcodes/ or https://evm.codes

        final var descrs = new ArrayList<Descr>(256);

        // Start with all the unique/ordinary opcodes
        """
          00 STOP           VOID                         TERMINAL
          01 ADD            BINARY
          02 MUL            BINARY
          03 SUB            BINARY
          04 DIV            BINARY
          05 SDIV           BINARY
          06 MOD            BINARY
          07 SMOD           BINARY
          08 ADDMOD         BINARY
          09 MULMOD         BINARY
          0A EXP            BINARY
          0B SIGNEXTEND     BINARY
          10 LT             BINARY     BOOL_RETURNED
          11 GT             BINARY     BOOL_RETURNED
          12 SLT            BINARY     BOOL_RETURNED
          13 SGT            BINARY     BOOL_RETURNED
          14 EQ             BINARY     BOOL_RETURNED
          15 ISZERO         UNARY      BOOL_RETURNED
          16 AND            BINARY
          17 OR             BINARY
          18 XOR            BINARY
          19 NOT            UNARY
          1A BYTE           BINARY
          1B SHL            BINARY
          1C SHR            BINARY
          1D SAR            BINARY
          20 KECCAK256      BINARY
          30 ADDRESS        NULLARY    ADDRESS_RETURNED
          31 BALANCE        UNARY
          32 ORIGIN         NULLARY    ADDRESS_RETURNED
          33 CALLER         NULLARY    ADDRESS_RETURNED
          34 CALLVALUE      NULLARY
          35 CALLDATALOAD   UNARY
          36 CALLDATASIZE   NULLARY
          37 CALLDATACOPY   EATS3
          38 CODESIZE       NULLARY
          39 CODECOPY       EATS3
          3A GASPRICE       NULLARY
          3B EXTCODESIZE    UNARY
          3C EXTCODECOPY    EATS4
          3D RETURNDATASIZE UNARY
          3E RETURNDATACOPY EATS3
          3F EXTCODEHASH    UNARY
          40 BLOCKHASH      UNARY
          41 COINBASE       NULLARY    ADDRESS_RETURNED
          42 TIMESTAMP      NULLARY
          43 NUMBER         NULLARY
          44 PREVRANDAO     NULLARY
          45 GASLIMIT       NULLARY
          46 CHAIND         NULLARY
          47 SELFBALANCE    NULLARY
          48 BASEFEE        NULLARY
          50 POP            EATS1
          51 MLOAD          UNARY
          52 MSTORE         EATS2
          53 MSTORE8        EATS2
          54 SLOAD          UNARY
          55 SSTORE         EATS2
          56 JUMP           EATS1                        JUMP1WAY
          57 JUMPI          EATS2                        JUMP2WAY
          58 PC             NULLARY
          59 MSIZE          NULLARY
          5A GAS            NULLARY
          5B JUMPDEST       VOID                         JUMPDEST
          F0 CREATE         TERNARY    ADDRESS_RETURNED
          F1 CALL           SEPTENARY  BOOL_RETURNED     CALL
          F2 CALLCODE       SEPTENARY  BOOL_RETURNED     CALL
          F3 RETURN         EATS2                        RETURN
          F4 DELEGATECALL   SENARY     BOOL_RETURNED     CALL
          F5 CREATE2        QUATERNARY ADDRESS_RETURNED
          FA STATICCALL     SENARY     BOOL_RETURNED     CALL
          FD REVERT         EATS2                        TERMINAL
          FE INVALID        VOID                         TERMINAL
          FF SELFDESTRUCT   UNARY                        TERMINAL
          """
                .lines()
                .forEach(line -> {
                    final var no = line.split(" +");
                    final var op = Integer.parseUnsignedInt(no[0], 16);
                    final var properties = Properties.parse(
                            no.length > 2 ? Arrays.stream(no, 2, no.length).collect(Collectors.joining(",")) : "");
                    descrs.add(new Descr(op, no[1], properties));
                });

        // Add all the "multiple" opcodes
        for (int i = 1; i <= 32; i++) {
            descrs.add(new Descr(0x60 + i - 1, i, i, "PUSH" + i, EnumSet.of(Properties.PUSH, Properties.NULLARY)));
        }
        for (int i = 1; i <= 16; i++) {
            descrs.add(new Descr(0x80 + i - 1, "DUP" + i, EnumSet.of(Properties.DUP, Properties.STACK_FIDDLE), i));
        }
        for (int i = 1; i <= 16; i++) {
            descrs.add(new Descr(0x90 + i - 1, "SWAP" + i, EnumSet.of(Properties.SWAP, Properties.STACK_FIDDLE), i));
        }
        for (int i = 0; i <= 4; i++) {
            descrs.add(new Descr(
                    0xA0 + i, "LOG" + i, EnumSet.of(Properties.LOG, Properties.valueOf("EATS" + (i + 2))), i));
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

    /** Returns a Stream<Integer> of a range of integers, inclusive of both ends */
    @NonNull
    private static Stream<Integer> intRange(final int low, final int high) {
        return IntStream.rangeClosed(low, high).boxed();
    }

    /** Variadic Stream concatenation (why isn't this part of Java Streams class?) */
    @SafeVarargs
    @NonNull
    private static Stream<Integer> concatStreams(final Stream<Integer>... sis) {
        Stream<Integer> s = Stream.empty();
        for (final var str : sis) s = Stream.concat(s, str);
        return s;
    }

    /** Utility method to trim matched "brackets" from the ends of a string */
    @NonNull
    private static String trimMatched(final @NonNull String brackets, final @NonNull String s) {
        if (brackets.length() < 2) throw new IllegalArgumentException("brackets too short");
        if (s.length() < 2) return s;
        if (s.charAt(0) != brackets.charAt(0)) return s;
        if (s.charAt(s.length() - 1) != brackets.charAt(1)) return s;
        return s.substring(1, s.length() - 1);
    }

    /** Provide a formatted EVM opcode table (as a multi-line string) */
    @NonNull
    public static String formatOpcodeTable() {
        final var LARGE_BLUE_CIRCLE = "\uD83D\uDD35";
        final var sb = new StringBuilder(25000);
        for (int i = 0; i < 256; i++) {
            final var opcode = getOpcode(i);
            final var bbstart = opcode.isBasicBlockStart();
            final var bbend = opcode.isBasicBlockEnd();
            final var lineStart = sb.length();
            sb.append("%3d: ".formatted(i));
            sb.append(opcode);
            if (bbstart || bbend) {
                sb.append("                                        ");
                sb.setLength(lineStart + 60);
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
