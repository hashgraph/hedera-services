# Java Regression Suite(JRS) Developer Testing
# **Table of Contents**

- [Description](#toc-description)
- [Introduction to JRS](#introduction)
    - [File Types](#file-types)
        - [Experiment Config JSON](#experiment-config)
        - [Regression Config JSON](#regression-config)
    - [Naming Conventions](#naming-conventions)
- [GCP SetUp](#gcp_setup)
- [Instructions for kicking off Regression from CLI](#cli-run)
- [Current Nightly Regression](#nightly-regression)
    - [Tests in nightly](#test-description)
    - [Region SetUp](#region-setup)


<a name="toc-description"></a>

# **Description**
This document describes the steps needed to run a JRS test using the infrastructure in 
swirlds-platform-regression.

<a name="introduction"></a>

# **Introduction to JRS**
The Java Regression Suite (JRS) runs on a remote machine.
The JRS relies on two types of json files, i.e., Regression configuration json and experiment configuration json.
The regression configuration holds information on keys, GCP config, GCP Private Key, and which experiments to run, along with some minor things to help the test runner keep track of their information.
The experiment setup gives specific information on the setup of the experiment and how to tell if it passed. This includes: duration of the test, settings, restart, reconnect, freeze, what jar to use, options for the jar, which validator(s) to use
