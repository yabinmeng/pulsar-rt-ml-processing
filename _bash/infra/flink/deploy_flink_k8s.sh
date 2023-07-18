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
    echo "       -flinkDeployDir  : (Optional) The directory that has the Flink deployment yaml files."
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

if [[ ${portForwardOnly} -eq 0 ]]; then
    echo ">> Create the K8s namespace ${k8sNamespace} if doesn't exist ..."
    existingFlinkNs=$(kubectl get namespace | grep ${k8sNamespace})
    if [[ -z "${existingFlinkNs}" ]]; then
        kubectl create namespace ${k8sNamespace}
    fi
    echo

    echo ">> Deploy the Flink resources ..."
    echo "   - \"flink\" configuration ..."
    kubectl -n ${k8sNamespace} create -f ${flinkDeployDir}/flink-configuration-configmap.yaml
    echo "   - \"jobmanager\" service ..."
    kubectl -n ${k8sNamespace} create -f ${flinkDeployDir}/jobmanager-service.yaml
    echo "   - \"jobmanager\" deployment ..."
    kubectl -n ${k8sNamespace} create -f ${flinkDeployDir}/jobmanager-session-deployment-non-ha.yaml
    echo "   - \"taskmanager\" deployment ..."
    kubectl -n ${k8sNamespace} create -f ${flinkDeployDir}/taskmanager-session-deployment.yaml
    echo

    echo ">> Wait for flink-jobmanager deployment to be completely available ..."
    kubectl wait -n ${k8sNamespace} deployment flink-jobmanager --timeout=600s --for condition=Available=True
    echo
fi

jobManagerPodName=$(kubectl -n ${k8sNamespace} get pod -l=component="jobmanager" -o name)
echo ">> Forward the following Flink ports to localhost: ${FLINK_PORTS[@]} ..."
if [[ -n "${jobManagerPodName// }" ]]; then
    startK8sPortForward \
        ${k8sNamespace} \
        ${jobManagerPodName} \
        "flink_port_forward.nohup" \
        "${FLINK_PORTS[@]}"
else
    echo "   [WARN] Doesn't detect the corresponding Flink job manager K8s Pod, skip port forwarding!"
fi

echo
