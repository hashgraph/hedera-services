/*
 * Copyright (C) 2022 Hedera Hashgraph, LLC
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
package com.hedera.services.state.merkle;

import static com.hedera.services.state.merkle.MerkleAccountState.RELEASE_0230_VERSION;
import static com.hedera.services.state.merkle.MerkleAccountState.RELEASE_0250_ALPHA_VERSION;
import static com.hedera.services.state.merkle.MerkleAccountState.RELEASE_0250_VERSION;
import static com.hedera.services.state.merkle.MerkleAccountState.RELEASE_0270_VERSION;

import com.hedera.test.serde.SelfSerializableDataTest;
import com.hedera.test.serde.SerializedForms;
import com.hedera.test.utils.SeededPropertySource;
import com.swirlds.common.utility.CommonUtils;

public class MerkleAccountStateSerdeTest extends SelfSerializableDataTest<MerkleAccountState> {
    public static final int NUM_TEST_CASES = 2 * MIN_TEST_CASES_PER_VERSION;

    @Override
    protected Class<MerkleAccountState> getType() {
        return MerkleAccountState.class;
    }

    @Override
    protected int getNumTestCasesFor(final int version) {
        return version < MerkleAccountState.RELEASE_0260_VERSION
                ? MIN_TEST_CASES_PER_VERSION
                : NUM_TEST_CASES;
    }

    @Override
    protected byte[] getSerializedForm(final int version, final int testCaseNo) {
        return getForm(version, testCaseNo);
    }

    @Override
    protected MerkleAccountState getExpectedObject(final int version, final int testCaseNo) {
        final var propertySource = SeededPropertySource.forSerdeTest(version, testCaseNo);
        if (version == RELEASE_0230_VERSION) {
            return propertySource.next0242AccountState();
        } else if (version <= RELEASE_0250_VERSION) {
            final var seededAccount = propertySource.next0250AccountState();
            if (version == RELEASE_0250_ALPHA_VERSION) {
                seededAccount.setNumTreasuryTitles(0);
                seededAccount.setNftsOwned(0);
            }
            return seededAccount;
        } else {
            final var seededAccount = getExpectedObject(propertySource);
            if (version < RELEASE_0270_VERSION) {
                seededAccount.setStakedToMe(0);
                seededAccount.setDeclineReward(false);
                seededAccount.setStakePeriodStart(-1);
                seededAccount.setStakedNum(0);
                seededAccount.setStakeAtStartOfLastRewardedPeriod(-1);
            }
            return seededAccount;
        }
    }

    @Override
    protected MerkleAccountState getExpectedObject(final SeededPropertySource propertySource) {
        return propertySource.nextAccountState();
    }

    private static byte[] getForm(final int version, final int testCaseNo) {
        return version == RELEASE_0230_VERSION
                ? CommonUtils.unhex(hexed024Forms[testCaseNo])
                : SerializedForms.loadForm(MerkleAccountState.class, version, testCaseNo);
    }

    private static final String[] hexed024Forms = {
        // The 0.24 forms
        "0100000000000000020000000000ed33e400000000000000185c2548b75e34082e7fd6390250ec85f25c2194fb17ca2662641392aeea16cb954fb8abb0c2a5106c6e1830fd4c79efa0000000645412211c7c042212385315792d433c46126a2b3b621166222405732467327e384b484d64144c2b495b2265640a7b4b125f7410593d616c464f6e6b1e603e0f581042795e6c16155f40370b371f3f146e220f1357210c693854176f3037756a6b1e553b0900010101f35ba643324efa37000000017d24b40e843d249f01a7d864fee3e95f5933e55f143342880000000000000000699678a30b0512690000002497a3a4ab886f682f120e1951b276c693cdb485b593cbc9143fc5ad098df7c9fd2f73acdc14c8dc43000000000000000000000000",
        "0100000000000000020000000000ef843a000000000000002479a3f6f4eb08dc8c35ace9cdd6751b3932f7bcb56a75aa5164ca4020890f574583be51846fdf4e079ad9d85c29fb744b11c53bd4761bb88d24b3f96d00000064227327405c0b5b6f5511551d3350474c517578280c3e4a7475020904064c67612a7c6e613a101e2b0b5d203f061075287874085a623b771d41373921717a5c103f38061c6a5f2c7d2a2c3c29234e235d2841166c5d19362c102a5f2b025a4d2f29257d1d01000101f35ba643324efa37000000013b91f2ff57c7166714421e8ee3d12f6c71e4ac7aba80b0e60000000000000000738734c6b81b1552000000249226400895bc803541b4faeb3207558c0768f90bc132b51fcf6058027e7a984acf0aed7b7f1b39a6000000000000000000000000",
        "0100000000000000020000000000ed33e40000000000000018759a1591ed3a228d48f79768661df08644c059dfe0b0bb401f8bc90e2bd9bd7e382b648720d5b1a7295a8e1cd30c1d710000006437516c0c0f421c48415f75634141555b7853022e03673a0b6d777d146f66512d322b3c2016722e257c1d1b2f2319531e69700c276b70201c4b27275b5d022c6507481272534b53652b52054a7110434737463c7b5b0a0e156f1b163c4c7c03475934225301000101f35ba643324efa37000000013bf9730eb87e92b242d91185bf21048f74b3e0a2df9aab8c00000000000000001b996e999edb21fe0000002452015b3850d86b5cb4c46925fde08adac1615770d7ca8f6ffb5d03deab090ee5abc797b122892443000000000000000000000000",
        "0100000000000000020000000000ed33e400000000000000181e202222d4ca95432d31457fe817ee7618c4993a3938dbc32191fa37dd9cacea4e1a2d8b4b6848381bbb0df9b697593c00000064326d2936023d27552a41572f1c217344110d1a37796a4c3e0f2b767b49210a12382312260949634e7d6f00295c490c793d163c6e60470a56242e2349136f5112021824775e016c2f65010d7e777a08081e6167482a4e7a74495d07780b2d5e4f7c392c1800000101f35ba643324efa37000000015adc9ab1e86a5a114a2122d4801bfa003a664bf91227c92c00000000000000002ef997ccf95ea8f200000024ddfbfdef3863bd121aa0541b9e1304f90139c6238914b2f701b60ecc12e286902cf9204e0f2f1a52000000000000000000000000",
        "0100000000000000020000000000ef843a00000000000000244953d500b42ff63812fec78be8d21d67dce01ecbf8e9185da4cd272bc02468b9d93e5744240e5fec3b820ec369c86dfd827f41ae78c794f1e3c50ae4000000643a0e424303365725485e3f6c627c051316233a31541e635432766307517b093a0a4561693f2f5048113e582511405c263c39763b2145087d156d37562e01060e0557054e61363129463f56057c0c0e504224381c1c381c6d4e38442c1c404c216212570101010101f35ba643324efa37000000013cf7bcb9cc73324618a01cf48aafbed3498e92b35a0452a4000000000000000047e50ae9b36e1950000000246bbf2218f045d8c351ce4def012f48636280108f2748404be60e427f0fddab831a368b5b79e1a38a000000000000000000000000",
    };
}
