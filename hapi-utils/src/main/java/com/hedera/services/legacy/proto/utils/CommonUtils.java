package com.hedera.services.legacy.proto.utils;

/*-
 * ‌
 * Hedera Services API Utilities
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

import com.google.protobuf.ByteString;
import com.google.protobuf.InvalidProtocolBufferException;
import com.google.protobuf.TextFormat;
import com.hederahashgraph.api.proto.java.SignatureMap;
import com.hederahashgraph.api.proto.java.SignedTransaction;
import com.hederahashgraph.api.proto.java.Transaction;
import com.hederahashgraph.api.proto.java.TransactionBody;
import com.hederahashgraph.api.proto.java.TransactionOrBuilder;

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.ObjectOutput;
import java.io.ObjectOutputStream;
import java.net.URISyntaxException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.Base64;

/**
 * Common utilities.
 */
public final class CommonUtils {
  private CommonUtils() {
    throw new UnsupportedOperationException("Utility Class");
  }

  /**
   * Sleep given seconds.
   *
   * @param timeInMillis time to sleep in milliseconds
   * @throws InterruptedException when thread sleep interruption
   */
  public static void napMillis(long timeInMillis) throws InterruptedException {
    Thread.sleep(timeInMillis);
  }

  /**
   * Sleep given seconds.
   *
   * @param timeInSec time to sleep in seconds
   *
   * @throws InterruptedException when thread sleep interruption
   */
  public static void nap(int timeInSec)  throws InterruptedException {
    Thread.sleep(timeInSec * 1000L);
   }

  public static void nap(double timeInSec)  throws InterruptedException {
    Thread.sleep((long) timeInSec * 1000);
  }


  /**
   * Write bytes to a file.
   *
   * @param path the file path to write bytes
   * @param data the byte array data
   *
   * @throws IOException when error with IO
   */
  public static void writeToFile(String path, byte[] data) throws IOException {
    writeToFile(path, data, false);
  }

  /**
   * Write bytes to a file.
   *
   * @param path file write path
   * @param data byte array representation of data to write
   * @param append append to existing file if true
   *
   * @throws IOException when error with IO
   */
  public static void writeToFile(String path, byte[] data, boolean append) throws IOException {
    File f = new File(path);
    File parent = f.getParentFile();
    if (!parent.exists()) {
      parent.mkdirs();
    }
    try (FileOutputStream fos = new FileOutputStream(f, append)) {
      fos.write(data);
      fos.flush();
    } catch (IOException e) {
      throw e;
    }
  }

  /**
   * Write string to a file using UTF_8 encoding.
   *
   * @param path the file path to write bytes
   * @param data the byte array data
   * @throws IOException when error with IO
   */
  public static void writeToFileUTF8(String path, String data) throws IOException {
    byte[] bytes = data.getBytes(StandardCharsets.UTF_8);
    writeToFile(path, bytes);
  }

  /**
   * Write string to a file using UTF_8 encoding.
   *
   * @param data data represented as string
   * @param path file write path
   * @param append append to existing file if true
   * @throws IOException when error with IO
   */
  public static void writeToFileUTF8(String path, String data, boolean append) throws IOException {
    byte[] bytes = data.getBytes(StandardCharsets.UTF_8);
    writeToFile(path, bytes, append);
  }

  /**
   * Encode bytes as base64.
   *
   * @param bytes to be encoded
   * @return base64 string
   */
  public static String base64encode(byte[] bytes) {
    String rv = null;
    rv = Base64.getEncoder().encodeToString(bytes);
    return rv;
  }

  /**
   * Serialize a Java object to bytes.
   *
   * @param object the Java object to be serialized
   * @return converted bytes
   * @throws IOException when error with IO
   */
  public static byte[] convertToBytes(Object object) throws IOException {
    try (ByteArrayOutputStream bos = new ByteArrayOutputStream();
         ObjectOutput out = new ObjectOutputStream(bos)) {
      out.writeObject(object);
      return bos.toByteArray();
    }
  }

  /**
   * Copy bytes.
   *
   * @param start from position
   * @param length number of bytes to copy
   * @param bytes source byte array
   *
   * @return copied byte array
   */
  public static byte[] copyBytes(int start, int length, byte[] bytes) {
    byte[] rv = new byte[length];
    for (int i = 0; i < length; i++) {
      rv[i] = bytes[start + i];
    }
    return rv;
  }

  /**
   * Reads a resource file
   *
   * @param filePath the resource file to be read
   * @param myClass the calling class
   * @param <T> type to read binary into
   *
   * @return type T from the binary input
   * @throws IOException when error with IO
   * @throws URISyntaxException when invalid URI syntax
   */
  public static <T> byte[] readBinaryFileAsResource(String filePath, Class<T> myClass)
      throws IOException, URISyntaxException {
    Path path = Paths.get(myClass.getClassLoader().getResource(filePath).toURI());
    return Files.readAllBytes(path);
  }

  /**
   * Generates a human readable string for grpc transaction.
   *
   * @param grpcTransaction GRPC transaction
   *
   * @return generated readable string
   * @throws InvalidProtocolBufferException when protocol buffer is invalid
   */
  public static String toReadableString(Transaction grpcTransaction) throws InvalidProtocolBufferException {
    String rv = null;
    try {
      TransactionBody body = extractTransactionBody(grpcTransaction);
      rv = "body=" + TextFormat.shortDebugString(body) + "; sigs="
              + TextFormat.shortDebugString(extractSignatureMap(grpcTransaction));
    } catch (InvalidProtocolBufferException e) {
      throw e;
    }
    return rv;
  }

  /**
   * Generates a short human readable string for grpc transaction.
   *
   * @param grpcTransaction GRPC transaction
   *
   * @return generated readable string
   * @throws InvalidProtocolBufferException when protocol buffer is invalid
   */
  public static String toReadableTransactionID(
          Transaction grpcTransaction) throws InvalidProtocolBufferException {
    TransactionBody body = extractTransactionBody(grpcTransaction);
    return "txID=" + TextFormat.shortDebugString(body.getTransactionID());
  }

  public static ByteString extractTransactionBodyByteString(TransactionOrBuilder transaction)
      throws InvalidProtocolBufferException {
    ByteString signedTransactionBytes = transaction.getSignedTransactionBytes();
    if (!signedTransactionBytes.isEmpty()) {
      return SignedTransaction.parseFrom(signedTransactionBytes).getBodyBytes();
    }

    return transaction.getBodyBytes();
  }

  public static byte[] extractTransactionBodyBytes(TransactionOrBuilder transaction)
          throws InvalidProtocolBufferException {
    return extractTransactionBodyByteString(transaction).toByteArray();
  }

  public static TransactionBody extractTransactionBody(TransactionOrBuilder transaction)
          throws InvalidProtocolBufferException {
    return TransactionBody.parseFrom(extractTransactionBodyByteString(transaction));
  }

  public static SignatureMap extractSignatureMap(TransactionOrBuilder transaction)
          throws InvalidProtocolBufferException {
    ByteString signedTransactionBytes = transaction.getSignedTransactionBytes();
    if (!signedTransactionBytes.isEmpty()) {
      return SignedTransaction.parseFrom(signedTransactionBytes).getSigMap();
    }

    return transaction.getSigMap();
  }

  public static SignatureMap extractSignatureMapOrUseDefault(TransactionOrBuilder transaction) {
    try {
      return extractSignatureMap(transaction);
    } catch (InvalidProtocolBufferException ignoreToReturnDefault) { }
    return SignatureMap.getDefaultInstance();
  }

  public static Transaction.Builder toTransactionBuilder(TransactionOrBuilder transactionOrBuilder) {
    if (transactionOrBuilder instanceof Transaction) {
      return ((Transaction) transactionOrBuilder).toBuilder();
    }

    return (Transaction.Builder) transactionOrBuilder;
  }

  public static MessageDigest getSha384Hash() throws NoSuchAlgorithmException {
    return MessageDigest.getInstance("SHA-384");
  }

  public static byte[] noThrowSha384HashOf(byte[] byteArray) {
    try {
      return getSha384Hash().digest(byteArray);
    } catch (NoSuchAlgorithmException ignoreToReturnEmptyByteArray) { }
    return new byte[0];
  }

  public static ByteString sha384HashOf(byte[] byteArray) {
    return ByteString.copyFrom(noThrowSha384HashOf(byteArray));
  }

  public static ByteString sha384HashOf(Transaction transaction) {
    if (transaction.getSignedTransactionBytes().isEmpty()) {
      return sha384HashOf(transaction.toByteArray());
    }

    return sha384HashOf(transaction.getSignedTransactionBytes().toByteArray());
  }

  public static void writeTxId2File(String txIdString) throws IOException {
    writeToFileUTF8("output/txIds.txt", ProtoCommonUtils.getCurrentInstantUTC() + "-->" + txIdString + "\n", true);
  }
}
