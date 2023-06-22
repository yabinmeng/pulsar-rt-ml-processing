#! /usr/local/bin/bash

CUR_SCRIPT_FOLDER=$( cd -- "$( dirname -- "${BASH_SOURCE[0]}" )" &> /dev/null && pwd )
VERTEX_RTML_HOMEDIR=$( cd -- "${CUR_SCRIPT_FOLDER}/.." &> /dev/null && pwd )

source "${VERTEX_RTML_HOMEDIR}/../../_bash_/utilities.sh"

echo

##
# Show usage info 
#
usage() {   
  echo
  echo "Usage: startSimulatorProducer.sh [-h]" 
  echo "                                 [-n <record_num>]"
  echo "       -n : (Optional) The number of the records to publish."
  echo "                       default to -1, which means to receive indefinitely."
  echo
}

if [[ $# -gt 2 ]]; then
  usage
  errExit 10 "Incorrect input parametere count!"
fi

numRecords=-1
while [[ "$#" -gt 0 ]]; do
   case $1 in
      -h)   usage; exit 0           ;;
      -n)   numRecords=$2; shift;   ;;
      *)    errExit 20 "Unknown input parameter passed: $1" ;;
   esac
   shift
done
debugMsg "numRecords=${numRecords}"

mainCfgPropFile="${VERTEX_RTML_HOMEDIR}/conf/main-cfg.properties"
if ! [[ -f "${mainCfgPropFile}" ]]; then
  errExit 30 "Can't find the required main configuration properties file!"
fi

if [[ ${numRecords} -eq 0 || ${numRecords} -lt -1 ]]; then
  errExit 40 "Invalid number of records to receive: ${numRecords}!"
fi

VERTEX_RTML_HOMEDIR_APP="${VERTEX_RTML_HOMEDIR}/code/rtml-app"
clientAppJarFile="${VERTEX_RTML_HOMEDIR_APP}/rtml-simulator/target/rtml-simulator-1.0.0.jar"
if ! [[ -f "${clientAppJarFile}" ]]; then
  errExit 40 "Can't find the required client application jar file!"
fi

java \
  -cp ${clientAppJarFile} \
  com.example.rtmldemo.RtmlSimulatorProducer \
  -n ${numRecords} \
  -f ${mainCfgPropFile}