# !/usr/local/bin/python3

############################################################
#
#   Python3 only
#
#   must install matplotlib to work properly
#
#
#  Given a list of statistics
#  scan csv files and draw
#  graph
#
#  By default, run this script from sdk/testing directory,
#  it will list subdirectories under sdk/testing/results and ask
#  use to choose one subdirectory to scan for results.
#
#  Then it scans platform csv files AND swirldslog
#
############################################################


import collections
import csv
import glob
import os
import platform
import pprint
import sys
import matplotlib

from datetime import datetime, timedelta, timezone

def _parse_isoformat_date(dtstr):
    # It is assumed that this function will only be called with a
    # string of length exactly 10, and (though this is not used) ASCII-only
    year = int(dtstr[0:4])
    if dtstr[4] != '-':
        raise ValueError('Invalid date separator: %s' % dtstr[4])

    month = int(dtstr[5:7])

    if dtstr[7] != '-':
        raise ValueError('Invalid date separator')

    day = int(dtstr[8:10])

    return [year, month, day]


def _parse_hh_mm_ss_ff(tstr):
    # Parses things of the form HH[:MM[:SS[.fff[fff]]]]
    len_str = len(tstr)

    time_comps = [0, 0, 0, 0]
    pos = 0
    for comp in range(0, 3):
        if (len_str - pos) < 2:
            raise ValueError('Incomplete time component')

        time_comps[comp] = int(tstr[pos:pos+2])

        pos += 2
        next_char = tstr[pos:pos+1]

        if not next_char or comp >= 2:
            break

        if next_char != ':':
            raise ValueError('Invalid time separator: %c' % next_char)

        pos += 1

    if pos < len_str:
        if tstr[pos] != '.':
            raise ValueError('Invalid microsecond component')
        else:
            pos += 1

            len_remainder = len_str - pos
            if len_remainder not in (3, 6):
                raise ValueError('Invalid microsecond component')

            time_comps[3] = int(tstr[pos:])
            if len_remainder == 3:
                time_comps[3] *= 1000

    return time_comps


def _parse_isoformat_time(tstr):
    # Format supported is HH[:MM[:SS[.fff[fff]]]][+HH:MM[:SS[.ffffff]]]
    len_str = len(tstr)
    if len_str < 2:
        raise ValueError('Isoformat time too short')

    # This is equivalent to re.search('[+-]', tstr), but faster
    tz_pos = (tstr.find('-') + 1 or tstr.find('+') + 1)
    timestr = tstr[:tz_pos-1] if tz_pos > 0 else tstr

    time_comps = _parse_hh_mm_ss_ff(timestr)

    tzi = None
    if tz_pos > 0:
        tzstr = tstr[tz_pos:]

        # Valid time zone strings are:
        # HH:MM               len: 5
        # HH:MM:SS            len: 8
        # HH:MM:SS.ffffff     len: 15

        if len(tzstr) not in (5, 8, 15):
            raise ValueError('Malformed time zone string')

        tz_comps = _parse_hh_mm_ss_ff(tzstr)
        if all(x == 0 for x in tz_comps):
            tzi = timezone.utc
        else:
            tzsign = -1 if tstr[tz_pos - 1] == '-' else 1

            td = timedelta(hours=tz_comps[0], minutes=tz_comps[1],
                           seconds=tz_comps[2], microseconds=tz_comps[3])

            tzi = timezone(tzsign * td)

    time_comps.append(tzi)

    return time_comps

def datetime_from_isformat(date_string):
    """Construct a datetime from the output of datetime.isoformat()."""
    if not isinstance(date_string, str):
        raise TypeError('fromisoformat: argument must be str')

    # Split this at the separator
    dstr = date_string[0:10]
    tstr = date_string[11:]

    try:
        date_components = _parse_isoformat_date(dstr)
    except ValueError:
        raise ValueError(f'Invalid isoformat string: {date_string!r}')

    if tstr:
        try:
            time_components = _parse_isoformat_time(tstr)
        except ValueError:
            raise ValueError(f'Invalid isoformat string: {date_string!r}')
    else:
        time_components = [0, 0, 0, 0, None]

    return datetime(*(date_components + time_components))

# enable non-interactive mode if no DISPLAY defined in environment
# this is for running script as backend to generate PDF, PNG, etc
# must call use('Agg') before import plt
if os.name == 'posix' and "DISPLAY" not in os.environ:
    print("No display detected")
    matplotlib.use('Agg')

import matplotlib.pyplot as plt
import argparse
import math
import matplotlib.widgets
import matplotlib.patches
import mpl_toolkits.axes_grid1
import re
from threading import Thread
import numpy as np
from matplotlib.backends.backend_pdf import PdfPages

pp = pprint.PrettyPrinter(indent=4)

CONST_PAUSE = "GC Pauses"


#
#  An slider to select different graph pages
#
# reference https://stackoverflow.com/questions/41143782/paging-scrolling-through-set-of-2d-heat-maps-in-matplotlib
#
class PageSlider(matplotlib.widgets.Slider):

    def __init__(self, ax, label, numpages=10, valinit=0, valfmt='%1d',
                 closedmin=True, closedmax=True,
                 dragging=True, **kwargs):

        self.facecolor = kwargs.get('facecolor', "w")
        self.activecolor = kwargs.pop('activecolor', "b")
        self.fontsize = kwargs.pop('fontsize', 10)
        self.numpages = numpages

        super(PageSlider, self).__init__(ax, label, 0, numpages,
                                         valinit=valinit, valfmt=valfmt, **kwargs)

        self.poly.set_visible(False)
        self.vline.set_visible(False)
        self.pageRects = []
        for i in range(numpages):
            facecolor = self.activecolor if i == valinit else self.facecolor
            r = matplotlib.patches.Rectangle((float(i) / numpages, 0), 1. / numpages, 1,
                                             transform=ax.transAxes, facecolor=facecolor)
            ax.add_artist(r)
            self.pageRects.append(r)
            ax.text(float(i) / numpages + 0.5 / numpages, 0.5, str(i + 1),
                    ha="center", va="center", transform=ax.transAxes,
                    fontsize=self.fontsize)
        self.valtext.set_visible(False)

        divider = mpl_toolkits.axes_grid1.make_axes_locatable(ax)
        bax = divider.append_axes("right", size="5%", pad=0.05)
        fax = divider.append_axes("right", size="5%", pad=0.05)
        self.button_back = matplotlib.widgets.Button(bax, label=r'$\blacktriangleleft$',
                                                     color=self.facecolor, hovercolor=self.activecolor)
        self.button_forward = matplotlib.widgets.Button(fax, label=r'$\blacktriangleright$',
                                                        color=self.facecolor, hovercolor=self.activecolor)
        self.button_back.label.set_fontsize(self.fontsize)
        self.button_forward.label.set_fontsize(self.fontsize)
        self.button_back.on_clicked(self.backward)
        self.button_forward.on_clicked(self.forward)

    def _update(self, event):
        super(PageSlider, self)._update(event)
        i = int(self.val)
        if i >= self.valmax:
            return
        self._colorize(i)

    def _colorize(self, i):
        for j in range(self.numpages):
            self.pageRects[j].set_facecolor(self.facecolor)
        self.pageRects[i].set_facecolor(self.activecolor)

    def forward(self, event):
        current_i = int(self.val)
        i = current_i + 1
        if (i < self.valmin) or (i >= self.valmax):
            return
        self.set_val(i)
        self._colorize(i)

    def backward(self, event):
        current_i = int(self.val)
        i = current_i - 1
        if (i < self.valmin) or (i >= self.valmax):
            return
        self.set_val(i)
        self._colorize(i)


##############################
# default global
##############################

# key log information to analyze in swirlds.log
log_search_keys = {
    # label                 key message to search for
    "commEventDiscarded": "received commEventDiscarded",
    "getEventWait": "EventInfo.getEventWait waiting",
    "isValidWait": "EventInfo.isValidWait waiting",
    "failed instantiate": "failed to instantiate new",
    "fallBehindDie": "has fallen behind, will die",
    "invalidState": "Received invalid state signature",
    "Critical": "Critical exception",
    "beforeParents": "Received event before its parents",
    "invalidIntakeQueue": "invalid event in intake queue",
    "broken": "didn't receive anything from",
    "Exception": "Exception",

}

# default statistic entries to be graphed
default_stat_names = [
    "time",  # must be the first

    # "bytes/sec_catchup",
    # "bytes/sec_sent",
    # "bytes/sec_sync",
    # "bytes/sec_sys",
    # "bytes/sec_trans",
    # "bytes/trans",
    # "bytes/trans_sys",
    # "cEvents/sec",
    # "conns",
    # "consEvtsH/sec",
    # "consEvtsHAvg",
    # "cpuLoadSys",
    # "discEvReq",
    # "DiskspaceFree",
    # "DiskspaceUsed",
    # "DiskspaceWhole",
    # "dupEv%",
    # "dupEv/sec",
    # "ev/syncR",
    # "ev/syncS",
    # "events/sec",
    # "eventsInMem",
    # "fracSyncSlowed",
    # "getNumUserTransEvents",
    # "icSync/sec",
    # "irSync/sec",
    # "lastSeq",
    # "local",
    # "memberID",
    # "members",
    # "memFree",
    # "memMax",
    # "memTot",
    # "numReportFallenBehind",
    # "ping",
    # "proc",
    # "q1",
    # "q2",
    # "q3",
    # "q4",
    # "qStream",
    # "rescuedEv/sec",
    # "rounds/sec",
    # "roundSup",
    # "sec/sync",
    # "sec/sync1",
    # "sec/sync2",
    # "sec/sync3",
    # "sec/sync4",
    # "secC2C",
    # "SecC2H",
    # "secC2R",
    # "secC2RC",
    # "secNewSigState",
    # "secOR2T",
    # "secR2C",
    # "secR2F",
    # "secR2nR",
    # "secSC2T",
    # "secStateCopy",
    # "secTransH",
    # "shouldCreateEvent",
    # "simCallSyncsMax",
    # "simListenSyncs",
    # "simSyncs",
    # "sleep1/sec",
    # "sleep2/sec",
    # "sleep3/sec",
    # "staleEv/sec",
    # "staleEvTot",
    # "stateSigQWait",
    # "stateSigs",
    # "sync/secC",
    # "sync/secR",
    # "threads",
    # "timeFracAdd",
    # "timeFracDot",
    # "TLS",
    # "trans",
    # "trans/event",
    # "trans/event_sys",
    # "trans/sec",
    # "trans/sec_sys",
    # "transCons",
    # "transEvent",
    # "transH/sec",
    # "write",
    # "Dig/sec",
    # "DigBatches/sec",
    # "DigBatchSz",
    # "DigLockUp/sec",
    # "DigPulse/sec",
    # "DigQuDepth",
    # "DigSliceSz",
    # "DigSpans/sec",
    # "DigSubWrkItmTime",
    # "DigWrkTime",
    # "MaxDigBatchSz",
    # "MaxSigBatchSz",
    # "MinDigBatchSz",
    # "MinSigBatchSz",
    # "PlatSigEnqueueTime",
    # "PlatSigExpandTime",
    # "Sig/sec",
    # "SigBatches/sec",
    # "SigBatchSz",
    # "SigIntakeEnqueueTime",
    # "SigIntakeListSize",
    # "SigIntakePulse/sec",
    # "SigIntakePulseTime",
    # "SigIntakeQueueDepth",
    # "SigInval/sec",
    # "SigLockUp/sec",
    # "SigPulse/sec",
    # "SigQuDepth",
    # "SigSliceSz",
    # "SigSpans/sec",
    # "SigSubWrkItmTime",
    # "SigVal/sec",
    # "SigWrkTime",
    # "TtlDig",
    # "TtlSig",
    # "TtlSigInval",
    # "TtlSigVal"
    "cpuLoadSys", "trans/sec", "transH/sec", "lastSeq",
    "q1", "q2", "q3", "q4",
    "transEvent", "transCons", "trans/event", "trans/sec_sys",
    "dupEv%", "sec/sync4", "ev/syncS", "ev/syncR",
    "secC2C", "memFree", "SecC2H", "bytes/sec_sync",
    "fcmCreate", "fcmUpdate", "fcmTransfer", "fcmDelete",
    #    "fileCreate", "fileUpdate", "fileAppend", "fileDelete",
    #    "cryptoTransferRcv", "cryptoTransferSub", "cryptoTransferRcv/sec", "cryptoTransferSub/sec",
    #    "cryptoTransferHdl", "createAccountRcv", "createAccountSub", "createAccountHdl",

    # transactions
    "createAccountHdl",
    "createAccountHdl/sec",
    "createAccountRcv",
    "createAccountRcv/sec",
    "createAccountSub",
    "createAccountSub/sec",
    "updateAccountHdl",
    "updateAccountHdl/sec",
    "updateAccountRcv",
    "updateAccountRcv/sec",
    "updateAccountSub",
    "updateAccountSub/sec",
    "cryptoDeleteHdl",
    "cryptoDeleteHdl/sec",
    "cryptoDeleteRcv",
    "cryptoDeleteRcv/sec",
    "cryptoDeleteSub",
    "cryptoDeleteSub/sec",
    "cryptoTransferHdl",
    "cryptoTransferHdl/sec",
    "cryptoTransferRcv",
    "cryptoTransferRcv/sec",
    "cryptoTransferSub",
    "cryptoTransferSub/sec",
    "cryptoGetBalanceRcv",
    "cryptoGetBalanceRcv/sec",
    "cryptoGetBalanceSub",
    "cryptoGetBalanceSub/sec",

    "createContractHdl",
    "createContractHdl/sec",
    "createContractRcv",
    "createContractRcv/sec",
    "createContractSub",
    "createContractSub/sec",
    "updateContractHdl",
    "updateContractHdl/sec",
    "updateContractRcv",
    "updateContractRcv/sec",
    "updateContractSub",
    "updateContractSub/sec",
    "deleteContractHdl",
    "deleteContractHdl/sec",
    "deleteContractRcv",
    "deleteContractRcv/sec",
    "deleteContractSub",
    "deleteContractSub/sec",

    "createTopicHdl",
    "createTopicHdl/sec",
    "createTopicRcv",
    "createTopicRcv/sec",
    "createTopicSub",
    "createTopicSub/sec",
    "deleteTopicHdl",
    "deleteTopicHdl/sec",
    "deleteTopicRcv",
    "deleteTopicRcv/sec",
    "deleteTopicSub",
    "deleteTopicSub/sec",
    "submitMessageHdl",
    "submitMessageHdl/sec",
    "submitMessageRcv",
    "submitMessageRcv/sec",
    "submitMessageSub",
    "submitMessageSub/sec",

    "updateTopicHdl",
    "updateTopicHdl/sec",
    "updateTopicRcv",
    "updateTopicRcv/sec",
    "updateTopicSub",
    "updateTopicSub/sec",

    "createFileHdl",
    "createFileHdl/sec",
    "createFileRcv",
    "createFileRcv/sec",
    "createFileSub",
    "createFileSub/sec",
    "deleteFileHdl",
    "deleteFileHdl/sec",
    "deleteFileRcv",
    "deleteFileRcv/sec",
    "deleteFileSub",
    "deleteFileSub/sec",
    "updateFileHdl",
    "updateFileHdl/sec",
    "updateFileRcv",
    "updateFileRcv/sec",
    "updateFileSub",
    "updateFileSub/sec",

    # queries

    "getAccountInfoRcv",
    "getAccountInfoRcv/sec",
    "getAccountInfoSub",
    "getAccountInfoSub/sec",
    "getAccountRecordsRcv",
    "getAccountRecordsRcv/sec",
    "getAccountRecordsSub",
    "getAccountRecordsSub/sec",

    "contractCallLocalMethodRcv",
    "contractCallLocalMethodRcv/sec",
    "contractCallLocalMethodSub",
    "contractCallLocalMethodSub/sec",
    "contractCallMethodHdl",
    "contractCallMethodHdl/sec",
    "contractCallMethodRcv",
    "contractCallMethodRcv/sec",
    "contractCallMethodSub",
    "contractCallMethodSub/sec",
    "ContractGetBytecodeRcv",
    "ContractGetBytecodeRcv/sec",
    "ContractGetBytecodeSub",
    "ContractGetBytecodeSub/sec",
    "getBySolidityIDRcv",
    "getBySolidityIDRcv/sec",
    "getBySolidityIDSub",
    "getBySolidityIDSub/sec",
    "getContractInfoRcv",
    "getContractInfoRcv/sec",
    "getContractInfoSub",
    "getContractInfoSub/sec",
    "smartContractSystemDeleteHdl",
    "smartContractSystemDeleteHdl/sec",
    "smartContractSystemDeleteRcv",
    "smartContractSystemDeleteRcv/sec",
    "smartContractSystemDeleteSub",
    "smartContractSystemDeleteSub/sec",
    "smartContractSystemUndeleteHdl",
    "smartContractSystemUndeleteHdl/sec",
    "smartContractSystemUndeleteRcv",
    "smartContractSystemUndeleteRcv/sec",
    "smartContractSystemUndeleteSub",
    "smartContractSystemUndeleteSub/sec",

    "getFileContentRcv",
    "getFileContentRcv/sec",
    "getFileContentSub",
    "getFileContentSub/sec",
    "getFileInfoRcv",
    "getFileInfoRcv/sec",
    "getFileInfoSub",
    "getFileInfoSub/sec",

    "fileSystemDeleteHdl",
    "fileSystemDeleteHdl/sec",
    "fileSystemDeleteRcv",
    "fileSystemDeleteRcv/sec",
    "fileSystemDeleteSub",
    "fileSystemDeleteSub/sec",
    "fileSystemUndeleteHdl",
    "fileSystemUndeleteHdl/sec",
    "fileSystemUndeleteRcv",
    "fileSystemUndeleteRcv/sec",
    "fileSystemUndeleteSub",
    "fileSystemUndeleteSub/sec",
    "appendContentHdl",
    "appendContentHdl/sec",
    "appendContentRcv",
    "appendContentRcv/sec",
    "appendContentSub",
    "appendContentSub/sec",
    "getTopicInfoRcv",
    "getTopicInfoRcv/sec",
    "getTopicInfoSub",
    "getTopicInfoSub/sec",
    "avgHdlSubMsgSize",
    "platformTxnNotCreated/sec",
    "avgAcctLookupRetryAttempts",
    "avgAcctRetryWaitMs",
    "getStakersByAccountIDRcv",
    "getStakersByAccountIDRcv/sec",
    "getStakersByAccountIDSub",
    "getStakersByAccountIDSub/sec",
    "getTransactionReceiptsRcv",
    "getTransactionReceiptsRcv/sec",
    "getTransactionReceiptsSub",
    "getTransactionReceiptsSub/sec",
    "getTxRecordByContractIDRcv",
    "getTxRecordByContractIDRcv/sec",
    "getTxRecordByContractIDSub",
    "getTxRecordByContractIDSub/sec",
    "getTxRecordByTxIDRcv",
    "getTxRecordByTxIDRcv/sec",
    "getTxRecordByTxIDSub",
    "getTxRecordByTxIDSub/sec",
    "recordStreamQueueSize",
    "sigVerifyAsync/sec",
    "sigVerifySync/sec",
    "thresholdRecInState",

    "cEvents/sec", "sigVerifyAsync/sec", "sigVerifySync/sec", "secTransH",
    "expandDoneWaitCount", "expandDoneWaitTime", "zeroSigsCountExpand",
    "zeroSigsCountHandle",
    "threads", "stateSigs", "secStateCopy", "acctLookupRetries/sec",
    "avgAcctLookupRetryAttempts", "avgAcctRetryWaitMs", "fracSyncSlowed"
]

occuurence = {
    "broken": "didn't receive anything from",
}

SWIRLDS_LOG = "swirlds.log"

# use multi thread for scanning
threads = []

use_thread = True

##############################
# command line parsing
##############################

parser = argparse.ArgumentParser()

parser.add_argument('-d', '--dir',
                    help='directory to scan for test result',
                    default='results',
                    dest="directory")

parser.add_argument('-c', '--csv',
                    help='default prefix of csv file to scan for',
                    default='platform',
                    dest="csvPrefix")

parser.add_argument('-S', '--step',
                    help='sampling rate of csv data to reduce number of data points',
                    default='2',
                    type=int,
                    dest="arrayStep")

parser.add_argument('-i', '--invalidState',
                    help='scan invalid state',
                    action='store_true',
                    dest="scanInvalidState")

parser.add_argument('-n', '--nodeid',
                    help='only print graph of node id',
                    default='-1',
                    type=int,
                    dest="nodeid")

parser.add_argument('-g', '--graphOnly',
                    help='only scan csv files and graph it, do not scan swirlds.log',
                    action='store_true',
                    dest="graphOnly")

parser.add_argument('-p', '--pdfOnly',
                    help='no graph gui, only output to pdf ',
                    action='store_true',
                    dest="pdfOnly")

parser.add_argument('-a', '--append',
                    help='append a list of statistic name to be analyzed, will append to default ones',
                    default=[],
                    nargs='+',
                    dest="statNames")

parser.add_argument('-A', '--AUTOSCAN',
                    help='No GUI, scan direcotoreis with prefix and print out summary of swirlds.logs',
                    default='',
                    dest="autoScan")

parser.add_argument('-s', '--startTime',
                    help='start graphing from this UTC timestamp',
                    default='',
                    dest="startTime")

parser.add_argument('-e', '--endTime',
                    help='end graphing after this UTC timestamp',
                    default='',
                    dest="endTime")

PARAMETER = parser.parse_args(sys.argv[1:])

pp.pprint(PARAMETER)

#########################################
# Change default setting according to OS
#########################################
print("OS=" + os.name)
print("Platform=" + platform.system())
if platform.system() == "Linux" and PARAMETER.pdfOnly:
    print("Use smaller font for save PDF on Ubuntu backend mode")
    plt.rcParams.update({'font.size': 5})


#
#
#
def print_occurance():
    for keyword in occuurence:
        if keyword in stat_data_dict.keys():
            print("Occurance of ", keyword)
            # pp.pprint(stat_data_dict[keyword])
            numbers = list(stat_data_dict[keyword].values());
            print(keyword, " max = ", np.max(numbers))
            print(keyword, " min = ", np.min(numbers))
            print(keyword, " mean = ", np.mean(numbers))
            print("\n")


#
# Print multple choices
#
def print_choice(choice_list):
    count = 0
    print('\n\n\n')
    for choice in choice_list:
        print("<%d> %s" % (count, choice))
        count += 1


#
# from a list of choices choose one, if answer is not in range try again
#
def multi_choice(choice_list):
    ans = 0
    while True:
        print_choice(choice_list)
        try:
            ans = int(input('\n Choose a directory ?'))
            if ans not in range(len(choice_list)):
                raise ValueError
            else:
                print("You choosed ", choice_list[ans])
                break;
        except ValueError:

            print("That's not an option!")
    return ans


#
#  Given a path, find the last created file/dir by time
#
def find_last_dirfile(path):
    list_of_files = glob.glob(path + "/*")  # * means all if need specific format then *.csv
    latest_file = max(list_of_files, key=os.path.getctime)
    return latest_file


def extract_nodeid(file_name):
    # strip node id from file anme
    (drive, path) = os.path.splitdrive(file_name)
    (path, base) = os.path.split(path)
    head, tail = os.path.split(path)
    nodeid = tail.replace("node000", "")
    return nodeid


#
# scan swirlds.log for invalid state errors
# sample
# 2019-02-19 23:44:01.549 945773 <       stateHash  7   > Exception: 7 Received invalid state signature! round:4824 memberId:0 details:
#
def scan_invalid_state(file_name):
    nodeid = extract_nodeid(file_name)
    my_dict = {}
    with open(file_name) as f:
        for line in f:
            #  match patte             my_id                                  round number          ohter_id
            m = re.search('Exception: (\d+) Received invalid state signature! round:(\d+) memberId:(\d+)', line)

            if m is not None:
                round_num = m.group(2)
                other_id = m.group(3)
                time_str = line.split()[1]
                if other_id in my_dict:
                    my_dict[other_id]["count"] += 1
                else:
                    my_dict.update({other_id: {
                        "count": 0,
                        "time": time_str,
                        "round": round_num
                    }})

    invalid_state_dict.update({nodeid: my_dict})


#
# given a file, search the key words appears how many times
# insert result in stat_data_dict
#
#  stat_data_dict data format
#
#  {  "stat1" : {
#         "0" : 5
#         "1" : 0
#         "2" : 7
#     }
#     "stat2" : {
#         "0" : 3
#         "1" : 0
#         "2" : 0
#     }
#  }
#
def search_log_file(file_name, search_dict):
    nodeid = extract_nodeid(file_name)
    global stat_data_dict
    try:
        file = open(file_name, 'r').read()
        for keyword in search_dict:
            count = file.count(search_dict[keyword])

            # print("Find %s for %d times in file %s " % (search_dict[keyword], count, file_name))
            if keyword in stat_data_dict.keys():
                stat_data_dict[keyword].update({nodeid: count})
            else:
                stat_data_dict.update({keyword: {nodeid: count}})

            # pp.pprint(stat_data_dict[keyword])

        # search exception
        # with open(file_name, 'r') as searchfile:
        #     print (" ------   File name ", file_name, " ---------")
        #     for line in searchfile:
        #         if "Exception" in line:
        #             if "Connection refused" not in line:
        #                 print(line)

    except (IOError, OSError) as e:
        print("Failed to open file ", file_name, " size too big ? size = ", os.path.getsize(file_name))


#
# given a GC log file, search the key words appears find its time stamp and values
#
#
#  {  "Gc_Pauses" : {
#         "0" : {
#                  "time" : [],
#                  "values" : []
#               }
#         "1" : {
#                  "time" : [],
#                  "values" : []
#               }
#         "2" : {
#                  "time" : [],
#                  "values" : []
#               }
#  }
#
def search_gc_log_file(file_name, search_dict):
    nodeid = extract_nodeid(file_name)
    global gc_log_stat
    try:
        with open(file_name) as search:
            values = []
            times = []
            data_map = {}
            for line in search:
                if "Pause Young (Allocation Failure)" in line and "gc          " in line:
                    replaced = re.sub('[\]\[]', ' ', line)  # replace ']' and '[' with space
                    segments = replaced.split()  # split to columns
                    # pp.pprint ( segments)
                    # print(" first ", segments[0].replace('s', ''), " last ",  segments[-1].replace('ms',''))
                    values.append(float(segments[-1].replace('ms', '')))
                    times.append(float(segments[0].replace('s', '')))

            # print (" size "  ,len(times))
            # print (" size " , len(values))
            data_values = {}
            data_values["time"] = times
            data_values["values"] = values

            # pp.pprint(data_values)

            # time_value.update({nodeid, data_values)
            if CONST_PAUSE in stat_data_dict.keys():
                data_map = stat_data_dict[CONST_PAUSE]
            else:
                stat_data_dict.update({CONST_PAUSE: {}})
                data_map = stat_data_dict[CONST_PAUSE]
            # pp.pprint(data_map)

            data_map[nodeid] = data_values
            stat_data_dict[CONST_PAUSE] = data_map

            # pp.pprint(stat_data_dict)

    except (IOError, OSError) as e:
        print("Failed to open file ", file_name, " size too big ? size = ", os.path.getsize(file_name))


#
# given a file list, search the key words appears how many times
#
def search_log_in_list(file_list, search_dict):
    for file_name in file_list:
        if use_thread:
            t = Thread(target=search_log_file, args=[file_name, search_dict])
            t.start()
            threads.append(t)
        else:
            search_log_file(file_name, search_dict)

        if PARAMETER.scanInvalidState:
            scan_invalid_state(file_name)
        # end for
    if PARAMETER.scanInvalidState:
        pp.pprint(invalid_state_dict)


def search_gc_log_in_list(file_list, search_dict):
    for file_name in file_list:
        if use_thread:
            t = Thread(target=search_gc_log_file, args=[file_name, search_dict])
            t.start()
            threads.append(t)
        else:
            search_gc_log_file(file_name, search_dict)


#
# extrat data from csv file given a stat list
#
def extract_stat_data(file, stat_name_list):
    pos_map = {}
    data_map = {}

    last_row_timestamp = 2 ** 31 - 1
    csv_gap_counter = 0

    # extract node id from *?.csv
    simple_name = os.path.basename(file)
    simple_name = os.path.splitext(simple_name)[0]  # remove csv suffix
    nodeid = simple_name.replace(PARAMETER.csvPrefix, "")  # remove file prefix
    with open(file, 'r') as f:
        reader = csv.reader(f)
        for i, row in enumerate(reader):
            if len(row) > 5:  # only apply start/end time on data lines
                if PARAMETER.startTime or PARAMETER.endTime:
                    isSkipped = False
                    isStopped = False
                    for column in row:
                        if "UTC" in column:
                            if PARAMETER.startTime and column < PARAMETER.startTime:
                                isSkipped = True
                            if PARAMETER.endTime and column > PARAMETER.endTime:
                                isStopped = True
                            break
                    if isStopped:
                        break
                    if isSkipped:
                        continue
            for j, column in enumerate(row):
                try:
                    if column not in (None, "") and j in pos_map:  # this happen more frequently then match stat name
                        # conver true false to number
                        if column == "true":
                            column = "1"
                        elif column == "false":
                            column = "0"

                        # print("add column data ", column, " j ", j, " pos_map ", pos_map.keys())

                        # convert UTC timestamp to epoch time
                        if pos_map[j] == "time":
                            column = column.replace(" UTC", "")
                            column = datetime_from_isformat(column).timestamp()
                            this_row_timestamp = int(column)
                            if (this_row_timestamp - last_row_timestamp) > 5:  # csv raws has big gap
                                csv_gap_counter = csv_gap_counter + 1

                            last_row_timestamp = this_row_timestamp

                        if len(PARAMETER.autoScan):  # only keep last value
                            data_map[pos_map[j]] = [float(column)]
                        else:
                            data_map[pos_map[j]].append(float(column))
                    elif j >= 2 and column in stat_name_list and (
                            column not in pos_map.values()):  # if stat string in list looking for
                        # print( "pos_map" , pos_map)
                        pos_map.update({j: column})  # remember this stat column position
                        data_map.update({column: []})  # add empty list
                        # print("find state ", column, " at ", j)
                except ValueError:
                    print(file, " parsing error, skip ", pos_map[j], i, j, column)
    # insert to stat_data_dict
    for stat in data_map:
        if stat in stat_data_dict:
            # print("insert state ", stat, " with ", data_map[stat])
            stat_data_dict[stat].update({nodeid: data_map[stat]})

    print("file ", file, " has ", csv_gap_counter, " big csv gaps ")


#
# extrat data from csv file list and save in map
#
def extract_list(file_list, stat_name_list):
    # build the empty dict
    for stat in stat_name_list:
        stat_data_dict.update({stat: {}})

    for file_name in file_list:
        if use_thread:
            t = Thread(target=extract_stat_data, args=[file_name, stat_name_list])
            t.start()
            threads.append(t)
        else:
            extract_stat_data(file_name, stat_name_list)


#
# get max, min and average of number array
#
def number_array_min_max_avg(stat_name, numbers):
    if len(numbers) == 0:
        print(stat_name, " is empty")
        return
    print(stat_name, " max = ", np.max(numbers))
    print(stat_name, " min = ", np.min(numbers))
    print(stat_name, " mean = ", np.mean(numbers))
    print("\n")


# Draw graph of the given statistic
#
# stat_name
#       name of statistic
# fig_count
#       index in subplot
#
def draw_subplot(stat_name, fig_count):
    global line_ref
    global nodeid_ref
    LINE_STYLES = ['solid', 'dashed']
    NUM_STYLES = len(LINE_STYLES)
    data_dict = stat_data_dict[stat_name]
    if len(data_dict) > 0:
        row = math.floor(fig_count / fig_per_row)
        column = fig_count % fig_per_row
        sub_axes[row, column].clear()  # clear prev subplot

        # plot keywords count from swirlds.log
        if stat_name in log_search_keys:
            data_list = []
            index = range(len(data_dict.keys()))
            for key in data_dict.keys():
                data_list.append(data_dict[key])
            # data_list = list(data_dict.values())
            # for nodeid, values in data_dict.items():
            #     data_list.append(values)
            sub_axes[row, column].bar(index, data_list)
            sub_axes[row, column].set_xticklabels(data_dict.keys())
            sub_axes[row, column].get_xaxis().set_visible(True)
            sub_axes[row, column].set_xticks(index)
            # pp.pprint(data_dict.keys())
            # pp.pprint(data_list)

        # plot statistci entries from csv
        else:
            # print("Key ", stat_name)
            # pp.pprint(data_dict.keys())
            i = 0
            accumulated_values = []
            line_ref = []
            nodeid_ref = []
            for nodeid, values in data_dict.items():
                if stat_name == CONST_PAUSE:
                    timestamps = data_dict[nodeid]["time"]
                    values = data_dict[nodeid]["values"]
                else:
                    timestamps = stat_data_dict["time"][nodeid]  # epoch seconds
                # pp.pprint(values)
                # pp.pprint(timestamps)
                # print(" nodeid ", nodeid)
                # print(" tiemstamps size ", len(timestamps))
                # print(" values size ", len(values))
                # print(" last timestamp ", timestamps[-1])

                if PARAMETER.nodeid != -1 and PARAMETER.nodeid != int(nodeid):
                    continue

                # if len(accumulated_values) == 0:
                #     accumulated_values = values
                # else:
                #     accumulated_values = [x + y for x, y in zip(accumulated_values, values)]
                accumulated_values = accumulated_values + np.trim_zeros(values)

                if stat_name == CONST_PAUSE:
                    sampled_values = values
                    sampled_time = timestamps
                else:
                    # sample values to reduce data points
                    sampled_values = values[::PARAMETER.arrayStep]
                    sampled_time = timestamps[::PARAMETER.arrayStep]

                xlables_new = [x / 60 for x in sampled_time]  # seconds to epoch minutes

                # trim size if values and timestamp are not same size
                if (len(xlables_new) < len(sampled_values)):
                    sampled_values = sampled_values[:(len(xlables_new) - len(sampled_values))]

                if (len(xlables_new) > len(sampled_values)):
                    xlables_new = xlables_new[:(len(sampled_values) - len(xlables_new))]

                if fig_count == 1:  # only set lable once
                    lines = sub_axes[row, column].plot(xlables_new, sampled_values, label=nodeid)
                else:
                    lines = sub_axes[row, column].plot(xlables_new, sampled_values)
                lines[0].set_linestyle(LINE_STYLES[i % NUM_STYLES])
                # lines[0].set_color(LINE_COLORS[i%NUM_COLORS])
                line_ref.append(lines[0])
                nodeid_ref.append(nodeid)

                # print("Node id ", nodeid, " color ",  lines[0].get_color() , " sytle ", LINE_STYLES[i%NUM_STYLES] )
                i = i + 1
            sub_axes[row, column].set_xlabel('time (min)')
            sub_axes[row, column].get_xaxis().set_visible(True)  # hide x-axis labels

            sub_axes[row, column].legend(line_ref, nodeid_ref)
            number_array_min_max_avg(stat_name, accumulated_values)

        sub_axes[row, column].set_title(stat_name)
    else:
        print("No data for stat_name " + stat_name)


# update subplot on the page
def draw_page(page_num):
    for i in range(fig_per_page):
        index = page_num * fig_per_page + i + 1  # skip the first timestamp
        try:
            if index < total_figures:
                # find stat name by page and index
                stat_key = list(stat_data_dict.keys())[index]
                draw_subplot(stat_key, i)
                sub_axes[math.floor(i / fig_per_row), i % fig_per_row].set_visible(True)
            else:
                # clear the subplot
                sub_axes[math.floor(i / fig_per_row), i % fig_per_row].set_visible(False)
        except IndexError:
            print(" Index error, check default_stat_names may have repeated names ")

    print("-----------------------")


def slider_update(val):
    global current_page
    current_page = int(slider.val)
    # print("Page select ", current_page)
    draw_page(current_page)


def press(event):
    global current_page
    global selected
    global directory_list
    # print('press', event.key)
    if event.key == 'right':
        current_page = (current_page + 1) % num_pages
    elif event.key == 'left':
        if current_page == 0:
            current_page = num_pages - 1
        else:
            current_page = current_page - 1
    elif event.key == 'enter':
        if (selected + 1) < len(directory_list):
            selected += 1
            # plot next graph
            current_page = 0
            read_plot_subdirectory(PARAMETER.directory + '/' + directory_list[selected])
        else:
            print('Last directory already, could not move to next one')
            print('\a')  # beep since this is the last

    else:
        # ignore all other key strokes to avoid redraw graph unnecessariliy
        return

    slider.set_val(current_page)
    slider._colorize(current_page)
    # print ("update page ", current_page)
    # draw_page(current_page)
    # plt.draw()


#
# list subdirectory and ask user to choose one
#
def choose_subdirecotry():
    global selected
    global directory_list
    directory_list = [f for f in os.listdir(PARAMETER.directory) if
                      ((not f.startswith('.')) and os.path.isdir(os.path.join(PARAMETER.directory, f)))]
    directory_list = sorted(directory_list)
    if (len(directory_list) == 1):  # auto select if only one directory available
        selected = 0
    else:
        selected = multi_choice(directory_list)
    return (PARAMETER.directory + '/' + directory_list[selected])


#
#  found subdiretories started with the prefix
#
def find_prefix_dir(prefix):
    directory_list = [f for f in os.listdir(PARAMETER.directory) if
                      ((f.startswith(prefix)) and os.path.isdir(os.path.join(PARAMETER.directory, f)))]
    directory_list = sorted(directory_list)
    return directory_list


#
# scan platform stat csv fils given a directory
#
def scan_csv_files(directory):
    # find a list of csv files
    csv_file_list = []
    for filename in glob.iglob(directory + '/**/' + PARAMETER.csvPrefix + '*.csv', recursive=True):
        csv_file_list.append(filename)

    extract_list(csv_file_list, default_stat_names)


#
# scan Gargage collector logs
#
def scan_gc_logs(directory):
    # find a list of gc log files
    gc_log_list = []
    for filename in glob.iglob(directory + '/**/' + 'myapp-gc.log', recursive=True):
        gc_log_list.append(filename)
    if (len(gc_log_list) > 0):
        search_gc_log_in_list(gc_log_list, log_search_keys)
    else:
        print("No gc og found")


#
# scan log files given a directory
#
def scan_swirlds_log(directory):
    # find a list of swirlds.log files
    log_file_list = []
    for filename in glob.iglob(directory + '/**/' + SWIRLDS_LOG, recursive=True):
        log_file_list.append(filename)
    if (len(log_file_list) > 0):
        search_log_in_list(log_file_list, log_search_keys)
    else:
        print("No swirlds.log found")


#
# clear global data
#
def clear_data():
    global stat_data_dict, invalid_state_dict, threads
    stat_data_dict = {}
    invalid_state_dict = {}
    threads = []


#
#  list subdirectoreis under target directory
#  and prompt user to select one subdirectory
#  then scan csv and swirlds.log under the
#  selected subdirectory and save data
#  in global variable stat_data_dict
#
def scan_csv_and_logs():
    global stat_data_dict
    global choose_directory

    if len(PARAMETER.autoScan) > 0:
        # auto scan mode to scan all subdirectories with defined prefix and
        # print out summary
        direcotry_list = find_prefix_dir(PARAMETER.autoScan)
        for direcotry in direcotry_list:
            print('------------------------    %s   ------------------------' % direcotry)
            scan_swirlds_log(PARAMETER.directory + '/' + direcotry)
            scan_csv_files(PARAMETER.directory + '/' + direcotry)
            for x in threads:
                x.join()

            pp.pprint(stat_data_dict)
            clear_data()
    else:
        choose_directory = choose_subdirecotry()
        print("The directory to search for csv files is %s" % (choose_directory))
        scan_csv_files(choose_directory)

        scan_gc_logs(choose_directory)

        if PARAMETER.graphOnly == False:
            scan_swirlds_log(choose_directory)

        # Wait for all of them to finish
        for x in threads:
            x.join()

        remove_empty_data()

        # timestamp alignment()
        align_time_stamp()


#
# press enter key to read and plot next directory
#
def read_plot_subdirectory(subdirectory):
    global fig
    clear_data()

    print("The directory to search for csv files is %s" % (subdirectory))
    scan_csv_files(subdirectory)

    scan_gc_logs(choose_directory)

    if PARAMETER.graphOnly == False:
        scan_swirlds_log(subdirectory)

    # Wait for all of them to finish
    for x in threads:
        x.join()

    remove_empty_data()

    # timestamp alignment()
    align_time_stamp()

    fig.suptitle(subdirectory, fontsize=16)
    draw_page(0)
    print_occurance()


#
#  Figure window create, init and event handling
#  draw first page and show figure
#
def graphing():
    global sub_axes
    global slider
    global choose_directory
    global fig

    # divide fiugre window into multple subplot
    fig, sub_axes = plt.subplots(row_per_page, fig_per_row)
    fig.subplots_adjust(bottom=0.18)

    if PARAMETER.pdfOnly:
        fig.canvas.manager.full_screen_toggle()  # toggle fullscreen mode
        with PdfPages('multipage_pdf.pdf') as pdf:
            for i in range(0, num_pages):
                draw_page(i)
                pdf.savefig()
    else:
        # draw a slider for pages
        ax_slider = fig.add_axes([0.1, 0.05, 0.8, 0.04])
        slider = PageSlider(ax_slider, 'Page', num_pages, activecolor="orange")
        slider.on_changed(slider_update)

        # keyboard event handling
        fig.canvas.mpl_connect('key_press_event', press)
        fig.suptitle(choose_directory, fontsize=16)

        # show first page and legend
        draw_page(0)
        # plt.figlegend(shadow=True, fancybox=True)
        fig.canvas.manager.full_screen_toggle()  # toggle fullscreen mode

        plt.show()


def remove_empty_data():
    remove = [key for key in stat_data_dict if len(stat_data_dict[key]) == 0]
    for k in remove:
        print("empty ", k)
        del stat_data_dict[k]


#
# each csv row has different timestamp
# this function try to align timestamp of csv files from all nodes
#
def align_time_stamp():
    timestamps = stat_data_dict["time"]  # epoch seconds
    # find the min UTC timestamp among all nodes by comparing the first
    # timestamp of all nodes
    min = 2 ** 31 - 1
    for nodeid, values in timestamps.items():
        try:
            first_value = values[0]
            if first_value < min:
                min = first_value
            print(" last UTC time string ", values[-1])
        except IndexError:  #handle sometimes truncated CSV files
            pp.pprint(values)


    print(" find min UTC time ", min)

    # all timestamp values minus this min value
    for nodeid, values in timestamps.items():
        values[:] = [x - min for x in values]
        timestamps[nodeid] = values

    # update stat dictonary
    stat_data_dict["time"] = timestamps


############################################################
#   main
############################################################


# 1) global variable initialization

#  data structure
# invalid_state_dict{
#     "0":{
#         "1" : {
#             "count" : 10,
#             "time"  : xxxx
#         "2" :
#     }
#     "1":{
#         "1" :
#         "2" :
#     }
# }
invalid_state_dict = {}  # savd invalid_state occurrences

#  stat_data_dict data format
#
#  {  "stat1" : {
#         "0" : [ ]
#         "1" : [ ]
#         "2" : [ ]
#     }
#     "stat2" : {
#         "0" : [ ]
#         "1" : [ ]
#         "2" : [ ]
#     }
#  }

# keep the dictioary sorted as the order of insertion or creation
stat_data_dict = collections.OrderedDict()

default_stat_names = default_stat_names + PARAMETER.statNames

fig_per_row = 2
row_per_page = 2
fig_per_page = fig_per_row * row_per_page

#  2) extract data

scan_csv_and_logs()

# stat_data_dict contains both csv stat names and log key errors
total_figures = len(stat_data_dict) - 1  # not including timestamp

num_pages = math.ceil(total_figures / fig_per_page)
current_page = 0

print_occurance()

#  3) draw graphs if not auto scan mode
if len(PARAMETER.autoScan) == 0:
    graphing()
