import re
import sys
import os
import json
import argparse
from collections import OrderedDict

class TestJobResult:
    def __init__(self,  status, job_url, step_failed):
        self.job_url = job_url
        self.status = status
        self.step_failed = step_failed

    def summarize_test():
        return ''

    def is_successful():
        return self.status == 'Passed'



workflow_conf = 'nightly-regression-config.json'
summary_report = 'nightly-regression-report.txt'

job_type_success_criteria = OrderedDict()
exp_ordered_job_type = OrderedDict()

each_job_status = OrderedDict()

# This function read the config for the workflow to validate
def init_workflow_conf(config_file):
    global exp_ordered_job_type
    with open(os.path.join(os.environ.get("REPO"), ".circleci/scripts/resources", workflow_conf), "r") as f:
        data = json.load(f)
        exp_ordered_job_type = data["ordered-job-types"]
    print(exp_ordered_job_type)


# For each job that needs more detailed validation and reporting, this function read the information that
# can used to validate the failure or success of the steps to be validated.
def init_jobs():

    with open(os.path.join(os.environ.get("REPO"), ".circleci/scripts/resources/build-artifact-success-pattern.json"), "r") as f:
        data = json.load(f)
        build_artifact_success = data["build-artifact-success-pattern"]
        job_type_success_criteria["build-artifact"] = build_artifact_success

    with open(os.path.join(os.environ.get("REPO"), ".circleci/scripts/resources/target-testnet-success-pattern.json"), "r") as f:
        data = json.load(f)
        target_testnet_success = data["target-testnet-success-pattern"]
        job_type_success_criteria["regression-target-testnet"] = target_testnet_success

    with open(os.path.join(os.environ.get("REPO"), ".circleci/scripts/resources/umbrella-redux-success-pattern.json"), "r") as f:
        data = json.load(f)
        umbrella_redux_success = data["umbrella-redux-success-pattern"]

        job_type_success_criteria["long-contract_txns6"] = umbrella_redux_success
        job_type_success_criteria["long-crypto_txns300"] = umbrella_redux_success
        job_type_success_criteria["long-file_ops"] = umbrella_redux_success
        job_type_success_criteria["long-consensus_ops"] = umbrella_redux_success

        job_type_success_criteria["short-contract_txns6"] = umbrella_redux_success
        job_type_success_criteria["short-crypto_txns300"] = umbrella_redux_success
        job_type_success_criteria["short-file_ops"] = umbrella_redux_success
        job_type_success_criteria["short-consensus_ops"] = umbrella_redux_success

    with open(os.path.join(os.environ.get("REPO"), ".circleci/scripts/resources/validate-freeze-success-pattern.json"), "r") as f:
        data = json.load(f)
        validate_freeze_success = data["validate-freeze-success-pattern"]

        job_type_success_criteria["regression-validate-freeze"] = validate_freeze_success

    with open(os.path.join(os.environ.get("REPO"), ".circleci/scripts/resources/umbrella-test-success-pattern.json"), "r") as f:
        data = json.load(f)
        umbrella_test_success = data["umbrella-test-success-pattern"]

        job_type_success_criteria["long-umbrella"] = umbrella_test_success
        job_type_success_criteria["short-umbrella"] = umbrella_test_success

    with open(os.path.join(os.environ.get("REPO"), ".circleci/scripts/resources/accessory-tests-success-pattern.json"), "r") as f:
        data = json.load(f)
        accessory_tests_success = data["accessory-tests-success-pattern"]

        job_type_success_criteria["regression-run-accessory-tests"] = accessory_tests_success
        job_type_success_criteria["rerun-accessory-tests"] = accessory_tests_success
        job_type_success_criteria["run-accessory-tests"] = accessory_tests_success

    with open(os.path.join(os.environ.get("REPO"), ".circleci/scripts/resources/update-feature-success-pattern.json"), "r") as f:
        data = json.load(f)
        update_feature_success = data["update-feature"]

        job_type_success_criteria["update-feature"] = update_feature_success

    with open(os.path.join(os.environ.get("REPO"), ".circleci/scripts/resources/update-jar-files-success-pattern.json"), "r") as f:
        data = json.load(f)
        update_jar_files_success = data["update-jar-files"]

        job_type_success_criteria["update-jar-files"] = update_jar_files_success

# The following two steps will be opened when they platform side stats validations are available.
    # with open(os.path.join(os.environ.get("REPO"), ".circleci/scripts/resources/validate_server_stat_1_success_pattern.json"), "r") as f:
    #     data = json.load(f)
    #     validate_server_stat_1_success = data["validate_server_stat_1_success_pattern"]

    #     job_type_success_criteria["validate-server-stat-1"] = validate_server_stat_1_success

    # with open(os.path.join(os.environ.get("REPO"), ".circleci/scripts/resources/validate_server_stat_2_success_pattern.json"), "r") as f:
    #     data = json.load(f)
    #     validate_server_stat_2_success = data["validate_server_stat_2_success_pattern"]

    #     job_type_success_criteria["validate-server-stat-2"] = validate_server_stat_2_success


# This function checks the log message (pattern) for the critical steps of job and make decision whether this
# job should be marked as failure or not. And if it fails, remember where it fails for reporting purpose.
def circleci_job_succeeded(log_file, success_criteria, job_type, job):
    status = "Passed"
    job_url = ''
    step_failed = None

    with open(log_file, 'r') as f:
        log_contents = f.read()
        if success_criteria is None:
            status = "Passed"    # empty criteria means anything is fine or should it mean False for missing success criteria?
        for step, pattern in success_criteria.items():
            r = re.compile(pattern)
            if not r.search(log_contents):
                status = "FAILED"
                step_failed = step
                break
        job_url_line = re.search(r'current circleci build URL: (https://circleci.com/gh/hashgraph/hedera-services/\d+)', log_contents)
        job_url = job_url_line.group(1)
        print("Job {0} URL: {1}".format(job, job_url))

    job_result = TestJobResult(status, job_url, step_failed)

    return job_result

# This method get all the info collected and generated a tabular formatted slack message for reporting.
# Right now, we can only reach job level URI due to limitation of CircleCi's.
# We need to find a way to reach step level so users can directly jump to the exact places
# where it fails. Otherwise, it's annoying to click through the circle's link if the job has multiple steps.
def report_regression_status(overall_status):
    full_report_path = os.path.join(
        os.environ.get("REPO"), 'client-logs', summary_report)
    print("Test report file: {}".format(full_report_path))
    with open(full_report_path, 'w+') as f:
        f.writelines(
            '```================== THIS REGRESSION TEST REPORT ===================\n')

        f.writelines(' Overall Status: {}\n'.format(overall_status))
        f.writelines(
            '\n---------------- ITEMIZED REPORT --------------------\n')
        f.writelines("{0:30s}{1:4s}{2:8s}  {3:33s}\n".format(
            "TEST JOB NAME", "    ", "STATUS", "WHERE IT FAILED"))
        for key, value in each_job_status.items():
            if value.status == 'Passed':
                status = "Passed"
                flag = "    "
            elif value.status == 'FAILED':
                status = "*FAILED*"
                flag = " ** "
            else:
                status = "No Run"
                flag = " -- "

            fixed_len_key = "<{0}|{1}>".format(value.job_url, key)
            if value.status == 'Not Run':
                f.writelines("{0:30s}{1:4s}{2:8s}\n".format(key, flag, status))
            elif value.step_failed is not None:
                f.writelines("{0:30s}{1:4s}<{2}|{3:8s}>  <{2}|{4:33s}>\n".format(key, flag, value.job_url, status, value.step_failed[0:32]))
            else:
                f.writelines("{0:30s}{1:4s}<{2}|{3:8s}>\n".format(key, flag, value.job_url, status))

        f.writelines('================== END REPORT ===================```\n')

# This AWS instance IPes aren't available till it's allocated. This function
# will collect the dynamic info for rebuilding the patterns to be validated.
def get_AWS_testnet_IPs(log_file):
    IPs = []
    with open(log_file, "r") as f:
        data = f.read()

        match = re.search("PLAY RECAP \*+\n((.*\n){4})", data)
        IP_lines = [line for line in match.group(
            1).split(sep='\n') if len(line) > 0]
        for line in IP_lines:
            IP = re.search('(\d+\.\d+\.\d+\.\d+)[ \t]+:[ \t]+ok=\d+.*', line)
            IPs.append(IP.group(1))

    return IPs


# Rebuild the messages to be validated based on dynamic IP info. Please refer to
# function get_AWS_testnet_IPs(...) for more info.
def rebuild_success_criteria(success_criteria, log_file):
    IPs = get_AWS_testnet_IPs(log_file)
    new_criteria = OrderedDict()
    for IP in IPs:
        key = ("Start node {}".format(IP))
        value = "{0}{1}".format(IP, success_criteria['Start testnet'])
        new_criteria[key] = value

    return new_criteria


# This and following classes are for extending validation for more detailed requirements.
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

# The main method to validate and report the build results.
if __name__ == '__main__':
    parser = argparse.ArgumentParser()
    parser.add_argument('-w', '--workflow',
                        help='Workflow name. The workflow config file will be ${workflow}-conf.json',
                        default='nightly-regression',
                        dest='workflow')

    parser.add_argument('-p', '--log_path',
                        help='Parent log path',
                        default=os.path.join(os.environ.get("REPO"),"client-logs"),
                        dest='log_path')

    args = parser.parse_args(sys.argv[1:])
    if (args.workflow):
        workflow = args.workflow
        workflow_conf = workflow + "-conf.json"
        summary_report = workflow + "-report.txt"

    print("Summarize the test results for workflow {}".format(workflow))
    print("log_path {}".format(args.log_path))

    init_workflow_conf(workflow_conf)
    init_jobs()

    print("exp_ordered_job_types: ")
    print(exp_ordered_job_type)

    overall_status = 'Passed'

    if os.path.exists(args.log_path):
        log_parent_path = args.log_path
        child_log_paths = [os.path.basename(os.path.normpath(child_log_path[0]))
                           for child_log_path in os.walk(log_parent_path)]

        # Here we expect the jobs to follow the sequence of the workflow
        for key, value in exp_ordered_job_type.items():
            job_type = key
            job_type_must_pass = value
            print("The job is '{0}' and it must pass: {1}".format(job_type, job_type_must_pass))
            r = re.compile(".*" + job_type + ".*")
            jobs = [job for job in child_log_paths if r.match(job)]
            # Certain jobs may be optional for some worflows.
            if len(jobs) >= 1:
                for job in jobs:
                    log_file = os.path.join(
                        log_parent_path, job, "hapi-client.log")
                    success_criteria = job_type_success_criteria.get(
                        job_type)

                    if job_type == "regression-target-testnet":
                        success_criteria = rebuild_success_criteria(
                            success_criteria, log_file)

                    current_job_result = circleci_job_succeeded(
                        log_file, success_criteria, job_type, job)
                    each_job_status[job] = current_job_result
                    if not current_job_result.status == 'Passed':
                        print("Job {} failed.".format(job))
                        if job_type_must_pass:
                            overall_status = 'Failed'
                        elif overall_status == 'Passed':
                            overall_status = 'Passed with error'
                    else:
                        print("Job {} succeeded.".format(job))

            else:
                print("Job {} not run.".format(job_type))
                each_job_status[job_type] = TestJobResult("Not Run", '',None)
                if job_type_must_pass:
                    overall_status = 'Failed'
                elif overall_status == 'Passed':
                    overall_status = 'Passed with error'

    report_regression_status(overall_status)
