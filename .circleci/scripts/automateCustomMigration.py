#!/usr/bin/env python3

#----------------------------------------------------------------------------------------------------------------------------------------------------------#
#
# ReadMe:
# this script needs these arguments mentioned in the config json:
#	NETWORKUSED			- Kind of network we are testing migration on - mainnet / testnet
#	INFRASTRUCTURE_REPO	- Path to infrastructure repository to use ansible playbooks
#	INVENTORY			- Intervory file which contains IPs to the instances we are going to test migration on
#	BRANCH_NAME			- Newer branch of service-hedera repo we want to deploy the nodes with
#	SERVICES_REPO		- Path to services-hedera repository
#	S3_BUCKET_NAME			- Name of the s3 bucket that the saved states and startUpAccount are saved in.
#   SAVEDSTATE_0		- Key to Saved State zip for node 1
#   SAVEDSTATE_1		- Key to Saved State zip for node 2
#   SAVEDSTATE_2		- Key to Saved State zip for node 3
#   SAVEDSTATE_3		- Skey to aved State zip for node 4
#	PAYER_ACCOUNT_NUM	- Account number of the key that you have ins tartup account ex: 95
#	STARTUP_ACCOUNT		- Key to the startUpAccount that we need to use to run UmbrellaRedux
#	RUNNING_HASH_VERSION- Verion to verify
#
#.  Only coded this custom migration script to do 4 node migration test.
#
#
#
#	Example:
#	python automatePostSteps.py customMigrationConfig.json
#
#
#----------------------------------------------------------------------------------------------------------------------------------------------------------#

import sys
import shutil
import os
import zipfile
import yaml
import time
import json

#----------------------------------------------------------------------------------------------------------------------------------------------------------#
#------------------------------------------------------------------------- INPUTS -------------------------------------------------------------------------#

CONFIG_FILE=sys.argv[1]

NETWORKUSED=""

INFRASTRUCTURE_REPO=""

INVENTORY=""

BRANCH_NAME=""

SERVICES_REPO=""

S3_BUCKET_NAME=""

SAVEDSTATE_0=""

SAVEDSTATE_1=""

SAVEDSTATE_2=""

SAVEDSTATE_3=""

PAYER_ACCOUNT_NUM=""

STARTUP_ACCOUNT=""

RUNNING_HASH_VERSION=""

NODE_ADDRESSES = []

START_DIR=os.getcwd()

#----------------------------------------------------------------------------------------------------------------------------------------------------------#
#--------------------------------------------------------------------- READ CONFIG ------------------------------------------------------------------------#

def readConfig():

	with open(CONFIG_FILE) as f:
		config 					= json.load(f)

		global NETWORKUSED
		NETWORKUSED = config['networkUsed']
		global INFRASTRUCTURE_REPO
		INFRASTRUCTURE_REPO = config['infrastructureRepo']
		global INVENTORY
		INVENTORY = config['inventory']
		global BRANCH_NAME
		BRANCH_NAME = config['serviceBranch']
		global SERVICES_REPO
		SERVICES_REPO = config['serviceRepo']
		global SAVEDSTATE_0
		SAVEDSTATE_0 = config['savedState0']
		global SAVEDSTATE_1
		SAVEDSTATE_1 = config['savedState1']
		global SAVEDSTATE_2
		SAVEDSTATE_2 = config['savedState2']
		global SAVEDSTATE_3
		SAVEDSTATE_3 = config['savedState3']
		global PAYER_ACCOUNT_NUM
		PAYER_ACCOUNT_NUM = config['payerAccountNum']
		global STARTUP_ACCOUNT
		STARTUP_ACCOUNT = config['startupAccount']
		global S3_BUCKET_NAME
		S3_BUCKET_NAME = config['s3bucketName']
		global RUNNING_HASH_VERSION
		RUNNING_HASH_VERSION = config['topicRunningHashVersion']

readConfig()

#----------------------------------------------------------------------------------------------------------------------------------------------------------#
#--------------------------------------------------------------------- VALIDATE INPUTS --------------------------------------------------------------------#

def validateSavedStates(savedStateKey, savedStateName):
    try:
        os.system("aws s3 cp s3://{}/{} ./{}.zip".format(S3_BUCKET_NAME, savedStateKey, savedStateName))
        f = open("./{}.zip".format(savedStateName))
        with zipfile.ZipFile("./{}.zip".format(savedStateName), 'r') as savedState:
            savedState.extractall("0/")
    except IOError:
        print("{} not found.".format(savedStateName))
    finally:
        f.close()

validateSavedStates(SAVEDSTATE_0, "savedState0")
validateSavedStates(SAVEDSTATE_1, "savedState1")
validateSavedStates(SAVEDSTATE_2, "savedState2")
validateSavedStates(SAVEDSTATE_3, "savedState3")

def validateInputs():

	NO_OF_NODES=0

	if NETWORKUSED == "mainnet":
		NO_OF_NODES=13
	elif NETWORKUSED == "publictestnet":
            NO_OF_NODES=4
	else:
		print("please enter the correct network name has to be one of :")
		print("mainnet")
		print("publictestnet")

	try:
		f = open("{}/terraform/deployments/ansible/inventory/{}".format(INFRASTRUCTURE_REPO , INVENTORY))
	except IOError:
		print("{}/terraform/deployments/ansible/inventory/{}".format(INFRASTRUCTURE_REPO , INVENTORY))
		print("Invetory file not found")
		sys.exit()
	finally:
		f.close()

	try:
		f = open("{}/test-clients/src/main/java/com/hedera/services/bdd/suites/records/MigrationValidationPostSteps.java".format(SERVICES_REPO))
	except IOError:
		print("services repo not present")
		sys.exit()
	finally:
		f.close()

	try:
		os.system("aws s3 cp s3://{}/{} {}/startupAccount.txt".format(S3_BUCKET_NAME, STARTUP_ACCOUNT, SERVICES_REPO))
		f = open("{}/startupAccount.txt".format(SERVICES_REPO))
	except IOError:
		print("startupAccount not found ")
		sys.exit()
	finally:
		f.close()

	return NO_OF_NODES

NO_OF_NODES=validateInputs()
print("No of nodes required : {}".format(NO_OF_NODES))

#----------------------------------------------------------------------------------------------------------------------------------------------------------#
#---------------------------------------------------------------------- BUILD SAVED.ZIP -------------------------------------------------------------------#

def buildSavedZip():

	source_path = "{}/opt/hgcapp/services-hedera/"
	dest_path = "saved/saved/com.hedera.services.ServicesMain/{}/123/"

	for x in range(0, NO_OF_NODES):
		for root, dirs, files in os.walk(source_path.format(x)):
			for dir in dirs:
				if dir == "123":
					fullSourcePath = os.path.join(root, dir)

		shutil.copytree( "{}/".format( fullSourcePath ), "{}".format(dest_path.format(x)) )

	try:
		shutil.make_archive('saved', 'zip', 'saved')
	except Exception as e:
		print (e)
buildSavedZip()

#----------------------------------------------------------------------------------------------------------------------------------------------------------#
#------------------------------------------------------------- COPY SAVED.ZIP TO INFRASTRUCTURE -----------------------------------------------------------#

shutil.copy("saved.zip", "{}/terraform/deployments/ansible/roles/hgc-deploy-psql-mig/files/".format(INFRASTRUCTURE_REPO))

#----------------------------------------------------------------------------------------------------------------------------------------------------------#
#---------------------------------------------------------------- RUN ANSIBLE MIGRATION SCRIPT ------------------------------------------------------------#

def runMigration():
	print("Network infrastructure used is : {}".format(INVENTORY))

	with open("{}/terraform/deployments/ansible/inventory/{}".format(INFRASTRUCTURE_REPO , INVENTORY), 'r') as fin:
		print(fin.read())

	ansible_path = "{}/terraform/deployments/ansible/".format(INFRASTRUCTURE_REPO)

	playbook_command = "ansible-playbook -i ./inventory/{} -u ubuntu -e branch={} -e enable_newrelic=false -e hgcapp_service_file=hgcappm410NR play-deploy-migration.yml > customMigrationLog.txt".format(INVENTORY, BRANCH_NAME)

	os.chdir(ansible_path)

	os.system(playbook_command)

runMigration()

#----------------------------------------------------------------------------------------------------------------------------------------------------------#
#------------------------------------------------------------------ Copy Swirlds.log ----------------------------------------------------------------------#

def copyLogs():
	time.sleep(60)
	os.chdir(START_DIR)

	node_address = ""
	inventory_f = open("{}/terraform/deployments/ansible/inventory/{}".format(INFRASTRUCTURE_REPO , INVENTORY), 'r')
	parsed_inventory_file = yaml.load(inventory_f, Loader=yaml.FullLoader)

	copy_swirld_log = "scp -o UserKnownHostsFile=/dev/null -o StrictHostKeyChecking=no ubuntu@{}:/opt/hgcapp/services-hedera/HapiApp2.0/output/swirlds.log /repo/output/{}/"
	os.mkdir("/repo/output")

	for n in range(0, NO_OF_NODES):
	    NODE_ADDRESSES.append(parsed_inventory_file[INVENTORY[6:]]["hosts"]["node0{}".format(n)]["ansible_host"])
		print("node address is : {}".format(NODE_ADDRESSES[n]))
		os.mkdir("/repo/output/{}".format(n))
		os.system(copy_swirld_log.format(NODE_ADDRESSES[n], n))

copyLogs()

#----------------------------------------------------------------------------------------------------------------------------------------------------------#
#---------------------------------------------------------------------- Run EET suite ---------------------------------------------------------------------#
time.sleep(60)
# os.chdir("{}".format(SERVICES_REPO))
# mvn_install_cmd = "mvn clean install"
# os.system(mvn_install_cmd)

test_clients_path = "{}test-clients".format(SERVICES_REPO)
os.chdir(test_clients_path)

mvn_test_cmd = 'mvn exec:java -Dexec.mainClass=com.hedera.services.bdd.suites.regression.UmbrellaReduxWithCustomNodes  -Dexec.args="{} {} {} {} {}" > /repo/output/CustomMigrationUmbrellaRedux{}.log'

for n in range(0, NO_OF_NODES):
	print("running UmbrellaReduxWithCustomNodes test on node {}".format(n))
	os.system(mvn_test_cmd.format(n+3, NODE_ADDRESSES[n], PAYER_ACCOUNT_NUM, "{}/startupAccount.txt".format(SERVICES_REPO), RUNNING_HASH_VERSION, n))
	time.sleep(80)

#----------------------------------------------------------------------------------------------------------------------------------------------------------#
#---------------------------------------------------------------------- validate logs ---------------------------------------------------------------------#

def validateLogs():
	os.chdir(START_DIR)

	for n in range(0, NO_OF_NODES):
		loaded_log = "SwirldsPlatform - Platform {} has loaded a saved state for round".format(n)
		with open( "/repo/output/{}/swirlds.log".format(n)) as swirldsLog_f:
			if loaded_log in swirldsLog_f.read():
				print ("Saved state is loaded on platform {}".format(n))
			else:
				print ("Saved state failed to load on platform {}".format(n))

			if 'ERROR' in swirldsLog_f.read():
				print ("Error Found in the swirlds log on platform{}".format(n))
		passed_UmbrellaRedux = "UmbrellaRedux - Spec{name=UmbrellaReduxWithCustomNodes, status=PASSED}"
		passed_Version = "UmbrellaRedux - Spec{name=messageSubmissionSimple, status=PASSED}"
		with open ("/repo/output/CustomMigrationUmbrellaRedux{}.log".format(n)) as eetLog_f:
			if passed_UmbrellaRedux in eetLog_f.read():
				print ("CustomMigrationUmbrellaRedux test passed successfully on node {}".format(n))
			else:
				print ("CustomMigrationUmbrellaRedux test failed.. please go through the eet logs")
		with open ("/repo/output/CustomMigrationUmbrellaRedux{}.log".format(n)) as eetLog_f:
        			if passed_Version in eetLog_f.read():
        				print ("messageSubmissionSimple test passed successfully on node {}".format(n))
        			else:
        				print ("messageSubmissionSimple test failed.. please go through the eet logs")

validateLogs()
