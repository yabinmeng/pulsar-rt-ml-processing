#! /usr/local/bin/bash

CUR_SCRIPT_FOLDER=$( cd -- "$( dirname -- "${BASH_SOURCE[0]}" )" &> /dev/null && pwd )
VERTEX_RTML_HOMEDIR=$( cd -- "${CUR_SCRIPT_FOLDER}/.." &> /dev/null && pwd )

exit

mvnExistence=$(chkSysSvcExistence mvn)
debugMsg "mvnExistence=${mvnExistence}"
if [[ ${mvnExistence} -eq 0 ]]; then
    errExit 30 "[ERROR] 'mvn' isn't installed on the local machine, which is required to build the applications!"
fi

# Build the prediction simulation applications
cd  "${VERTEX_RTML_HOMEDIR}/code/rtml-app/"
mvn clean install -Dmaven.plugin.validation=brief

# Build the DSRS REST server
cd "${VERTEX_RTML_HOMEDIR}/code/test-rest-server"
mvn clean install -Dmaven.plugin.validation=brief
