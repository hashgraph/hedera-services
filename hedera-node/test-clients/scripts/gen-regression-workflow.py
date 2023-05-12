import os
import re
import cmd
import sys
import itertools

CONFIG_YML_JOBS_PATH_TPL = '../.circleci/config-{}-jobs.yml-excerpt'
CONFIG_YML_WORKFLOW_PATH_TPL = '../.circleci/config-{}-workflow.yml-excerpt'
PROPS_BASE_DIR = './src/main/resource/eet-config'
PROFILE_MATCH = 'regression-(.*)[.]properties'
PROPS_TPL = './src/main/resource/eet-config/regression-{}.properties'

DURATION_UNIT = 'MINUTES'
DURATIONS = {
    'short': 17,
    'long': 85
}

def props_for(profile, duration):
    props_file = 'regression-{}.properties'.format(profile)
    with open(PROPS_TPL.format(profile), 'r') as f:
        lines = f.readlines()
        extra_props = lines[1].split()[1]
        return 'duration={},unit={},props={},{}'.format(
                duration, DURATION_UNIT, props_file, extra_props)

LONG_RUN_TPL = '''\
      - {}-{}-{}:
          requires:
            - regression-target-testnet
'''
LONG_UMBRELLA_RUN = '''\
      - long-umbrella:
          requires:
            - regression-target-testnet
'''
SHORT_RUN_TPL = '''\
      - {}-{}-{}:
          requires:
            - regression-validate-freeze
'''
SHORT_UMBRELLA_RUN = '''\
      - short-umbrella:
          requires:
            - regression-validate-freeze
'''
VALIDATE_FREEZE_HEADER = '''\
      - regression-validate-freeze:
          requires:
'''
ACCESSORY_RUN_HEADER = '''\
      - regression-run-accessory-tests:
          requires:
'''
REQUIREMENT_TPL = '''\
            - {}
'''
JOB_NAME_TPL = '{}-{}-{}'
JOB_TPL = '''  {}-{}-{}:
    executor:
      name: ci-test-executor
      tf_dir: "/infrastructure/terraform/deployments/aws-4-node-psql-swirlds"
      tf_workspace: "net-dev"
      use_existing_network: "1"
    steps:
      - attach_workspace:
          at: /
      - run-eet-suites:
          dsl-args: "UmbrellaRedux"
          ci-properties-map: "{}"
          node-terraform-index: {}
          node-account: {}
          perf-run: true
'''
UMBRELLA_JOB_TPL = '''  {}-umbrella:
    executor:
      name: ci-test-executor
      tf_dir: "/infrastructure/terraform/deployments/aws-4-node-psql-swirlds"
      tf_workspace: "net-dev"
      use_existing_network: "1"
    steps:
      - attach_workspace:
          at: /
      - run:
          name: Set log level to WARN
          command: /repo/.circleci/scripts/config-log4j-4reg.sh
      - run-umbrella-test:
          config-file: umbrella.properties.{}
'''
UMBRELLA_SUFFIXES = {
    'short': 'altHalfSizeOCRegPostFreeze',
    'long': 'altHalfSizeOCRegPreFreeze'
}

class RegressionWorkflowShell(cmd.Cmd):
    intro = 'Interactive construction of regression workflow from client profiles. Type help of ? to list commands.'
    prompt = '(workflow) '
    profile_counts = {}
    include_legacy_umbrella = True

    def do_umbrella(self, args):
        'Turn the legacy UmbrellaTest ON/OFF'
        try:
            self.include_legacy_umbrella = (True, False)[('ON', 'OFF').index(args)]
        except:
            print("Oops! USAGE: umbrella {ON | OFF}")

    def do_include(self, args):
        'Include a given number of parallel copies of a client profile'
        try:
            split_args = args.split()
            if len(split_args) == 1:
                split_args.append('1')
            profile, n_parallel = split_args
            self.profile_counts[profile] = int(n_parallel)
        except:
            print("Oops! USAGE: include <profile> <num-copies>")
    def complete_include(self, text, line, begin, end):
        avail_props = [p for p in next(os.walk(PROPS_BASE_DIR))[2] if p.endswith('.properties')]
        avail_profiles = [re.match(PROFILE_MATCH, p).group(1) for p in avail_props]
        if not text:
            return avail_profiles
        else:
            return list(filter(lambda profile: profile.startswith(text), avail_profiles))

    def do_summary(self, args):
        'Summarize the parallelism of the profiles mentioned so far'
        MAX_NAME_LEN = len('umbrella')
        if self.profile_counts:
            MAX_NAME_LEN = max(max(map(len, self.profile_counts.keys())), MAX_NAME_LEN)
            for k, v in self.profile_counts.items():
                print('{:<{width}} :: {}'.format(k, v, width=MAX_NAME_LEN))
        if self.include_legacy_umbrella:
            print('{:<{width}} :: {}'.format('umbrella', 1, width=MAX_NAME_LEN))

    def do_gen(self, args):
        'Generate a tagged pair of config.yml excerpts for the configured workflow'
        tag = args or 'misc'
        terraform_indexes = [0, 1, 2, 3]
        node_accounts = [3, 4, 5, 6]
        node_i = 0
        # First the jobs excerpt
        with open(CONFIG_YML_JOBS_PATH_TPL.format(tag), 'w') as f:
            if self.include_legacy_umbrella:
                for length in ('short', 'long'):
                    job = UMBRELLA_JOB_TPL.format(length, UMBRELLA_SUFFIXES[length])
                    f.write(job)
            for profile, count in self.profile_counts.items():
                for length in ('short', 'long'):
                    ci_props_map = props_for(profile, DURATIONS[length])
                    for i in range(count):
                        job = JOB_TPL.format(
                                length,
                                profile,
                                i,
                                ci_props_map,
                                terraform_indexes[node_i],
                                node_accounts[node_i])
                        node_i = (node_i + 1) % 4
                        f.write(job)
        # Next the workflow excerpt
        with open(CONFIG_YML_WORKFLOW_PATH_TPL.format(tag), 'w') as f:
            for profile, count in self.profile_counts.items():
                for i in range(count):
                    f.write(LONG_RUN_TPL.format('long', profile, i))
            if self.include_legacy_umbrella:
                f.write(LONG_UMBRELLA_RUN)
            f.write(VALIDATE_FREEZE_HEADER)
            for profile, count in self.profile_counts.items():
                for i in range(count):
                    f.write(REQUIREMENT_TPL.format(
                        JOB_NAME_TPL.format('long', profile, i)))
            for profile, count in self.profile_counts.items():
                for i in range(count):
                    f.write(SHORT_RUN_TPL.format('short', profile, i))
            if self.include_legacy_umbrella:
                f.write(SHORT_UMBRELLA_RUN)
            f.write(ACCESSORY_RUN_HEADER)
            for profile, count in self.profile_counts.items():
                for i in range(count):
                    f.write(REQUIREMENT_TPL.format(
                        JOB_NAME_TPL.format('short', profile, i)))

if __name__ == '__main__':
    RegressionWorkflowShell().cmdloop()
