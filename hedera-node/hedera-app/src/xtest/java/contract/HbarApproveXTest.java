/*
 * Copyright (C) 2023-2024 Hedera Hashgraph, LLC
 *
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
 */

package contract;

import static com.hedera.hapi.node.base.ResponseCodeEnum.INVALID_ALLOWANCE_SPENDER_ID;
import static com.hederahashgraph.api.proto.java.ResponseCodeEnum.SUCCESS;
import static contract.HtsErc721TransferXTestConstants.APPROVED_HEADLONG_ADDRESS;
import static contract.HtsErc721TransferXTestConstants.UNAUTHORIZED_SPENDER_ADDRESS;
import static contract.HtsErc721TransferXTestConstants.UNAUTHORIZED_SPENDER_HEADLONG_ADDRESS;
import static contract.HtsErc721TransferXTestConstants.UNAUTHORIZED_SPENDER_ID;
import static contract.XTestConstants.AN_ED25519_KEY;
import static contract.XTestConstants.OWNER_ADDRESS;
import static contract.XTestConstants.OWNER_BESU_ADDRESS;
import static contract.XTestConstants.OWNER_HEADLONG_ADDRESS;
import static contract.XTestConstants.OWNER_ID;
import static contract.XTestConstants.SENDER_CONTRACT_ID_KEY;
import static org.junit.jupiter.api.Assertions.assertEquals;

import com.esaulpaugh.headlong.abi.Tuple;
import com.hedera.hapi.node.base.AccountID;
import com.hedera.hapi.node.state.primitives.ProtoBytes;
import com.hedera.hapi.node.state.token.Account;
import com.hedera.node.app.service.contract.impl.exec.systemcontracts.has.hbarapprove.HbarApproveTranslator;
import java.math.BigInteger;
import java.util.HashMap;
import java.util.Map;
import org.apache.tuweni.bytes.Bytes;

/**
 * Exercises hbar allowance for a token via the following steps relative to an {@code OWNER} and {@code SENDER} accounts:
 * <ol>
 *     <li>Set Hbar Approval of OWNER for SPENDER via {@link HbarApproveTranslator#HBAR_APPROVE_PROXY}.</li>
 *     <li>Set Hbar Approval of OWNER for SPENDER via {@link HbarApproveTranslator#HBAR_APPROVE}.</li>
 *     <li>Fail Hbar Allowance if SPENDER is not found.</li>
 *  * </ol>
 */
public class HbarApproveXTest extends AbstractContractXTest {
    @Override
    protected void doScenarioOperations() {
        runHasCallAndExpectOnSuccess(
                OWNER_BESU_ADDRESS,
                bytesForRedirectForAccount(
                        HbarApproveTranslator.HBAR_APPROVE_PROXY.encodeCallWithArgs(
                                UNAUTHORIZED_SPENDER_HEADLONG_ADDRESS, BigInteger.valueOf(100L)),
                        OWNER_ID),
                output -> assertEquals(
                        asBytesResult(
                                HbarApproveTranslator.HBAR_APPROVE.getOutputs().encodeElements((long)
                                        SUCCESS.getNumber())),
                        output),
                "Successful execution of hbarAllowance for SENDER by OWNER.");
        runHasCallAndExpectOnSuccess(
                OWNER_BESU_ADDRESS,
                Bytes.wrapByteBuffer(HbarApproveTranslator.HBAR_APPROVE.encodeCall(
                        Tuple.of(OWNER_HEADLONG_ADDRESS, UNAUTHORIZED_SPENDER_HEADLONG_ADDRESS, BigInteger.ONE))),
                output -> assertEquals(
                        asBytesResult(
                                HbarApproveTranslator.HBAR_APPROVE.getOutputs().encodeElements((long)
                                        SUCCESS.getNumber())),
                        output),
                "Successful execution of hbarAllowance for SENDER by OWNER.");
        runHasCallAndExpectRevert(
                OWNER_BESU_ADDRESS,
                bytesForRedirectForAccount(
                        HbarApproveTranslator.HBAR_APPROVE_PROXY.encodeCallWithArgs(
                                APPROVED_HEADLONG_ADDRESS, BigInteger.valueOf(100L)),
                        OWNER_ID),
                INVALID_ALLOWANCE_SPENDER_ID,
                "Failed execution of hbarApprove when SPENDER is not found.",
                true);
    }

    @Override
    protected Map<ProtoBytes, AccountID> initialAliases() {
        return new HashMap<>() {
            {
                put(ProtoBytes.newBuilder().value(OWNER_ADDRESS).build(), OWNER_ID);
                put(ProtoBytes.newBuilder().value(UNAUTHORIZED_SPENDER_ADDRESS).build(), UNAUTHORIZED_SPENDER_ID);
            }
        };
    }

    @Override
    protected Map<AccountID, Account> initialAccounts() {
        return new HashMap<>() {
            {
                put(
                        OWNER_ID,
                        Account.newBuilder()
                                .accountId(OWNER_ID)
                                .alias(OWNER_ADDRESS)
                                .key(SENDER_CONTRACT_ID_KEY)
                                .tinybarBalance(1000000000L)
                                .build());
                put(
                        UNAUTHORIZED_SPENDER_ID,
                        Account.newBuilder()
                                .accountId(UNAUTHORIZED_SPENDER_ID)
                                .alias(UNAUTHORIZED_SPENDER_ADDRESS)
                                .key(AN_ED25519_KEY)
                                .build());
            }
        };
    }
}
