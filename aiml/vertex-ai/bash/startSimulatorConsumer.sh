#! /usr/local/bin/bash

CUR_SCRIPT_FOLDER=$( cd -- "$( dirname -- "${BASH_SOURCE[0]}" )" &> /dev/null && pwd )
RTML_DEMO_HOMEDIR=$( cd -- "${CUR_SCRIPT_FOLDER}/.." &> /dev/null && pwd )

source "${RTML_DEMO_HOMEDIR}/bash/_utilities_.sh"

echo

##
# Show usage info 
#
usage() {   
  echo
  echo "Usage: startSimulatorConsumer.sh [-h]" 
  echo "                                 [-n <record_num>]"
  echo "       -n : (Optional) The number of the records to receive."
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

mainCfgPropFile="${RTML_DEMO_HOMEDIR}/conf/cfg.properties"
if ! [[ -f "${mainCfgPropFile}" ]]; then
  errExit 30 "Can't find the required main configuration properties file!"
fi

if [[ ${numRecords} -eq 0 || ${numRecords} -lt -1 ]]; then
  errExit 40 "Invalid number of records to receive: ${numRecords}!"
fi

# generate a random alphanumeric string with length 20
randomStr=$(cat /dev/urandom | LC_ALL=C tr -dc 'a-zA-Z0-9' | fold -w 20 | head -n 1)

RTML_DEMO_HOMEDIR_APP="${RTML_DEMO_HOMEDIR}/code/rtml-app"
clientAppJarFile="${RTML_DEMO_HOMEDIR_APP}/rtml-simulator/target/rtml-simulator-1.0.0.jar"
if ! [[ -f "${clientAppJarFile}" ]]; then
  errExit 40 "Can't find the required client application jar file!"
fi

java \
  -cp ${clientAppJarFile} \
  com.example.rtmldemo.RtmlSimulatorConsumer \
  -n ${numRecords} \
  -f ${mainCfgPropFile} \
  -sbn mysub-${randomStr}