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
package com.hedera.services.bdd.suites.perf;

import static com.hedera.services.bdd.suites.HapiSuite.ONE_MILLION_HBARS;
import static com.hedera.services.bdd.suites.freeze.CommonUpgradeResources.DEFAULT_APPENDS_PER_BURST;
import static com.hedera.services.bdd.suites.freeze.CommonUpgradeResources.DEFAULT_UPGRADE_DELAY;
import static com.hedera.services.bdd.suites.freeze.CommonUpgradeResources.DEFAULT_UPGRADE_FILE_ID;
import static com.hedera.services.bdd.suites.freeze.CommonUpgradeResources.DEFAULT_UPGRADE_FILE_PATH;

import com.google.common.base.MoreObjects;
import com.hedera.services.bdd.spec.HapiPropertySource;

public class PerfTestLoadSettings {
    public static final int DEFAULT_TPS = 500;
    public static final int DEFAULT_TOLERANCE_PERCENTAGE = 5;
    public static final int DEFAULT_MINS = 1;
    public static final int DEFAULT_ALLOWED_SECS_BELOW = 60;
    public static final int DEFAULT_BURST_SIZE = 5;
    public static final int DEFAULT_THREADS = 50;
    public static final int DEFAULT_SUBMIT_MESSAGE_SIZE = 256;
    public static final int DEFAULT_SUBMIT_MESSAGE_SIZE_VAR = 64;
    // By default, it will fall back to original test scenarios
    public static final int DEFAULT_TOTAL_TEST_ACCOUNTS = 2;
    public static final int DEFAULT_TOTAL_TEST_TOPICS = 1;
    public static final int DEFAULT_TOTAL_TEST_TOKENS = 1;
    public static final int DEFAULT_TOTAL_TEST_TOKEN_ACCOUNTS = 2;
    public static final int DEFAULT_TEST_TREASURE_START_ACCOUNT = 1001;
    public static final int DEFAULT_TOTAL_TOKEN_ASSOCIATIONS = 0;
    public static final int DEFAULT_TOTAL_SCHEDULED = 0;
    public static final int DEFAULT_TOTAL_CLIENTS = 1;
    public static final int DEFAULT_MEMO_LENGTH = 25;
    public static final int DEFAULT_DURATION_CREATE_TOKEN_ASSOCIATION = 60; // in seconds
    public static final int DEFAULT_DURATION_TOKEN_TRANSFER = 60; // in seconds
    public static final long DEFAULT_INITIAL_BALANCE = ONE_MILLION_HBARS;
    public static final int DEFAULT_TEST_TOPIC_ID = 30_000;
    public static final int DEFAULT_BALANCES_EXPORT_PERIOD_SECS = 60;
    public static final boolean DEFAULT_EXPORT_BALANCES_ON_CLIENT_SIDE = false;
    private static final int DEFAULT_NODE_TO_STAKE = 0;

    private int tps = DEFAULT_TPS;
    private int tolerancePercentage = DEFAULT_TOLERANCE_PERCENTAGE;
    private int mins = DEFAULT_MINS;
    private int allowedSecsBelow = DEFAULT_ALLOWED_SECS_BELOW;
    private int burstSize = DEFAULT_BURST_SIZE;
    private int threads = DEFAULT_THREADS;
    private int hcsSubmitMessageSize = DEFAULT_SUBMIT_MESSAGE_SIZE;
    private int hcsSubmitMessageSizeVar = DEFAULT_SUBMIT_MESSAGE_SIZE_VAR;
    private int memoLength = DEFAULT_MEMO_LENGTH;

    /**
     * totalTestAccounts specifies how many Crypto accounts in the state file. All of them
     * participate random crypto transfer perf test
     */
    private int totalTestAccounts = DEFAULT_TOTAL_TEST_ACCOUNTS;

    /**
     * totalTestTopics specifies total topics in the state file. They are all used in random HCS
     * submitMessage perf test
     */
    private int totalTestTopics = DEFAULT_TOTAL_TEST_TOPICS;

    /**
     * totalTestTokens specifies how many tokens are created on the fly for each run in addition to
     * those tokens (if any) restored from state file. These tokens are actively used in random HCS
     * submitMessage perf test
     */
    private int totalTestTokens = DEFAULT_TOTAL_TEST_TOKENS;

    /**
     * totalTestTokenAccounts specifies the range of accounts, say 10000, starting from
     * testTreasureStartAccount that will be associated with each active test tokens and will
     * actively participate random token transfer perf test.
     */
    private int totalTestTokenAccounts = DEFAULT_TOTAL_TEST_TOKEN_ACCOUNTS;

    /**
     * testTreasureStartAccount specifies the first account number (by default, 1001) of a range
     * accounts that will be serve as the treasures of the totalTestTokens. One account for each
     * active test token
     */
    private int testTreasureStartAccount = DEFAULT_TEST_TREASURE_START_ACCOUNT;

    /**
     * The totalClients denotes total how many test clients (SuiteRunners) will be used to create
     * tokens and send HTS traffic to hedera services. This parameter is used to tell each test
     * client how many tokens and token association it needs to create for current test setup. When
     * running from SuiteRunner, it will use the total client node number as its value if this
     * parameter is not explicitly provided.
     */
    private int totalClients = DEFAULT_TOTAL_CLIENTS;

    /**
     * The duration for client account balances test to create account token association randomly
     */
    private int durationCreateTokenAssociation = DEFAULT_DURATION_CREATE_TOKEN_ASSOCIATION;

    /** The duration for client account balances test to do token transfer randomly */
    private int durationTokenTransfer = DEFAULT_DURATION_TOKEN_TRANSFER;
    /** Total token associations in the saved state file */
    private int totalTokenAssociations = DEFAULT_TOTAL_TOKEN_ASSOCIATIONS;
    /** Total scheduled transactions in the saved state file */
    private int totalScheduled = DEFAULT_TOTAL_SCHEDULED;
    /** Initial balance for the Crypto accounts created during LoadTest */
    private long initialBalance = DEFAULT_INITIAL_BALANCE;

    /** topic ID for reconnect and other tests; */
    private int testTopicId = DEFAULT_TEST_TOPIC_ID;
    // test client to tell hedera services how long to export account balances.
    private int balancesExportPeriodSecs = DEFAULT_BALANCES_EXPORT_PERIOD_SECS;
    // tell EET client whether to dump account balances or not
    private boolean clientToExportBalances = DEFAULT_EXPORT_BALANCES_ON_CLIENT_SIDE;

    /** upgrade test properties */
    private int upgradeDelay = DEFAULT_UPGRADE_DELAY;

    private String upgradeFilePath = DEFAULT_UPGRADE_FILE_PATH;
    private String upgradeFileId = DEFAULT_UPGRADE_FILE_ID;
    private int upgradeFileAppendsPerBurst = DEFAULT_APPENDS_PER_BURST;

    private int nodeToStake = DEFAULT_NODE_TO_STAKE;
    private HapiPropertySource ciProps = null;

    public PerfTestLoadSettings() {}

    public PerfTestLoadSettings(int tps, int mins, int threads) {
        this.tps = tps;
        this.mins = mins;
        this.threads = threads;
    }

    public long getInitialBalance() {
        return initialBalance;
    }

    public int getMemoLength() {
        return memoLength;
    }

    public int getTps() {
        return tps;
    }

    public int getTotalClients() {
        return totalClients;
    }

    public int getTolerancePercentage() {
        return tolerancePercentage;
    }

    public int getMins() {
        return mins;
    }

    public int getAllowedSecsBelow() {
        return allowedSecsBelow;
    }

    public int getBurstSize() {
        return burstSize;
    }

    public int getThreads() {
        return threads;
    }

    public int getHcsSubmitMessageSize() {
        return hcsSubmitMessageSize;
    }

    public int getHcsSubmitMessageSizeVar() {
        return hcsSubmitMessageSizeVar;
    }

    public int getTotalAccounts() {
        return totalTestAccounts;
    }

    public int getNodeToStake() {
        return nodeToStake;
    }

    public int getTotalTopics() {
        return totalTestTopics;
    }

    public int getTotalTokens() {
        return totalTestTokens;
    }

    public int getTotalTestTokenAccounts() {
        return totalTestTokenAccounts;
    }

    public int getDurationCreateTokenAssociation() {
        return durationCreateTokenAssociation;
    }

    public int getDurationTokenTransfer() {
        return durationTokenTransfer;
    }

    public int getTotalTokenAssociations() {
        return totalTokenAssociations;
    }

    public int getTotalScheduled() {
        return totalScheduled;
    }

    public int getBalancesExportPeriodSecs() {
        return balancesExportPeriodSecs;
    }

    public boolean getClientToExportBalances() {
        return clientToExportBalances;
    }

    public int getTestTreasureStartAccount() {
        return testTreasureStartAccount;
    }

    public int getTestTopicId() {
        return testTopicId;
    }

    public String getProperty(String property, String defaultValue) {
        if (null != ciProps && ciProps.has(property)) {
            return ciProps.get(property);
        }
        return defaultValue;
    }

    public int getIntProperty(String property, int defaultValue) {
        if (null != ciProps && ciProps.has(property)) {
            return ciProps.getInteger(property);
        }
        return defaultValue;
    }

    public boolean getBooleanProperty(String property, boolean defaultValue) {
        if (null != ciProps && ciProps.has(property)) {
            return ciProps.getBoolean(property);
        }
        return defaultValue;
    }

    public void setFrom(HapiPropertySource ciProps) {
        this.ciProps = ciProps;
        if (ciProps.has("tps")) {
            tps = ciProps.getInteger("tps");
        }
        if (ciProps.has("totalClients")) {
            totalClients = ciProps.getInteger("totalClients");
        }
        if (ciProps.has("mins")) {
            mins = ciProps.getInteger("mins");
        }
        if (ciProps.has("tolerance")) {
            tolerancePercentage = ciProps.getInteger("tolerancePercentage");
        }
        if (ciProps.has("burstSize")) {
            burstSize = ciProps.getInteger("burstSize");
        }
        if (ciProps.has("allowedSecsBelow")) {
            allowedSecsBelow = ciProps.getInteger("allowedSecsBelow");
        }
        if (ciProps.has("threads")) {
            threads = ciProps.getInteger("threads");
        }
        if (ciProps.has("totalTestAccounts")) {
            totalTestAccounts = ciProps.getInteger("totalTestAccounts");
        }
        if (ciProps.has("totalTestTopics")) {
            totalTestTopics = ciProps.getInteger("totalTestTopics");
        }
        if (ciProps.has("totalTestTokens")) {
            totalTestTokens = ciProps.getInteger("totalTestTokens");
        }
        if (ciProps.has("totalTestTokenAccounts")) {
            totalTestTokenAccounts = ciProps.getInteger("totalTestTokenAccounts");
        }
        if (ciProps.has("durationCreateTokenAssociation")) {
            durationCreateTokenAssociation = ciProps.getInteger("durationCreateTokenAssociation");
        }
        if (ciProps.has("durationTokenTransfer")) {
            durationTokenTransfer = ciProps.getInteger("durationTokenTransfer");
        }
        if (ciProps.has("testTreasureStartAccount")) {
            testTreasureStartAccount = ciProps.getInteger("testTreasureStartAccount");
        }
        if (ciProps.has("messageSize")) {
            hcsSubmitMessageSize = ciProps.getInteger("messageSize");
        }
        if (ciProps.has("messageSizeVar")) {
            hcsSubmitMessageSizeVar = ciProps.getInteger("messageSizeVar");
        }
        if (ciProps.has("memoLength")) {
            memoLength = ciProps.getInteger("memoLength");
        }
        if (ciProps.has("totalTokenAssociations")) {
            totalTokenAssociations = ciProps.getInteger("totalTokenAssociations");
        }
        if (ciProps.has("totalScheduled")) {
            totalScheduled = ciProps.getInteger("totalScheduled");
        }
        if (ciProps.has("initialBalance")) {
            initialBalance = ciProps.getLong("initialBalance");
        }
        if (ciProps.has("testTopicId")) {
            testTopicId = ciProps.getInteger("testTopicId");
        }
        if (ciProps.has("balancesExportPeriodSecs")) {
            balancesExportPeriodSecs = ciProps.getInteger("balancesExportPeriodSecs");
        }
        if (ciProps.has("clientToExportBalances")) {
            clientToExportBalances = ciProps.getBoolean("clientToExportBalances");
        }
        if (ciProps.has("upgradeDelay")) {
            upgradeDelay = ciProps.getInteger("upgradeDelay");
        }
        if (ciProps.has("upgradeFilePath")) {
            upgradeFilePath = ciProps.get("upgradeFilePath");
        }
        if (ciProps.has("upgradeFileId")) {
            upgradeFileId = ciProps.get("upgradeFileId");
        }
        if (ciProps.has("upgradeFileAppendsPerBurst")) {
            upgradeFileAppendsPerBurst = ciProps.getInteger("upgradeFileAppendsPerBurst");
        }
        if (ciProps.has("nodeToStake")) {
            nodeToStake = ciProps.getInteger("nodeToStake");
        }
    }

    @Override
    public String toString() {
        return MoreObjects.toStringHelper(this)
                .add("tps", tps)
                .add("totalClients", totalClients)
                .add("mins", mins)
                .add("tolerance", tolerancePercentage)
                .add("burstSize", burstSize)
                .add("allowedSecsBelow", allowedSecsBelow)
                .add("threads", threads)
                .add("totalTestAccounts", totalTestAccounts)
                .add("totalTestTopics", totalTestTopics)
                .add("totalTestTokens", totalTestTokens)
                .add("durationCreateTokenAssociation", durationCreateTokenAssociation)
                .add("durationTokenTransfer", durationTokenTransfer)
                .add("testActiveTokenAccounts", totalTestTokenAccounts)
                .add("testTreasureStartAccount", testTreasureStartAccount)
                .add("submitMessageSize", hcsSubmitMessageSize)
                .add("submitMessageSizeVar", hcsSubmitMessageSizeVar)
                .add("memoLength", memoLength)
                .add("totalTokenAssociations", totalTokenAssociations)
                .add("totalScheduledTransactions", totalScheduled)
                .add("initialBalance", initialBalance)
                .add("testTopicId", testTopicId)
                .add("balancesExportPeriodSecs", balancesExportPeriodSecs)
                .add("clientToExportBalances", clientToExportBalances)
                .add("upgradeDelay", upgradeDelay)
                .add("upgradeFilePath", upgradeFilePath)
                .add("upgradeFileId", upgradeFileId)
                .add("upgradeFileAppendsPerBurst", upgradeFileAppendsPerBurst)
                .add("nodeToStake", nodeToStake)
                .toString();
    }
}
