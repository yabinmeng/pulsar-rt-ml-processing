#! /usr/local/bin/bash

CUR_SCRIPT_FOLDER=$( cd -- "$( dirname -- "${BASH_SOURCE[0]}" )" &> /dev/null && pwd )
KASKADA_RTML_HOMEDIR=$( cd -- "${CUR_SCRIPT_FOLDER}/../.." &> /dev/null && pwd )

source "${KASKADA_RTML_HOMEDIR}/../_bash_/utilities.sh"

##
# Show usage info 
#
usage() {   
   echo
   echo "Usage: deploy_kaskada_local.sh.sh [-h]"
   echo "                                  [-binFolder <kaskada_binary_folder>]"
   echo "       -h         : Show usage info"
   echo "       -binFolder : Local folder where the Kaskada binary is located. Default to \"./kaskada/bin\"."
   echo
}

if [[ $# -gt 3 ]]; then
   usage
   echo "Incorrect input parametere count!"
   exit 10
fi

localKaskadaBinDir="./kaskada/bin"
while [[ "$#" -gt 0 ]]; do
   case $1 in
      -h) usage; exit 0 ;;
      -binFolder) localKaskadaBinDir=$2; shift ;;
      *) echo "Unknown input parameter passed: $1"; exit 20 ;;
   esac
   shift
done
debugMsg "localKaskadaBinDir=${localKaskadaBinDir}"

kasEngineBin="${localKaskadaBinDir}/kaskada-engine"
kasManagerBin="${localKaskadaBinDir}/kaskada-manager"
if ! [[ -f "${kasEngineBin}" && -f "${kasManagerBin}" ]]; then
   kasEngineBin=$(which kaskada-engine)
   kasManagerBin=$(which kaskada-manager)
fi
debugMsg "kasEngineBin=${kasEngineBin}"
debugMsg "kasManagerBin=${kasManagerBin}"

if ! [[ -f "${kasEngineBin}" && -f "${kasManagerBin}" ]]; then
   errExit 30 "Can't find the required Kaskada binaries in the specified loca directory: \"${localKaskadaBinDir}\"."
fi

echo "Deploy Kaskada engine locally ..."
echo "- Start Kaskada manager ..."
$(${kasManagerBin} 2>&1 > kaskada-manager.log 2>&1 &)

echo "- Start Kaskada engine ..."
$(${kasEngineBin} serve > kaskada-engine.log 2>&1 &)



echo
