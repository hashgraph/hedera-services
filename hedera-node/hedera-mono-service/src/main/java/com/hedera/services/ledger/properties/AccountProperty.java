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
import com.hedera.services.state.migration.HederaAccount;
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
public enum AccountProperty implements BeanProperty<HederaAccount> {
    IS_DELETED {
        @Override
        public BiConsumer<HederaAccount, Object> setter() {
            return (a, f) -> a.setDeleted((boolean) f);
        }

        @Override
        public Function<HederaAccount, Object> getter() {
            return HederaAccount::isDeleted;
        }
    },
    IS_RECEIVER_SIG_REQUIRED {
        @Override
        public BiConsumer<HederaAccount, Object> setter() {
            return (a, f) -> a.setReceiverSigRequired((boolean) f);
        }

        @Override
        public Function<HederaAccount, Object> getter() {
            return HederaAccount::isReceiverSigRequired;
        }
    },
    IS_SMART_CONTRACT {
        @Override
        public BiConsumer<HederaAccount, Object> setter() {
            return (a, f) -> a.setSmartContract((boolean) f);
        }

        @Override
        public Function<HederaAccount, Object> getter() {
            return HederaAccount::isSmartContract;
        }
    },
    BALANCE {
        @Override
        @SuppressWarnings("unchecked")
        public BiConsumer<HederaAccount, Object> setter() {
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
        public Function<HederaAccount, Object> getter() {
            return HederaAccount::getBalance;
        }
    },
    AUTO_RENEW_PERIOD {
        @Override
        public BiConsumer<HederaAccount, Object> setter() {
            return (a, v) -> a.setAutoRenewSecs((long) v);
        }

        @Override
        public Function<HederaAccount, Object> getter() {
            return HederaAccount::getAutoRenewSecs;
        }
    },
    EXPIRY {
        @Override
        public BiConsumer<HederaAccount, Object> setter() {
            return (a, v) -> a.setExpiry((long) v);
        }

        @Override
        public Function<HederaAccount, Object> getter() {
            return HederaAccount::getExpiry;
        }
    },
    KEY {
        @Override
        public BiConsumer<HederaAccount, Object> setter() {
            return (a, k) -> a.setAccountKey((JKey) k);
        }

        @Override
        public Function<HederaAccount, Object> getter() {
            return HederaAccount::getAccountKey;
        }
    },
    MEMO {
        @Override
        public BiConsumer<HederaAccount, Object> setter() {
            return (a, s) -> a.setMemo((String) s);
        }

        @Override
        public Function<HederaAccount, Object> getter() {
            return HederaAccount::getMemo;
        }
    },
    PROXY {
        @Override
        public BiConsumer<HederaAccount, Object> setter() {
            return (a, p) -> a.setProxy((EntityId) p);
        }

        @Override
        public Function<HederaAccount, Object> getter() {
            return HederaAccount::getProxy;
        }
    },
    NUM_NFTS_OWNED {
        @Override
        public BiConsumer<HederaAccount, Object> setter() {
            return (a, n) -> a.setNftsOwned((long) n);
        }

        @Override
        public Function<HederaAccount, Object> getter() {
            return HederaAccount::getNftsOwned;
        }
    },
    MAX_AUTOMATIC_ASSOCIATIONS {
        @Override
        public BiConsumer<HederaAccount, Object> setter() {
            return (a, t) -> a.setMaxAutomaticAssociations((int) t);
        }

        @Override
        public Function<HederaAccount, Object> getter() {
            return HederaAccount::getMaxAutomaticAssociations;
        }
    },
    USED_AUTOMATIC_ASSOCIATIONS {
        @Override
        public BiConsumer<HederaAccount, Object> setter() {
            return (a, t) -> a.setUsedAutomaticAssociations((int) t);
        }

        @Override
        public Function<HederaAccount, Object> getter() {
            return HederaAccount::getUsedAutoAssociations;
        }
    },
    NUM_CONTRACT_KV_PAIRS {
        @Override
        public BiConsumer<HederaAccount, Object> setter() {
            return (a, n) -> a.setNumContractKvPairs((int) n);
        }

        @Override
        public Function<HederaAccount, Object> getter() {
            return HederaAccount::getNumContractKvPairs;
        }
    },
    ALIAS {
        @Override
        public BiConsumer<HederaAccount, Object> setter() {
            return (a, t) -> a.setAlias((ByteString) t);
        }

        @Override
        public Function<HederaAccount, Object> getter() {
            return HederaAccount::getAlias;
        }
    },
    ETHEREUM_NONCE {
        @Override
        public BiConsumer<HederaAccount, Object> setter() {
            return (a, v) -> a.setEthereumNonce((long) v);
        }

        @Override
        public Function<HederaAccount, Object> getter() {
            return HederaAccount::getEthereumNonce;
        }
    },
    CRYPTO_ALLOWANCES {
        @Override
        public BiConsumer<HederaAccount, Object> setter() {
            return (a, t) -> a.setCryptoAllowancesUnsafe((Map<EntityNum, Long>) t);
        }

        @Override
        public Function<HederaAccount, Object> getter() {
            return HederaAccount::getCryptoAllowancesUnsafe;
        }
    },
    FUNGIBLE_TOKEN_ALLOWANCES {
        @Override
        public BiConsumer<HederaAccount, Object> setter() {
            return (a, t) -> a.setFungibleTokenAllowancesUnsafe((Map<FcTokenAllowanceId, Long>) t);
        }

        @Override
        public Function<HederaAccount, Object> getter() {
            return HederaAccount::getFungibleTokenAllowancesUnsafe;
        }
    },
    APPROVE_FOR_ALL_NFTS_ALLOWANCES {
        @Override
        public BiConsumer<HederaAccount, Object> setter() {
            return (a, t) -> a.setApproveForAllNfts((Set<FcTokenAllowanceId>) t);
        }

        @Override
        public Function<HederaAccount, Object> getter() {
            return HederaAccount::getApproveForAllNftsUnsafe;
        }
    },
    NUM_ASSOCIATIONS {
        @Override
        public BiConsumer<HederaAccount, Object> setter() {
            return (a, t) -> a.setNumAssociations((int) t);
        }

        @Override
        public Function<HederaAccount, Object> getter() {
            return HederaAccount::getNumAssociations;
        }
    },
    NUM_POSITIVE_BALANCES {
        @Override
        public BiConsumer<HederaAccount, Object> setter() {
            return (a, t) -> a.setNumPositiveBalances((int) t);
        }

        @Override
        public Function<HederaAccount, Object> getter() {
            return HederaAccount::getNumPositiveBalances;
        }
    },
    FIRST_CONTRACT_STORAGE_KEY {
        @Override
        public BiConsumer<HederaAccount, Object> setter() {
            return (a, t) -> a.setFirstUint256StorageKey((int[]) t);
        }

        @Override
        public Function<HederaAccount, Object> getter() {
            return HederaAccount::getFirstUint256Key;
        }
    },
    NUM_TREASURY_TITLES {
        @Override
        public BiConsumer<HederaAccount, Object> setter() {
            return (a, t) -> a.setNumTreasuryTitles((int) t);
        }

        @Override
        public Function<HederaAccount, Object> getter() {
            return HederaAccount::getNumTreasuryTitles;
        }
    },
    AUTO_RENEW_ACCOUNT_ID {
        @Override
        public BiConsumer<HederaAccount, Object> setter() {
            return (a, p) -> a.setAutoRenewAccount((EntityId) p);
        }

        @Override
        public Function<HederaAccount, Object> getter() {
            return HederaAccount::getAutoRenewAccount;
        }
    },
    DECLINE_REWARD {
        @Override
        public BiConsumer<HederaAccount, Object> setter() {
            return (a, t) -> a.setDeclineReward((boolean) t);
        }

        @Override
        public Function<HederaAccount, Object> getter() {
            return HederaAccount::isDeclinedReward;
        }
    },
    STAKED_ID {
        @Override
        public BiConsumer<HederaAccount, Object> setter() {
            return (a, t) -> {
                final var val = (long) t;
                a.setStakedId(val);
            };
        }

        @Override
        public Function<HederaAccount, Object> getter() {
            return HederaAccount::getStakedId;
        }
    }
}
