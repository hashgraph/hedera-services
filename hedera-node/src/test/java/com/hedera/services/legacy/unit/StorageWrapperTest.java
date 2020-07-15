package com.hedera.services.legacy.unit;

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

import com.hedera.services.legacy.handler.FCStorageWrapper;
import com.hedera.services.state.merkle.MerkleBlobMeta;
import com.hedera.services.state.merkle.MerkleOptionalBlob;
import com.hedera.services.legacy.exception.StorageKeyNotFoundException;

import java.time.Instant;
import java.util.Arrays;
import java.util.concurrent.ThreadLocalRandom;
import com.swirlds.fcmap.FCMap;
import org.apache.commons.lang3.ArrayUtils;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInstance;
import org.junit.jupiter.api.TestInstance.Lifecycle;
import org.junit.platform.runner.JUnitPlatform;
import org.junit.runner.RunWith;
@RunWith(JUnitPlatform.class)
@TestInstance(Lifecycle.PER_CLASS)
class StorageWrapperTest {
  private FCMap<MerkleBlobMeta, MerkleOptionalBlob> storageMap = new FCMap<>(new MerkleBlobMeta.Provider(), new MerkleOptionalBlob.Provider());
  private static final String TEST_CREATE_FILE_PATH = "/0/a1234/";
  private static final String TEST_EXPIRATION_AFTER_CREATE_FILE_PATH = "/0/a1235/";
  private static final String TEST_CREATE_DELETE_FILE_PATH = "/0/a1236/";
  private static final String TEST_CREATE_APPEND_FILE_PATH = "/0/a1237/";
  private static final String TEST_UPDATE_EXPIRATION_FILE_PATH = "/0/a1238/";
  private static final String TEST_OVERRIDE_FILE_PATH = "/0/a1239/";
  private static long TEST_FILE_CREATE_EXPIRATION_TIME = 4114602824L;
  private static long TEST_FILE_CREATE_CREATION_TIME = 4111012835L;
  private static long TEST_FILE_EXPIRATION_AFTER_CREATE_CREATION_TIME = 4111033835L;
  @Test
  void testFileCreateRead() {
    byte[] fileCreateContent = fillByteArrayWithRandomValues(5000);
    FCStorageWrapper storageWrapper = new FCStorageWrapper(storageMap);
    storageWrapper.fileCreate(TEST_CREATE_FILE_PATH, fileCreateContent, TEST_FILE_CREATE_CREATION_TIME, 0, TEST_FILE_CREATE_EXPIRATION_TIME, null);
    Assertions.assertTrue(storageWrapper.fileExists(TEST_CREATE_FILE_PATH));
    byte[] fileReadContent = storageWrapper.fileRead(TEST_CREATE_FILE_PATH);
    Assertions.assertTrue(ArrayUtils.isEquals(fileCreateContent, fileReadContent));
  }

  @Test
  void testFileExpirationAfterCreate() throws StorageKeyNotFoundException {
    byte[] fileCreateContent = fillByteArrayWithRandomValues(5000);
    FCStorageWrapper storageWrapper = new FCStorageWrapper(storageMap);
    long expirationTime = ThreadLocalRandom.current().nextLong(TEST_FILE_EXPIRATION_AFTER_CREATE_CREATION_TIME, TEST_FILE_EXPIRATION_AFTER_CREATE_CREATION_TIME + 1000000);
    storageWrapper.fileCreate(TEST_EXPIRATION_AFTER_CREATE_FILE_PATH, fileCreateContent, TEST_FILE_CREATE_CREATION_TIME, 0, expirationTime, null);
    Assertions.assertTrue(storageWrapper.fileExists(TEST_EXPIRATION_AFTER_CREATE_FILE_PATH));
  }
  
  
  @Test
  void testFileCreateDelete() throws StorageKeyNotFoundException {
    byte[] fileCreateContent = fillByteArrayWithRandomValues(5000);
    FCStorageWrapper storageWrapper = new FCStorageWrapper(storageMap);
    storageWrapper.fileCreate(TEST_CREATE_DELETE_FILE_PATH, fileCreateContent, TEST_FILE_CREATE_CREATION_TIME, 0, TEST_FILE_CREATE_EXPIRATION_TIME, null);
    Assertions.assertTrue(storageWrapper.fileExists(TEST_CREATE_DELETE_FILE_PATH));
    Instant modifyTimeStamp = Instant.now();
    long modifyTimeSec = modifyTimeStamp.getEpochSecond();
    storageWrapper.delete(TEST_CREATE_DELETE_FILE_PATH, modifyTimeSec, 0);
    Assertions.assertFalse(storageWrapper.fileExists(TEST_CREATE_DELETE_FILE_PATH));
  }
  
  @Test
  void testFileCreateAppend() throws StorageKeyNotFoundException {
    
    byte[] fileCombinedContentExpected = fillByteArrayWithRandomValues(6000);
    int fistChunklength = ThreadLocalRandom.current().nextInt(1000, 5000);
    byte[] file1Content = Arrays.copyOfRange(fileCombinedContentExpected, 0, fistChunklength);
    byte[] file2Content = Arrays.copyOfRange(fileCombinedContentExpected, fistChunklength, fileCombinedContentExpected.length);

    FCStorageWrapper storageWrapper = new FCStorageWrapper(storageMap);
    storageWrapper.fileCreate(TEST_CREATE_APPEND_FILE_PATH, file1Content, TEST_FILE_CREATE_CREATION_TIME, 0, TEST_FILE_CREATE_EXPIRATION_TIME, null);
    Assertions.assertTrue(storageWrapper.fileExists(TEST_CREATE_APPEND_FILE_PATH));
    Instant modifyTimeStamp = Instant.now();
    long modifyTimeSec = modifyTimeStamp.getEpochSecond();
    storageWrapper.fileUpdate(TEST_CREATE_APPEND_FILE_PATH, file2Content, modifyTimeSec, 0,
        TEST_FILE_CREATE_EXPIRATION_TIME);
    byte[] fileReadContent = storageWrapper.fileRead(TEST_CREATE_APPEND_FILE_PATH);
    Assertions.assertTrue(Arrays.equals(fileCombinedContentExpected, fileReadContent));
  }
  
  @Test
  void testFileExpirationUpdate() throws StorageKeyNotFoundException {
    byte[] fileCreateContent = fillByteArrayWithRandomValues(5000);
    FCStorageWrapper storageWrapper = new FCStorageWrapper(storageMap);
    long expirationTime = ThreadLocalRandom.current().nextLong(TEST_FILE_EXPIRATION_AFTER_CREATE_CREATION_TIME, TEST_FILE_EXPIRATION_AFTER_CREATE_CREATION_TIME + 1000000);
    storageWrapper.fileCreate(TEST_UPDATE_EXPIRATION_FILE_PATH, fileCreateContent, TEST_FILE_CREATE_CREATION_TIME, 0, expirationTime, null);
    Assertions.assertTrue(storageWrapper.fileExists(TEST_UPDATE_EXPIRATION_FILE_PATH));
  }
  
  @Test
  void testOverrideFile() {
    byte[] fileCreateContent = fillByteArrayWithRandomValues(5000);
    FCStorageWrapper storageWrapper = new FCStorageWrapper(storageMap);
    storageWrapper.fileCreate(TEST_OVERRIDE_FILE_PATH, fileCreateContent, TEST_FILE_CREATE_CREATION_TIME, 0, TEST_FILE_CREATE_EXPIRATION_TIME, null);
    Assertions.assertTrue(storageWrapper.fileExists(TEST_OVERRIDE_FILE_PATH));
    byte[] fileReadContent = storageWrapper.fileRead(TEST_OVERRIDE_FILE_PATH);
    Assertions.assertTrue(Arrays.equals(fileCreateContent, fileReadContent));
    byte[] fileOverRideContent = fillByteArrayWithRandomValues(2000);
    storageWrapper.fileCreate(TEST_OVERRIDE_FILE_PATH, fileOverRideContent, TEST_FILE_CREATE_CREATION_TIME, 0, TEST_FILE_CREATE_EXPIRATION_TIME, null);
    Assertions.assertTrue(storageWrapper.fileExists(TEST_OVERRIDE_FILE_PATH));
    byte[] fileReadAfterOverrideContent = storageWrapper.fileRead(TEST_OVERRIDE_FILE_PATH);
    Assertions.assertTrue(Arrays.equals(fileOverRideContent, fileReadAfterOverrideContent));
  }
  private byte[] fillByteArrayWithRandomValues(int arraySize) {
    byte[] arrayToReturn = new byte[arraySize];
    ThreadLocalRandom.current().nextBytes(arrayToReturn);
     return arrayToReturn;
  }
}
