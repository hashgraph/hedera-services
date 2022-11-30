package com.hedera.node.app.service.evm.store.contracts.precompile.codec;

import com.esaulpaugh.headlong.abi.ABIType;
import com.esaulpaugh.headlong.abi.Tuple;
import com.google.common.primitives.Ints;
import com.google.common.primitives.Longs;
import com.hederahashgraph.api.proto.java.AccountID;
import edu.umd.cs.findbugs.annotations.NonNull;
import java.util.Arrays;
import java.util.function.UnaryOperator;
import org.apache.tuweni.bytes.Bytes;

public class EvmDecodingFacade {

  private static final int WORD_LENGTH = 32;
  private static final int ADDRESS_BYTES_LENGTH = 20;
  private static final int ADDRESS_SKIP_BYTES_LENGTH = 12;

  private static final int FUNCTION_SELECTOR_BYTES_LENGTH = 4;

  public static Tuple decodeFunctionCall(
      @NonNull final Bytes input, final Bytes selector, final ABIType<Tuple> decoder) {
    if (!selector.equals(input.slice(0, FUNCTION_SELECTOR_BYTES_LENGTH))) {
      throw new IllegalArgumentException(
          "Selector does not match, expected "
              + selector
              + " actual "
              + input.slice(0, FUNCTION_SELECTOR_BYTES_LENGTH));
    }
    return decoder.decode(input.slice(FUNCTION_SELECTOR_BYTES_LENGTH).toArray());
  }

  public static AccountID convertLeftPaddedAddressToAccountId(
      final byte[] leftPaddedAddress, @NonNull final UnaryOperator<byte[]> aliasResolver) {
    final var addressOrAlias =
        Arrays.copyOfRange(leftPaddedAddress, ADDRESS_SKIP_BYTES_LENGTH, WORD_LENGTH);
    return accountIdFromEvmAddress(aliasResolver.apply(addressOrAlias));
  }

  public static AccountID accountIdFromEvmAddress(final byte[] bytes) {
    return AccountID.newBuilder()
        .setShardNum(shardFromEvmAddress(bytes))
        .setRealmNum(realmFromEvmAddress(bytes))
        .setAccountNum(numFromEvmAddress(bytes))
        .build();
  }

  public static long shardFromEvmAddress(final byte[] bytes) {
    return Ints.fromByteArray(Arrays.copyOfRange(bytes, 0, 4));
  }

  public static long realmFromEvmAddress(final byte[] bytes) {
    return Longs.fromByteArray(Arrays.copyOfRange(bytes, 4, 12));
  }

  public static long numFromEvmAddress(final byte[] bytes) {
    return Longs.fromBytes(
        bytes[12], bytes[13], bytes[14], bytes[15], bytes[16], bytes[17], bytes[18],
        bytes[19]);
  }
}
