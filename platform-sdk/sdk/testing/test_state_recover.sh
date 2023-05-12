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

#
# Run this script form sdk/ directory to test state recover from event files
#
#
# Arguments list
#  $1 json file to use
#  $2 jvm options
#  $3 skip step 1


set -eE

trap ' print_banner "TEST FAILED" ' ERR 

if [[ -z $1 ]]  # if test json not given, use default one
then
    TEST_JSON="FCM-Recovery-1k-5m.json"
else
    TEST_JSON="$1"
fi

if [[ -z $2 ]]
then
    JVM_OPTONS=""
else
    JVM_OPTONS=$2
    echo " JVM_OPTONS = $JVM_OPTONS"
fi

TEST_APP_PARAM=" PlatformTestingTool.jar, $TEST_JSON "
echo "Test App and parameters = $TEST_APP_PARAM "

PACKAGE="com.swirlds.demo.platform.PlatformTestingToolMain"

platform='unknown'
unamestr=`uname`
if [[ "$unamestr" == 'Linux' ]]; then
   platform='linux'
elif [[ "$unamestr" == 'Darwin' ]]; then
   platform='macOS'
fi

state_delete_num=0


# save the round number of deleted states
deleted_array=()

function get_last_round_number()
{
    local lastRoundNumber=`ls -tr data/saved/$PACKAGE/0/123 | sort -n| tail -1`
    echo "$lastRoundNumber"
}

function delete_last_round()
{
    # found the last round of state
    lastRound=$(get_last_round_number)
    echo "Deleting the round  $lastRound"
    deleted_array+=("$lastRound")

    # delete the last round for all node saved state
    `find data/saved/ -name $lastRound -exec rm -rf {} + `
}

#
# random generate true or false
#
function gen_random_choice()
{
    BINARY=2
    T=1
    number=$RANDOM
    let "number %= $BINARY"

    if [ "$number" -eq $T ]
    then
        return 0 
    else
        return 1
    fi  
}

function print_banner
{
    echo "################################"
    echo  $1
    echo "################################"
    
    echo "################################" >> swirlds.log
    echo  $1                                >> swirlds.log
    echo "################################" >> swirlds.log
}

#
# check the log whether it contains the expected new marker sentence
#
# Arguments
#   $1 searching string
#   $2 log name
#
function searchExpectedString
{
    if grep -q "$1" $2; then
        echo "SUCEED: Found expected string ($1) in $2"
    else
        echo "FAIL: Not found expected string ($1) in $2"
        exit 164
    fi
}


# run PTD in normal mode to generate multiple signed states
# also generate event files for recover test,
# and save expected map for check against recover state
function step1_original_run()
{
    print_banner "Running ${FUNCNAME[0]}"

    # remove old state and logs
    rm -rf data/saved/; rm -rf data/eventStream*; rm -rf data/platformtesting/; rm swirlds.log

    # recover setting.txt and config to default
    git checkout settings.txt
    git checkout config.txt

    # remove default enabled swirlds app
    sed -i -e /'app,.*'/d config.txt

    # enable PTD with json
    echo "app, $TEST_APP_PARAM " >> config.txt

    echo "Please make sure log4j.xml append is enabled."

    # Change settings.txt to save state more frequently
    sed -i -e s/'state.saveStatePeriod'.*/'state.saveStatePeriod,    20 '/g  settings.txt

    # save many signed state on disk so we can test of removing more signed state
    echo "state.signedStateDisk,     3000" >> settings.txt

    echo "Making sure enableStateRecovery is false"
    sed -i -e s/'enableStateRecovery'.*/'enableStateRecovery,   false '/g  settings.txt

    # enable streaming
    echo "enableEventStreaming,      true" >> settings.txt
    echo "eventsLogDir,              data/eventStream" >> settings.txt
    echo "eventsLogPeriod,           30" >> settings.txt

    # run PTD for a while until it quits itself
    # then we have needed old states, event stream, and PTD expected result
    java -jar $JVM_OPTONS swirlds.jar

    #back up states files before next deleting step
    rm -rf data/prevRun
    mkdir -p data/prevRun/$PACKAGE
    cp -r data/saved/$PACKAGE data/prevRun/

    rm -rf data/eventStreamOriginal
    cp -r data/eventStream/ data/eventStreamOriginal

    cp swirlds.log swirdsStep1.log
}

function get_node_last_round_number()
{
    local lastRoundNumber=`ls -tr data/saved/$PACKAGE/$1/123 | sort -n| wc -l`
    return $lastRoundNumber
}

#without freeze stage, some node might generate extra state, need to be delete to
#align with all nodes
function step_delete_extra_states
{
    node_names=($(ls -tr data/saved/$PACKAGE/))

    printf "Node names are %s\n" "${node_names[@]}"

    get_node_last_round_number ${node_names[0]} || min_states=$?
    echo "First node states amount = " $min_states
    for i in ${!node_names[@]}; do
        node_name=${node_names[$i]}
        get_node_last_round_number $node_name || lastRound=$?
        #echo "lastRound = " $lastRound

        if (( $min_states != $lastRound )); then
            echo "WARNING : Different number of states are generated from node $node_name"
        fi

        if (( $min_states > $lastRound )); then
            min_states=$lastRound
        fi
    done
    echo "min_states = " $min_states


    for i in ${!node_names[@]}; do
        node_name=${node_names[$i]}
        echo "-----------"
        all_states=($(ls -tr data/saved/$PACKAGE/$node_name/123 | sort -n))
        printf "Node $node_name has states : %s\n" "${all_states[@]}"

        if (( ${#all_states[@]} != $min_states)); then
            delete_states=${all_states[@]:$min_states}

            #echo "${#delete_states[@]}"

            for state in ${!delete_states[@]}; do
                state_num=${delete_states[$state]}
                echo "Delete extra state $state_num"
                rm -rf data/saved/$PACKAGE/$node_name/123/$state_num
            done
        fi
    done
}


# random delete one or two last rounds of signed state
function step2_delete_old_state()
{
    print_banner "Running ${FUNCNAME[0]}"

    save_lastRound=$(get_last_round_number)
    echo "Last round before delete is $save_lastRound"
    echo "States generated in previous run " `ls -tr data/saved/$PACKAGE/0/123 | sort -n`
    signed_state_amount=`ls -tr data/saved/$PACKAGE/0/123 | sort -n| wc -l `
    echo "Created signed state amount: $signed_state_amount"

    #random delete some states
    state_delete_num=$(( ( RANDOM % ($signed_state_amount - 1) + 1 ) ))

    echo "Deleting $state_delete_num signed states"

    # remove most of states only leaving one
    for ((i=1;i<=state_delete_num;i++)); do
        delete_last_round
    done 
}

function delete_event_files() {
    print_banner "Running ${FUNCNAME[0]}"
    event_files_amount=`ls -tr data/eventStream/events_0/*soc | sort -n| wc -l `
    echo "There are $event_files_amount event files"
    random_num=$(( ( RANDOM % ($state_delete_num ) * 2 ) + 2 ))

    echo "Deleting $random_num event files"
    ls -1a data/eventStream/events_0/*soc | sort -n| tail -$random_num | xargs rm 

    last_file=`ls -1a data/eventStream/events_0/*soc | tail -1`

    ls -la $last_file
    #truncate the last one
    file_size=`wc -c < $last_file`
    truncate_size=$(( ($file_size - 1)/2  ))
    echo "Truncate the last file $last_file bytes $truncate_size"
    
    split -b $truncate_size $last_file

    # results from split is in current directoires, names as xaa, xab, xac, etc
    cp xaa $last_file

    echo "After truncate"
    ls -la $last_file
}

# change settings to enable recover mode
function step3_recover()
{
    print_banner "Running ${FUNCNAME[0]}"

    # delete expectedMap so it can be rebuilt with last left saved state
    rm -rf data/platformtesting/

    rm -rf data/eventStreamRecover

    # enable state recover and set correct stream directory 
    echo "enableStateRecovery,   true" >> settings.txt
    echo "playbackStreamFileDirectory,   data/eventStream " >> settings.txt

    sed -i -e s/'state.saveStatePeriod'.*/'state.saveStatePeriod,    20 '/g  settings.txt

    # save event to different directory
    sed -i -e s/'eventsLogDir'.*/'eventsLogDir, data\/eventStreamRecover'/g  settings.txt

    # launch PTD in recover mode, it will return code 0 after saving recovered signed state to disk
    java -jar $JVM_OPTONS swirlds.jar -local 0

    searchExpectedString "Last recovered signed state has been saved in state recover mode" swirlds.log

    recover_lastRound=$(get_last_round_number)
    echo "Recovered last round $recover_lastRound"

    if [ $recover_lastRound != $save_lastRound ]; then
        echo "WARNING: Recover round not the same as the original deleted last round"
        # exit 20
    fi


    #compareState

    node0dir="data/saved/$PACKAGE/0/123"

    # copy to other node directory
    array=( $(ls -d data/saved/$PACKAGE/*/123) )
    for i in "${array[@]}"
    do
        if [ $i != $node0dir ]
            then
                echo "cp -r $node0dir/* $i"
                cp -r $node0dir/* $i
        fi
    done

}

function step_cmp_event_files
{
    print_banner "Running ${FUNCNAME[0]}"

    # compare generated event stream files with original ones, ignore files exist in original ones only
    diff_amount=`diff  data/eventStreamRecover/events_0/ data/eventStream/events_0/ | grep diff | wc -l`
    if [ $(( $diff_amount )) -eq 0 ]; then
        print_banner "Event files are same"
    else
        print_banner "Event files are different"

        # probably due to the last event in round bit is manually set
        last_event_file=`ls -1a data/eventStream/events_0/*soc | tail -1`
        last_recover_file=`ls -1a data/eventStreamRecover/events_0/*soc | tail -1`
        cmp_result=`cmp -l $last_event_file $last_recover_file 2>&1` || ret=$?
        echo "$cmp_result"  #something like  2442043   0   1
        #split result to strings
        vars=( $cmp_result )
        printf '%s\n' "${vars[@]}"

        if [[ "${vars[5]}" == "0" && "${vars[6]}" == "1" ]]; then
            # cmp: EOF on data/eventStreamRecover/events_0/2020-09-02T13_50_00.004246Z.soc
            # 2600400   0   1
            echo "Event file only different at one bit cause by lastInRoundReceived, which is expected"
        elif [[ "${vars[1]}" == "EOF" && "${vars[2]}" == "on" ]]; then
            # cmp: EOF on data/eventStreamRecover/events_0/2020-09-02T13_50_00.004246Z.soc
            echo "Event file only partial equal with the last recovered event is the lase one of its round"
        else
            echo "Recover event files are different compared with originals."
            exit 64
        fi        
    fi

    # # compare generated account files with original ones, ignore files exist in original ones only
    # diff_amount=`diff  data/accountBalances/balance0.0.3/ data/accountBalancesOriginal/balance0.0.3/ | grep diff | wc -l`
    # if [ $(( $diff_amount )) -eq 0 ]; then
    #     print_banner "Account files are same"
    # else
    #     print_banner "Account files are different"
    #     exit 65
    # fi 

} 


# compared recovered state files with expected ones
function compareState()
{
    recover_states=($(ls -tr data/saved/$PACKAGE/0/123 | sort -rn))

    #remove the last one which is round number of state before recovering
    unset 'recover_states[${#recover_states[@]}-1]'

    recover_string=`echo "${recover_states[*]}"`
    prev_string=`echo "${deleted_array[*]}"`

    echo "Recover states round are : <$recover_string>"
    echo "Deleted states round are : <$prev_string>"
    
    if [[ $recover_string != $prev_string ]]; then
        echo "ERROR: Recovered state round DOES NOT match expected"
        exit 21
    else
        echo "Recovered state round match expected"
    fi
}

# start PTD without submitting any new transaction, just check against expected map
function step4_check()
{
    print_banner "Running ${FUNCNAME[0]}"

    #disable recover mode
    sed -i -e s/'enableStateRecovery'.*/'enableStateRecovery,   false '/g  settings.txt

    #don't save new sigend state in the checking stage
    sed -i -e s/'state.saveStatePeriod'.*/'state.saveStatePeriod,   0 '/g  settings.txt

    #disable event streaming
    sed -i -e s/'enableEventStreaming'.*/'enableEventStreaming,  false'/g  settings.txt

    sed -i -e s/'PlatformTestingTool.jar'.*json/'PlatformTestingTool.jar, FCM-Recovery-Check.json '/g config.txt

    #restart PTD, it will check against saved expected map and exit, and should not create any new signed state
    java -jar $JVM_OPTONS swirlds.jar
}


# run with normal PTD with more transaction
function step5_normal_restart()
{
    print_banner "Running ${FUNCNAME[0]}"

    # Change settings.txt to save state more frequently
    sed -i -e s/'state.saveStatePeriod'.*/'state.saveStatePeriod,    20 '/g  settings.txt
    
    # save event to different directory
    sed -i -e s/'eventsLogDir'.*/'eventsLogDir, data\/eventStreamResume'/g  settings.txt

    #enable event streaming again
    sed -i -e s/'enableEventStreaming'.*/'enableEventStreaming,  true'/g  settings.txt

    # change config.txt to run a normal PTD test
    sed -i -e s/'PlatformTestingTool.jar'.*json/"$TEST_APP_PARAM"/g config.txt

    #restart PTD
    java -jar $JVM_OPTONS swirlds.jar

    RESULT=$?
    if [ $RESULT -eq 0 ]; then
        print_banner "TEST SUCCESS"
    else
        print_banner "TEST FAILED"
    fi
}


function skip_step1()
{
    #enable event streaming again
    sed -i -e s/'enableEventStreaming'.*/'enableEventStreaming,  true'/g  settings.txt

    rm -rf data/saved
    mkdir -p data/saved
    cp -r data/prevRun/* data/saved/ 
    cp -r data/eventStreamOriginal data/eventStream

    rm -f swirlds.log
}

function step_recover_posgres
{
    recover_common

    if [[ $platform == 'linux' ]]; then
        echo "Recover for Linux"
        recover_linux
    elif [[ $platform == 'macOS' ]]; then
        echo "Recover for macOS"
        recover_macOS
    fi

}

###############################
#
#   Main body 
#
###############################

if [[ -z $3 ]] ; then
    step1_original_run
else
    echo "Skip step 1"
    skip_step1
fi
step_delete_extra_states
step2_delete_old_state
delete_event_files
step3_recover
step_cmp_event_files
step4_check
step5_normal_restart