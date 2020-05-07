
#!/usr/bin/env bash

# script to run most circleci test at local macOS in batch mode
# 
# Steps
#   launch HGCApp
#   run 
#       ./scripts/local_circleci.sh
#   from test-clients/
#
#

mvn exec:java -Dexec.mainClass=com.hedera.services.bdd.suites.SuiteRunner -Dexec.args='0 3 TopicCreateSpecs' -Dexec.cleanupDaemonThreads=false

mvn exec:java -Dexec.mainClass=com.hedera.services.bdd.suites.SuiteRunner -Dexec.args='0 3 TopicUpdateSpecs' -Dexec.cleanupDaemonThreads=false

mvn exec:java -Dexec.mainClass=com.hedera.services.bdd.suites.SuiteRunner -Dexec.args='0 3 TopicDeleteSpecs' -Dexec.cleanupDaemonThreads=false

mvn exec:java -Dexec.mainClass=com.hedera.services.bdd.suites.SuiteRunner -Dexec.args='0 3 SubmitMessageSpecs' -Dexec.cleanupDaemonThreads=false

mvn exec:java -Dexec.mainClass=com.hedera.services.bdd.suites.SuiteRunner -Dexec.args='0 3 TopicGetInfoSpecs' -Dexec.cleanupDaemonThreads=false

mvn exec:java -Dexec.mainClass=com.hedera.services.bdd.suites.SuiteRunner -Dexec.args='0 3 ConsensusThrottlesSpecs' -Dexec.cleanupDaemonThreads=false

mvn exec:java -Dexec.mainClass=com.hedera.services.bdd.suites.SuiteRunner -Dexec.args='0 3 ControlAccountsExemptForUpdates' -Dexec.cleanupDaemonThreads=false

mvn exec:java -Dexec.mainClass=com.hedera.services.bdd.suites.SuiteRunner -Dexec.args='0 3 CryptoCreateSuite' -Dexec.cleanupDaemonThreads=false

mvn exec:java -Dexec.mainClass=com.hedera.services.bdd.suites.SuiteRunner -Dexec.args='0 3 CryptoRecordSanityChecks' -Dexec.cleanupDaemonThreads=false

mvn exec:java -Dexec.mainClass=com.hedera.services.bdd.suites.SuiteRunner -Dexec.args='0 3 SuperusersAreNeverThrottled' -Dexec.cleanupDaemonThreads=false

# non ETT

mvn exec:java -Dexec.mainClass=MultipleCryptoTransfers -Dexec.args='0 3 ' -Dexec.cleanupDaemonThreads=false

mvn exec:java -Dexec.mainClass=TxRecordTest -Dexec.args='0 3 ' -Dexec.cleanupDaemonThreads=false

mvn exec:java -Dexec.mainClass=NegativeAccountCreateTest -Dexec.args='0 3 ' -Dexec.cleanupDaemonThreads=false

mvn exec:java -Dexec.mainClass=NegativeCryptoQueryTest -Dexec.args='0 3 ' -Dexec.cleanupDaemonThreads=false
