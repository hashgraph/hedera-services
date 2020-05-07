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
import com.hederahashgraph.api.proto.java.AccountAmount;
import com.hederahashgraph.api.proto.java.ContractFunctionResult;
import com.hederahashgraph.api.proto.java.Timestamp;
import com.hederahashgraph.api.proto.java.TransactionReceipt;
import com.hederahashgraph.api.proto.java.TransactionRecord;
import com.hederahashgraph.api.proto.java.TransferList;
import com.swirlds.common.FastCopyable;
import java.io.DataInputStream;
import java.io.IOException;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Objects;
import java.util.OptionalLong;
import java.util.stream.Collectors;

import com.swirlds.common.io.FCDataInputStream;
import com.swirlds.common.io.FCDataOutputStream;
import org.apache.commons.codec.binary.StringUtils;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

/**
 * @author Akshay
 * @Date : 1/8/2019
 */
public class JTransactionRecord implements FastCopyable {
  private static final Logger log = LogManager.getLogger(JTransactionRecord.class);
  private static final long LEGACY_VERSION_1 = 1;
  private static final long LEGACY_VERSION_2 = 2;
  private static final long CURRENT_VERSION = 3;
  private JTransactionReceipt txReceipt;
  private byte[] txHash;
  private JTransactionID transactionID;
  private JTimestamp consensusTimestamp;
  private String memo;
  private long transactionFee;
  private JContractFunctionResult contractCallResult;
  private JContractFunctionResult contractCreateResult;
  private JTransferList jTransferList;
  private long expirationTime; //

  // track deserialized version to ensure hashes match when serializing later
  private OptionalLong versionToSerialize = OptionalLong.empty();

  public JTransactionRecord() {
  }

  public JTransactionRecord(JTransactionReceipt txReceipt, byte[] txHash,
      JTransactionID transactionID, JTimestamp consensusTimestamp, String memo,
      long transactionFee, JTransferList transferList, JContractFunctionResult contractCallResult,
      JContractFunctionResult createResult) {
    this.txReceipt = txReceipt;
    this.txHash = txHash;
    this.transactionID = transactionID;
    this.consensusTimestamp = consensusTimestamp;
    this.memo = memo;
    this.transactionFee = transactionFee;
    this.jTransferList = transferList;
    this.contractCallResult = contractCallResult;
    this.contractCreateResult = createResult;
  }

  public JTransactionRecord(final JTransactionRecord other) {
    this.txReceipt =
        (other.txReceipt != null) ? (JTransactionReceipt) other.txReceipt.copy() : null;
    this.txHash = (other.txHash != null) ? Arrays.copyOf(other.txHash, other.txHash.length) : null;
    this.transactionID =
        (other.transactionID != null) ? (JTransactionID) other.transactionID.copy() : null;
    this.consensusTimestamp =
        (other.consensusTimestamp != null) ? (JTimestamp) other.consensusTimestamp.copy() : null;
    this.memo = other.memo;
    this.transactionFee = other.transactionFee;
    this.expirationTime = other.expirationTime;
    this.jTransferList =
        (other.jTransferList != null) ? (JTransferList) other.jTransferList.copy() : null;
    this.contractCallResult =
        (other.contractCallResult != null) ? (JContractFunctionResult) other.contractCallResult
            .copy() : null;
    this.contractCreateResult =
        (other.contractCreateResult != null) ? (JContractFunctionResult) other.contractCreateResult
            .copy() : null;
  }

  public JTransactionReceipt getTxReceipt() {
    return txReceipt;
  }

  public byte[] getTxHash() {
    return txHash;
  }

  public JTransactionID getTransactionID() {
    return transactionID;
  }

  public JTimestamp getConsensusTimestamp() {
    return consensusTimestamp;
  }

  public String getMemo() {
    return memo;
  }

  public long getTransactionFee() {
    return transactionFee;
  }

  public void setVersionToSerializeContractFunctionResult(long version) {
    if (contractCallResult != null) {
      contractCallResult.setVersionToSerialize(version);
    }
    if (contractCreateResult != null) {
      contractCreateResult.setVersionToSerialize(version);
    }
  }

  public JContractFunctionResult getContractCallResult() {
    return contractCallResult;
  }

  public JContractFunctionResult getContractCreateResult() {
    return contractCreateResult;
  }

  public JTransferList getjTransferList() {
    return jTransferList;
  }

  public void setjTransferList(JTransferList jTransferList) {
    this.jTransferList = jTransferList;
  }

  public long getExpirationTime() {
    return expirationTime;
  }

  public void setExpirationTime(long expirationTime) {
    this.expirationTime = expirationTime;
  }

  public static List<TransactionRecord> convert(final List<JTransactionRecord> records) {
    if (records == null || records.isEmpty()) {
      return Collections.emptyList();
    }
    if (log.isDebugEnabled()) {
      log.debug("Converting JRecord to TxRecord..List size ::  " + records.size());
    }
    return records.stream().map(JTransactionRecord::convert).collect(Collectors.toList());
  }

  public static JTransactionRecord convert(TransactionRecord transactionRecord) {
    try {
      JTransactionReceipt jTransactionReceipt =
          JTransactionReceipt.convert(transactionRecord.getReceipt());
      JTransactionID jTransactionID = new JTransactionID();
      if (transactionRecord.hasTransactionID()) {
        jTransactionID = JTransactionID.convert(transactionRecord.getTransactionID());
      }

      JTransferList jTransferList = null;
      if (transactionRecord.hasTransferList()) {
        jTransferList = new JTransferList(
            transactionRecord.getTransferList().getAccountAmountsList());
      }

      JContractFunctionResult callResult = null;
      if (transactionRecord.hasContractCallResult()) {
        callResult = JContractFunctionResult.convert(transactionRecord.getContractCallResult());
      }

      JContractFunctionResult createResult = null;
      if (transactionRecord.hasContractCreateResult()) {
        createResult = JContractFunctionResult.convert(transactionRecord.getContractCreateResult());
      }

      return new JTransactionRecord(jTransactionReceipt,
          transactionRecord.getTransactionHash().toByteArray(), jTransactionID,
          JTimestamp.convert(transactionRecord.getConsensusTimestamp()),
          transactionRecord.getMemo(),
          transactionRecord.getTransactionFee(), jTransferList, callResult, createResult);
    } catch (Exception ex) {
      log.error("Conversion Of TransactionRecord to JTransactionRecord  Failed..", ex);
    }
    return new JTransactionRecord();
  }

  public static TransactionRecord convert(JTransactionRecord jTransactionRecord) {
    TransactionRecord.Builder builder = TransactionRecord.newBuilder();
    try {
      TransactionReceipt txReceipt = JTransactionReceipt.convert(jTransactionRecord.getTxReceipt());
      if (jTransactionRecord.getTransactionID() != null) {
        JAccountID payer = jTransactionRecord.getTransactionID().getPayerAccount();
        JTimestamp timestamp = jTransactionRecord.getTransactionID().getStartTime();
        
        // if both payer and timestamp are null, transactionID field will not be set
        if((payer != null) || (timestamp != null) )
          builder.setTransactionID(JTransactionID.convert(jTransactionRecord.getTransactionID()));
      }

      Timestamp timestamp = Timestamp.newBuilder().build();
      if (jTransactionRecord.getConsensusTimestamp() != null) {
        timestamp = JTimestamp.convert(jTransactionRecord.getConsensusTimestamp());
      }
      builder.setConsensusTimestamp(timestamp)
          .setTransactionFee(jTransactionRecord.getTransactionFee())
          .setReceipt(txReceipt);
      if (jTransactionRecord.getMemo() != null) {
        builder.setMemo(jTransactionRecord.getMemo());
      }
      if (jTransactionRecord.getTxHash().length > 0) {
        builder.setTransactionHash(ByteString.copyFrom(jTransactionRecord.getTxHash()));
      }
      if (jTransactionRecord.getjTransferList() != null) {
        List<AccountAmount> accountAmounts = jTransactionRecord.getjTransferList()
            .getjAccountAmountsList().stream()
            .map(JTransferList::convert)
            .collect(Collectors.toList());
        TransferList transferList = TransferList.newBuilder().addAllAccountAmounts(accountAmounts)
            .build();
        builder.setTransferList(transferList);
      }

      if (jTransactionRecord.getContractCallResult() != null) {
        ContractFunctionResult contractCallResult =
            JContractFunctionResult.convert(jTransactionRecord.getContractCallResult());
        builder.setContractCallResult(contractCallResult);
      }

      if (jTransactionRecord.getContractCreateResult() != null) {
        ContractFunctionResult contractCreateResult =
            JContractFunctionResult.convert(jTransactionRecord.getContractCreateResult());
        builder.setContractCreateResult(contractCreateResult);
      }
    } catch (Exception ex) {
      log.error("JTransactionRecord to TransactionRecord Proto Failed..", ex);
    }
    return builder.build();
  }

  /**
   * Custom serialize method. If some field is null then it will set 0 byte for that field otherwise
   * it add length of the byte first and then actual byte of the field.
   */
  private void serialize(final FCDataOutputStream outStream) throws IOException {
    outStream.writeLong(versionToSerialize.orElse(CURRENT_VERSION));
    outStream.writeLong(JObjectType.JTransactionRecord.longValue());

    if (this.txReceipt != null) {
      outStream.writeBoolean(true);
      this.txReceipt.copyTo(outStream);
      this.txReceipt.copyToExtra(outStream);
    } else {
      outStream.writeBoolean(false);
    }

    if (this.txHash != null && this.txHash.length > 0) {
      outStream.writeInt(this.txHash.length);
      outStream.write(this.txHash);
    } else {
      outStream.writeInt(0);
    }

    if (this.transactionID != null) {
      outStream.writeBoolean(true);
      this.transactionID.copyTo(outStream);
      this.transactionID.copyToExtra(outStream);
    } else {
      outStream.writeBoolean(false);
    }

    if (this.consensusTimestamp != null) {
      outStream.writeBoolean(true);
      this.consensusTimestamp.copyTo(outStream);
      this.consensusTimestamp.copyToExtra(outStream);
    } else {
      outStream.writeBoolean(false);
    }

    if (this.memo != null) {
      byte[] bytes = StringUtils.getBytesUtf8(this.memo);
      outStream.writeInt(bytes.length);
      outStream.write(bytes);
    } else {
      outStream.writeInt(0);
    }

    outStream.writeLong(this.transactionFee);

    if (this.jTransferList != null) {
      outStream.writeBoolean(true);
      this.jTransferList.copyTo(outStream);
      this.jTransferList.copyToExtra(outStream);
    } else {
      outStream.writeBoolean(false);
    }

    if (this.contractCallResult != null) {
      outStream.writeBoolean(true);
      this.contractCallResult.copyTo(outStream);
      this.contractCallResult.copyToExtra(outStream);
    } else {
      outStream.writeBoolean(false);
    }

    if (this.contractCreateResult != null) {
      outStream.writeBoolean(true);
      this.contractCreateResult.copyTo(outStream);
      this.contractCreateResult.copyToExtra(outStream);
    } else {
      outStream.writeBoolean(false);
    }
    if (versionToSerialize.orElse(CURRENT_VERSION) > LEGACY_VERSION_2) {
      outStream.writeLong(this.expirationTime);
    }
  }

  /**
   * Custom deserialization  of this class. It read first length of the field if it is 0 then sets
   * field to null otherwise it read bytes from DataInputStream of specified length and deserialize
   * those byte for the field.
   *
   * @param inStream input stream
   * @return deserialize JTransactionRecord
   */
  @SuppressWarnings("unchecked")
  public static <T extends FastCopyable> T deserialize(final DataInputStream inStream)
      throws IOException {
    JTransactionRecord jTransactionRecord = new JTransactionRecord();

    deserialize(inStream, jTransactionRecord);
    return (T) jTransactionRecord;
  }

  private static void deserialize(final DataInputStream inStream,
      final JTransactionRecord jTransactionRecord) throws IOException {

    final long version = inStream.readLong();
    if (version < LEGACY_VERSION_1 || version > CURRENT_VERSION) {
      throw new IllegalStateException("Illegal version was read from the stream");
    }

    if (version != CURRENT_VERSION) {
      jTransactionRecord.versionToSerialize = OptionalLong.of(version);
    }

    final long objectType = inStream.readLong();
    final JObjectType type = JObjectType.valueOf(objectType);
    if (!JObjectType.JTransactionRecord.equals(type)) {
      throw new IllegalStateException("Illegal JObjectType was read from the stream");
    }

    boolean tBytes;
    if (version == LEGACY_VERSION_1) {
      tBytes = inStream.readInt() > 0;
    } else {
      tBytes = inStream.readBoolean();
    }

    if (tBytes) {
      jTransactionRecord.txReceipt = JTransactionReceipt.deserialize(inStream);
    } else {
      jTransactionRecord.txReceipt = null;
    }

    byte[] hBytes = new byte[inStream.readInt()];
    if (hBytes.length > 0) {
      inStream.readFully(hBytes);
    }
    jTransactionRecord.txHash = hBytes;

    boolean txBytes;
    if (version == LEGACY_VERSION_1) {
      txBytes = inStream.readInt() > 0;
    } else {
      txBytes = inStream.readBoolean();
    }

    if (txBytes) {
      jTransactionRecord.transactionID = JTransactionID.deserialize(inStream);
    } else {
      jTransactionRecord.transactionID = null;
    }

    boolean cBytes;
    if (version == LEGACY_VERSION_1) {
      cBytes = inStream.readInt() > 0;
    } else {
      cBytes = inStream.readBoolean();
    }

    if (cBytes) {
      jTransactionRecord.consensusTimestamp = JTimestamp.deserialize(inStream);
    } else {
      jTransactionRecord.consensusTimestamp = null;
    }

    byte[] mBytes = new byte[inStream.readInt()];
    if (mBytes.length > 0) {
      inStream.readFully(mBytes);
      jTransactionRecord.memo = new String(mBytes);
    } else {
      jTransactionRecord.memo = null;
    }

    jTransactionRecord.transactionFee = inStream.readLong();

    boolean trBytes;

    if (version == LEGACY_VERSION_1) {
      trBytes = inStream.readInt() > 0;
    } else {
      trBytes = inStream.readBoolean();
    }

    if (trBytes) {
      jTransactionRecord.jTransferList = JTransferList.deserialize(inStream);
    } else {
      jTransactionRecord.jTransferList = null;
    }

    boolean clBytes;
    if (version == LEGACY_VERSION_1) {
      clBytes = inStream.readInt() > 0;
    } else {
      clBytes = inStream.readBoolean();
    }

    if (clBytes) {
      jTransactionRecord.contractCallResult = JContractFunctionResult.deserialize(inStream);
    } else {
      jTransactionRecord.contractCallResult = null;
    }

    boolean ccBytes;
    if (version == LEGACY_VERSION_1) {
      ccBytes = inStream.readInt() > 0;
    } else {
      ccBytes = inStream.readBoolean();
    }

    if (ccBytes) {
      jTransactionRecord.contractCreateResult = JContractFunctionResult.deserialize(inStream);
    } else {
      jTransactionRecord.contractCreateResult = null;
    }

    if (version >= CURRENT_VERSION) {
      jTransactionRecord.expirationTime = inStream.readLong();
    }
  }

  @Override
  public FastCopyable copy() {
    return new JTransactionRecord(this);
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
    JTransactionRecord that = (JTransactionRecord) o;
    return transactionFee == that.transactionFee &&
        txReceipt.equals(that.txReceipt) &&
        Arrays.equals(txHash, that.txHash) &&
        transactionID.equals(that.transactionID) &&
        Objects.equals(consensusTimestamp, that.consensusTimestamp) &&
        Objects.equals(memo, that.memo) &&
        Objects.equals(contractCallResult, that.contractCallResult) &&
        Objects.equals(contractCreateResult, that.contractCreateResult) &&
        Objects.equals(jTransferList, that.jTransferList) &&
        Objects.equals(expirationTime, that.expirationTime);
  }

  @Override
  public int hashCode() {
    int result = Objects
        .hash(txReceipt, transactionID, consensusTimestamp, memo, transactionFee,
            contractCallResult,
            contractCreateResult, jTransferList, expirationTime);
    result = 31 * result + Arrays.hashCode(txHash);
    return result;
  }
}
