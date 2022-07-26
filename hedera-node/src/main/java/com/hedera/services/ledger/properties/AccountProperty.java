/*
 * Copyright (C) 2020-2022 Hedera Hashgraph, LLC
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
package com.hedera.services.ledger.properties;

import com.google.protobuf.ByteString;
import com.hedera.services.exceptions.NegativeAccountBalanceException;
import com.hedera.services.legacy.core.jproto.JKey;
import com.hedera.services.state.merkle.MerkleAccount;
import com.hedera.services.state.submerkle.EntityId;
import com.hedera.services.state.submerkle.FcTokenAllowanceId;
import com.hedera.services.utils.EntityNum;
import java.util.Map;
import java.util.Set;
import java.util.function.BiConsumer;
import java.util.function.Function;

/**
 * Implements a property family whose instances can provide the getter/setter pairs relevant to
 * themselves on a {@link MerkleAccount} object.
 */
@SuppressWarnings("unchecked")
public enum AccountProperty implements BeanProperty<MerkleAccount> {
    IS_DELETED {
        @Override
        public BiConsumer<MerkleAccount, Object> setter() {
            return (a, f) -> a.setDeleted((boolean) f);
        }

        @Override
        public Function<MerkleAccount, Object> getter() {
            return MerkleAccount::isDeleted;
        }
    },
    IS_RECEIVER_SIG_REQUIRED {
        @Override
        public BiConsumer<MerkleAccount, Object> setter() {
            return (a, f) -> a.setReceiverSigRequired((boolean) f);
        }

        @Override
        public Function<MerkleAccount, Object> getter() {
            return MerkleAccount::isReceiverSigRequired;
        }
    },
    IS_SMART_CONTRACT {
        @Override
        public BiConsumer<MerkleAccount, Object> setter() {
            return (a, f) -> a.setSmartContract((boolean) f);
        }

        @Override
        public Function<MerkleAccount, Object> getter() {
            return MerkleAccount::isSmartContract;
        }
    },
    BALANCE {
        @Override
        @SuppressWarnings("unchecked")
        public BiConsumer<MerkleAccount, Object> setter() {
            return (a, v) -> {
                try {
                    a.setBalance(((Number) v).longValue());
                } catch (ClassCastException cce) {
                    throw new IllegalArgumentException(
                            "Wrong argument type! Argument needs to be of type int or long. Actual"
                                    + " value: "
                                    + v,
                            cce);
                } catch (NegativeAccountBalanceException nabe) {
                    throw new IllegalArgumentException(
                            "Argument 'v="
                                    + v
                                    + "' would cause account 'a="
                                    + a
                                    + "' to have a negative balance!",
                            nabe);
                }
            };
        }

        @Override
        public Function<MerkleAccount, Object> getter() {
            return MerkleAccount::getBalance;
        }
    },
    AUTO_RENEW_PERIOD {
        @Override
        public BiConsumer<MerkleAccount, Object> setter() {
            return (a, v) -> a.setAutoRenewSecs((long) v);
        }

        @Override
        public Function<MerkleAccount, Object> getter() {
            return MerkleAccount::getAutoRenewSecs;
        }
    },
    EXPIRY {
        @Override
        public BiConsumer<MerkleAccount, Object> setter() {
            return (a, v) -> a.setExpiry((long) v);
        }

        @Override
        public Function<MerkleAccount, Object> getter() {
            return MerkleAccount::getExpiry;
        }
    },
    KEY {
        @Override
        public BiConsumer<MerkleAccount, Object> setter() {
            return (a, k) -> a.setAccountKey((JKey) k);
        }

        @Override
        public Function<MerkleAccount, Object> getter() {
            return MerkleAccount::getAccountKey;
        }
    },
    MEMO {
        @Override
        public BiConsumer<MerkleAccount, Object> setter() {
            return (a, s) -> a.setMemo((String) s);
        }

        @Override
        public Function<MerkleAccount, Object> getter() {
            return MerkleAccount::getMemo;
        }
    },
    PROXY {
        @Override
        public BiConsumer<MerkleAccount, Object> setter() {
            return (a, p) -> a.setProxy((EntityId) p);
        }

        @Override
        public Function<MerkleAccount, Object> getter() {
            return MerkleAccount::getProxy;
        }
    },
    NUM_NFTS_OWNED {
        @Override
        public BiConsumer<MerkleAccount, Object> setter() {
            return (a, n) -> a.setNftsOwned((long) n);
        }

        @Override
        public Function<MerkleAccount, Object> getter() {
            return MerkleAccount::getNftsOwned;
        }
    },
    MAX_AUTOMATIC_ASSOCIATIONS {
        @Override
        public BiConsumer<MerkleAccount, Object> setter() {
            return (a, t) -> a.setMaxAutomaticAssociations((int) t);
        }

        @Override
        public Function<MerkleAccount, Object> getter() {
            return MerkleAccount::getMaxAutomaticAssociations;
        }
    },
    USED_AUTOMATIC_ASSOCIATIONS {
        @Override
        public BiConsumer<MerkleAccount, Object> setter() {
            return (a, t) -> a.setUsedAutomaticAssociations((int) t);
        }

        @Override
        public Function<MerkleAccount, Object> getter() {
            return MerkleAccount::getUsedAutoAssociations;
        }
    },
    NUM_CONTRACT_KV_PAIRS {
        @Override
        public BiConsumer<MerkleAccount, Object> setter() {
            return (a, n) -> a.setNumContractKvPairs((int) n);
        }

        @Override
        public Function<MerkleAccount, Object> getter() {
            return MerkleAccount::getNumContractKvPairs;
        }
    },
    ALIAS {
        @Override
        public BiConsumer<MerkleAccount, Object> setter() {
            return (a, t) -> a.setAlias((ByteString) t);
        }

        @Override
        public Function<MerkleAccount, Object> getter() {
            return MerkleAccount::getAlias;
        }
    },
    ETHEREUM_NONCE {
        @Override
        public BiConsumer<MerkleAccount, Object> setter() {
            return (a, v) -> a.setEthereumNonce((long) v);
        }

        @Override
        public Function<MerkleAccount, Object> getter() {
            return MerkleAccount::getEthereumNonce;
        }
    },
    CRYPTO_ALLOWANCES {
        @Override
        public BiConsumer<MerkleAccount, Object> setter() {
            return (a, t) -> a.setCryptoAllowancesUnsafe((Map<EntityNum, Long>) t);
        }

        @Override
        public Function<MerkleAccount, Object> getter() {
            return MerkleAccount::getCryptoAllowancesUnsafe;
        }
    },
    FUNGIBLE_TOKEN_ALLOWANCES {
        @Override
        public BiConsumer<MerkleAccount, Object> setter() {
            return (a, t) -> a.setFungibleTokenAllowancesUnsafe((Map<FcTokenAllowanceId, Long>) t);
        }

        @Override
        public Function<MerkleAccount, Object> getter() {
            return MerkleAccount::getFungibleTokenAllowancesUnsafe;
        }
    },
    APPROVE_FOR_ALL_NFTS_ALLOWANCES {
        @Override
        public BiConsumer<MerkleAccount, Object> setter() {
            return (a, t) -> a.setApproveForAllNfts((Set<FcTokenAllowanceId>) t);
        }

        @Override
        public Function<MerkleAccount, Object> getter() {
            return MerkleAccount::getApproveForAllNftsUnsafe;
        }
    },
    NUM_ASSOCIATIONS {
        @Override
        public BiConsumer<MerkleAccount, Object> setter() {
            return (a, t) -> a.setNumAssociations((int) t);
        }

        @Override
        public Function<MerkleAccount, Object> getter() {
            return MerkleAccount::getNumAssociations;
        }
    },
    NUM_POSITIVE_BALANCES {
        @Override
        public BiConsumer<MerkleAccount, Object> setter() {
            return (a, t) -> a.setNumPositiveBalances((int) t);
        }

        @Override
        public Function<MerkleAccount, Object> getter() {
            return MerkleAccount::getNumPositiveBalances;
        }
    },
    FIRST_CONTRACT_STORAGE_KEY {
        @Override
        public BiConsumer<MerkleAccount, Object> setter() {
            return (a, t) -> a.setFirstUint256StorageKey((int[]) t);
        }

        @Override
        public Function<MerkleAccount, Object> getter() {
            return MerkleAccount::getFirstUint256Key;
        }
    },
    NUM_TREASURY_TITLES {
        @Override
        public BiConsumer<MerkleAccount, Object> setter() {
            return (a, t) -> a.state().setNumTreasuryTitles((int) t);
        }

        @Override
        public Function<MerkleAccount, Object> getter() {
            return MerkleAccount::getNumTreasuryTitles;
        }
    },
    AUTO_RENEW_ACCOUNT_ID {
        @Override
        public BiConsumer<MerkleAccount, Object> setter() {
            return (a, p) -> a.setAutoRenewAccount((EntityId) p);
        }

        @Override
        public Function<MerkleAccount, Object> getter() {
            return MerkleAccount::getAutoRenewAccount;
        }
    },
    DECLINE_REWARD {
        @Override
        public BiConsumer<MerkleAccount, Object> setter() {
            return (a, t) -> a.setDeclineReward((boolean) t);
        }

        @Override
        public Function<MerkleAccount, Object> getter() {
            return MerkleAccount::isDeclinedReward;
        }
    },
    STAKED_ID {
        @Override
        public BiConsumer<MerkleAccount, Object> setter() {
            return (a, t) -> {
                final var val = (long) t;
                a.setStakedId(val);
            };
        }

        @Override
        public Function<MerkleAccount, Object> getter() {
            return MerkleAccount::getStakedId;
        }
    }
}
