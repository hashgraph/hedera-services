package com.hedera.services.legacy.core.jproto;

/*-
 * ‌
 * Hedera Services Node
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

import com.google.protobuf.ByteString;
import com.hederahashgraph.api.proto.java.ResponseCodeEnum;
import com.hederahashgraph.api.proto.java.TopicID;
import com.hederahashgraph.api.proto.java.TransactionReceipt;
import com.hederahashgraph.builder.RequestBuilder;
import com.swirlds.common.FastCopyable;
import com.swirlds.common.io.FCDataInputStream;
import com.swirlds.common.io.FCDataOutputStream;
import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.IOException;
import java.util.Arrays;
import java.util.Objects;

import org.apache.commons.codec.binary.Hex;
import org.apache.commons.codec.binary.StringUtils;

import javax.annotation.Nullable;

/**
 * This is custom class equivalent to Transaction Receipt proto
 *
 * @author Akshay
 * @Date : 1/8/2019
 */
public class JTransactionReceipt implements FastCopyable {
  private static final long LEGACY_VERSION_1 = 1;
  private static final long VERSION_BEFORE_HCS = 2;
  static final long VERSION_WITHOUT_EXPLICIT_RUNNING_HASH_VERSION = 3;
  static final long CURRENT_VERSION = 4;

  static final long MISSING_RUNNING_HASH_VERSION = 0L;

  private String status;
  private JAccountID accountID;
  private JAccountID fileID;
  private JAccountID contractID;
  private JExchangeRateSet exchangeRate;

  // new fields after VERSION_BEFORE_HCS
  private JAccountID topicID;
  private long topicSequenceNumber; // 0 represents unset/invalid.
  private byte[] topicRunningHash; // null if empty/unset.
  // After VERSION_WITHOUT_EXPLICIT_RUNNING_HASH_VERSION
  private long runningHashVersion = MISSING_RUNNING_HASH_VERSION;

  public JTransactionReceipt() {
  }

  public JTransactionReceipt(@Nullable String status, @Nullable JAccountID accountID, @Nullable JAccountID fileID,
                             @Nullable JAccountID contractID, @Nullable JExchangeRateSet exchangeRate,
                             @Nullable JAccountID topicId, long topicSequenceNumber, @Nullable byte[] topicRunningHash) {
    this(status, accountID, fileID, contractID, exchangeRate,
            topicId, topicSequenceNumber, topicRunningHash, MISSING_RUNNING_HASH_VERSION);
  }

  public JTransactionReceipt(@Nullable String status, @Nullable JAccountID accountID, @Nullable JAccountID fileID,
          @Nullable JAccountID contractID, @Nullable JExchangeRateSet exchangeRate,
          @Nullable JAccountID topicId, long topicSequenceNumber, @Nullable byte[] topicRunningHash, long runningHashVersion) {
    this.status = status;
    this.accountID = accountID;
    this.fileID = fileID;
    this.contractID = contractID;
    this.exchangeRate = exchangeRate;
    this.topicID = topicId;
    this.topicSequenceNumber = topicSequenceNumber;
    setTopicRunningHash(topicRunningHash);
    this.runningHashVersion = runningHashVersion;
  }

  /**
   * Deep copy constructor.
   * @param other
   */
  public JTransactionReceipt(final JTransactionReceipt other) {
    this.status = other.status;
    this.accountID = (other.accountID != null) ? (JAccountID) other.accountID.copy() : null;
    this.fileID = (other.fileID != null) ? (JAccountID) other.fileID.copy() : null;
    this.contractID = (other.contractID != null) ? (JAccountID) other.contractID.copy() : null;
    this.exchangeRate =
        (other.exchangeRate != null) ? (JExchangeRateSet) other.exchangeRate.copy() : null;
    this.topicID = (other.topicID != null) ? (JAccountID)other.topicID.copy() : null;
    this.topicSequenceNumber = other.topicSequenceNumber;
    this.topicRunningHash = ((null != other.topicRunningHash) && (other.topicRunningHash.length > 0)) ?
            Arrays.copyOf(other.topicRunningHash, other.topicRunningHash.length) : null;
    this.runningHashVersion = other.runningHashVersion;
  }

  public long getRunningHashVersion() {
    return runningHashVersion;
  }

  public String getStatus() {
    return status;
  }

  public void setStatus(String status) {
    this.status = status;
  }

  public JAccountID getAccountID() {
    return accountID;
  }

  public void setAccountID(JAccountID accountID) {
    this.accountID = accountID;
  }

  public JAccountID getFileID() {
    return fileID;
  }

  public void setFileID(JAccountID fileID) {
    this.fileID = fileID;
  }

  public JAccountID getContractID() {
    return contractID;
  }

  public void setContractID(JAccountID contractID) {
    this.contractID = contractID;
  }

  public JExchangeRateSet getExchangeRate() {
    return exchangeRate;
  }

  public void setExchangeRate(JExchangeRateSet exchangeRate) {
    this.exchangeRate = exchangeRate;
  }

  public JAccountID getTopicID() {
    return topicID;
  }

  public void setTopicID(JAccountID topicId) {
    this.topicID = topicId;
  }

  public long getTopicSequenceNumber() {
    return topicSequenceNumber;
  }

  public void setTopicSequenceNumber(long topicSequenceNumber) {
    this.topicSequenceNumber = topicSequenceNumber;
  }

  public byte[] getTopicRunningHash() {
    return topicRunningHash;
  }

  /**
   * Shallow assignment.
   * @param topicRunningHash
   */
  public void setTopicRunningHash(byte[] topicRunningHash) {
    this.topicRunningHash = ((null != topicRunningHash) && (topicRunningHash.length > 0)) ? topicRunningHash : null;
  }

  public static JTransactionReceipt convert(TransactionReceipt receipt) {
    String status = receipt.getStatus() != null ? receipt.getStatus().name() : null;
    JAccountID jAccountID =
        receipt.hasAccountID() ? JAccountID.convert(receipt.getAccountID()) : null;
    JAccountID jFileID = receipt.hasFileID() ? JAccountID.convert(receipt.getFileID()) : null;
    JAccountID jContractID =
        receipt.hasContractID() ? JAccountID.convert(receipt.getContractID()) : null;
    JExchangeRateSet jExchangeRateSet =
        receipt.hasExchangeRate() ? JExchangeRateSet.convert(receipt.getExchangeRate()) : null;
    JAccountID topicId = receipt.hasTopicID() ? JAccountID.convert(receipt.getTopicID()) : null;
    long runningHashVersion = Math.max(MISSING_RUNNING_HASH_VERSION, receipt.getTopicRunningHashVersion());
    return new JTransactionReceipt(status, jAccountID, jFileID, jContractID, jExchangeRateSet, topicId,
            receipt.getTopicSequenceNumber(), receipt.getTopicRunningHash().toByteArray(), runningHashVersion);
  }

  public static TransactionReceipt convert(JTransactionReceipt txReceipt) {
    TransactionReceipt.Builder builder = TransactionReceipt.newBuilder()
        .setStatus(ResponseCodeEnum.valueOf(txReceipt.getStatus()));
    if (txReceipt.getAccountID() != null) {
      builder.setAccountID(RequestBuilder.getAccountIdBuild(
          txReceipt.getAccountID().getAccountNum(),
          txReceipt.getAccountID().getRealmNum(),
          txReceipt.getAccountID().getShardNum()));
    }
    if (txReceipt.getFileID() != null) {
      builder.setFileID(RequestBuilder.getFileIdBuild(
          txReceipt.getFileID().getAccountNum(),
          txReceipt.getFileID().getRealmNum(),
          txReceipt.getFileID().getShardNum()));
    }
    if (txReceipt.getContractID() != null) {
      builder.setContractID(RequestBuilder.getContractIdBuild(
          txReceipt.getContractID().getAccountNum(),
          txReceipt.getContractID().getRealmNum(),
          txReceipt.getContractID().getShardNum()));
    }
    if (txReceipt.getExchangeRate() != null) {
      builder.setExchangeRate(JExchangeRateSet.convert(txReceipt.getExchangeRate()));
    }
    if (txReceipt.getTopicID() != null) {
      var receiptTopic = txReceipt.getTopicID();
      builder.setTopicID(TopicID.newBuilder().setShardNum(receiptTopic.getShardNum())
              .setRealmNum(receiptTopic.getRealmNum())
              .setTopicNum(receiptTopic.getAccountNum()).build());
    }
    if (txReceipt.getTopicSequenceNumber() != 0) {
      builder.setTopicSequenceNumber(txReceipt.getTopicSequenceNumber());
    }
    if (txReceipt.getTopicRunningHash() != null) {
      builder.setTopicRunningHash(ByteString.copyFrom(txReceipt.getTopicRunningHash()));
    }
    if (txReceipt.getRunningHashVersion() != MISSING_RUNNING_HASH_VERSION) {
      builder.setTopicRunningHashVersion(txReceipt.getRunningHashVersion());
    }
    return builder.build();
  }

  /**
   * Custom serialize method. If some field is null then it will set 0 byte for that field otherwise
   * it add length of the byte first and then actual byte of the field.
   */
  private void serialize(final DataOutputStream outStream) throws IOException {
  	if (runningHashVersion != MISSING_RUNNING_HASH_VERSION) {
      outStream.writeLong(CURRENT_VERSION);
    } else {
      outStream.writeLong(VERSION_WITHOUT_EXPLICIT_RUNNING_HASH_VERSION);
    }
    outStream.writeLong(JObjectType.JTransactionReceipt.longValue());

    if (this.accountID != null) {
      outStream.writeBoolean(true);
      this.accountID.copyTo((FCDataOutputStream) outStream);
      this.accountID.copyToExtra((FCDataOutputStream) outStream);
    } else {
      outStream.writeBoolean(false);
    }

    if (this.fileID != null) {
      outStream.writeBoolean(true);
      this.fileID.copyTo((FCDataOutputStream) outStream);
      this.fileID.copyToExtra((FCDataOutputStream) outStream);
    } else {
      outStream.writeBoolean(false);
    }

    if (this.contractID != null) {
      outStream.writeBoolean(true);
      this.contractID.copyTo((FCDataOutputStream) outStream);
      this.contractID.copyToExtra((FCDataOutputStream) outStream);
    } else {
      outStream.writeBoolean(false);
    }

    if (this.status != null) {
      byte[] sBytes = StringUtils.getBytesUtf8(this.status);
      outStream.writeInt(sBytes.length);
      outStream.write(sBytes);
    } else {
      outStream.writeInt(0);
    }

    if (this.exchangeRate != null) {
      outStream.writeBoolean(true);
      this.exchangeRate.copyTo((FCDataOutputStream) outStream);
      this.exchangeRate.copyToExtra((FCDataOutputStream) outStream);
    } else {
      outStream.writeBoolean(false);
    }

    // new fields after VERSION_BEFORE_HCS
    if (this.topicID != null) {
      outStream.writeBoolean(true);
      this.topicID.copyTo((FCDataOutputStream) outStream);
      this.topicID.copyToExtra((FCDataOutputStream) outStream);
    } else {
      outStream.writeBoolean(false);
    }
    // Save space. Assume topic sequenceNumber and runningHash are either both there or both not.
    if ((0L != this.topicSequenceNumber) || ((null != this.topicRunningHash) && (this.topicRunningHash.length > 0))) {
      outStream.writeBoolean(true);
      outStream.writeLong(this.topicSequenceNumber);
      var len = (null == this.topicRunningHash) ? 0 : this.topicRunningHash.length;
      outStream.writeInt(len);
      if (len > 0) {
        outStream.write(this.topicRunningHash);
      }
    } else {
      outStream.writeBoolean(false);
    }

    if (runningHashVersion != MISSING_RUNNING_HASH_VERSION) {
      outStream.writeLong(runningHashVersion);
    }
  }

  @SuppressWarnings("unchecked")
  public static <T extends FastCopyable> T deserialize(final DataInputStream inStream)
      throws IOException {
    final JTransactionReceipt receipt = new JTransactionReceipt();

    deserialize(inStream, receipt);
    return (T) receipt;
  }

  private static void deserialize(final DataInputStream inStream, final JTransactionReceipt receipt) throws IOException {
    final long version = inStream.readLong();
    if (version < LEGACY_VERSION_1 || version > CURRENT_VERSION) {
      throw new IllegalStateException("Illegal version was read from the stream");
    }

    final long objectType = inStream.readLong();
    final JObjectType type = JObjectType.valueOf(objectType);
    if (!JObjectType.JTransactionReceipt.equals(type)) {
      throw new IllegalStateException("Illegal JObjectType was read from the stream");
    }

    final boolean accountIDPresent = inStream.readBoolean();
    if (accountIDPresent) {
      receipt.accountID = JAccountID.deserialize(inStream);
    }

    final boolean fileIDPresent = inStream.readBoolean();
    if (fileIDPresent) {
      receipt.fileID = JAccountID.deserialize(inStream);
    }

    final boolean contractIDPresent = inStream.readBoolean();
    if (contractIDPresent) {
      receipt.contractID = JAccountID.deserialize(inStream);
    }

    byte[] sBytes = new byte[inStream.readInt()];
    if (sBytes.length > 0) {
      inStream.readFully(sBytes);
      receipt.status = StringUtils.newStringUtf8(sBytes);
    } else {
      receipt.status = null;
    }

    final boolean exchangeRatePresent = inStream.readBoolean();
    if (exchangeRatePresent) {
      receipt.exchangeRate = JExchangeRateSet.deserialize(inStream);
    }

    receipt.topicID = null;
    receipt.topicSequenceNumber = 0;
    receipt.topicRunningHash = null;
    if (version > VERSION_BEFORE_HCS) {
      if (inStream.readBoolean()) { // topicID
        receipt.topicID = JAccountID.deserialize(inStream);
      }
      if (inStream.readBoolean()) { // topicSequenceNumber and topicRunningHash
        receipt.topicSequenceNumber = inStream.readLong();
        int len = inStream.readInt();
        if (len > 0) {
          receipt.topicRunningHash = new byte[len];
          inStream.readFully(receipt.topicRunningHash);
        }
      }
    }

    if (version > VERSION_WITHOUT_EXPLICIT_RUNNING_HASH_VERSION) {
      receipt.runningHashVersion = inStream.readLong();
    } else {
      receipt.runningHashVersion = MISSING_RUNNING_HASH_VERSION;
    }
  }

  @Override
  public FastCopyable copy() {
    return new JTransactionReceipt(this);
  }

  @Override
  public void copyTo(final FCDataOutputStream outStream) throws IOException {
    serialize(outStream);
  }

  @Override
  public void copyFrom(final FCDataInputStream inStream) throws IOException {
  }

  @Override
  public void copyToExtra(final FCDataOutputStream outStream) throws IOException {

  }

  @Override
  public void copyFromExtra(final FCDataInputStream inStream) throws IOException {

  }

  @Override
  public void diffCopyTo(final FCDataOutputStream outStream, final FCDataInputStream inStream)
      throws IOException {
    serialize(outStream);
  }

  @Override
  public void diffCopyFrom(final FCDataOutputStream outStream, final FCDataInputStream inStream)
      throws IOException {
    deserialize(inStream, this);
  }

  @Override
  public void delete() {

  }

  @Override
  public boolean equals(Object o) {
    if (this == o) {
      return true;
    }
    if (o == null || getClass() != o.getClass()) {
      return false;
    }
    JTransactionReceipt that = (JTransactionReceipt) o;
    return this.runningHashVersion == that.runningHashVersion &&
        Objects.equals(status, that.status) &&
        Objects.equals(accountID, that.accountID) &&
        Objects.equals(fileID, that.fileID) &&
        Objects.equals(contractID, that.contractID) &&
            Objects.equals(topicID, that.topicID) &&
            Objects.equals(topicSequenceNumber, that.topicSequenceNumber) &&
            Arrays.equals(topicRunningHash, that.topicRunningHash);
  }

  @Override
  public int hashCode() {
    return Objects.hash(
            runningHashVersion, status, accountID, fileID, contractID, topicID, topicSequenceNumber, topicRunningHash);
  }

  @Override
  public String toString() {
    return "JTransactionReceipt{" +
        "status='" + status + '\'' +
        ", accountID=" + accountID +
        ", fileID=" + fileID +
        ", contractID=" + contractID +
            ", topicID=" + topicID +
            ", topicSequenceNumber=" + topicSequenceNumber +
            ", topicRunningHash=" + ((null != topicRunningHash) ? Hex.encodeHexString(topicRunningHash) : "") +
            ", runningHashVersion=" + runningHashVersion +
        '}';
  }
}
