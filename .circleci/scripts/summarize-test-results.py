import re
import sys
import os
import json
from collections import OrderedDict

class TestJobResult:
    def __init__(self,  status, job_url):
        self.job_url = job_url
        self.status = status

    def summarize_test():
        return ''

    def is_successful():
        return self.status




REGRESSION_JOB_TYPE_SUCCESS_CRITERIA = {}

EXPECTED_ORDERED_REGRESSION_JOB_TYPE = [
    "build-artifact",
    "regression-target-testnet",
    "long-contract_txns6",
    "long-crypto_txns300",
    "long-file_ops",
    "long-consensus_ops",
    "long-umbrella",
    "regression-validate-freeze",
#    "validate-server-stat-1",
    "short-contract_txns6",
    "short-crypto_txns300",
    "short-file_ops",
    "short-consensus_ops",
    "short-umbrella",
    "regression-run-accessory-tests",
#    "validate-server-stat-2",
]


REGRESSION_JOB_TYPE_MUST_PASS = {
    "build-artifact": True,
    "regression-target-testnet": True,
    "long-contract_txns6": False,
    "long-crypto_txns300": False,
    "long-file_ops": False,
    "long-consensus_ops": False,
    "long-umbrella": False,
    "regression-validate-freeze": True,
#   "validate-server-stat-1": False,   # for test only. Should be True
    "short-contract_txns6": False,
    "short-crypto_txns300": False,
    "short-file_ops": False,
    "short-consensus_ops": False,
    "short-umbrella": False,
    "regression-run-accessory-tests": True,
#    "validate-server-stat-2": False,   # for test only. Should be True
}


SUMMARY_REPORT_FILE = 'client-logs/regression-test-summary.txt'
each_job_status = OrderedDict()


def init():
    # global EXPECTED_ORDERED_REGRESSION_JOB_TYPE
    # with open(os.path.join(os.environ.get("REPO"), ".circleci/scripts/resources/expected_ordered_regression_job_type.json"), "r") as f:
    #     data = json.load(f)
    #     EXPECTED_ORDERED_REGRESSION_JOB_TYPE = data["expected_ordered_regression_job_type"]

    with open(os.path.join(os.environ.get("REPO"), ".circleci/scripts/resources/build_artifact_success_pattern.json"), "r") as f:
        data = json.load(f)
        build_artifact_success = data["build_artifact_success_pattern"]
#        print("build_artifact_success_pattern: {}".format(build_artifact_success))
        REGRESSION_JOB_TYPE_SUCCESS_CRITERIA["build-artifact"] = build_artifact_success

    with open(os.path.join(os.environ.get("REPO"), ".circleci/scripts/resources/target_testnet_success_pattern.json"), "r") as f:
        data = json.load(f)
        target_testnet_success = data["target_testnet_success_pattern"]
#        print("target_testnet_success_pattern: {}".format(target_testnet_success))
        REGRESSION_JOB_TYPE_SUCCESS_CRITERIA["regression-target-testnet"] = target_testnet_success

    with open(os.path.join(os.environ.get("REPO"), ".circleci/scripts/resources/umbrella_redux_success_pattern.json"), "r") as f:
        data = json.load(f)
        umbrella_redux_success = data["umbrella_redux_success_pattern"]

        REGRESSION_JOB_TYPE_SUCCESS_CRITERIA["long-contract_txns6"] = umbrella_redux_success
        REGRESSION_JOB_TYPE_SUCCESS_CRITERIA["long-crypto_txns300"] = umbrella_redux_success
        REGRESSION_JOB_TYPE_SUCCESS_CRITERIA["long-file_ops"] = umbrella_redux_success
        REGRESSION_JOB_TYPE_SUCCESS_CRITERIA["long-consensus_ops"] = umbrella_redux_success

        REGRESSION_JOB_TYPE_SUCCESS_CRITERIA["short-contract_txns6"] = umbrella_redux_success
        REGRESSION_JOB_TYPE_SUCCESS_CRITERIA["short-crypto_txns300"] = umbrella_redux_success
        REGRESSION_JOB_TYPE_SUCCESS_CRITERIA["short-file_ops"] = umbrella_redux_success
        REGRESSION_JOB_TYPE_SUCCESS_CRITERIA["short-consensus_ops"] = umbrella_redux_success

    with open(os.path.join(os.environ.get("REPO"), ".circleci/scripts/resources/validate_freeze_success_pattern.json"), "r") as f:
        data = json.load(f)
        validate_freeze_success = data["validate_freeze_success_pattern"]

        REGRESSION_JOB_TYPE_SUCCESS_CRITERIA["regression-validate-freeze"] = validate_freeze_success

    with open(os.path.join(os.environ.get("REPO"), ".circleci/scripts/resources/umbrella_test_success_pattern.json"), "r") as f:
        data = json.load(f)
        umbrella_test_success = data["umbrella_test_success_pattern"]

        REGRESSION_JOB_TYPE_SUCCESS_CRITERIA["long-umbrella"] = umbrella_test_success
        REGRESSION_JOB_TYPE_SUCCESS_CRITERIA["short-umbrella"] = umbrella_test_success

    with open(os.path.join(os.environ.get("REPO"), ".circleci/scripts/resources/accessory_tests_success_pattern.json"), "r") as f:
        data = json.load(f)
        accessory_tests_success = data["accessory_tests_success_pattern"]

        REGRESSION_JOB_TYPE_SUCCESS_CRITERIA["regression-run-accessory-tests"] = accessory_tests_success

    # with open(os.path.join(os.environ.get("REPO"), ".circleci/scripts/resources/validate_server_stat_1_success_pattern.json"), "r") as f:
    #     data = json.load(f)
    #     validate_server_stat_1_success = data["validate_server_stat_1_success_pattern"]

    #     REGRESSION_JOB_TYPE_SUCCESS_CRITERIA["validate-server-stat-1"] = validate_server_stat_1_success

    # with open(os.path.join(os.environ.get("REPO"), ".circleci/scripts/resources/validate_server_stat_2_success_pattern.json"), "r") as f:
    #     data = json.load(f)
    #     validate_server_stat_2_success = data["validate_server_stat_2_success_pattern"]

    #     REGRESSION_JOB_TYPE_SUCCESS_CRITERIA["validate-server-stat-2"] = validate_server_stat_2_success


def circleci_job_succeeded(log_file, success_criteria, job_type, job):
    status = True
    job_url = ''

    with open(log_file, 'r') as f:
        log_contents = f.read()
        if success_criteria is None:
            status = True    # empty criteria means anything is fine or should it mean False for missing success criteria?
        for i in success_criteria:
            #            print("Current pattern to be matched: {}".format(i))
            r = re.compile(i)
            if not r.search(log_contents):
#                print("Expected message '{}' for job \"{}\" not found.".format(i, job))
                status = False
                break
        job_url_line = re.search(r'current circleci build URL: (https://circleci.com/gh/hashgraph/hedera-services/\d+)', log_contents)
        job_url = job_url_line.group(1)
        print("Job {0} URL: {1}".format(job, job_url))

    job_result = TestJobResult(status, job_url)

    return job_result


def report_regression_status(overall_status):
    full_report_path = os.path.join(
        os.environ.get("REPO"), SUMMARY_REPORT_FILE)
    with open(full_report_path, 'w+') as f:
        f.writelines(
            '================== THIS REGRESSION TEST REPORT ===================\n')

        f.writelines(' Overall Status: {}\n'.format(overall_status))
        f.writelines(
            '\n---------------- ITEMIZED REPORT --------------------\n')
        f.writelines("{0:28s}\t{1:4s}{2:8s}  {3:30s}\n".format(
            "TEST JOB NAME", "    ", "STATUS", "TEST JOB URL"))
        for key, value in each_job_status.items():
            if value.status:
                status = "Passed"
                flag = "    "
            elif not value.status:
                status = "FAILED"
                flag = " ** "
            else:
                status = "Not run"
                flag = " ** "

            f.writelines("{0:28s}\t{1:4s}{2:8s}  {3:30s}\n".format(key, flag, status, value.job_url))

        f.writelines('================== END REPORT ===================\n')


def get_AWS_testnet_IPs(log_file):
    IPs = []
    with open(log_file, "r") as f:
        data = f.read()

        match = re.search("PLAY RECAP \*+\n((.*\n){4})", data)
        IP_lines = [line for line in match.group(
            1).split(sep='\n') if len(line) > 0]
        IP_PATTERN = re.compile(r'\d+[.]\d+[.]\d+[.]\d+')
        for line in IP_lines:
            IP = re.search('(\d+\.\d+\.\d+\.\d+)[ \t]+:[ \t]+ok=\d+.*', line)
            IPs.append(IP.group(1))

    return IPs


def rebuild_success_criteria(success_criteria, log_file):
    IPs = get_AWS_testnet_IPs(log_file)
    new_criteria = []
    for IP in IPs:
        new_criteria.append(IP + success_criteria[0])

    return new_criteria


class CircleCiJob:
    def __init__(self, job_name, log_file, report_file):
        self.job_name = job_name
        self.log_file = log_file
        self.report_file = report_file
        self.status = 'Pass'

    def append_to_report():
        pass

    def summarize_test():
        return ''

    def is_successful():
        return self.status


class BuildArtifactJob(CircleCiJob):
    pass
    # def summarize_test():
    #     with open(self.log_file) as f:
    #         for line in f.readlines():


class TestnetJob(CircleCiJob):
    pass


class ContractTestJob(CircleCiJob):
    pass


class RestartTestJob(CircleCiJob):
    pass


class CryptoTestJob(CircleCiJob):
    pass


class FileOpTestJob(CircleCiJob):
    pass


class UmbrellaTestJob(CircleCiJob):
    pass


class AccessoryTestJob(CircleCiJob):
    pass


log_parent_path = '.'

if __name__ == '__main__':
    print("Summarize the test results of this workflow...")

    init()

    overall_status = 'Passed'

    if sys.argv[1] and os.path.exists(sys.argv[1]):
        log_parent_path = sys.argv[1]
        child_log_paths = [os.path.basename(os.path.normpath(child_log_path[0]))
                           for child_log_path in os.walk(log_parent_path)]

        for job_type in EXPECTED_ORDERED_REGRESSION_JOB_TYPE:
            r = re.compile(".*" + job_type + ".*")
            jobs = [job for job in child_log_paths if r.match(job)]
            if len(jobs) >= 1:
                for job in jobs:
                    log_file = os.path.join(
                        log_parent_path, job, "hapi-client.log")
                    success_criteria = REGRESSION_JOB_TYPE_SUCCESS_CRITERIA.get(
                        job_type)

                    if job_type == "regression-target-testnet":
                        success_criteria = rebuild_success_criteria(
                            success_criteria, log_file)

                    current_job_result = circleci_job_succeeded(
                        log_file, success_criteria, job_type, job)
                    each_job_status[job] = current_job_result
                    if not current_job_result.status:
                        print("Job {} failed.".format(job))
                        if REGRESSION_JOB_TYPE_MUST_PASS[job_type]:
                            overall_status = 'Failed'
                        elif overall_status == 'Passed':
                            overall_status = 'Passed with error'
                    else:
                        print("Job {} succeeded.".format(job))

            else:
                print("Job {} not run.".format(job_type))
                each_job_status[job_type] = TestJobResult(None, '')
                if REGRESSION_JOB_TYPE_MUST_PASS[job_type]:
                    overall_status = 'Failed'
                elif overall_status == 'Passed':
                    overall_status = 'Passed with error'

    report_regression_status(overall_status)
