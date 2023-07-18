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
    echo "Usage: deploy_pulsar_k8s.sh [-h]"
    echo "                            [-portForwardOnly]"
    echo "                            [-pulsarDeployDir <pulsar_deployment_directory>]"
    echo "       -h  : Show usage info"
    echo "       -portForwardOnly : (Optional) Only do port forwarding when specified."
    echo "       -pulsarDeployDir  : (Optional) The directory that includes Pulsar deployment yaml files."
    echo
}

if [[ $# -gt 4 ]]; then
    usage
    errorExit 10 "Incorrect input parameter count!"
fi

portForwardOnly=0
pulsarDeployDir="${SCENARIO_HOMEDIR}/_deployment/pulsar-k8s"
while [[ "$#" -gt 0 ]]; do
    case $1 in
        -h) usage; exit 0 ;;
        -portForwardOnly) portForwardOnly=1; ;;
        -pulsarDeployDir) pulsarDeployDir=$2; shift ;;
        *) errExit 20 "Unknown input parameter passed: $1!"
    esac
    shift
done
debugMsg "portForwardOnly=${portForwardOnly}"
debugMsg "pulsarDeployDir=${pulsarDeployDir}"

kubeCtlExistence=$(chkSysSvcExistence kubectl)
debugMsg "kubeCtlExistence=${kubeCtlExistence}"
if [[ ${kubeCtlExistence} -eq 0 ]]; then
    errExit 30 "'kubectl' isn't installed on the local machine, which is required to build scenario programs!"
fi

if ! [[ -d "${pulsarDeployDir}" &&
        -f "${pulsarDeployDir}/values.yaml" ]]; then
    errExit 40 "Can't find the required Pulsar deployment yaml files in the specified directory: \"${pulsarDeployDir}\"."
fi

k8sNamespace=pulsar
helmName=pulsar

if [[ ${portForwardOnly} -eq 0 ]]; then
    echo ">> Create the K8s namespace ${k8sNamespace} if doesn't exist ..."
    existingPulsarNs=$(kubectl get namespace | grep ${k8sNamespace})
    if [[ -z "${existingPulsarNs}" ]]; then
      kubectl create namespace ${k8sNamespace}
    fi
    echo

    echo ">> Deploy the Pulsar resources ..."
    helm repo update
    helm -n ${k8sNamespace} install ${helmName} -f ${pulsarDeployDir}/values.yaml datastax-pulsar/pulsar
    echo

    echo ">> Wait for \"pulsar-broker\" deployment to be completely available ..."
    kubectl wait -n ${k8sNamespace} deployment pulsar-broker --timeout=600s --for condition=Available=True
    echo
fi


proxySvcName=$(kubectl -n ${k8sNamespace} get svc -l=component="proxy" -o name)
debugMsg "proxySvcName=${proxySvcName}"
echo ">> Forward the following Pulsar ports to localhost: ${PULSAR_PORTS[@]} ..."
if [[ -n "${proxySvcName// }" ]]; then
    startK8sPortForward \
        ${k8sNamespace} \
        ${proxySvcName} \
        "pulsar_port_forward.nohup" \
        "${PULSAR_PORTS[@]}"
else
    echo "   [WARN] Doesn't detect the corresponding Pulsar service, skip port forwarding!"
fi