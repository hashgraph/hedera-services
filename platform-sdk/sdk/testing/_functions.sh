#!/usr/bin/env bash

#
# Copyright 2016-2022 Hedera Hashgraph, LLC
#
# This software is the confidential and proprietary information of
# Hedera Hashgraph, LLC. ("Confidential Information"). You shall not
# disclose such Confidential Information and shall use it only in
# accordance with the terms of the license agreement you entered into
# with Hedera Hashgraph.
#
# HEDERA HASHGRAPH MAKES NO REPRESENTATIONS OR WARRANTIES ABOUT THE SUITABILITY OF
# THE SOFTWARE, EITHER EXPRESS OR IMPLIED, INCLUDING BUT NOT LIMITED
# TO THE IMPLIED WARRANTIES OF MERCHANTABILITY, FITNESS FOR A
# PARTICULAR PURPOSE, OR NON-INFRINGEMENT. HEDERA HASHGRAPH SHALL NOT BE LIABLE FOR
# ANY DAMAGES SUFFERED BY LICENSEE AS A RESULT OF USING, MODIFYING OR
# DISTRIBUTING THIS SOFTWARE OR ITS DERIVATIVES.
#

####################
# Function summary #
####################
# getAwsIamFleetRole()
# checkAndCreateKeyPair()
# checkAndImportPublicKey
# getOrCreateAwsSecurityGroup()()
# startAwsInstances(availabilityZone, numberOfInstances, spotPrice)
# waitForAwsFleetToStart(fleetId, numberOfInstancesToWaitFor)
# testSshUntilWorking(IPaddress)
# stopAwsInstances(fleetId)
# ... TODO
#################################
# List of global variables used #
#################################
# iamFleetRole
# secGroupId
# ... TODO

#
# Gets the aws-ec2-spot-fleet-tagging-role from AWS,
# this is needed for fleet creation. Stores it in the global
# variable $iamFleetRole
#
function getAwsIamFleetRole(){
  local awsResponse=`aws iam get-role --role-name aws-ec2-spot-fleet-tagging-role --output text`
  checkExitCodeAndExitOnError "getAwsIamFleetRole"
  iamFleetRole=`echo $awsResponse | awk '$1 == "ROLE" {print $2}'`
}

#
# Returns the index of a substring, or -1 if not found
# Arguments:
#   string to search in
#   substring to find
#
strindex() { 
  x="${1%%$2*}"
  [[ "$x" = "$1" ]] && echo -1 || echo "${#x}"
}

#
# Checks whether the user has a key pair called SwirldsKeyPair
# on AWS and if the private key is in this directory. If not, 
# it will create the key pair and the pem file
#
function checkAndCreateKeyPair(){
  #aws ec2 describe-key-pairs --key-name $awsKeyPairName
  if [ -z "$pemfile" ]; then
    aws ec2 delete-key-pair --key-name "$awsKeyPairName" >/dev/null 2>&1
    local awsResponse=`aws ec2 create-key-pair --key-name "$awsKeyPairName" --output text`
    checkExitCodeAndExitOnError "checkAndCreateKeyPair"
    
    local oldifs="$IFS"
    IFS=
    local beginString="-----BEGIN RSA PRIVATE KEY-----"
    local endString="-----END RSA PRIVATE KEY-----"
    local beginKey=`strindex $awsResponse $beginString`
    local endKey=`strindex $awsResponse $endString`
    #echo $beginKey
    #echo $endKey
    echo "${awsResponse:$beginKey:$endKey-$beginKey+${#endString}}" > "$awsKeyPairName.pem"
    
    pemfile="$awsKeyPairName.pem"
    chmod 600 $pemfile

    IFS="$oldifs"
  fi
  
}

#
# Return the current users AWS ID
#
function getCurrentUserId(){
  aws iam get-user --query "User.{UserId:UserId}" --output text
}

#
# Checks whether the user has a key pair called Swirlds[userId]
# and imports the local pub file if it doesn't exist
#
# Arguments:
#   AWS region
#
function checkAndImportPublicKey(){
  awsKeyPairName="Swirlds"`getCurrentUserId`
  aws ec2 describe-key-pairs --key-name $awsKeyPairName --region $1 >/dev/null 2>&1
  if [ $? -ne 0 ]; then
    if [ -z "$pubfile" ]; then
      echo "ERROR: Must have a .pub file to import to AWS"
      exit -1
    fi
    
    local publicKey=`cat $pubfile`

    publicKey=${publicKey//-----BEGIN PUBLIC KEY-----}
    publicKey=${publicKey//-----END PUBLIC KEY-----}
    publicKey=${publicKey//[[:space:]]}
    
    aws ec2 import-key-pair --key-name $awsKeyPairName --public-key-material "$publicKey" --region $1 >/dev/null
    
    checkExitCodeAndExitOnError "checkAndImportPublicKey"
  fi
}

#
# Looks for the Swirlds security group on AWS and creates it if it's not there
#
# Arguments:
#   AWS region
#
function getOrCreateAwsSecurityGroup(){
  local swirldsSecGroup="SwirldsSecGroup";
  local secGroupId=`aws ec2 describe-security-groups --output text --group-names $swirldsSecGroup --region $1 --query "SecurityGroups[*].{GID:GroupId}" 2>/dev/null`
  if [ -z "$secGroupId"  ]; then
    secGroupId=`aws ec2 create-security-group --description $swirldsSecGroup --group-name $swirldsSecGroup --output text --region $1`
    checkExitCodeAndExitOnError "aws ec2 create-security-group"
    aws ec2 authorize-security-group-ingress --group-id "$secGroupId" --region $1 --ip-permissions \
"[\
{\"IpProtocol\": \"icmp\", \"FromPort\": -1, \"ToPort\": -1, \"IpRanges\": [{\"CidrIp\": \"0.0.0.0/0\"}], \"Ipv6Ranges\": [{\"CidrIpv6\": \"::/0\"}]},\
{\"IpProtocol\": \"tcp\", \"FromPort\": 0, \"ToPort\": 65535, \"IpRanges\": [{\"CidrIp\": \"0.0.0.0/0\"}], \"Ipv6Ranges\": [{\"CidrIpv6\": \"::/0\"}]}\
]"
  fi
  checkExitCodeAndExitOnError "aws ec2 authorize-security-group-ingress"
  echo $secGroupId
}

#
# Gets the ami ID for the specified region
#
#   Arguments:
#     region
#     AMI name
#
function getSwirldsAmiId(){
  aws ec2 describe-images --filters "Name=name,Values=$2" --output text --region $1 --query "Images[*].{ImageId:ImageId}"
}

#
# Starts an AWS spot fleet
#
# Arguments:
#   region
#   numberOfInstances
#   spotPrice
#   amiId
#   securityGroupId
# Returns:
#   spot fleed ID
#
function startAwsSpotInstances(){ #TODO set an expiration date just in case
#      \"Placement\": {\"AvailabilityZone\": \"$1\"},
spotInstanceLaunchSpecification="\
{
  \"IamFleetRole\": \"$iamFleetRole\",
  \"AllocationStrategy\": \"lowestPrice\",
  \"TargetCapacity\": $2,
  \"SpotPrice\": \"$3\",
  \"TerminateInstancesWithExpiration\": true,
  \"LaunchSpecifications\": [
    {
      \"ImageId\": \"$4\",
      \"InstanceType\": \"m4.4xlarge\",
      \"KeyName\": \"$awsKeyPairName\",
      \"SpotPrice\": \"$3\",
      \"SecurityGroups\": [
        {
          \"GroupId\": \"$5\"
        }
      ]
    }
  ],
  \"Type\": \"request\"
}
"
  aws ec2 request-spot-fleet --region $1 --output text --spot-fleet-request-config "$spotInstanceLaunchSpecification" #--dry-run
  
}

#
# Starts AWS instances from a template
#
# Arguments:
#   region
#   template
#   count
# Returns:
#   instance IDs
#
function startAwsInstancesFromTemplate(){
  aws ec2 run-instances --count $3 --launch-template "LaunchTemplateId=$2" --output text --region $1 --query "Instances[*].[InstanceId]" 
}


#
# Starts AWS on-demand instances
#
# Arguments:
#   region
#   count
#   AMI ID
#   instance type
#   sec group ID
# Returns:
#   instance IDs
#
function startAwsInstances(){
  aws ec2 run-instances --region $1 --count $2 --output text --image-id $3 --instance-type $4 --key-name $awsKeyPairName --security-group-ids $5 --query "Instances[*].[InstanceId]"
}

#
# Waits for an AWS spot fleet to start
#
# Arguments:
#   region
#   fleetId
#   numberOfInstancesToWaitFor
#
waitForAwsFleetToStart()
{
  while true
  do
    fleetStatus=`aws ec2 describe-spot-fleet-instances --spot-fleet-request-id $2 --output text --region $1`
    #fleetStatus=`cat fleet.txt`
    
    checkExitCodeAndExitOnError "waitForAwsFleetToStart"
    
    activeInstances=`echo "$fleetStatus" | grep -o "healthy" | wc -l` #TODO can say unhealthy, must differentiate
    
    if [ $activeInstances -eq $3 ]
    then
      printf "\n"
      return 0
    else
    #echo "Instances not ready yet, waiting"
    printf "."
      sleep 2
    fi
  done
}

#
# Returns an array containing the instance for the region supplied
#
# Arguments:
#   region
#
function getInstancesForRegion(){
  for i in ${!regionList[@]}; do
    if [[ "${regionList[i]}" = "$1" ]]; then
      #echo "-- $1"
      break
    fi
  done
  
  prevInstances=0
  for (( j=0; j<i ; j++ )); do let "prevInstances += ${numberOfInstancesPerRegion[$j]}"; done
  thisRegionInstanceNumber="${numberOfInstancesPerRegion[$i]}"
  regionInstances=( "${instanceIds[@]:$prevInstances:$thisRegionInstanceNumber}" )
  
  echo "${regionInstances[*]}"
}

#
# Waits for AWS instances to start
#
# Arguments:
#   region
#   numberOfInstancesToWaitFor
#   instanceIds
#
function waitForAwsInstancesToStart(){
  local instances=( $3 )
  for (( start=0; start<$2; start=$start+$numberOfInstancesPerAwsRequest)); do
    instanceBatch=( "${instances[@]:$start:$numberOfInstancesPerAwsRequest}" )
    echo "Waiting for instances $start-$(( start+${#instanceBatch[@]}-1 )) in $1"
    #echo "${instanceBatch[*]}"
    while true; do
      local instancesRunning=`aws ec2 describe-instances --region $1 --instance-ids ${instanceBatch[*]} --output text --query "Reservations[*].Instances[*].State.[Name]" | grep -o "running" | wc -l`
      local instancesRunning=$2
      
      if [ $instancesRunning -eq $2 ]
      then
        printf "\n"
        break
      else
      #echo "Instances not ready yet, waiting"
      printf "|$instancesRunning"
        sleep 2
      fi
    done
    #echo "${instanceBatch[*]}"
  done
  

}

#
# Repeatedly tests the ssh connection until it works
#
# Arguments:
#   IP address
testSshUntilWorking()
{
  local errorCode=-1
  local sshCounter=0
  while [[ $errorCode -ne 0 ]] ; do
    echo "Checking SSH"
    #if te AMI instance isn't up and ready in 3 minutes, it's probbaly not ever going to be up, so break the loop and hard exit.
    if [ $sshCounter -ge 180 ]; then
      exit 1
    else
      sshCounter=$(( $sshCounter + 1))
    fi
    ssh -q -o StrictHostKeyChecking=no -i $pemfile "$sshUsername@$1" "exit"
    errorCode=$?
    sleep 1
  done
}

#
# Stops an AWS fleet
#
# Arguments:
#   region
#   fleetId
#
stopAwsFleet(){
  #TODO make it accept an array of fleet ids
  aws ec2 cancel-spot-fleet-requests --region $1 --spot-fleet-request-ids "$2" --terminate-instances
}

#
# Terminates AWS instances
#
# Arguments:
#   region
#   instanceIds
#
function terminateAwsInstances(){
  local instances=( $2 )
  local instanceNumber=${#instances[@]}
  for (( start=0; start<$instanceNumber; start=$start+$numberOfInstancesPerAwsRequest)); do
    instanceBatch=( "${instances[@]:$start:$numberOfInstancesPerAwsRequest}" )
    echo "Terminating instances $start-$(( start+${#instanceBatch[@]}-1 )) in $1"
    aws ec2 terminate-instances --region $1 --instance-ids ${instanceBatch[*]} --output text > /dev/null
  done
}


#
# Gets ip addresses of the AWS instances and puts them
# in files named "privateAddresses.txt" and "publicAddresses.txt".
# Also loads them in global variables named privateAddresses and
# publicAddresses
#
# Arguments:
#   region
#   instanceIds
#
getAwsIpAddresses()
{
  #fleetFilter="\"Name=tag:aws:ec2spot:fleet-request-id,Values=$2\""
  #--filters Name=instance-state-name,Values=running
  #echo "fleetFilter $fleetFilter"
  #addresses=`aws ec2 describe-instances --query "Reservations[*].Instances[*].[PrivateIpAddress,PublicIpAddress]" --output text`
  local instances=( $2 )
  local instanceNumber=${#instances[@]}
  for (( start=0; start<$instanceNumber; start=$start+$numberOfInstancesPerAwsRequest )); do
    instanceBatch=( "${instances[@]:$start:$numberOfInstancesPerAwsRequest}" )
    echo "Getting IP addresses for instances $start-$(( start+${#instanceBatch[@]}-1 )) in $1"
    addresses=`aws ec2 describe-instances --region $1 --output text --instance-ids ${instanceBatch[*]} --query "Reservations[*].Instances[*].[PrivateIpAddress,PublicIpAddress]"`
    x=0
    for add in $addresses; do
      #echo "address: |$add|"
      if [[ $add == *"None"* ]]; then
        x=$((x+1))
        continue
      fi
      if (( $x % 2 == 0 ))
      then
        echo $add >> "$privateAddressesFile"
      else
        echo $add >> "$publicAddressesFile"
      fi
      x=$((x+1))
    done  
  done
  

}

#
# Uploads the folder remoteExperiment to the specified IP,
# the path to the folder is in the global var pathToRemoteExperiment
#
# Arguments:
#   IP address
#
uploadRemoteExperimentToNode()
{
  #echo "rsync -a -r -v -z -P -e \"ssh -o StrictHostKeyChecking=no -i $pemfile\" $pathToRemoteExperiment \"$sshUsername@$1:.\""
  rsync -a -r -z -e "ssh -o StrictHostKeyChecking=no -i $pemfile" $pathToRemoteExperiment "$sshUsername@$1:." --delete
}

#
# Uploads remoteExperiment to all nodes by public IP,
# checks whether the node is local and skips
#
uploadRemoteExperimentToAllNodes()
{
  for i in ${!privateAddresses[@]}; do
    #if [[ $localIps == *"${privateAddresses[$i]}"* ]]; then
    #  continue
    #fi
    uploadRemoteExperimentToNode ${publicAddresses[$i]} &
  done
  wait
}

#
# Uploads the platform from the sdk dir to the specified IP, as well as files
# from the current directory
#
# Arguments:
#   IP address
#   include directory
#   array of files to include, must be passed as files[@]
#
function uploadFromSdkToNode(){ #TODO enable this to work in multiple modes
  local -a files=("${!3}")
  local includes=""
  for i in ${files[@]}; do
    includes="$includes --include=$i"
  done
  
  rsync -a -r -z -e "ssh -o StrictHostKeyChecking=no -i $pemfile" \
  --include="data/" \
  --include="data/apps/" \
  --include="data/fc_template/" \
  --include="data/fc_template/**" \
  --include="data/lib/" \
  --include="data/lib/**" \
  --include="data/repos/" \
  --include="data/repos/**" \
  --include="data/keys/" \
  --include="data/keys/**" \
  --include="swirlds.jar" \
  --include="kernels/" \
  --include="kernels/**" \
  --include="_experimentConfig.sh" \
  --include="_config.sh" \
  --include="_functions.sh" \
  --include="privateAddresses.txt" \
  --include="publicAddresses.txt" \
  --include="*.pem" \
  --include="settings.txt" \
  --include="log4j2.xml" \
  --include="json_file_name.txt" \
  --include="*.json" \
  --include="Platform10Region200K.json" \
  --include="badgerize.sh"\
  $includes \
  --exclude="*" \
  "$2" "./" "../"  "$sshUsername@$1:./remoteExperiment/" --delete --delete-excluded

  # mirror_server.txt and mainnet_config.txt generate under testing directory
  rsync -a -r -z -e "ssh -o StrictHostKeyChecking=no -i $pemfile" \
  --include="mirror_server.txt" \
  --exclude="*" \
  "./" "$pemfile" "$sshUsername@$1:./remoteExperiment/"

  # if mainnet mode
  if [[ -n $MIRROR_JVM_OPTS ]] ; then
    rsync -a -r -z -e "ssh -o StrictHostKeyChecking=no -i $pemfile" \
    --include="mainnet_config.txt" \
    --exclude="*" \
    "./" "$pemfile" "$sshUsername@$1:./remoteExperiment/"
  fi

  # if stream client mode
  if [[ -n $STREAM_CLIENT ]] ; then
    rsync -a -r -z -e "ssh -o StrictHostKeyChecking=no -i $pemfile" \
    --include="streamPublicAddress.txt" \
    --exclude="*" \
    "./" "$pemfile" "$sshUsername@$1:./remoteExperiment/"
  fi

  # if stream server mode
  if [[ -n $STREAM_SERVER ]] ; then
    rsync -a -r -z -e "ssh -o StrictHostKeyChecking=no -i $pemfile" \
    --include="log4j2_stream.xml" \
    --exclude="*" \
   "../" "$pemfile" "$sshUsername@$1:./remoteExperiment/"
  fi
}


#
# Copies the SDK to another local directory
#
# Arguments:
#   destination dir
#
function copySdkLocally(){
  rsync -a -r \
  --include="data/" \
  --include="data/apps/" \
  --include="data/apps/**" \
  --include="data/fc_template/" \
  --include="data/fc_template/**" \
  --include="data/lib/" \
  --include="data/lib/**" \
  --include="data/repos/" \
  --include="data/repos/**" \
  --include="data/keys/" \
  --include="data/keys/**" \
  --include="swirlds.jar" \
  --include="kernels/" \
  --include="kernels/**" \
  --include="_experimentConfig.sh" \
  --include="_config.sh" \
  --include="_functions.sh" \
  --include="privateAddresses.txt" \
  --include="publicAddresses.txt" \
  --include="$pemfile" \
  --include="config.txt" \
  --include="settings.txt" \
  --include="log4j2.xml" \
  --include="json_file_name.txt" \
  --include="*.json" \
  --include="badgerize.sh"\
  --exclude="*" \
  "./" "../"  "$pemfile" "$1/" --delete --delete-excluded
}


# used by makeConfigFile
function baseXencode() { #TODO quite slow, should explore a faster solution 
  awk 'BEGIN{b=split(ARGV[1],D,"");n=ARGV[2];do{d=int(n/b);i=D[n-b*d+1];r=i r;n=d}while(n!=0);print r}' "$1" "$2"
}
# used by makeConfigFile
function base26encode() {
  baseXencode "abcdefghijklmnopqrstuvwxyz" "$1"
}

function base26encodeNew() {
  local num=$1
  local -a arr
  while [[ $num -gt 0 ]]; do
    local mod=$(( $num%26 ))
    #echo "mod $mod"
    local ascii=$(( 97+$mod ))
    arr+=(`printf "\x$(printf %x $ascii)"`)
    num=$(( $num/26 ))
  done
  
  for i in {3..0}; do
    if [[ $i -gt ${#arr[@]}-1 ]]; then
      printf "a"
    else
      printf "${arr[$i]}"
    fi
  done
  #printf "\n"
}

#
# Makes a swirlds config file
# Arguments:
#   app with settings
#
function makeConfigFile(){
  local port=40123
  local configFile="$pathToRemoteExperiment/config.txt"
  > $configFile
  echo "app, $1" >> $configFile
  
  if [[ -n $STREAM_CLIENT ]] ; then
    echo "load stream server address"
    streamPublicAddresses=( `cat ./streamPublicAddress.txt` )
  fi

  for node in ${!privateAddresses[@]}; do
    #this name generation is a bit slow, maybe replace it with a predefined set of names
    if [[ -n $2 ]] ; then
	local nodeName="${2}node${node}"
    else
        local nodeName=`base26encodeNew $node`
    fi
    #local padding="aaaa"
    #name=`printf '%s%s' "${padding:${#nodeName}}" $nodeName`
    echo $nodeName
    
    port=$((port+1))

    if [[ -n $STREAM_CLIENT_SERVER ]]; then
      if [[ $node -lt $STREAM_CLIENT_SERVER ]] 
      then  
        streamport=$(expr $node + 60051)
        echo " address, $nodeName, $nodeName, 1, ${privateAddresses[$node]}, $port, ${publicAddresses[$node]}, $port, , localhost, $streamport" >> $configFile
      else
        echo " address, $nodeName, $nodeName, 1, ${privateAddresses[$node]}, $port, ${publicAddresses[$node]}, $port" >> $configFile
      fi
    elif [[ -n $STREAM_CLIENT ]] ; then
      if [[ $node -lt ${#streamPublicAddresses[@]} ]] 
      then  
        echo " address, $nodeName, $nodeName, 1, ${privateAddresses[$node]}, $port, ${publicAddresses[$node]}, $port, , ${streamPublicAddresses[$node]}, 50051" >> $configFile
      else
        echo " address, $nodeName, $nodeName, 1, ${privateAddresses[$node]}, $port, ${publicAddresses[$node]}, $port" >> $configFile
      fi

    else
      echo " address, $nodeName, $nodeName, 1, ${privateAddresses[$node]}, $port, ${publicAddresses[$node]}, $port" >> $configFile     
    fi
  done
  
  #echo "config.txt created as:"
  #cat config.txt

   # if running as mirror mode
    if [[ -n $MIRROR_JVM_OPTS ]] ; then
      mv $configFile "$pathToRemoteExperiment/mirror_config.txt"
    fi

   
}


#
# Makes a swirlds config file for running multiple nodes
# on a single AWS instances
# Arguments:
#   app with settings
#
function makeMultiNodeConfigFile(){
  local port=50203
  local configFile="$pathToRemoteExperiment/config.txt"
  > $configFile
  echo "app, $1" >> $configFile
  
  for node in $( eval echo {1..$MULTI_NODES_AMOUNT} );do
    #this name generation is a bit slow, maybe replace it with a predefined set of names
    local nodeName=`base26encodeNew $node`
    
    port=$((port+1))
    echo " address, $nodeName, $nodeName, 1, 127.0.0.1, $port, 127.0.0.1, $port" >> $configFile 
  done
}


function makeMainNetAdddrForMirrorNode(){
  local port=40123
  local configFile="mainnet_config.txt"
  > $configFile
  echo "app, $1" >> $configFile
  
  for node in ${!privateAddresses[@]}; do
    #this name generation is a bit slow, maybe replace it with a predefined set of names
    local nodeName=`base26encodeNew $node`
    #local padding="aaaa"
    #name=`printf '%s%s' "${padding:${#nodeName}}" $nodeName`
    #echo $nodeName
    
    port=$((port+1))

    if [[ -n $ENABLE_EVENT_STREAM ]] ; then
      #if it's first node, enable stream event
      if [[ $port -eq 40124 ]] 
      then  
        echo " address, $nodeName, $nodeName, 1, ${privateAddresses[$node]}, $port, ${publicAddresses[$node]}, $port, localhost, 50051" >> $configFile
      else
        echo " address, $nodeName, $nodeName, 1, ${privateAddresses[$node]}, $port, ${publicAddresses[$node]}, $port" >> $configFile
      fi

    else
      echo " address, $nodeName, $nodeName, 1, ${privateAddresses[$node]}, $port, ${publicAddresses[$node]}, $port" >> $configFile     
    fi
  done
      
}

function makeMirrorAddrLocalFile(){
  local port=60000
  # use current minute to add an offset to port used,
  # since previous launched AWS instances may still in shutdowning status may try
  # to connect to current network nodes
  
  current_min=`date +"%M"`
  port=$((port+$current_min*20))  

  local mirrorAddrFile="mirror_server.txt"
  > $mirrorAddrFile
  
  for node in ${!privateAddresses[@]}; do
    #this name generation is a bit slow, maybe replace it with a predefined set of names
    local nodeName=`base26encodeNew $node`
    #local padding="aaaa"
    #name=`printf '%s%s' "${padding:${#nodeName}}" $nodeName`
    #echo $nodeName
    
    port=$((port+1))

    echo " address, ${privateAddresses[$node]}, $port, ${publicAddresses[$node]}, $port" >> $mirrorAddrFile
  done
}

# pause for user to finish other task first
pause(){
 read -n1 -rsp $'Press any key to continue or Ctrl+C to exit...\n'
}


#
# Makes a settings.txt file
#
# Arguments:
#   Array containg all the settings
makeSettingsFile()
{
  settingsFile="$pathToRemoteExperiment/settings.txt"
  > $settingsFile
  arrayOfSettings=("$@")
  for ((i = 0; i < $#; i++)); do
    echo ${arrayOfSettings[i]} >> $settingsFile
  done
  
  #echo "settings.txt created as:"
	#cat $settingsFile
  
}

#
# Starts all nodes
#
startAllNodes()
{
  if [[ "$testRunningOn" == "local" ]]
  then
    cd ..
    java -jar swirlds.jar >output.log 2>&1 &
    cd testing
  else
    for i in ${!privateAddresses[@]}; do
      ssh -o StrictHostKeyChecking=no -i $pemfile "$sshUsername@${publicAddresses[$i]}" "cd remoteExperiment; nohup java -jar swirlds.jar -local $i >output.log 2>&1 &" &
    done
    wait
  fi
}

#
# Starts certain nodes. The node indexes to start should be supplied
#
# Arguments:
#   the node indexes to start supplied as 'startCertainNodes indexes[@]'
#
startCertainNodes()
{
  local -a nodes=("${!1}")
  
  if [[ "$testRunningOn" == "local" ]]
  then
    cd ..
    java -jar swirlds.jar -local ${indexes[@]} >output.log 2>&1 &
    cd testing
  else
    for i in "${nodes[@]}"; do
      ssh -o StrictHostKeyChecking=no -i $pemfile "$sshUsername@${publicAddresses[$i]}" "cd remoteExperiment; nohup java -jar swirlds.jar -local $i >output.log 2>&1 &" &
    done
    wait
  fi
}

#
# Stops all nodes
#
stopAllNodes()
{
  runCommandOnAllNodes "kill \$(pgrep java)"
}

#
# Stops all nodes and waits for them to finish
#
stopAllNodesAndWait(){
  if [[ "$testRunningOn" == "local" ]]
  then
    kill $(pgrep java)
    waitForProcess "java"
  else
    stopAllNodes
    runCommandOnAllNodes 'waitForProcess "java"'
  fi
}

#
# Run the command on all nodes
# if ran from a node, it will check whether the whether it's local
# of remote and run it accordingly
#
# Arguments:
#   Command to run
#
runCommandOnAllNodes()
{
  for i in ${!privateAddresses[@]}; do
    #local isLocal=0
    #for ip in $localIps; do
    #  if [[ "$ip" == "${privateAddresses[$i]}" ]]; then
    #    isLocal=1
    #    break
    #  fi
    #done
    #if [[ $isLocal -gt 0 ]]; then
      #echo "Running \"$1\" locally"
    #  eval "$1" &
    #else
      #echo "Running \"$1\" on ${privateAddresses[$i]}"
      ssh -o StrictHostKeyChecking=no -i $pemfile "$sshUsername@${publicAddresses[$i]}" "cd remoteExperiment; . _config.sh; . _functions.sh; $1" &
    #fi
  done
  wait
}

#
# Runs a function from the _functions.sh file on a remote node
#
# Arguments:
#   IP address to run the function on
#   Function with arguments
runFunctionOnRemoteNode()
{
  ssh -o StrictHostKeyChecking=no -i $pemfile "$sshUsername@$1" "cd remoteExperiment; . _config.sh; . _functions.sh; $2"
}

#
# Runs a function from the _functions.sh file on a remote node,
# adds a nohup so it doesn't wait for the end
#
# Arguments:
#   IP address to run the function on
#   Function with arguments
runNohupFunctionOnRemoteNode()#
{
  ssh -o StrictHostKeyChecking=no -i $pemfile "$sshUsername@$1" "cd remoteExperiment; nohup bash -c \". _config.sh; . _functions.sh; $2\" > nohup.log 2>&1 &"
}

#
# Gets the results files from a single remote node
#
# Arguments:
#   IP address of the node
#   Destination directory
getReslultsFromNode()# args(ip,destination)
(
  rsync -a -r -z -e "ssh -o StrictHostKeyChecking=no -i $pemfile" \
  "$sshUsername@$1:remoteExperiment/*.csv" \
  ":remoteExperiment/*.log" \
  ":remoteExperiment/*.xml" \
  ":remoteExperiment/*.txt" \
  ":remoteExperiment/*.json" \
  ":remoteExperiment/badger_*" \
  ":remoteExperiment/stream_*" \
  "$2"

    # if mirror mode
  if [[ -n $STREAM_SERVER ]] ; then
    rsync -a -r -z -e "ssh -o StrictHostKeyChecking=no -i $pemfile" \
    "$sshUsername@$1:remoteExperiment/stream_50051/*" \
    "$2/stream_50051/"
  fi
)

#
# Gets the results from all nodes
#
# Arguments:
#   Directory to put the results in
#
getAllResults()
{
  if [[ "$testRunningOn" == "local" ]]
  then
    # copy logs locally
    cp ../*.csv                "$1"
    cp ../output.log           "$1"
    cp ../swirlds.log          "$1"
    cp ../*.log		       "$1"
    cp ../config.txt           "$1"
    cp ../settings.txt         "$1"
    cp ../*.txt     "$1"
    cp ../threadDump*          "$1" 2>/dev/null
    #cp -r ../data/saved        "$dir"
  else
    runBadgerize
    # download all the experiment results
    for i in ${!privateAddresses[@]}; do
      local subDir=`printf "$1/node%04d/" $i`
      mkdir -p "$subDir"
      getReslultsFromNode "${publicAddresses[$i]}" "$subDir" &
    done
    wait
  fi
}

#
# Compress all the results on the remote node and them copy them to master.
# This is done in case the log files are really big and may fill up the HDD.
#
# Arguments:
#   Directory to put the results in
#
getAllResultsCompressed()
{
  for i in ${!publicAddresses[@]}; do
    ssh -o StrictHostKeyChecking=no -i $pemfile "$sshUsername@${publicAddresses[$i]}" "cd remoteExperiment; tar cfz data.tar.gz *.csv swirlds.log output.log settings.txt settingsUsed.txt" &
  done
  wait
  
  for i in ${!privateAddresses[@]}; do
    local subDir=`printf "$1/node%04d/" $i`
    mkdir -p "$subDir"
  
    rsync -a -r -z -e "ssh -o StrictHostKeyChecking=no -i $pemfile" "$sshUsername@${publicAddresses[$i]}:remoteExperiment/data.tar.gz" "$subDir" &
  done
  wait
}

#
# Checks the exit code of the last command that ran,
# if it's an error, exits
#
checkExitCodeAndExitOnError()
{
  if [ $? -ne 0 ]
  then
    echo "ERROR $1 failed, exiting" >&2
    exit -1
  fi
}

#
# Waits for the specified PID to finish
#
# Arguments
#   PID
waitForPid()
{
  local PID=$1
  if [ -z "$PID" ]; then
      return 0
  fi
  echo "Waiting for process $PID"
  while [ -e /proc/$PID ]
  do
    printf "."
    sleep 1
  done
  printf "\n"
  echo "Process $PID has finished"
}

#
# Waits for a bash script to finish,
# script must be started with "bash script.sh"
#
# Arguments:
#   Name of script
#
waitForScript()
{
  local name=$1
  local getpid="ps -o pid,args -C bash | awk '/$name/ { print \$1 }'"
  local pids=`eval $getpid`
  #echo "pids $pids"
  for pid in $pids; do
    echo "this $$"
    if [[ $pid -eq $$ ]]; then
      continue
    fi
    waitForPid $pid
  done
echo "waitForScript finished, should have exited."
}

#
# Waits for a process to finish
#
# Arguments:
#   Name of process
#
waitForProcess()
{
  waitForPid `pgrep $1`
}


#
# Matches a single regex group and returns it
# 
# Arguments
#   string to match
#   regular expression
function regex(){
  #echo "---"; echo "$1"; echo "---"; echo "$2"; echo "---"; 
  if [[ $1 =~ $2 ]]
  then
      echo "${BASH_REMATCH[1]}"
  fi
}

#
# Disables internal field seperator, very useful for passing arguments with
# spaces to functions
#
function disableIFS(){
  oldifs="$IFS"
  IFS=
}

#
# Restores the internal field seperator to what it used to be
#
function restoreIFS(){
  IFS="$oldifs"
}

#
# Lets the user choose an option from an array of options, if there is only
# one, that option will be chosen. The option chosen will be put in a global
# variable called 'optionChosen'.
#
# Arguments:
#   an array of options, should be passed as 'chooseOption array[@]'
#
function chooseOption(){
  local -a options=("${!1}")

  local choise=
  if [[ ${#options[@]} -gt 1 ]]; then
    while [ -z "$choise" ]; do
      echo "Please choose one of the following:"
      for i in ${!options[@]}; do
        echo "[$i] - ${options[$i]}"
      done
      
      read -p 'Option number: ' choise
      
      local re='^[0-9]+$'
      if ! [[ $choise =~ $re ]] ; then
        choise=
      else
        if [[ $choise -gt ${#options[@]}-1 ]]; then
          choise=
        fi
      fi
      
    done
  else
    choise=0
  fi

  # returns the results as a global variable
  optionChosen="${options[$choise]}"
}

#
# Create $testDir/_experimentConfig.sh with or without runDetails.sh
# Arguments:
#   $testConfig $testDir
#
function createExperimentConfig(){
  if [[ -f runDetails.sh ]]; then
    cat runDetails.sh <(echo) "$1" > "$2/_experimentConfig.sh"
  else
    cp "$1" "$2/_experimentConfig.sh"
  fi
}

#
# Loads config from a test that has already been started
#
function chooseTest(){
  local findTests=`find ./ -maxdepth 1 -name "test*" 2>/dev/null`
  if [[ -z "$findTests" ]]; then
    echo "No tests found"
    exit -1
  fi

  oldifs="$IFS"
  IFS=$'\n'
  local tests
  for test in $findTests; do
    tests+=("${test:2}")
  done
  IFS="$oldifs"

  chooseOption tests[@]

  testDir=$optionChosen
  
  . "$testDir/_experimentConfig.sh";
  
  fleetIdsFile="$testDir/fleetIds.txt"
  privateAddressesFile="$testDir/privateAddresses.txt"
  publicAddressesFile="$testDir/publicAddresses.txt"
  instanceIdsFile="$testDir/instanceIds.txt"

  privateAddresses=( `cat "$privateAddressesFile"` )
  publicAddresses=( `cat "$publicAddressesFile"` )
  fleetRequestIds=( `cat "$fleetIdsFile" 2>/dev/null` )
  instanceIds=( `cat "$instanceIdsFile"` )
}

#
# Finds which tests are available and prompts to user to pick one. The test
# chosen will be put in a global variable called 'optionChosen'.
#
function chooseTestToRun(){
  local findTests=`find ./ -maxdepth 1 -name "_test*" 2>/dev/null`
  if [[ -z "$findTests" ]]; then
    echo "No tests found"
    exit -1
  fi

  local oldifs="$IFS"
  IFS=$'\n'
  local tests
  for test in $findTests; do
    tests+=("${test:2}")
  done
  IFS="$oldifs"

  chooseOption tests[@]
  
}

#
# Calculates the UTC freeze time based on the input argument and sets the
# following global variables:
#   freezeTime     - the UTC time when the freeze will begin
#   freezeSettings - the platform settings to freeze at this time
#
# Arguments: the offset, from now, to start the freeze
#
function setFreezeTimeAndFreezeSettings(){
  # calculate the freeze time, the platform checks UTC time, so we set it according to the UTC clock
  freezeTime=`date -u --date="$1"`
  freezeTimeHour=`date -u --date="$freezeTime" +"%H"`
  freezeTimeMinute=`date -u --date="$freezeTime" +"%M"`
  # remove the seconds from freeze time
  freezeTime=`date -u --date="$freezeTimeHour:$freezeTimeMinute:00"`

  # make settings and config
  freezeSettings=("freezeActive, 1"
                "freezeTimeStartHour, $freezeTimeHour"
                "freezeTimeStartMin, $freezeTimeMinute" 
                "freezeTimeEndHour, $freezeTimeHour"
                "freezeTimeEndMin, $freezeTimeMinute")
}

#
# Sleeps until 15 seconds after the freeze starts
#
function sleepUntilFreeze(){
  # calculate the time and sleep
  local nowSec=`date +%s`
  local freezeTimeSec=`date -u --date="$freezeTime" +%s`
  local sleepFor=$(( $freezeTimeSec - $nowSec + 15)); # wake up 15 sec after freeze start
  echo "Sleeping until `date -u --date="$sleepFor seconds"`"
  sleep "$sleepFor"
  printf "Woke up at: "
  date -u
}

#
# Sleeps for the supplied number of seconds and prints info to the console
#
# Arguments:
#   the number of seconds to sleep for
#
function sleepFor(){
  printf "Sleeping for $1 seconds, will wake up at: "
  date --date="$sleep seconds"
  sleep $1
  printf "Woke up at: "
  date
}

#
# Generates keys for the current number of nodes
#
function generateKeysForNodes(){
  local -a nodes
  for node in ${!privateAddresses[@]}; do
    nodes+=( `base26encodeNew $node` )
  done
  
  echo "generating keys for nodes "  "${nodes[@]}"
  ../data/keys/generate.sh "${nodes[@]}"

}

#
# Deletes all the keys in the keys directory
#
function deleteKeysForNodes(){
  $pathToRemoteExperiment/data/keys/clean.sh
}

function deleteAvailableVolumes(){
  for region in ${regionList[@]}; do
    echo ${region}
    volumeList=$(aws ec2 describe-volumes --region ${region} --filters Name=status,Values=available| awk '{print $9}')
    for volumeId in ${volumeList[@]}; do
      let totalVolumes=totalVolumes+1
      printf ">> Deleting volume ${volumeId} in region ${region} with prejudice........ "
      aws ec2 delete-volume --volume-id "${volumeId}" --region "${region}" >> "logFile.log" 2>&1
      cmdResult=$?

      if [ $cmdResult -eq 0 ]; then
        printf "done <<\n"
        let volumeCount=volumeCount+1
      else
        printf "FAILED <<\n"
      fi
    done
  done
  echo "***** Purged ${volumeCount} of ${totalVolumes} unattached volumes *****"
}

#
# runs the badgerize script for each node, which tars the results in remoteExperiment directoy so it can be bundled with other results
#
function runBadgerize(){
  for i in ${!publicAddresses[@]}; do
# get a list of directories, find the one that ends in duration (the experiment) remove the size of the directoy before returning the line
    testResultDir=`ssh -i $pemfile "$sshUsername@${publicAddresses[0]}" du ~ | grep Experiment$ | cut -f 1 --complement`
    ssh -o StrictHostKeyChecking=no -i $pemfile "$sshUsername@${publicAddresses[$i]}" "chmod 777 '${testResultDir}'"
  done
  wait
}
