#! /bin/bash

CUR_SCRIPT_FOLDER=$( cd -- "$( dirname -- "${BASH_SOURCE[0]}" )" &> /dev/null && pwd )
SCENARIO_HOMEDIR=$( cd -- "${CUR_SCRIPT_FOLDER}/../../.." &> /dev/null && pwd )

source "${SCENARIO_HOMEDIR}/_bash/utilities.sh"
# DEBUG=true
echo

##
# Show usage info
#
usage() {
    echo
    echo "Usage: build_spark_docker_image.sh [-h]"
    echo "                                   [-sparkDeployDir <spark_deployment_directory>]"
    echo "       -h  : Show usage info"
    echo "       -sparkDeployDir  : (Optional) The directory that has the Spark deployment yaml files."
    echo
}

if [[ $# -gt 3 ]]; then
    usage
    errorExit 10 "Incorrect input parameter count!"
fi

sparkDeployDir="${SCENARIO_HOMEDIR}/_deployment/spark-k8s"
while [[ "$#" -gt 0 ]]; do
    case $1 in
        -h) usage; exit 0 ;;
        -sparkDeployDir) sparkDeployDir=$2; shift ;;
        *) errExit 20 "Unknown input parameter passed: $1!"
    esac
    shift
done
debugMsg "sparkDeployDir=${sparkDeployDir}"

dockerExistence=$(chkSysSvcExistence kubectl)
debugMsg "dockerExistence=${dockerExistence}"
if [[ ${dockerExistence} -eq 0 ]]; then
    errExit 30 "'docker' isn't installed on the local machine, which is required to build scenario programs!"
fi

dockerFileHomeDir="${sparkDeployDir}/docker-build"
if ! [[ -d "${dockerFileHomeDir}" &&
        -f "${dockerFileHomeDir}//Dockerfile" ]]; then
    errExit 40 "Can't find the required docker build files in the specified directory: \"${dockerFileHomeDir}\"."
fi

docker build -f ${dockerFileHomeDir}/Dockerfile -t spark-hadoop:3.4.0 ${dockerFileHomeDir}
