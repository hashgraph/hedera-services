package com.hedera.services.legacy.unit.utils;

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

import com.hedera.services.legacy.utils.TransactionValidationUtils;
import com.hedera.test.mocks.TestContextValidator;
import com.hederahashgraph.api.proto.java.ContractCallTransactionBody;
import com.hederahashgraph.api.proto.java.ContractCreateTransactionBody;
import com.hederahashgraph.api.proto.java.Duration;
import com.hederahashgraph.api.proto.java.ResponseCodeEnum;
import com.hederahashgraph.api.proto.java.TransactionBody;
import com.hederahashgraph.builder.RequestBuilder;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.junit.Assert;
import org.junit.FixMethodOrder;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInstance;
import org.junit.platform.runner.JUnitPlatform;
import org.junit.runner.RunWith;
import org.junit.runners.MethodSorters;

/**
 * Unit tests for TransactionValidationUtils.
 *
 * @author Peter Lynn Created on 2019-06-26
 */
@RunWith(JUnitPlatform.class)
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
@FixMethodOrder(MethodSorters.NAME_ASCENDING)
@DisplayName("Test Suite for validateTxSpecificBody")
public class TransactionValidationUtilsTest {

  private static final Logger log = LogManager.getLogger(TransactionValidationUtilsTest.class);
  private static final long POSITIVE_NUMBER = 20L;
  private static final long NEGATIVE_NUMBER = -15L;
  private Duration defaultDuration;
  private Duration zeroDuration;

  @BeforeAll
  public void setUp() throws Throwable {
    defaultDuration = RequestBuilder.getDuration(3600 * 24);
    zeroDuration = RequestBuilder.getDuration(0);
  }

  private TransactionBody getContractCreateTxBody(long gas, long value, Duration renewalDuration) {
    ContractCreateTransactionBody createBody =    ContractCreateTransactionBody.newBuilder()
        .setGas(gas).setInitialBalance(value).setAutoRenewPeriod(renewalDuration)
        .build();
    TransactionBody txBody = TransactionBody.newBuilder()
        .setContractCreateInstance(createBody)
        .build();
    return txBody;
  }

  private TransactionBody getContractCallTxBody(long gas, long value) {
    ContractCallTransactionBody callBody = ContractCallTransactionBody.newBuilder()
        .setGas(gas).setAmount(value)
        .build();
    TransactionBody txBody = TransactionBody.newBuilder()
        .setContractCall(callBody)
        .build();
    return txBody;
  }

  @Test
  @DisplayName("001 createContract: Success")
  public void createContractSuccess() {
    TransactionBody txBody = getContractCreateTxBody(POSITIVE_NUMBER, POSITIVE_NUMBER, defaultDuration);
    ResponseCodeEnum result = TransactionValidationUtils.validateTxSpecificBody(txBody, TestContextValidator.TEST_VALIDATOR);
    Assert.assertEquals(ResponseCodeEnum.OK, result);
  }

  @Test
  @DisplayName("002 createContract: Zero Duration")
  public void createContractDuration() {
    TransactionBody txBody = getContractCreateTxBody(POSITIVE_NUMBER, POSITIVE_NUMBER, zeroDuration);
    ResponseCodeEnum result = TransactionValidationUtils.validateTxSpecificBody(txBody, TestContextValidator.TEST_VALIDATOR);
    Assert.assertEquals(ResponseCodeEnum.INVALID_RENEWAL_PERIOD, result);
  }

  @Test
  @DisplayName("003 createContract: Negative Gas")
  public void createContractGas() {
    TransactionBody txBody = getContractCreateTxBody(NEGATIVE_NUMBER, POSITIVE_NUMBER, defaultDuration);
    ResponseCodeEnum result = TransactionValidationUtils.validateTxSpecificBody(txBody, TestContextValidator.TEST_VALIDATOR);
    Assert.assertEquals(ResponseCodeEnum.CONTRACT_NEGATIVE_GAS, result);
  }

  @Test
  @DisplayName("004 createContract: Negative Initial Balance")
  public void createContractValue() {
    TransactionBody txBody = getContractCreateTxBody(POSITIVE_NUMBER, NEGATIVE_NUMBER, defaultDuration);
    ResponseCodeEnum result = TransactionValidationUtils.validateTxSpecificBody(txBody, TestContextValidator.TEST_VALIDATOR);
    Assert.assertEquals(ResponseCodeEnum.CONTRACT_NEGATIVE_VALUE, result);
  }

  @Test
  @DisplayName("011 callContract: Success")
  public void callContractSuccess() {
    TransactionBody txBody = getContractCallTxBody(POSITIVE_NUMBER, POSITIVE_NUMBER);
    ResponseCodeEnum result = TransactionValidationUtils.validateTxSpecificBody(txBody, TestContextValidator.TEST_VALIDATOR);
    Assert.assertEquals(ResponseCodeEnum.OK, result);
  }

  @Test
  @DisplayName("012 callContract: Negative Gas")
  public void callContractGas() {
    TransactionBody txBody = getContractCallTxBody(NEGATIVE_NUMBER, POSITIVE_NUMBER);
    ResponseCodeEnum result = TransactionValidationUtils.validateTxSpecificBody(txBody, TestContextValidator.TEST_VALIDATOR);
    Assert.assertEquals(ResponseCodeEnum.CONTRACT_NEGATIVE_GAS, result);
  }

  @Test
  @DisplayName("013 callContract: Negative Amount")
  public void callContractValue() {
    TransactionBody txBody = getContractCallTxBody(POSITIVE_NUMBER, NEGATIVE_NUMBER);
    ResponseCodeEnum result = TransactionValidationUtils.validateTxSpecificBody(txBody, TestContextValidator.TEST_VALIDATOR);
    Assert.assertEquals(ResponseCodeEnum.CONTRACT_NEGATIVE_VALUE, result);
  }
}
