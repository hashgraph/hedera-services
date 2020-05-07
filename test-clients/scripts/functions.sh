function mvnExec() {
  mvn exec:java \
	-Dexec.mainClass=com.hedera.services.legacy.$1 \
	-Dexec.args="$2 $3 $4 $5" \
	-Dexec.cleanupDaemonThreads=false
}
