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
    echo "Usage: deploy_flink_k8s.sh [-h]"
    echo "                           [-portForwardOnly]"
    echo "                           [-flinkDeployDir <flink_deployment_directory>]"
    echo "       -h  : Show usage info"
    echo "       -portForwardOnly : (Optional) Only do port forwarding when specified."
    echo "       -flinkDeployDir  : (Optional) The directory that includes Flink deployment yaml files."
    echo
}

if [[ $# -gt 4 ]]; then
    usage
    errorExit 10 "Incorrect input parameter count!"
fi

portForwardOnly=0
flinkDeployDir="${SCENARIO_HOMEDIR}/_deployment/flink-k8s"
while [[ "$#" -gt 0 ]]; do
    case $1 in
        -h) usage; exit 0 ;;
        -portForwardOnly) portForwardOnly=1; ;;
        -flinkDeployDir) flinkDeployDir=$2; shift ;;
        *) errExit 20 "Unknown input parameter passed: $1!"
    esac
    shift
done
debugMsg "portForwardOnly=${portForwardOnly}"
debugMsg "flinkDeployDir=${flinkDeployDir}"

kubeCtlExistence=$(chkSysSvcExistence kubectl)
debugMsg "kubeCtlExistence=${kubeCtlExistence}"
if [[ ${kubeCtlExistence} -eq 0 ]]; then
    errExit 30 "'kubectl' isn't installed on the local machine, which is required to build scenario programs!"
fi

if ! [[ -d "${flinkDeployDir}" &&
        -f "${flinkDeployDir}/flink-configuration-configmap.yaml" &&
        -f "${flinkDeployDir}/jobmanager-service.yaml" &&
        -f "${flinkDeployDir}/jobmanager-session-deployment-non-ha.yaml" &&
        -f "${flinkDeployDir}/taskmanager-session-deployment.yaml" ]]; then
    errExit 40 "Can't find the required flink deployment yaml files in the specified directory: \"${flinkDeployDir}\"."
fi

k8sNamespace=flink

echo ">> Tear down the Flink cluster ..."
jobManagerPodName=$(kubectl -n ${k8sNamespace} get pod -l=component="jobmanager" -o name)
debugMsg "jobManagerPodName=${jobManagerPodName}"

if [[ -n "${jobManagerPodName// }" ]]; then
    echo
    echo "   - Terminate the forwarded Flink ports: ${FLINK_PORTS[@]} ... "
    stopK8sPortForward "${FLINK_PORTS[@]}"
    echo

    if [[ ${portForwardOnly} -eq 0 ]]; then
        echo "   - Uninstall the Flink cluster from the K8s namespace \"${k8sNamespace}\" ... "
        echo "    * \"taskmanager\" deployment ..."
        kubectl -n ${k8sNamespace} delete -f ${flinkDeployDir}/taskmanager-session-deployment.yaml
        echo "    * \"jobmanager\" deployment ..."
        kubectl -n ${k8sNamespace} delete -f ${flinkDeployDir}/jobmanager-session-deployment-non-ha.yaml
        echo "    * \"jobmanager\" service ..."
        kubectl -n ${k8sNamespace} delete -f ${flinkDeployDir}/jobmanager-service.yaml
        echo "    * \"flink\" configuration ..."
        kubectl -n ${k8sNamespace} delete -f ${flinkDeployDir}/flink-configuration-configmap.yaml
        echo
    fi
else
    echo "   [WARN] Doesn't detect the Flink \"jobmanager\" deployment. Likely there is no Flink cluster deployed in the K8s namespace \"${k8sNamespace}\"!"
fi

echo
