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

import static com.hedera.services.sysfiles.validation.ExpectedCustomThrottles.OPS_FOR_RELEASE_TWENTY;
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
import static com.hederahashgraph.api.proto.java.HederaFunctionality.TokenPause;
import static com.hederahashgraph.api.proto.java.HederaFunctionality.TokenRevokeKycFromAccount;
import static com.hederahashgraph.api.proto.java.HederaFunctionality.TokenUnfreezeAccount;
import static com.hederahashgraph.api.proto.java.HederaFunctionality.TokenUnpause;
import static com.hederahashgraph.api.proto.java.HederaFunctionality.TokenUpdate;
import static com.hederahashgraph.api.proto.java.HederaFunctionality.TransactionGetReceipt;
import static com.hederahashgraph.api.proto.java.HederaFunctionality.TransactionGetRecord;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ExpectedCustomThrottlesTest {
	@Test
	void releaseTwentyHasExpected() {
		assertEquals(52, OPS_FOR_RELEASE_TWENTY.size());

		assertTrue(OPS_FOR_RELEASE_TWENTY.contains(CryptoCreate), "Missing CryptoCreate!");
		assertTrue(OPS_FOR_RELEASE_TWENTY.contains(CryptoTransfer), "Missing CryptoTransfer!");
		assertTrue(OPS_FOR_RELEASE_TWENTY.contains(CryptoUpdate), "Missing CryptoUpdate!");
		assertTrue(OPS_FOR_RELEASE_TWENTY.contains(CryptoDelete), "Missing CryptoDelete!");
		assertTrue(OPS_FOR_RELEASE_TWENTY.contains(FileCreate), "Missing FileCreate!");
		assertTrue(OPS_FOR_RELEASE_TWENTY.contains(FileUpdate), "Missing FileUpdate!");
		assertTrue(OPS_FOR_RELEASE_TWENTY.contains(FileDelete), "Missing FileDelete!");
		assertTrue(OPS_FOR_RELEASE_TWENTY.contains(FileAppend), "Missing FileAppend!");
		assertTrue(OPS_FOR_RELEASE_TWENTY.contains(ContractCreate), "Missing ContractCreate!");
		assertTrue(OPS_FOR_RELEASE_TWENTY.contains(ContractUpdate), "Missing ContractUpdate!");
		assertTrue(OPS_FOR_RELEASE_TWENTY.contains(ContractCreate), "Missing ContractCreate!");
		assertTrue(OPS_FOR_RELEASE_TWENTY.contains(ContractDelete), "Missing ContractDelete!");
		assertTrue(OPS_FOR_RELEASE_TWENTY.contains(ConsensusCreateTopic), "Missing ConsensusCreateTopic!");
		assertTrue(OPS_FOR_RELEASE_TWENTY.contains(ConsensusUpdateTopic), "Missing ConsensusUpdateTopic!");
		assertTrue(OPS_FOR_RELEASE_TWENTY.contains(ConsensusDeleteTopic), "Missing ConsensusDeleteTopic!");
		assertTrue(OPS_FOR_RELEASE_TWENTY.contains(ConsensusSubmitMessage), "Missing ConsensusSubmitMessage!");
		assertTrue(OPS_FOR_RELEASE_TWENTY.contains(TokenCreate), "Missing TokenCreate!");
		assertTrue(OPS_FOR_RELEASE_TWENTY.contains(TokenFreezeAccount), "Missing TokenFreezeAccount!");
		assertTrue(OPS_FOR_RELEASE_TWENTY.contains(TokenGetNftInfo), "Missing TokenGetNftInfo!");
		assertTrue(OPS_FOR_RELEASE_TWENTY.contains(TokenGetAccountNftInfos), "Missing TokenGetAccountNftInfos!");
		assertTrue(OPS_FOR_RELEASE_TWENTY.contains(TokenGetNftInfos), "Missing TokenGetNftInfos!");
		assertTrue(OPS_FOR_RELEASE_TWENTY.contains(TokenUnfreezeAccount), "Missing TokenUnfreezeAccount!");
		assertTrue(OPS_FOR_RELEASE_TWENTY.contains(TokenPause), "Missing TokenPause!");
		assertTrue(OPS_FOR_RELEASE_TWENTY.contains(TokenUnpause), "Missing TokenUnpause!");
		assertTrue(OPS_FOR_RELEASE_TWENTY.contains(TokenGrantKycToAccount), "Missing TokenGrantKycToAccount!");
		assertTrue(OPS_FOR_RELEASE_TWENTY.contains(TokenRevokeKycFromAccount), "Missing TokenRevokeKycFromAccount!");
		assertTrue(OPS_FOR_RELEASE_TWENTY.contains(TokenDelete), "Missing TokenDelete!");
		assertTrue(OPS_FOR_RELEASE_TWENTY.contains(TokenMint), "Missing TokenMint!");
		assertTrue(OPS_FOR_RELEASE_TWENTY.contains(TokenBurn), "Missing TokenBurn!");
		assertTrue(OPS_FOR_RELEASE_TWENTY.contains(TokenAccountWipe), "Missing TokenAccountWipe!");
		assertTrue(OPS_FOR_RELEASE_TWENTY.contains(TokenUpdate), "Missing TokenUpdate!");
		assertTrue(OPS_FOR_RELEASE_TWENTY.contains(TokenAssociateToAccount), "Missing TokenAssociateToAccount!");
		assertTrue(OPS_FOR_RELEASE_TWENTY.contains(TokenDissociateFromAccount), "Missing TokenDissociateFromAccount!");
		assertTrue(OPS_FOR_RELEASE_TWENTY.contains(TokenFeeScheduleUpdate), "Missing TokenFeeScheduleUpdate!");
		assertTrue(OPS_FOR_RELEASE_TWENTY.contains(ScheduleCreate), "Missing ScheduleCreate!");
		assertTrue(OPS_FOR_RELEASE_TWENTY.contains(ScheduleSign), "Missing ScheduleSign!");
		assertTrue(OPS_FOR_RELEASE_TWENTY.contains(ScheduleDelete), "Missing ScheduleDelete!");
		assertTrue(OPS_FOR_RELEASE_TWENTY.contains(ConsensusGetTopicInfo), "Missing ConsensusGetTopicInfo!");
		assertTrue(OPS_FOR_RELEASE_TWENTY.contains(ContractCallLocal), "Missing ContractCallLocal!");
		assertTrue(OPS_FOR_RELEASE_TWENTY.contains(ContractGetInfo), "Missing ContractGetInfo!");
		assertTrue(OPS_FOR_RELEASE_TWENTY.contains(ContractGetBytecode), "Missing ContractGetBytecode!");
		assertTrue(OPS_FOR_RELEASE_TWENTY.contains(ContractGetRecords), "Missing ContractGetRecords!");
		assertTrue(OPS_FOR_RELEASE_TWENTY.contains(CryptoGetAccountBalance), "Missing CryptoGetAccountBalance!");
		assertTrue(OPS_FOR_RELEASE_TWENTY.contains(CryptoGetAccountRecords), "Missing CryptoGetAccountRecords!");
		assertTrue(OPS_FOR_RELEASE_TWENTY.contains(CryptoGetInfo), "Missing CryptoGetInfo!");
		assertTrue(OPS_FOR_RELEASE_TWENTY.contains(FileGetContents), "Missing FileGetContents!");
		assertTrue(OPS_FOR_RELEASE_TWENTY.contains(FileGetInfo), "Missing FileGetInfo!");
		assertTrue(OPS_FOR_RELEASE_TWENTY.contains(TransactionGetReceipt), "Missing TransactionGetReceipt!");
		assertTrue(OPS_FOR_RELEASE_TWENTY.contains(TransactionGetRecord), "Missing TransactionGetRecord!");
		assertTrue(OPS_FOR_RELEASE_TWENTY.contains(GetVersionInfo), "Missing GetVersionInfo!");
		assertTrue(OPS_FOR_RELEASE_TWENTY.contains(TokenGetInfo), "Missing TokenGetInfo!");
		assertTrue(OPS_FOR_RELEASE_TWENTY.contains(ScheduleGetInfo), "Missing ScheduleGetInfo!");
	}
}
