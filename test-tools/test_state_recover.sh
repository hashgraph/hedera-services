#!/usr/bin/env bash

#
# Run this script form hedera-services/hedera-node/ directory to test state recover from event files
#
#
# Arguments list
#  $1 skip step 1
#  $2  source state files
#  $3  source event files
#  $4  event file interval
#  $5  end time stamp
set -eE

trap ' print_banner "TEST FAILED" ' ERR 

PACKAGE="com.hedera.node.app.ServicesMain"
CLIENT="mvn exec:java -Dexec.mainClass=com.hedera.services.bdd.suites.perf.CryptoTransferLoadTest -Dexec.args='10 2 2' -Dexec.cleanupDaemonThreads=false"

platform='unknown'
unamestr=`uname`
if [[ "$unamestr" == 'Linux' ]]; then
   platform='linux'
elif [[ "$unamestr" == 'Darwin' ]]; then
   platform='macOS'
fi

state_delete_num=0

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

# run HGCApp in normal mode to generate multiple signed states
# also generate event files for recover test,
# and save expected map for check against recover state
function step1_original_run()
{
    print_banner "Running ${FUNCNAME[0]}"

    # remove old state and logs
    rm -rf data/saved/; rm -rf data/eventStream*/; rm -f output/*.log
    rm -rf data/accountBalances

    # recover settings.txt and config to default
    git checkout settings.txt

    echo "Please make sure log4j.xml append is enabled."

    # Change settings.txt to save state more frequently
    sed -i -e s/'state.saveStatePeriod'.*/'state.saveStatePeriod,    60 '/g  settings.txt

    # save many signed state on disk so we can test of removing more signed state
    echo "state.signedStateDisk,     3000" >> settings.txt
    echo "accountBalanceExportPeriodMinutes=1" >> data/config/application.properties

    echo "Making sure enableStateRecovery is false"
    sed -i -e s/'enableStateRecovery'.*/'enableStateRecovery,   false '/g  settings.txt

    # save database
    echo "dbBackup.active,           1" >> settings.txt

    echo "checkSignedStateFromDisk,           0" >> settings.txt

    # enable streaming
    echo "enableEventStreaming,      true" >> settings.txt
    echo "eventsLogDir,              data/eventStream" >> settings.txt
    echo "eventsLogPeriod,           15" >> settings.txt

    launch_hgc_and_client

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
    event_files_amount=`ls -tr data/eventStream/events_0.0.3/*evts | sort -n| wc -l `
    echo "There are $event_files_amount event files"
    random_num=$(( ( RANDOM % ($state_delete_num ) * 2 ) + 2 ))

    echo "Deleting $random_num event files"
    ls -1a data/eventStream/events_0.0.3/*evts | sort -n| tail -$random_num | xargs rm 

    last_file=`ls -1a data/eventStream/events_0.0.3/*evts | tail -1`

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

    rm -rf data/eventStreamRecover
    rm -rf data/accountBalancesOriginal

    # enable state recover and set correct stream directory 
    echo "enableStateRecovery,   true" >> settings.txt
    echo "playbackStreamFileDirectory,   data/eventStream " >> settings.txt

    sed -i -e s/'state.saveStatePeriod'.*/'state.saveStatePeriod,    60 '/g  settings.txt

    # save event to different directory
    echo "eventsLogDir,   data/eventStreamRecover" >> settings.txt
    echo "enableEventStreaming,  true" >> settings.txt
    
    # back up account balance
    cp -r data/accountBalances data/accountBalancesOriginal
    rm -rf data/accountBalances/*

    echo "signedStateFreq, 1" >> settings.txt
    # launch HGCApp in recover mode
    ret=0
    java -Djava.awt.headless=true -Xmx14g -Xms12g -cp 'data/lib/*' com.swirlds.platform.Browser -local 0 || ret=$?

    echo "Recover mode exited with: $ret"

    node0dir="data/saved/$PACKAGE/0/123"


}

function step_cmp_event_files
{
    print_banner "Running ${FUNCNAME[0]}"

    # compare generated event stream files with original ones, ignore files exist in original ones only
    diff_amount=`diff  data/eventStreamRecover/events_0.0.3/ data/eventStream/events_0.0.3/ | grep diff | wc -l`
    if [ $(( $diff_amount )) -eq 0 ]; then
        print_banner "Event files are same"
    else
        print_banner "Event files are different"

        # probably due to the last event in round bit is manually set
        last_event_file=`ls -1a data/eventStream/events_0.0.3/*evts | tail -1`
        last_recover_file=`ls -1a data/eventStreamRecover/events_0.0.3/*evts | tail -1`
        cmp_result=`cmp -l $last_event_file $last_recover_file 2>&1` || ret=$?
        echo "$cmp_result"  #something like  2442043   0   1
        #split result to strings
        vars=( $cmp_result )
        printf '%s\n' "${vars[@]}"

        if [[ "${vars[5]}" == "0" && "${vars[6]}" == "1" ]]; then
            # cmp: EOF on data/eventStreamRecover/events_0.0.3/2020-09-02T13_50_00.004246Z.evts
            # 2600400   0   1
            echo "Event file only different at one bit cause by lastInRoundReceived, which is expected"
        elif [[ "${vars[1]}" == "EOF" && "${vars[2]}" == "on" ]]; then
            # cmp: EOF on data/eventStreamRecover/events_0.0.3/2020-09-02T13_50_00.004246Z.evts
            echo "Event file only partial equal with the last recovered event is the lase one of its round"
        else
            echo "Recover event files are different compared with originals."
            exit 64
        fi        
    fi

    # compare generated account files with original ones, ignore files exist in original ones only
    diff_amount=`diff  data/accountBalances/balance0.0.3/ data/accountBalancesOriginal/balance0.0.3/ | grep diff | wc -l`
    if [ $(( $diff_amount )) -eq 0 ]; then
        print_banner "Account files are same"
    else
        print_banner "Account files are different"
        exit 65
    fi 

} 

# copy newly generated signed state to other nodes
# $1 total number of nodes
function step_copy_nodes
{
    print_banner "Running ${FUNCNAME[0]}"
    if [[ -n $1 ]]; then
        i="0"
        while [[ $i -le $1 ]]

        do
            echo $i
            if [ $i != "0" ]
                then
                    mkdir -p data/saved/$PACKAGE/$i
                    mkdir -p data/saved/$PACKAGE/$i/123
                    cp -r $node0dir/* data/saved/$PACKAGE/$i/123
            fi   
            ((i = i + 1))         
        done    
    else
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
    fi

  
}

function step4_check()
{
    print_banner "Running ${FUNCNAME[0]}"

    #disable recover mode
    sed -i -e s/'enableStateRecovery'.*/'enableStateRecovery,   false '/g  settings.txt

    #don't save new sigend state in the checking stage
    sed -i -e s/'state.saveStatePeriod'.*/'state.saveStatePeriod,   0 '/g  settings.txt

    #disable event streaming
    sed -i -e s/'enableEventStreaming'.*/'enableEventStreaming,  false'/g  settings.txt

    # TBD check swirlds state within last signed state should be the same
    echo "Checking done"
}

# run with normal HGCApp with more transaction
function step5_normal_restart()
{
    print_banner "Running ${FUNCNAME[0]}"

    # Change settings.txt to save state more frequently
    sed -i -e s/'state.saveStatePeriod'.*/'state.saveStatePeriod,    60 '/g  settings.txt
    
    # save event to different directory
    sed -i -e s/'eventsLogDir'.*/'eventsLogDir, data\/eventStreamResume'/g  settings.txt

    #enable event streaming again
    sed -i -e s/'enableEventStreaming'.*/'enableEventStreaming,  true'/g  settings.txt


    #restart HGCApp
    launch_hgc_and_client

    RESULT=$?
    if [ $RESULT -eq 0 ]; then
        print_banner "TEST SUCCESS"
    else
        print_banner "TEST FAILED"
    fi
}

function launch_hgc_and_client
{
    echo
    echo
    print_banner "Launching HGCApp "
    echo
    echo

    # run HGCApp for a while then shut it down
    # then we have needed old states, event stream, and HGCApp expected result
    java -Djava.awt.headless=true -cp 'data/lib/*' com.swirlds.platform.Browser &
    pid=$!  # remember prcoess ID of server
    echo "Running server as java process $pid"
    sleep 80
    print_banner "Now lanching client "

    # meanwhile launch haiClient
    cd ../test-clients
    eval "$CLIENT"
    
    cd -
    sleep 30

    print_banner "Killing HGCApp "
    kill -9 $pid
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

#
# Arguments list
#  $1  source state files
#  $2  source event files
#  $3  event file interval
#  $4  end time stamp

function prepare_recover
{
    echo "Please make sure application.properties that environment=0 and nettyMode=DEV "
    echo "Please make sure uniqueListeningPortFlag=1 is commened out "
    echo "Please make sure eventsLogPeriod match original event file interval "
    echo "Please make sure the number of address in config.txt match the number of nodes from the signed state original network"
    echo ""
    echo ""
    
    # recover settings.txt and config to default
    git checkout settings.txt

    # save database
    echo "dbBackup.active,            1" >> settings.txt
    echo "checkSignedStateFromDisk,   0" >> settings.txt    
    echo "eventsLogPeriod,           $3" >> settings.txt
    if [[ -n $4 ]]; then
        echo "Use customized end timestamp"
        echo "playbackEndTimeStamp,      $4" >> settings.txt
    fi
    rm -rf data/saved/*
    rm -rf data/eventStream/*
    rm -f output/*.log

    cp -r $1/* data/saved/ 
    cp -r $2/* data/eventStream/ 

}

###############################
#
#   Main body 
#
###############################

if [[ "recover" == $1 ]] ; then
    # echo "Start Production Version Recover Process"
    prepare_recover $2 $3 $4 $5
    step2_delete_old_state
    step3_recover
    step_cmp_event_files
    step_copy_nodes $5
    step4_check
    exit
elif [[ "reload" == $1 ]] ; then
    echo "Reload from saved state and restore database first"
    rm -f output/*.log
    java -Djava.awt.headless=true -cp 'data/lib/*' com.swirlds.platform.Browser
elif [[ "skip1" == $1 ]] ; then
    echo "Skip step 1"
    skip_step1
else
    step1_original_run
fi
step_delete_extra_states
step2_delete_old_state
delete_event_files
step3_recover
step_cmp_event_files
step_copy_nodes
step4_check
step5_normal_restart
