package com.hedera.services.legacy.core;

/*-
 * ‌
 * Hedera Services Test Clients
 * ​
 * Copyright (C) 2018 - 2021 Hedera Hashgraph, LLC
 * ​
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
 * ‍
 */

import java.io.UnsupportedEncodingException;
import org.apache.commons.codec.DecoderException;
import org.apache.commons.codec.binary.Hex;

/**
 * Hex utilities.
 *
 * @author hua
 */
public class HexUtils {

  /**
   * Encodes bytes to a hex string.
   *
   * @param bytes data to be encoded
   * @return hex string
   */
  public static String bytes2Hex(byte[] bytes) {
    String str = Hex.encodeHexString(bytes);
    return str;
  }

  /**
   * Convert hex string to bytes.
   *
   * @param data to be converted
   * @return converted bytes
   */
  public static byte[] hexToBytes(String data) throws DecoderException {
    byte[] rv = Hex.decodeHex(data);
    return rv;
  }

  public static void main(String[] args) throws DecoderException, UnsupportedEncodingException {
    String in = "hedera hashgraph team";

    byte[] bytes = in.getBytes();
    String hex = bytes2Hex(bytes);
    System.out.println("in=" + in + "\nhex=" + hex);

    byte[] bytes1 = hexToBytes(hex);
    String out = bytes2String(bytes1, "UTF-8");
    System.out.println("out=" + out);
  }

  /**
   * Decodes a byte array to a string using given character set.
   *
   * @param bytesData data to decode
   * @param charsetName character set name for decoding, e.g. "UTF-8"
   * @return decoded string
   */
  public static String bytes2String(byte[] bytesData, String charsetName)
      throws UnsupportedEncodingException {
    String decodedDataUsing = new String(bytesData, charsetName);
    System.out.println("Text decoded using UTF-8 : " + decodedDataUsing);
    return decodedDataUsing;
  }
}
