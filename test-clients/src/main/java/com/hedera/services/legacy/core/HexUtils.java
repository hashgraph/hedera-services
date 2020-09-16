package com.hedera.services.legacy.core;

/*-
 * ‌
 * Hedera Services Test Clients
 * ​
 * Copyright (C) 2018 - 2020 Hedera Hashgraph, LLC
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

  public static String bytes2String1() {
    String testString = "Crunchify Example on Byte[] to String";
    byte[] bytesData = testString.getBytes();

    System.out.println("testString : " + testString);
    System.out
        .println("\nbytesData : " + bytesData); // .getBytes on String will return Hashcode value
    System.out.println(
        "bytesData.toString() : " + bytesData.toString()); // .toString() will return Hashcode value

    String decodedData = new String(bytesData); // Create new String Object and assign byte[] to it
    System.out.println("\nText Decryted : " + decodedData);
    String decodedDataUsingUTF8 = null;
    try {
      decodedDataUsingUTF8 = new String(bytesData, "UTF-8"); // Best way to decode using "UTF-8"
      System.out.println("Text Decryted using UTF-8 : " + decodedDataUsingUTF8);
    } catch (UnsupportedEncodingException e) {
      e.printStackTrace();
    }

    return decodedDataUsingUTF8;
  }

  /**
   * Encodes a long value to a hex string.
   *
   * @param longVal data to be encoded
   * @return hex string
   */
  public static String long2Hex(long longVal) {
    String str = bytes2Hex(CommonUtils.longToBytes(longVal));
    return str;
  }

  /**
   * Decodes a hex string to a long value.
   *
   * @param hexStr data to be decoded
   * @return long value
   */
  public static long hex2long(String hexStr) throws DecoderException {
    long rv = CommonUtils.bytesToLong(hexToBytes(hexStr));
    return rv;
  }

  /**
   * Converts Hex to Binary
   */
  public static String hexToBin(String hex) {
    String bin = "";
    String binFragment = "";
    int iHex;
    hex = hex.trim();
    hex = hex.replaceFirst("0x", "");

    for (int i = 0; i < hex.length(); i++) {
      iHex = Integer.parseInt("" + hex.charAt(i), 16);
      binFragment = Integer.toBinaryString(iHex);

      while (binFragment.length() < 4) {
        binFragment = "0" + binFragment;
      }
      bin += binFragment;
    }
    return bin;
  }

  /**
   * Converts Binary to Hex
   */
  public static String convertBinaryToHex(String binInPut) {
    int chunkLength = binInPut.length() / 4, startIndex = 0, endIndex = 4;
    String chunkVal = null;
    String val = "";
    for (int i = 0; i < chunkLength; i++) {
      chunkVal = binInPut.substring(startIndex, endIndex);
      val = val + Integer.toHexString(Integer.parseInt(chunkVal, 2));
      startIndex = endIndex;
      endIndex = endIndex + 4;
    }

    return val;

  }

}
