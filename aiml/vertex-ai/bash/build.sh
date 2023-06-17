#! /usr/local/bin/bash

CUR_SCRIPT_FOLDER=$( cd -- "$( dirname -- "${BASH_SOURCE[0]}" )" &> /dev/null && pwd )
RTML_DEMO_HOMEDIR=$( cd -- "${CUR_SCRIPT_FOLDER}/.." &> /dev/null && pwd )

source "${RTML_DEMO_HOMEDIR}/bash/_utilities_.sh"

mvnExistence=$(chkSysSvcExistence mvn)
debugMsg "mvnExistence=${mvnExistence}"
if [[ ${mvnExistence} -eq 0 ]]; then
    errExit 30 "[ERROR] 'mvn' isn't installed on the local machine, which is required to build the applications!"
fi

# Build the prediction simulation applications
cd  "${RTML_DEMO_HOMEDIR}/code/rtml-app/"
mvn clean install -Dmaven.plugin.validation=brief

# Build the DSRS REST server
cd "${RTML_DEMO_HOMEDIR}/code/test-rest-server"
mvn clean install -Dmaven.plugin.validation=brief
