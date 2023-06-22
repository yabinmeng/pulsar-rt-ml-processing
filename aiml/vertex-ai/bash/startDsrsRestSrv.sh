#! /usr/local/bin/bash

CUR_SCRIPT_FOLDER=$( cd -- "$( dirname -- "${BASH_SOURCE[0]}" )" &> /dev/null && pwd )
VERTEX_RTML_HOMEDIR=$( cd -- "${CUR_SCRIPT_FOLDER}/.." &> /dev/null && pwd )

source "${VERTEX_RTML_HOMEDIR}/../../_bash_/utilities.sh"

VERTEX_RTML_HOMEDIR_DSRS_SRV="${VERTEX_RTML_HOMEDIR}/code/test-rest-server"
java -jar ${VERTEX_RTML_HOMEDIR_DSRS_SRV}/target/test-rest-server-1.0.0.jar
