package com.hedera.services.sysfiles.validation;

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

import org.junit.jupiter.api.Test;

import static com.hederahashgraph.api.proto.java.HederaFunctionality.ConsensusGetTopicInfo;
import static com.hederahashgraph.api.proto.java.HederaFunctionality.*;
import static org.junit.jupiter.api.Assertions.*;
import static com.hedera.services.sysfiles.validation.ExpectedCustomThrottles.OPS_FOR_RELEASE_0130;

class ExpectedCustomThrottlesTest {
	@Test
	void release0130HasExpected() {
		assertDoesNotThrow(ExpectedCustomThrottles::new);

		assertEquals(46, OPS_FOR_RELEASE_0130.size());
		// and:
		assertTrue(OPS_FOR_RELEASE_0130.contains(CryptoCreate), "Missing CryptoCreate!");
		assertTrue(OPS_FOR_RELEASE_0130.contains(CryptoTransfer), "Missing CryptoTransfer!");
		assertTrue(OPS_FOR_RELEASE_0130.contains(CryptoUpdate), "Missing CryptoUpdate!");
		assertTrue(OPS_FOR_RELEASE_0130.contains(CryptoDelete), "Missing CryptoDelete!");
		assertTrue(OPS_FOR_RELEASE_0130.contains(FileCreate), "Missing FileCreate!");
		assertTrue(OPS_FOR_RELEASE_0130.contains(FileUpdate), "Missing FileUpdate!");
		assertTrue(OPS_FOR_RELEASE_0130.contains(FileDelete), "Missing FileDelete!");
		assertTrue(OPS_FOR_RELEASE_0130.contains(FileAppend), "Missing FileAppend!");
		assertTrue(OPS_FOR_RELEASE_0130.contains(ContractCreate), "Missing ContractCreate!");
		assertTrue(OPS_FOR_RELEASE_0130.contains(ContractUpdate), "Missing ContractUpdate!");
		assertTrue(OPS_FOR_RELEASE_0130.contains(ContractCreate), "Missing ContractCreate!");
		assertTrue(OPS_FOR_RELEASE_0130.contains(ContractDelete), "Missing ContractDelete!");
		assertTrue(OPS_FOR_RELEASE_0130.contains(ConsensusCreateTopic), "Missing ConsensusCreateTopic!");
		assertTrue(OPS_FOR_RELEASE_0130.contains(ConsensusUpdateTopic), "Missing ConsensusUpdateTopic!");
		assertTrue(OPS_FOR_RELEASE_0130.contains(ConsensusDeleteTopic), "Missing ConsensusDeleteTopic!");
		assertTrue(OPS_FOR_RELEASE_0130.contains(ConsensusSubmitMessage), "Missing ConsensusSubmitMessage!");
		assertTrue(OPS_FOR_RELEASE_0130.contains(TokenCreate), "Missing TokenCreate!");
		assertTrue(OPS_FOR_RELEASE_0130.contains(TokenFreezeAccount), "Missing TokenFreezeAccount!");
		assertTrue(OPS_FOR_RELEASE_0130.contains(TokenUnfreezeAccount), "Missing TokenUnfreezeAccount!");
		assertTrue(OPS_FOR_RELEASE_0130.contains(TokenGrantKycToAccount), "Missing TokenGrantKycToAccount!");
		assertTrue(OPS_FOR_RELEASE_0130.contains(TokenRevokeKycFromAccount), "Missing TokenRevokeKycFromAccount!");
		assertTrue(OPS_FOR_RELEASE_0130.contains(TokenDelete), "Missing TokenDelete!");
		assertTrue(OPS_FOR_RELEASE_0130.contains(TokenMint), "Missing TokenMint!");
		assertTrue(OPS_FOR_RELEASE_0130.contains(TokenBurn), "Missing TokenBurn!");
		assertTrue(OPS_FOR_RELEASE_0130.contains(TokenAccountWipe), "Missing TokenAccountWipe!");
		assertTrue(OPS_FOR_RELEASE_0130.contains(TokenUpdate), "Missing TokenUpdate!");
		assertTrue(OPS_FOR_RELEASE_0130.contains(TokenAssociateToAccount), "Missing TokenAssociateToAccount!");
		assertTrue(OPS_FOR_RELEASE_0130.contains(TokenDissociateFromAccount), "Missing TokenDissociateFromAccount!");
		assertTrue(OPS_FOR_RELEASE_0130.contains(ScheduleCreate), "Missing ScheduleCreate!");
		assertTrue(OPS_FOR_RELEASE_0130.contains(ScheduleSign), "Missing ScheduleSign!");
		assertTrue(OPS_FOR_RELEASE_0130.contains(ScheduleDelete), "Missing ScheduleDelete!");
		assertTrue(OPS_FOR_RELEASE_0130.contains(ConsensusGetTopicInfo), "Missing ConsensusGetTopicInfo!");
		assertTrue(OPS_FOR_RELEASE_0130.contains(ContractCallLocal), "Missing ContractCallLocal!");
		assertTrue(OPS_FOR_RELEASE_0130.contains(ContractGetInfo), "Missing ContractGetInfo!");
		assertTrue(OPS_FOR_RELEASE_0130.contains(ContractGetBytecode), "Missing ContractGetBytecode!");
		assertTrue(OPS_FOR_RELEASE_0130.contains(ContractGetRecords), "Missing ContractGetRecords!");
		assertTrue(OPS_FOR_RELEASE_0130.contains(CryptoGetAccountBalance), "Missing CryptoGetAccountBalance!");
		assertTrue(OPS_FOR_RELEASE_0130.contains(CryptoGetAccountRecords), "Missing CryptoGetAccountRecords!");
		assertTrue(OPS_FOR_RELEASE_0130.contains(CryptoGetInfo), "Missing CryptoGetInfo!");
		assertTrue(OPS_FOR_RELEASE_0130.contains(FileGetContents), "Missing FileGetContents!");
		assertTrue(OPS_FOR_RELEASE_0130.contains(FileGetInfo), "Missing FileGetInfo!");
		assertTrue(OPS_FOR_RELEASE_0130.contains(TransactionGetReceipt), "Missing TransactionGetReceipt!");
		assertTrue(OPS_FOR_RELEASE_0130.contains(TransactionGetRecord), "Missing TransactionGetRecord!");
		assertTrue(OPS_FOR_RELEASE_0130.contains(GetVersionInfo), "Missing GetVersionInfo!");
		assertTrue(OPS_FOR_RELEASE_0130.contains(TokenGetInfo), "Missing TokenGetInfo!");
		assertTrue(OPS_FOR_RELEASE_0130.contains(ScheduleGetInfo), "Missing ScheduleGetInfo!");
	}
}
