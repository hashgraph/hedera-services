#!/usr/bin/env python3

#----------------------------------------------------------------------------------------------------------------------------------------------------------#
#
# ReadMe:
# this script needs 7 arguments in the following order:
#	SIGNEDSTATE		- Path to signed state folder named as the roundnumber and contains statefile and postgres backup
#	NETWORKUSED		- Kind of network we are testing migration on - mainnet / testnet
#	INFRASTRUCTURE_REPO	- Path to infrastructure repository to use ansible playbooks
#	INVENTORY		- Intervory file which contains IPs to the instances we are going to test migration on
#	PEM_FILE		- Permissions file that allows us to access the instances that are mentioned in the inventory file
#	BRANCH_NAME		- Newer branch of service-hedera repo we want to deploy the nodes with
#	SERVICES_REPO		- Path to services-hedera repository
#
#
#
#	Example:
#	python automatePostSteps.py 53940670 mainnet ~/IdeaProjects/infrastructure main-mig-perf ~/Downloads/serviceregression.pem dev ~/IdeaProjects/services-hedera
#
#
#
#----------------------------------------------------------------------------------------------------------------------------------------------------------#

import sys
import shutil
import os
import re
import time
import zipfile

#----------------------------------------------------------------------------------------------------------------------------------------------------------#
#------------------------------------------------------------------------- INPUTS -------------------------------------------------------------------------#

SIGNEDSTATE=sys.argv[1]

NETWORKUSED=sys.argv[2]

INFRASTRUCTURE_REPO=sys.argv[3]

INVENTORY=sys.argv[4]

PEM_FILE=sys.argv[5]

BRANCH_NAME=sys.argv[6]

SERVICES_REPO=sys.argv[7]

START_DIR=os.getcwd()

#----------------------------------------------------------------------------------------------------------------------------------------------------------#
#--------------------------------------------------------------------- VALIDATE INPUTS --------------------------------------------------------------------#

os.system("rm -rf sav* output")

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

    try:
        f = open("{}".format(PEM_FILE))
    except IOError:
        print("PEM file not found")
    finally:
        f.close()

    try:
        f = open("{}/test-clients/src/main/java/com/hedera/services/bdd/suites/records/MigrationValidationPostSteps.java".format(SERVICES_REPO))
    except IOError:
        print("seervices repo not present")
    finally:
        f.close()

    return NO_OF_NODES

NO_OF_NODES=validateInputs()

print("No of nodes required : {}".format(NO_OF_NODES))

#----------------------------------------------------------------------------------------------------------------------------------------------------------#
#---------------------------------------------------------------------- BUILD SAVED.ZIP -------------------------------------------------------------------#

def buildSavedZip():

	path = "saved/saved/com.hedera.services.ServicesMain/{}/123"

	for x in range(0, NO_OF_NODES):
		shutil.copytree("{}".format(SIGNEDSTATE), "{}/{}".format(path.format(x), SIGNEDSTATE))

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

	playbook_command = "ansible-playbook -i ./inventory/{} --private-key {} -u ubuntu -e branch={} -e enable_newrelic=false -e hgcapp_service_file=hgcappm410NR -e saved_round_number={} play-deploy-migration.yml".format(INVENTORY, PEM_FILE, BRANCH_NAME, SIGNEDSTATE)

	os.chdir(ansible_path)

	os.system(playbook_command)

runMigration()

#----------------------------------------------------------------------------------------------------------------------------------------------------------#
#------------------------------------------------------------------ Copy Swirlds.log ----------------------------------------------------------------------#

def copyLogs():
	os.chdir(START_DIR)

	inventory_f = open("{}/terraform/deployments/ansible/inventory/{}".format(INFRASTRUCTURE_REPO , INVENTORY), 'r')
	data = inventory_f.read()
	inventory_f.close()
	ip_line_pattern = re.compile(r'\s*(ansible_host:)\s+(\d+[.]\d+[.]\d+[.]\d+)')

	ips = []
	for matches in re.findall(ip_line_pattern, data):
		ips.append(matches[1])

	copy_swirld_log = "scp -i {} ubuntu@{}:/opt/hgcapp/services-hedera/HapiApp2.0/output/swirlds.log output/{}/"

	os.mkdir("output")

	for i, ip in enumerate(ips):
		print("node[{}] => ip: {}".format(i, ip))
		node_address = ip
		os.mkdir("output/{}".format(i))
		os.system(copy_swirld_log.format(PEM_FILE, node_address, i))

print("Wait for 120 seconds before getting the log files...")
time.sleep(120)

copyLogs()

#----------------------------------------------------------------------------------------------------------------------------------------------------------#
#---------------------------------------------------------------------- Run EET suite ---------------------------------------------------------------------#

test_clients_path = "{}/test-clients".format(SERVICES_REPO)
os.chdir(test_clients_path)

mvn_install_cmd = "mvn clean install"
mvn_test_cmd = 'mvn exec:java -Dexec.mainClass=com.hedera.services.bdd.suites.records.MigrationValidationPostSteps -Dexec.args="migration_config_testnet.properties" > migrationPostStepsTest.log'

os.system(mvn_install_cmd)
os.system(mvn_test_cmd)

#----------------------------------------------------------------------------------------------------------------------------------------------------------#
#---------------------------------------------------------------------- validate logs ---------------------------------------------------------------------#

def validateLogs():
	os.chdir(START_DIR)

	for n in range(0, NO_OF_NODES):
		loaded_log = "Platform {} has loaded a saved state for round {}".format(n, SIGNEDSTATE)
#		print("Expecting : {} for node {}".format(loaded_log, n))
		with open( "output/{}/swirlds.log".format(n)) as swirldsLog_f:
			if loaded_log in swirldsLog_f.read():
				print ("Saved state is loaded on platform {}".format(n))
			else:
				print ("Saved state failed to load on platform {}".format(n))

			if 'ERROR' in swirldsLog_f.read():
				print ("Error Found in the swirlds log on platform{}".format(n))

	passed_eet = "MigrationValidationPostSteps - Spec{name=migrationPreservesEntitiesPostStep, status=PASSED}"
	with open ("{}/migrationPostStepsTest.log".format(test_clients_path)) as eetLog_f:
		if passed_eet in eetLog_f.read():
			print ("Migration test passed successfully")
		else:
			print ("Migration test failed.. please go through the eet logs")
			print ("{}/migrationPostStepsTest.log".format(test_clients_path))

validateLogs()
