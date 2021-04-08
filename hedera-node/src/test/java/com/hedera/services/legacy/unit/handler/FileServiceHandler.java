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
import com.hedera.services.legacy.unit.FCStorageWrapper;
import com.hedera.services.legacy.unit.GlobalFlag;
import com.hedera.services.legacy.unit.InvalidFileIDException;
import com.hedera.services.legacy.unit.InvalidFileWACLException;
import com.hedera.services.legacy.unit.serialization.HFileMetaSerdeTest;
import com.hedera.services.state.submerkle.ExchangeRates;
import com.hederahashgraph.api.proto.java.FileCreateTransactionBody;
import com.hederahashgraph.api.proto.java.FileGetInfoResponse.FileInfo;
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
import java.io.UnsupportedEncodingException;
import java.time.Instant;

import static com.hedera.services.utils.EntityIdUtils.readableId;

public class FileServiceHandler {
  private static final Logger log = LogManager.getLogger(FileServiceHandler.class);

  private GlobalFlag globalFlag;
  private FCStorageWrapper storageWrapper;

  public FileServiceHandler(
          FCStorageWrapper storageWrapper,
          FeeScheduleInterceptor feeScheduleInterceptor,
          ExchangeRates exchangeRates
  ) {
    this.globalFlag = GlobalFlag.getInstance();
    this.storageWrapper = storageWrapper;
  }

  /**
     * Converts a string to a byte array with UTF-8 encoding.
     *
     * @param str
     * 		string to be converted
     * @return converted byte array, or an empty array if there is an UnsupportedEncodingException.
     */
    public static byte[] string2bytesUTF8(String str) {
        byte[] rv = new byte[0];
        try {
            rv = str.getBytes("UTF-8");
        } catch (UnsupportedEncodingException e) {
        }
        return rv;
    }

	public static FileInfo lookupInfo(FileID fid, FCStorageWrapper fcfs) throws Exception {
		String metaPath = FeeCalcUtilsTest.pathOfMeta(fid);
		if (fcfs.fileExists(metaPath)) {
			long size = fcfs.getSize(FeeCalcUtilsTest.pathOf(fid));
			HFileMeta jInfo = HFileMeta.deserialize(fcfs.fileRead(metaPath));
			return HFileMetaSerdeTest.toGrpc(jInfo, fid, size);
		} else {
			throw new InvalidFileIDException(String.format("No such file '%s'!", readableId(fid)), fid);
		}
	}

  public static JKey convertWacl(KeyList waclAsKeyList) throws InvalidFileWACLException {
        try {
            return JKey.mapKey(Key.newBuilder().setKeyList(waclAsKeyList).build());
        } catch (DecoderException e) {
            throw new InvalidFileWACLException("input wacl=" + waclAsKeyList, e);
        }
    }

  /**
   * Creates a file on the ledger.
   */
  public TransactionRecord createFile(TransactionBody gtx, Instant timestamp, FileID fid,
      final long selfId) {
    TransactionRecord txRecord;
    TransactionID txId = gtx.getTransactionID();
    Instant startTime = RequestBuilder.convertProtoTimeStamp(txId.getTransactionValidStart());
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
          .fileCreate(fileDataPath, fileData, startTime.getEpochSecond(), startTime.getNano(),
              expireTimeSec, string2bytesUTF8(fileDataPath));

      // create virtual file for the meta data
      HFileMeta fi = new HFileMeta(false, jkey, expireTimeSec);

      String fileMetaDataPath = FeeCalcUtilsTest.pathOfMeta(fid);
      storageWrapper.fileCreate(fileMetaDataPath, fi.serialize(), startTime.getEpochSecond(),
          startTime.getNano(), expireTimeSec, null);

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

    TransactionReceipt receipt = RequestBuilder.getTransactionReceipt(fid, status,
        globalFlag.getExchangeRateSet());
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

  public FileInfo getFileInfo(FileID fid) throws Exception {
  	return lookupInfo(fid, storageWrapper);
  }

  public FCStorageWrapper getStorageWrapper() {
    return storageWrapper;
  }
}
