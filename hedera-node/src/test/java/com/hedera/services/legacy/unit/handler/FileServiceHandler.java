package com.hedera.services.legacy.unit.handler;

/*-
 * ‌
 * Hedera Services Node
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

import com.google.protobuf.TextFormat;
import com.hedera.services.fees.calculation.FeeCalcUtilsTest;
import com.hedera.services.files.HFileMeta;
import com.hedera.services.legacy.core.jproto.JKey;
import com.hedera.services.legacy.unit.StorageTestHelper;
import com.hedera.services.legacy.unit.InvalidFileWACLException;
import com.hederahashgraph.api.proto.java.ExchangeRateSet;
import com.hederahashgraph.api.proto.java.FileCreateTransactionBody;
import com.hederahashgraph.api.proto.java.FileID;
import com.hederahashgraph.api.proto.java.Key;
import com.hederahashgraph.api.proto.java.KeyList;
import com.hederahashgraph.api.proto.java.ResponseCodeEnum;
import com.hederahashgraph.api.proto.java.TransactionBody;
import com.hederahashgraph.api.proto.java.TransactionID;
import com.hederahashgraph.api.proto.java.TransactionReceipt;
import com.hederahashgraph.api.proto.java.TransactionRecord;
import com.hederahashgraph.builder.RequestBuilder;
import org.apache.commons.codec.DecoderException;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import java.io.IOException;
import java.time.Instant;

public class FileServiceHandler {
  private static final Logger log = LogManager.getLogger(FileServiceHandler.class);
  private StorageTestHelper storageWrapper;

  FileServiceHandler(final StorageTestHelper storageWrapper) {
    this.storageWrapper = storageWrapper;
  }

  private static JKey convertWacl(final KeyList waclAsKeyList) throws InvalidFileWACLException {
        try {
            return JKey.mapKey(Key.newBuilder().setKeyList(waclAsKeyList).build());
        } catch (DecoderException e) {
            throw new InvalidFileWACLException("input wacl=" + waclAsKeyList, e);
        }
    }

  /**
   * Creates a file on the ledger.
   */
  TransactionRecord createFile(final TransactionBody gtx, final Instant timestamp, final FileID fid, final long selfId) {
    TransactionRecord txRecord;
    TransactionID txId = gtx.getTransactionID();
    FileCreateTransactionBody tx = gtx.getFileCreate();

    // get wacl and handle exception
    ResponseCodeEnum status = ResponseCodeEnum.SUCCESS;
    try {
      JKey jkey = convertWacl(tx.getKeys());

      // create virtual file for the data bytes
	  byte[] fileData = tx.getContents().toByteArray();
	  long fileSize = 0L;
	  if (fileData != null) {
	  	fileSize = fileData.length;
	  }
	  // compare size to allowable size
	  if (1024 * 1024L < fileSize) {
	  	throw new MaxFileSizeExceeded(
				String.format("The file size %d (bytes) is greater than allowed %d (bytes) ", fileSize,
	  					1024 * 1024L));
	  }
      String fileDataPath = FeeCalcUtilsTest.pathOf(fid);
      long expireTimeSec =
          RequestBuilder.convertProtoTimeStamp(tx.getExpirationTime()).getEpochSecond();

      if (log.isDebugEnabled()) {
        log.debug("Creating file at path :: " + fileDataPath + " :: nodeId = " + selfId);
      }
      storageWrapper
          .fileCreate(fileDataPath, fileData);

      // create virtual file for the meta data
      HFileMeta fi = new HFileMeta(false, jkey, expireTimeSec);

      String fileMetaDataPath = FeeCalcUtilsTest.pathOfMeta(fid);
      storageWrapper.fileCreate(fileMetaDataPath, fi.serialize());

    } catch (InvalidFileWACLException e) {
      if (log.isDebugEnabled()) {
        log.debug("File WACL invalid! tx=" + TextFormat.shortDebugString(tx), e);
      }
      status = ResponseCodeEnum.INVALID_FILE_WACL;
    } catch (IOException e) {
      if (log.isDebugEnabled()) {
        log.debug("File WACL serialization problem! tx=" + TextFormat.shortDebugString(tx), e);
      }
      status = ResponseCodeEnum.SERIALIZATION_FAILED;
    } catch (MaxFileSizeExceeded e) {
      status = ResponseCodeEnum.MAX_FILE_SIZE_EXCEEDED;
      log.debug("Maximum File Size Exceeded {}", ()->e);
	}

    TransactionReceipt receipt = RequestBuilder.getTransactionReceipt(fid, status, ExchangeRateSet.getDefaultInstance());
    TransactionRecord.Builder txRecordBuilder = TransactionRecord.newBuilder().setReceipt(receipt)
        .setConsensusTimestamp(RequestBuilder.getTimestamp(timestamp)).setTransactionID(txId)
        .setMemo(gtx.getMemo()).setTransactionFee(gtx.getTransactionFee());

    txRecord = txRecordBuilder.build();
    if (log.isDebugEnabled()) {
      log.debug("createFile TransactionRecord creation in state: " + Instant.now() + "; txRecord="
          + TextFormat.shortDebugString(txRecord) + "; tx=" + TextFormat.shortDebugString(tx));
    }
    return txRecord;
  }
}
