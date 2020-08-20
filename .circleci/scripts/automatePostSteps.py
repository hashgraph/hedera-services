#!/usr/bin/env python3

#----------------------------------------------------------------------------------------------------------------------------------------------------------#
#
# ReadMe:
# this script needs 7 or 8 arguments in the following order:
#	SIGNEDSTATE		    - Path to signed state folder named as the roundnumber and contains statefile and postgres backup
#	NETWORKUSED		    - Kind of network we are testing migration on - mainnet / testnet
#	INFRASTRUCTURE_REPO	- Path to infrastructure repository to use ansible playbooks
#	INVENTORY		    - Intervory file which contains IPs to the instances we are going to test migration on
#	PEM_FILE		    - Permissions file that allows us to access the instances that are mentioned in the inventory file
#	BRANCH_NAME		    - Newer branch of service-hedera repo we want to deploy the nodes with
#	SERVICES_REPO		- Path to services-hedera repository
#   True/False          - [Optional] to decide whether also do stop-restart steps after migration
#
#
#	Example:
#	python automatePostSteps.py 53940670 mainnet ~/IdeaProjects/infrastructure main-mig-perf ~/Downloads/serviceregression.pem dev ~/IdeaProjects/services-hedera [True/False]
#
#
#
#----------------------------------------------------------------------------------------------------------------------------------------------------------#

import sys
import shutil
import os
import re
import time

#----------------------------------------------------------------------------------------------------------------------------------------------------------#
#------------------------------------------------------------------------- INPUTS -------------------------------------------------------------------------#

SIGNEDSTATE=sys.argv[1]

NETWORKUSED=sys.argv[2]

INFRASTRUCTURE_REPO=sys.argv[3]

INVENTORY=sys.argv[4]

PEM_FILE=sys.argv[5]

BRANCH_NAME=sys.argv[6]

SERVICES_REPO=sys.argv[7]

TEST_RESTART = False
if len(sys.argv) > 8 and sys.argv[8].lower() == 'true':
	TEST_RESTART=True

START_DIR=os.getcwd()

MIGRATION_CONF_PROP_FILE = ""
DEFAULT_START_UP_ACCT = "StartUpAccount"

NODE0 = "0.0.0.0"
ACCOUNT0 = "0.0.3"

WAIT_SECONDS_1 = 0
WAIT_SECONDS_2 = 0
#----------------------------------------------------------------------------------------------------------------------------------------------------------#
#--------------------------------------------------------------------- VALIDATE INPUTS --------------------------------------------------------------------#


def validateInputs():

    NO_OF_NODES=0
    global MIGRATION_CONF_PROP_FILE
    global WAIT_SECONDS_1
    global WAIT_SECONDS_2
    if NETWORKUSED == "mainnet":
        NO_OF_NODES=13
        WAIT_SECONDS_1 = 90
        WAIT_SECONDS_2 = 60
        MIGRATION_CONF_PROP_FILE = "migration_config_mainnet.properties"
    elif NETWORKUSED == "testnet":
        NO_OF_NODES=4
        WAIT_SECONDS_1 = 60
        WAIT_SECONDS_2 = 30
        MIGRATION_CONF_PROP_FILE = "migration_config_testnet.properties"
    else:
        print("please enter the correct network name has to one of :")
        print("mainnet")
        print("testnet")
        sys.exit(1)

    try:
        f = open("{}/terraform/deployments/ansible/inventory/{}".format(INFRASTRUCTURE_REPO , INVENTORY))
    except IOError:
        print("{}/terraform/deployments/ansible/inventory/{}".format(INFRASTRUCTURE_REPO , INVENTORY))
        sys.exit("Invetory file not found")
    finally:
        f.close()

    try:
        f = open("{}".format(PEM_FILE))
    except IOError:
        sys.exit("PEM file not found")
    finally:
        f.close()

    try:
        f = open("{}/test-clients/src/main/java/com/hedera/services/bdd/suites/records/MigrationValidationPostSteps.java".format(SERVICES_REPO))
    except IOError:
        sys.exit("services repo not present")
    finally:
        f.close()

    return NO_OF_NODES

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
		sys.exit("Failed to build saved.zip file")

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

	os.system("rm -rf output")
	os.mkdir("output")

	global NODE0
	NODE0 = ips[0]

	for i, ip in enumerate(ips):
		print("node[{}] => ip: {}".format(i, ip))
		node_address = ip
		os.mkdir("output/{}".format(i))
		os.system(copy_swirld_log.format(PEM_FILE, node_address, i))

#----------------------------------------------------------------------------------------------------------------------------------------------------------#
#---------------------------------------------------------------------- Run EET suite ---------------------------------------------------------------------#

def replaceStartupAcctWith(client_parent_path, current_network):
	startup_acct_path = "{}/src/main/resource/{}.txt".format(client_parent_path, DEFAULT_START_UP_ACCT)
	backup_startup_acct_path = "{}/src/main/resource/{}.txt.backup".format(client_parent_path, DEFAULT_START_UP_ACCT)

	save_default_startup_acct_cmd = "mv {} {}".format(startup_acct_path, backup_startup_acct_path)
	os.system(save_default_startup_acct_cmd)

	new_startup_acct_path = "{}/src/main/resource/{}_{}.txt".format(client_parent_path, DEFAULT_START_UP_ACCT, current_network)
	replace_default_startup_acct_cmd = "cp {} {}".format(new_startup_acct_path, startup_acct_path)
	os.system(replace_default_startup_acct_cmd)

def restoreStartupAcct(client_parent_path):
	startup_acct_path = "{}/src/main/resource/{}.txt".format(client_parent_path, DEFAULT_START_UP_ACCT)
	backup_startup_acct_path = "{}/src/main/resource/{}.txt.backup".format(client_parent_path, DEFAULT_START_UP_ACCT)
	restore_start_up_acct_cmd = "mv {} {}".format(backup_startup_acct_path, startup_acct_path)
	os.system(restore_start_up_acct_cmd)

#----------------------------------------------------------------------------------------------------------------------------------------------------------#
#--------------------------------------------------------- RUN mvn commands  SCRIPT -----------------------------------------------------------------------#

def runMvnCmd(mvnCmd, buildFirst):
	test_clients_path = "{}/test-clients".format(SERVICES_REPO)
	os.chdir(test_clients_path)

	replaceStartupAcctWith(test_clients_path, NETWORKUSED)

	if buildFirst:
		mvn_install_cmd = "mvn clean install"
		ret_code = os.system(mvn_install_cmd)
		if ret_code != 0:
			sys.exit("mvn build test-client failed")

	ret_code = os.system(mvnCmd)

	restoreStartupAcct(test_clients_path)
	if ret_code != 0:
		sys.exit("Running mvn exec:java FreezeIntellijNetwork failed")


#----------------------------------------------------------------------------------------------------------------------------------------------------------#
#--------------------------------------------------------- RUN ANSIBLE PLAY BOOK SCRIPT -------------------------------------------------------------------#

def runAnisbleCmd(ansibleCmd):

	ansible_path = "{}/terraform/deployments/ansible/".format(INFRASTRUCTURE_REPO)

	os.chdir(ansible_path)

	if os.system(ansibleCmd) != 0:
		sys.exit("Running ansible playbook {} failed".format(ansibleCmd))
	else:
		print("Running ansible playbook {} succeeded".format(ansibleCmd))

#----------------------------------------------------------------------------------------------------------------------------------------------------------#
#---------------------------------------------------------------------- clear logs ------------------------------------------------------------------------#

def cleanLogs():
	test_clients_path = "{}/test-clients".format(SERVICES_REPO)
	testLog = "{}/migrationPostStepsTest.log".format(test_clients_path)
	os.system("rm -f {}".format(testLog))
	os.system("rm -rf sav* output")


#----------------------------------------------------------------------------------------------------------------------------------------------------------#
#---------------------------------------------------------------------- validate logs ---------------------------------------------------------------------#

def validateLogs():
	os.chdir(START_DIR)
	test_clients_path = "{}/test-clients".format(SERVICES_REPO)

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


#----------------------------------------------------------------------------------------------------------------------------------------------------------#
#-------------------------------------------------------------- Main Migration Workflow -------------------------------------------------------------------#

# The main steps to validate the migration
if __name__ == '__main__':
	msg = ""
	if TEST_RESTART:
		msg = "Will do migration test for {} with restart steps".format(NETWORKUSED)
	else:
		msg = "Will do migration test for {}".format(NETWORKUSED)
	print(msg)

	cleanLogs()
	NO_OF_NODES=validateInputs()
	print("No of nodes required : {}".format(NO_OF_NODES))

	with open("{}/terraform/deployments/ansible/inventory/{}".format(INFRASTRUCTURE_REPO , INVENTORY), 'r') as fin:
		print(fin.read())

	buildSavedZip()
	shutil.copy("saved.zip", "{}/terraform/deployments/ansible/roles/hgc-deploy-psql-mig/files/".format(INFRASTRUCTURE_REPO))

	migrationCmd = "ansible-playbook -i ./inventory/{} --private-key {} -u ubuntu -e branch={} -e app_dir={} -e enable_newrelic=false -e hgcapp_service_file=hgcappm410NR -e saved_round_number={} play-deploy-migration.yml".format(INVENTORY, PEM_FILE, BRANCH_NAME, SERVICES_REPO, SIGNEDSTATE)
	runAnisbleCmd(migrationCmd)

	print("Wait for {} seconds before getting the log files. The wait is important. Otherwise we may get log file without the info we need".format(WAIT_SECONDS_1))
	time.sleep(WAIT_SECONDS_1)
	copyLogs()

	print("Run MigrationPostValidationTests ...")
	migrationPostValidationCmd = "mvn exec:java -Dexec.mainClass=com.hedera.services.bdd.suites.records.MigrationValidationPostSteps -Dexec.args={} > migrationPostStepsTest.log".format(MIGRATION_CONF_PROP_FILE)
	runMvnCmd(migrationPostValidationCmd, True)

	validateLogs()

	# Below are optional stop and restart workflow steps
	if TEST_RESTART:
		print("Now do a stop and restart validate reload of saved state.")

		print("Prepare to freeze network.  NODE0: {}".format(NODE0))
		freeze_cmd = "mvn exec:java -Dexec.mainClass=com.hedera.services.bdd.suites.freeze.FreezeIntellijNetwork"
		runMvnCmd(freeze_cmd, False)

		print("Wait for {} seconds before killing the services process...".format(WAIT_SECONDS_1))
		time.sleep(WAIT_SECONDS_1)

		print("Stop hedera services ...")
		stop_services_cmd = "ansible-playbook -i ./inventory/{} --private-key {} -u ubuntu  -e enable_newrelic=false play-stop.yml".format(INVENTORY, PEM_FILE)
		runAnisbleCmd(stop_services_cmd)

		# Can do something here
		print("Wait for {} seconds before restarting hedera services process...".format(WAIT_SECONDS_2))
		time.sleep(WAIT_SECONDS_2)
		print("Re-start hedera services ...")
		start_services_cmd = "ansible-playbook -i ./inventory/{} --private-key {} -u ubuntu  -e enable_newrelic=false play-start.yml".format(INVENTORY, PEM_FILE)
		runAnisbleCmd(start_services_cmd)

		print("Re-run MigrationPostValidationTests after restart...")
		cleanLogs()
		time.sleep(WAIT_SECONDS_2)
		runMvnCmd(migrationPostValidationCmd, False)
		time.sleep(WAIT_SECONDS_2)
		copyLogs()
		validateLogs()

