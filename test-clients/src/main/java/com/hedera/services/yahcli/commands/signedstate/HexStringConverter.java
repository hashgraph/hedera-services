package com.hedera.services.yahcli.commands.signedstate;

import static java.lang.Byte.toUnsignedInt;
import static org.apache.commons.codec.binary.Hex.decodeHex;

import java.util.regex.Pattern;
import org.jetbrains.annotations.Contract;
import org.jetbrains.annotations.NotNull;
import picocli.CommandLine.ITypeConverter;
import picocli.CommandLine.TypeConversionException;

public class HexStringConverter implements ITypeConverter<HexStringConverter.Bytes> {

  // Unfortunately can't return `short[]` directly from the custom type converter because Piccoli
  // gets confused: When the argument is declared (at the command class) it thinks it wants a
  // multi-value type (even if `arity=1` is specified). Thus: need to wrap it in a stupid class.
  // Would have used a record but Sonarlint complains (properly) that records with array members
  // need overrides for `equals`/`hashcode`/`toString`, and at that point, why bother?
  // See https://stackoverflow.com/a/74207195/751579.
  static public class Bytes {final short[] bytes; public Bytes(short[] b) { bytes=b;}}

  @Override
  public @NotNull Bytes convert(String value) throws Exception {
    if (0 != value.length() % 2) throw new TypeConversionException("-b bytecode must have even number of hexits");
    if (!Pattern.compile("[0-9a-fA-F]+").matcher(value).matches()) throw new TypeConversionException("-b bytecode has invalid characters (not hexits)");
    return new Bytes(toUnsignedBuffer(decodeHex(value)));
  }

  @Contract(pure = true)
  private @NotNull short[] toUnsignedBuffer(byte @NotNull [] signedBuffer) {
    // In Java, `byte[]` is a buffer of _signed_ bytes.  In the real world (and every other common
    // programming language) a byte array is a buffer of _unsigned_ bytes the way network programmers
    // and device I/O programmers and crypto programmers and bytecode interpreter programmers - in
    // fact, _every_ kind of programmer! - expects.  So here we take our buffer of signed bytes and
    // turn it into a buffer of _unsigned_ bytes, so all will be right with the world.  (Takes 2x
    // space, but that's a small price to pay for clearing away unnecessary cognitive effort by all
    // programmers trying to understand this app.) (Oh, and it also means the compiler/JITer won't
    // know that the array elements are restricted to 0..255, oh well.)
    short[] r = new short[signedBuffer.length];
    int i = 0;
    for (byte b : signedBuffer) r[i++] = (short)toUnsignedInt(b);
    return r;

    // Q: Why doesn't `Arrays` have a `static void SetAll(byte[] array, IntToShortFunction generator)`?
    // A: Because `short` is the bastard stepchild of Java's framework libraries.  P.S., there's no
    //    `IntToShortFunction` interface either ... or `ShortStream` class,  or `Streams::toArray`
    //    overload that'll give you a `short[]`, etc. etc. etc.
  }
}
