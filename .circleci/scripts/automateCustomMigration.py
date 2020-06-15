#!/usr/bin/env python3

#----------------------------------------------------------------------------------------------------------------------------------------------------------#
#
# ReadMe:
# this script needs these arguments mentioned in the config json:
#	NETWORKUSED			- Kind of network we are testing migration on - mainnet / testnet
#	INFRASTRUCTURE_REPO	- Path to infrastructure repository to use ansible playbooks
#	INVENTORY			- Intervory file which contains IPs to the instances we are going to test migration on
#	PEM_FILE			- Permissions file that allows us to access the instances that are mentioned in the inventory file
#	BRANCH_NAME			- Newer branch of service-hedera repo we want to deploy the nodes with
#	SERVICES_REPO		- Path to services-hedera repository
#	BUCKET_NAME			- Name of the s3 bucket that the saved states and startUpAccount are saved in.
#   SAVEDSTATE_0		- Key to Saved State zip for node 1
#   SAVEDSTATE_1		- Key to Saved State zip for node 2
#   SAVEDSTATE_2		- Key to Saved State zip for node 3
#   SAVEDSTATE_3		- Skey to aved State zip for node 4
#	PAYER_ACCOUNT_NUM	- Account number of the key that you have ins tartup account ex: 95
#	STARTUP_ACCOUNT		- Key to the startUpAccount that we need to use to run UmbrellaRedux
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
import boto3
import botocore

#----------------------------------------------------------------------------------------------------------------------------------------------------------#
#------------------------------------------------------------------------- INPUTS -------------------------------------------------------------------------#

CONFIG_FILE=sys.argv[1]

NETWORKUSED=""

INFRASTRUCTURE_REPO=""

INVENTORY=""

PEM_FILE=""

BRANCH_NAME=""

SERVICES_REPO=""

BUCKET_NAME=""

SAVEDSTATE_0=""

SAVEDSTATE_1=""

SAVEDSTATE_2=""

SAVEDSTATE_3=""

PAYER_ACCOUNT_NUM=""

STARTUP_ACCOUNT=""

RUNNING_HASH_VERSION=""

NODE_ADDRESSES = []

START_DIR=os.getcwd()

ACCESS_KEY = "AKIARLBM6VADFE5EUMHY"

SECRET_KEY = "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"

#----------------------------------------------------------------------------------------------------------------------------------------------------------#
#--------------------------------------------------------------------- READ CONFIG ------------------------------------------------------------------------#

def reafConfig():

	with open(CONFIG_FILE) as f:
		config 					= json.load(f)

		global NETWORKUSED
		NETWORKUSED = config['networkUsed']
		global INFRASTRUCTURE_REPO
		INFRASTRUCTURE_REPO = config['infrastructureRepo']
		global INVENTORY
		INVENTORY = config['inventory']
		global PEM_FILE
		PEM_FILE = config['pemFile']
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
		global BUCKET_NAME
		BUCKET_NAME = config['bucketName']
		global RUNNING_HASH_VERSION
		RUNNING_HASH_VERSION = config['topicRunningHashVersion']

reafConfig()

#----------------------------------------------------------------------------------------------------------------------------------------------------------#
#--------------------------------------------------------------------- DOWNLOAD FROM S3 -------------------------------------------------------------------#

def downloadFileFromS3(accessKey, secretKey, bucketName, fileName, localPath):
	s3Client = boto3.client('s3', aws_access_key_id=accessKey, aws_secret_access_key=secretKey)
	s3Client.download_file(bucketName, fileName, localPath)
	print("downloaded file {} from s3".format(localPath))

#----------------------------------------------------------------------------------------------------------------------------------------------------------#
#--------------------------------------------------------------------- VALIDATE INPUTS --------------------------------------------------------------------#

def validateInputs():

	NO_OF_NODES=0

	if NETWORKUSED == "mainnet":
		NO_OF_NODES=13
	elif NETWORKUSED == "publictestnet":
            NO_OF_NODES=4
	else:
		print("please enter the correct network name has to one of :")
		print("mainnet")
		print("publictestnet")

	try:
		f = open("{}/terraform/deployments/ansible/inventory/{}".format(INFRASTRUCTURE_REPO , INVENTORY))
	except IOError:
		print("{}/terraform/deployments/ansible/inventory/{}".format(INFRASTRUCTURE_REPO , INVENTORY))
		print("Invetory file not found")
	finally:
		f.close()

	# for Local
	# try:
	# 	f = open("{}".format(PEM_FILE))
	# except IOError:
	# 	print("PEM file not found")
	# finally:
	# 	f.close()

	try:
		f = open("{}/test-clients/src/main/java/com/hedera/services/bdd/suites/records/MigrationValidationPostSteps.java".format(SERVICES_REPO))
	except IOError:
		print("services repo not present")
	finally:
		f.close()
	try:
		downloadFileFromS3(ACCESS_KEY, SECRET_KEY, BUCKET_NAME, SAVEDSTATE_0, "./savedState0.zip")
		f = open("{}".format("./savedState0.zip"))
		with zipfile.ZipFile("./savedState0.zip", 'r') as savedState0:
			savedState0.extractall("0/")
	except IOError:
		print("State file 0 not found ")
	finally:
		f.close()
	try:
		downloadFileFromS3(ACCESS_KEY, SECRET_KEY, BUCKET_NAME, SAVEDSTATE_1, "./savedState1.zip")
		f = open("{}".format("./savedState1.zip"))
		with zipfile.ZipFile("./savedState1.zip", 'r') as savedState1:
			savedState1.extractall("1/")
	except IOError:
		print("State file 1 not found ")
	finally:
		f.close()
	try:
		downloadFileFromS3(ACCESS_KEY, SECRET_KEY, BUCKET_NAME, SAVEDSTATE_2, "./savedState2.zip")
		f = open("{}".format("./savedState2.zip"))
		with zipfile.ZipFile("./savedState2.zip", 'r') as savedState2:
			savedState2.extractall("2/")
	except IOError:
		print("State file 2 not found ")
	finally:
		f.close()
	try:
		downloadFileFromS3(ACCESS_KEY, SECRET_KEY, BUCKET_NAME, SAVEDSTATE_3, "./savedState3.zip")
		f = open("{}".format("./savedState3.zip"))
		with zipfile.ZipFile("./savedState3.zip", 'r') as savedState3:
			savedState3.extractall("3/")
	except IOError:
		print("State file 3 not found ")
	finally:
		f.close()
	try:
		downloadFileFromS3(ACCESS_KEY, SECRET_KEY, BUCKET_NAME, STARTUP_ACCOUNT, "./startupAccount.txt")
		f = open("{}".format("./startupAccount.txt"))
	except IOError:
		print("startupAccount not found ")
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
					print("Source path is : {}".format(fullSourcePath))

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

	# for local
	#playbook_command = "ansible-playbook -i ./inventory/{} --private-key {} -u ubuntu -e branch={} -e enable_newrelic=false -e hgcapp_service_file=hgcappm410NR play-deploy-migration.yml".format(INVENTORY, PEM_FILE, BRANCH_NAME)

	# for CircleCI
	playbook_command = "ansible-playbook -i ./inventory/{} -u ubuntu -e branch={} -e enable_newrelic=false -e hgcapp_service_file=hgcappm410NR play-deploy-migration.yml".format(INVENTORY, BRANCH_NAME)

	os.chdir(ansible_path)

	os.system(playbook_command)

runMigration()

#----------------------------------------------------------------------------------------------------------------------------------------------------------#
#------------------------------------------------------------------ Copy Swirlds.log ----------------------------------------------------------------------#

def copyLogs():
	os.chdir(START_DIR)

	node_address = ""
	inventory_f = open("{}/terraform/deployments/ansible/inventory/{}".format(INFRASTRUCTURE_REPO , INVENTORY), 'r')
	#parsed_inventory_file = yaml.load(inventory_f, Loader=yaml.FullLoader)
	for i in range(0, 11):
		node_address = inventory_f.readline()

	# for local
	#copy_swirld_log = "scp -i {} ubuntu@{}:/opt/hgcapp/services-hedera/HapiApp2.0/output/swirlds.log output/{}/"

	# for CircleCI
	copy_swirld_log = "scp -o UserKnownHostsFile=/dev/null -o StrictHostKeyChecking=no ubuntu@{}:/opt/hgcapp/services-hedera/HapiApp2.0/output/swirlds.log /output/{}/"

	os.mkdir("/output")

	for n in range(0, NO_OF_NODES):
		NODE_ADDRESSES.append(inventory_f.readline().rstrip()[20:])
		print("node address is : {}".format(NODE_ADDRESSES[n]))
		for x in range(0, 3):
			inventory_f.readline()
		os.mkdir("/output/{}".format(n))
		os.system(copy_swirld_log.format(NODE_ADDRESSES[n], n))

copyLogs()

#----------------------------------------------------------------------------------------------------------------------------------------------------------#
#---------------------------------------------------------------------- Run EET suite ---------------------------------------------------------------------#

test_clients_path = "{}/test-clients".format(SERVICES_REPO)
os.chdir(SERVICES_REPO)
mvn_install_cmd = "mvn clean install"
os.chdir(test_clients_path)
mvn_test_cmd = 'mvn exec:java -Dexec.mainClass=com.hedera.services.bdd.suites.regression.UmbrellaReduxWithCustomNodes  -Dexec.args="{} {} {} {} {}" > /output/CustomMigrationUmbrellaRedux{}.log'

os.system(mvn_install_cmd)

for n in range(0, NO_OF_NODES):
	os.system(mvn_test_cmd.format(n+3, NODE_ADDRESSES[n], PAYER_ACCOUNT_NUM, "./startupAccount.txt", n, RUNNING_HASH_VERSION))
	time.sleep(90)

#----------------------------------------------------------------------------------------------------------------------------------------------------------#
#---------------------------------------------------------------------- validate logs ---------------------------------------------------------------------#

def validateLogs():
	os.chdir(START_DIR)

	for n in range(0, NO_OF_NODES):
		loaded_log = "SwirldsPlatform - Platform {} has loaded a saved state for round".format(n)
		with open( "/output/{}/swirlds.log".format(n)) as swirldsLog_f:
			if loaded_log in swirldsLog_f.read():
				print ("Saved state is loaded on platform {}".format(n))
			else:
				print ("Saved state failed to load on platform {}".format(n))

			if 'ERROR' in swirldsLog_f.read():
				print ("Error Found in the swirlds log on platform{}".format(n))
		passed_eet = "UmbrellaRedux - Spec{name=UmbrellaRedux, status=PASSED}"
		with open ("{}/CustomMigrationUmbrellaRedux{}.log".format(test_clients_path, n)) as eetLog_f:
			if passed_eet in eetLog_f.read():
				print ("CustomMigrationUmbrellaRedux test passed successfully on node {}".format(n))
			else:
				print ("CustomMigrationUmbrellaRedux test failed.. please go through the eet logs")
				print ("{}/CustomMigrationUmbrellaRedux{}.log".format(test_clients_path, n))

validateLogs()
