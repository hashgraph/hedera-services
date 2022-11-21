package com.hedera.services.ethereum;

import static org.hyperledger.besu.nativelib.secp256k1.LibSecp256k1.CONTEXT;
import static org.hyperledger.besu.nativelib.secp256k1.LibSecp256k1.SECP256K1_EC_UNCOMPRESSED;

import com.sun.jna.ptr.LongByReference;
import java.nio.ByteBuffer;
import org.bouncycastle.jcajce.provider.digest.Keccak;
import org.hyperledger.besu.nativelib.secp256k1.LibSecp256k1;

public final class EthSigsUtils {

  private EthSigsUtils(){}

  public static byte[] recoverAddressFromPubKey(byte[] pubKeyBytes) {
    LibSecp256k1.secp256k1_pubkey pubKey = new LibSecp256k1.secp256k1_pubkey();
    var parseResult =
        LibSecp256k1.secp256k1_ec_pubkey_parse(
            CONTEXT, pubKey, pubKeyBytes, pubKeyBytes.length);
    if (parseResult == 1) {
      return recoverAddressFromPubKey(pubKey);
    } else {
      return null;
    }
  }

  static byte[] recoverAddressFromPubKey(LibSecp256k1.secp256k1_pubkey pubKey) {
    final ByteBuffer recoveredFullKey = ByteBuffer.allocate(65);
    final LongByReference fullKeySize = new LongByReference(recoveredFullKey.limit());
    LibSecp256k1.secp256k1_ec_pubkey_serialize(
        CONTEXT, recoveredFullKey, fullKeySize, pubKey, SECP256K1_EC_UNCOMPRESSED);

    recoveredFullKey.get(); // read and discard - recoveryId is not part of the account hash
    var preHash = new byte[64];
    recoveredFullKey.get(preHash, 0, 64);
    var keyHash = new Keccak.Digest256().digest(preHash);
    var address = new byte[20];
    System.arraycopy(keyHash, 12, address, 0, 20);
    return address;
  }
}
