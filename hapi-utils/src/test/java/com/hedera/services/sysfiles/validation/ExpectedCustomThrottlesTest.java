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

import static com.hedera.services.sysfiles.validation.ExpectedCustomThrottles.OPS_FOR_RELEASE_0160;
import static com.hederahashgraph.api.proto.java.HederaFunctionality.ConsensusCreateTopic;
import static com.hederahashgraph.api.proto.java.HederaFunctionality.ConsensusDeleteTopic;
import static com.hederahashgraph.api.proto.java.HederaFunctionality.ConsensusGetTopicInfo;
import static com.hederahashgraph.api.proto.java.HederaFunctionality.ConsensusSubmitMessage;
import static com.hederahashgraph.api.proto.java.HederaFunctionality.ConsensusUpdateTopic;
import static com.hederahashgraph.api.proto.java.HederaFunctionality.ContractCallLocal;
import static com.hederahashgraph.api.proto.java.HederaFunctionality.ContractCreate;
import static com.hederahashgraph.api.proto.java.HederaFunctionality.ContractDelete;
import static com.hederahashgraph.api.proto.java.HederaFunctionality.ContractGetBytecode;
import static com.hederahashgraph.api.proto.java.HederaFunctionality.ContractGetInfo;
import static com.hederahashgraph.api.proto.java.HederaFunctionality.ContractGetRecords;
import static com.hederahashgraph.api.proto.java.HederaFunctionality.ContractUpdate;
import static com.hederahashgraph.api.proto.java.HederaFunctionality.CryptoCreate;
import static com.hederahashgraph.api.proto.java.HederaFunctionality.CryptoDelete;
import static com.hederahashgraph.api.proto.java.HederaFunctionality.CryptoGetAccountBalance;
import static com.hederahashgraph.api.proto.java.HederaFunctionality.CryptoGetAccountRecords;
import static com.hederahashgraph.api.proto.java.HederaFunctionality.CryptoGetInfo;
import static com.hederahashgraph.api.proto.java.HederaFunctionality.CryptoTransfer;
import static com.hederahashgraph.api.proto.java.HederaFunctionality.CryptoUpdate;
import static com.hederahashgraph.api.proto.java.HederaFunctionality.FileAppend;
import static com.hederahashgraph.api.proto.java.HederaFunctionality.FileCreate;
import static com.hederahashgraph.api.proto.java.HederaFunctionality.FileDelete;
import static com.hederahashgraph.api.proto.java.HederaFunctionality.FileGetContents;
import static com.hederahashgraph.api.proto.java.HederaFunctionality.FileGetInfo;
import static com.hederahashgraph.api.proto.java.HederaFunctionality.FileUpdate;
import static com.hederahashgraph.api.proto.java.HederaFunctionality.GetVersionInfo;
import static com.hederahashgraph.api.proto.java.HederaFunctionality.ScheduleCreate;
import static com.hederahashgraph.api.proto.java.HederaFunctionality.ScheduleDelete;
import static com.hederahashgraph.api.proto.java.HederaFunctionality.ScheduleGetInfo;
import static com.hederahashgraph.api.proto.java.HederaFunctionality.ScheduleSign;
import static com.hederahashgraph.api.proto.java.HederaFunctionality.TokenAccountWipe;
import static com.hederahashgraph.api.proto.java.HederaFunctionality.TokenAssociateToAccount;
import static com.hederahashgraph.api.proto.java.HederaFunctionality.TokenBurn;
import static com.hederahashgraph.api.proto.java.HederaFunctionality.TokenCreate;
import static com.hederahashgraph.api.proto.java.HederaFunctionality.TokenDelete;
import static com.hederahashgraph.api.proto.java.HederaFunctionality.TokenDissociateFromAccount;
import static com.hederahashgraph.api.proto.java.HederaFunctionality.TokenFeeScheduleUpdate;
import static com.hederahashgraph.api.proto.java.HederaFunctionality.TokenFreezeAccount;
import static com.hederahashgraph.api.proto.java.HederaFunctionality.TokenGetAccountNftInfos;
import static com.hederahashgraph.api.proto.java.HederaFunctionality.TokenGetInfo;
import static com.hederahashgraph.api.proto.java.HederaFunctionality.TokenGetNftInfo;
import static com.hederahashgraph.api.proto.java.HederaFunctionality.TokenGetNftInfos;
import static com.hederahashgraph.api.proto.java.HederaFunctionality.TokenGrantKycToAccount;
import static com.hederahashgraph.api.proto.java.HederaFunctionality.TokenMint;
import static com.hederahashgraph.api.proto.java.HederaFunctionality.TokenRevokeKycFromAccount;
import static com.hederahashgraph.api.proto.java.HederaFunctionality.TokenUnfreezeAccount;
import static com.hederahashgraph.api.proto.java.HederaFunctionality.TokenUpdate;
import static com.hederahashgraph.api.proto.java.HederaFunctionality.TransactionGetReceipt;
import static com.hederahashgraph.api.proto.java.HederaFunctionality.TransactionGetRecord;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ExpectedCustomThrottlesTest {
	@Test
	void release0160HasExpected() {
		assertEquals(50, OPS_FOR_RELEASE_0160.size());

		assertTrue(OPS_FOR_RELEASE_0160.contains(CryptoCreate), "Missing CryptoCreate!");
		assertTrue(OPS_FOR_RELEASE_0160.contains(CryptoTransfer), "Missing CryptoTransfer!");
		assertTrue(OPS_FOR_RELEASE_0160.contains(CryptoUpdate), "Missing CryptoUpdate!");
		assertTrue(OPS_FOR_RELEASE_0160.contains(CryptoDelete), "Missing CryptoDelete!");
		assertTrue(OPS_FOR_RELEASE_0160.contains(FileCreate), "Missing FileCreate!");
		assertTrue(OPS_FOR_RELEASE_0160.contains(FileUpdate), "Missing FileUpdate!");
		assertTrue(OPS_FOR_RELEASE_0160.contains(FileDelete), "Missing FileDelete!");
		assertTrue(OPS_FOR_RELEASE_0160.contains(FileAppend), "Missing FileAppend!");
		assertTrue(OPS_FOR_RELEASE_0160.contains(ContractCreate), "Missing ContractCreate!");
		assertTrue(OPS_FOR_RELEASE_0160.contains(ContractUpdate), "Missing ContractUpdate!");
		assertTrue(OPS_FOR_RELEASE_0160.contains(ContractCreate), "Missing ContractCreate!");
		assertTrue(OPS_FOR_RELEASE_0160.contains(ContractDelete), "Missing ContractDelete!");
		assertTrue(OPS_FOR_RELEASE_0160.contains(ConsensusCreateTopic), "Missing ConsensusCreateTopic!");
		assertTrue(OPS_FOR_RELEASE_0160.contains(ConsensusUpdateTopic), "Missing ConsensusUpdateTopic!");
		assertTrue(OPS_FOR_RELEASE_0160.contains(ConsensusDeleteTopic), "Missing ConsensusDeleteTopic!");
		assertTrue(OPS_FOR_RELEASE_0160.contains(ConsensusSubmitMessage), "Missing ConsensusSubmitMessage!");
		assertTrue(OPS_FOR_RELEASE_0160.contains(TokenCreate), "Missing TokenCreate!");
		assertTrue(OPS_FOR_RELEASE_0160.contains(TokenFreezeAccount), "Missing TokenFreezeAccount!");
		assertTrue(OPS_FOR_RELEASE_0160.contains(TokenGetNftInfo), "Missing TokenGetNftInfo!");
		assertTrue(OPS_FOR_RELEASE_0160.contains(TokenGetAccountNftInfos), "Missing TokenGetAccountNftInfos!");
		assertTrue(OPS_FOR_RELEASE_0160.contains(TokenGetNftInfos), "Missing TokenGetNftInfos!");
		assertTrue(OPS_FOR_RELEASE_0160.contains(TokenUnfreezeAccount), "Missing TokenUnfreezeAccount!");
		assertTrue(OPS_FOR_RELEASE_0160.contains(TokenGrantKycToAccount), "Missing TokenGrantKycToAccount!");
		assertTrue(OPS_FOR_RELEASE_0160.contains(TokenRevokeKycFromAccount), "Missing TokenRevokeKycFromAccount!");
		assertTrue(OPS_FOR_RELEASE_0160.contains(TokenDelete), "Missing TokenDelete!");
		assertTrue(OPS_FOR_RELEASE_0160.contains(TokenMint), "Missing TokenMint!");
		assertTrue(OPS_FOR_RELEASE_0160.contains(TokenBurn), "Missing TokenBurn!");
		assertTrue(OPS_FOR_RELEASE_0160.contains(TokenAccountWipe), "Missing TokenAccountWipe!");
		assertTrue(OPS_FOR_RELEASE_0160.contains(TokenUpdate), "Missing TokenUpdate!");
		assertTrue(OPS_FOR_RELEASE_0160.contains(TokenAssociateToAccount), "Missing TokenAssociateToAccount!");
		assertTrue(OPS_FOR_RELEASE_0160.contains(TokenDissociateFromAccount), "Missing TokenDissociateFromAccount!");
		assertTrue(OPS_FOR_RELEASE_0160.contains(TokenFeeScheduleUpdate), "Missing TokenFeeScheduleUpdate!");
		assertTrue(OPS_FOR_RELEASE_0160.contains(ScheduleCreate), "Missing ScheduleCreate!");
		assertTrue(OPS_FOR_RELEASE_0160.contains(ScheduleSign), "Missing ScheduleSign!");
		assertTrue(OPS_FOR_RELEASE_0160.contains(ScheduleDelete), "Missing ScheduleDelete!");
		assertTrue(OPS_FOR_RELEASE_0160.contains(ConsensusGetTopicInfo), "Missing ConsensusGetTopicInfo!");
		assertTrue(OPS_FOR_RELEASE_0160.contains(ContractCallLocal), "Missing ContractCallLocal!");
		assertTrue(OPS_FOR_RELEASE_0160.contains(ContractGetInfo), "Missing ContractGetInfo!");
		assertTrue(OPS_FOR_RELEASE_0160.contains(ContractGetBytecode), "Missing ContractGetBytecode!");
		assertTrue(OPS_FOR_RELEASE_0160.contains(ContractGetRecords), "Missing ContractGetRecords!");
		assertTrue(OPS_FOR_RELEASE_0160.contains(CryptoGetAccountBalance), "Missing CryptoGetAccountBalance!");
		assertTrue(OPS_FOR_RELEASE_0160.contains(CryptoGetAccountRecords), "Missing CryptoGetAccountRecords!");
		assertTrue(OPS_FOR_RELEASE_0160.contains(CryptoGetInfo), "Missing CryptoGetInfo!");
		assertTrue(OPS_FOR_RELEASE_0160.contains(FileGetContents), "Missing FileGetContents!");
		assertTrue(OPS_FOR_RELEASE_0160.contains(FileGetInfo), "Missing FileGetInfo!");
		assertTrue(OPS_FOR_RELEASE_0160.contains(TransactionGetReceipt), "Missing TransactionGetReceipt!");
		assertTrue(OPS_FOR_RELEASE_0160.contains(TransactionGetRecord), "Missing TransactionGetRecord!");
		assertTrue(OPS_FOR_RELEASE_0160.contains(GetVersionInfo), "Missing GetVersionInfo!");
		assertTrue(OPS_FOR_RELEASE_0160.contains(TokenGetInfo), "Missing TokenGetInfo!");
		assertTrue(OPS_FOR_RELEASE_0160.contains(ScheduleGetInfo), "Missing ScheduleGetInfo!");
	}
}
