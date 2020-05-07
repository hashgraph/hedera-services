
Introduction
============

From command line, user can launch Client Manager, by passing arguments to Client Manager, user
can select which test case to run, and what arguments are passed to the specific test case.

Client Manager would instantiate multiple client threads (Client Base Thread).

Each test thread is extended from Client Base Thread. Some commonly used functions are
defined in class ClientBaeThread to be shared by all derived test cases.


0) Typical command sample

mvn exec:java -Dexec.mainClass=ClientManager -Dexec.args=' <TEST_NAME> <THREAD_AMOUNT> <IP> <PORT> <NODE_ID> <SIGMAP> ..... ' -Dexec.cleanupDaemonThreads=false


The first few arguments are used by ClientManager

	<TEST_NAME> 		is the test case name, such as CryptoCreate or CryptoTransferUpdate, etc
	<THREAD_AMOUNT>     number of client thread running
	<IP>				ip address of node
	<PORT>				GRPC port number of node
	<NODE_ID>           node ID
	<SIGMAP>            boolean value of using signature map or sigature list

	Arguments after <SIGMAP> are passed to test case thread

You can run a short test with quickClientThread.sh, which include all following tests with relative small value to get a quick run.

	cd services-hedera/hapiClient
	./quickClientThread.sh <NODE_3_IP_ADDRESS>

	or test on localhost with

	./quickClientThread.sh

1) Crypto Create Account

mvn exec:java -Dexec.mainClass=ClientManager -Dexec.args=' CryptoCreate 1 <IP> 50211 3 true 2000 100 ' -Dexec.cleanupDaemonThreads=false


First 6 arguments are passed to ClientManager

	test thead name   CryptoCreateThread
	number of thread  3
	node ip address   34.235.149.133
	prot number       50211
	node ID           3
	use signatureMap  true


	the remained arguments are passed to test thread, for example input to CryptoCreate is "2000 100" which means target TPS per thread is 100 TPS,
	and test case should create 2000 crypto accounts


2) Crypto Update

mvn exec:java -Dexec.mainClass=ClientManager -Dexec.args=' CryptoTransferUpdate 3 <IP> 50211 3 true 2000000000 15 10000 true ' -Dexec.cleanupDaemonThreads=false


	for example input to CryptoTransferUpdate is "2000000000 15 10000 1 "

	total transaction per thread  	2000000000
	target TPS per thread    		15
	initial balance         		10000, after some operation when balance is too long new account will create to continue
	transfer test of update test   	true for update, false for transfer

    <optional>
    PEM file name                   A path of PEM file name
    account number                  the account number which owns the PEM file

3) Crypt Transfer
mvn exec:java -Dexec.mainClass=ClientManager -Dexec.args=' CryptoTransferUpdate 3 <IP> 50211 3 true 2000000000 15 10000 false ' -Dexec.cleanupDaemonThreads=false



4) Smart Contract Big Array Test

mvn exec:java -Dexec.mainClass=ClientManager -Dexec.args=' ContractBigArray 1 <IP> 50211 3 true 2000 20 15' -Dexec.cleanupDaemonThreads=false

	for example input to ContractBigArray is "2000 20 "

	numberOfIterations  2000
	data size in KByte  20
	Target              15

5) Smart Contract Create Test

mvn exec:java -Dexec.mainClass=ClientManager -Dexec.args=' ContractCreate 1 <IP> 50211 3 true 2000 20' -Dexec.cleanupDaemonThreads=false

	for example input to ContractCreate is "2000 20 "

	numberOfIterations  2000
	TPS                 20

6) Smart Contract Call

mvn exec:java -Dexec.mainClass=ClientManager -Dexec.args=' ContractCall 1 SERVER_IP 50211 3 true 10 2 FETCH_RECEIPT' -Dexec.cleanupDaemonThreads=false

mvn exec:java -Dexec.mainClass=ClientManager -Dexec.args=' ContractCall 1 SERVER_IP 50211 3 true 10 2 FETCH_RECORD_ANSWER' -Dexec.cleanupDaemonThreads=false

mvn exec:java -Dexec.mainClass=ClientManager -Dexec.args=' ContractCall 1 SERVER_IP 50211 3 true 10 2 FETCH_RECORD_COST_ANSWER' -Dexec.cleanupDaemonThreads=false

mvn exec:java -Dexec.mainClass=ClientManager -Dexec.args=' ContractCall 1 SERVER_IP 50211 3 true 10 2 FETCH_RECORD_COST_ANSWER 30 account.pem 1005' -Dexec.cleanupDaemonThreads=false

	for example input to ContractCall is "2000 20 <FETCH_MODE> "

	numberOfIterations  10
	TPS                 2
	fetch mode          FETCH_RECORD_COST_ANSWER or FETCH_RECORD_ANSWER or FETCH_RECEIPT
	record TTL          30

    <optional>
    PEM file name                   A path of PEM file name
    account number                  the account number which owns the PEM file

7) Smart contract local call

mvn exec:java -Dexec.mainClass=ClientManager -Dexec.args=' ContractCallLocal 1 SERVER_IP 50211 3 true 2000 20' -Dexec.cleanupDaemonThreads=false

	Parameters:
	numberOfIterations  2000
	TPS                 20


8) ERC20 Token distribution and transfer

mvn exec:java -Dexec.mainClass=ClientManager -Dexec.args='ERC20Contract 1 SERVER_IP 50211 3 true 2000 20 5' -Dexec.cleanupDaemonThreads=false

	Parameters:
	numberOfIterations  				2000
	TPS                 				20
	how many token account to create  	5

9) Mix different test client to verify record stream file and event stream file

mvn exec:java -Dexec.mainClass=ClientManager -Dexec.args='MixedClient 1 SERVER_IP 50211 3 true ubuntu my-key.pem' -Dexec.cleanupDaemonThreads=false

	Parameters:
	ssh user name of server node 	  ubuntu
	ssh key file name                 my-key.pem

10) Create new account and generate PEM key file for later use

Will generate a file with name similar to "account_1001.pem "

mvn exec:java -Dexec.mainClass=ClientManager -Dexec.args='CreateAccountPemFile 1  SERVER_IP  50211 3 true 1 1000000000 wonder' -Dexec.cleanupDaemonThreads=false

	Parameters:
	number of account to create  	  3
	initial balance                   2000005
	password for PEM file             wonder (default is "password" if not given)



11) Check balance after transactions and queries, must be run as a single thread
otherwise one thread is retrieving balance another thread may cause the change of balance
extended from CryptoTransferUpdate or ContractCall, using the same parameters

mvn exec:java -Dexec.mainClass=ClientManager -Dexec.args='CryptoTransferListCheck 1 SERVER_IP 50211 3 true 30 15 10000000 true ' -Dexec.cleanupDaemonThreads=false
mvn exec:java -Dexec.mainClass=ClientManager -Dexec.args='CryptoTransferListCheck 1 SERVER_IP 50211 3 true 30 15 10000000 false ' -Dexec.cleanupDaemonThreads=false

mvn exec:java -Dexec.mainClass=ClientManager -Dexec.args='ContractTransferListCheck 1 SERVER_IP 50211 3 true 100 20' -Dexec.cleanupDaemonThreads=false

mvn exec:java -Dexec.mainClass=ClientManager -Dexec.args='FileDeleteBalanceCheck 1 SERVER_IP 50211 3 true' -Dexec.cleanupDaemonThreads=false

mvn exec:java -Dexec.mainClass=ClientManager -Dexec.args='ContractCallLocalTransferListCheck 1 SERVER_IP 50211 3 true 10 20' -Dexec.cleanupDaemonThreads=false

mvn exec:java -Dexec.mainClass=ClientManager -Dexec.args='ContractDeleteTransferListCheck 1 SERVER_IP 50211 3 true' -Dexec.cleanupDaemonThreads=false
