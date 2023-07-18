#! /usr/local/bin/bash

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
    echo "Usage: deploy_kaskada_k8s.sh [-h]"
    echo "                             [-portForwardOnly]"
    echo "                             [-localHemRepo <local_helm_repo_name>]"
    echo "         -h  : Show usage info"
    echo "         -portForwardOnly : (Optional) Only do port forwarding when specified."
    echo "         -localHelmRepo   : (Optional) The Kaskada helm repo local folder. Default to \"./kaskada/charts/kaskada-canary\"."
    echo
}
#
# NOTE: right now the Kaskada helm chart is not publicly available, so we need to use a local helm repo!
#


if [[ $# -gt 4 ]]; then
    usage
    errorExit 10 "Incorrect input parameter count!"
fi

portForwardOnly=0
localKaskadaHelmRepoDir="./kaskada/charts/kaskada-canary"
while [[ "$#" -gt 0 ]]; do
    case $1 in
        -h) usage; exit 0 ;;
        -portForwardOnly) portForwardOnly=1; ;;
        -localHelmRepo) localKaskadaHelmRepoDir=$2; shift ;;
        *) errExit 20 "Unknown input parameter passed: $1!"
    esac
    shift
done
debugMsg "portForwardOnly=${portForwardOnly}"
debugMsg "localKaskadaHelmRepoDir=${localKaskadaHelmRepoDir}"

kubeCtlExistence=$(chkSysSvcExistence kubectl)
debugMsg "kubeCtlExistence=${kubeCtlExistence}"
if [[ ${kubeCtlExistence} -eq 0 ]]; then
    errExit 30 "'kubectl' isn't installed on the local machine, which is required to build scenario programs!"
fi

k8sNamespace=kaskada
helmName=kaskada

if [[ ${portForwardOnly} -eq 0 ]]; then
    if ! [[ -d "${localKaskadaHelmRepoDir}" &&
            -f "${localKaskadaHelmRepoDir}/Chart.yaml" &&
            -f "${localKaskadaHelmRepoDir}/values.yaml" ]]; then
        errExit 40 "Can't find the required Kaskada helm repo in the specified directory: \"${localKaskadaHelmRepoDir}\"."
    fi

    echo ">> Create the K8s namespace ${k8sNamespace} if doesn't exist ..."
    existingK8sNs=$(kubectl get namespace | grep ${k8sNamespace})
    if [[ -z "${existingK8sNs}" ]]; then
        kubectl create namespace ${k8sNamespace}
    fi
    echo

    echo ">> Deploy the Kaskada resources ..."

    # Use the provided Kaska helm chart values.yaml file provided in this repo
    cp -rf "${localKaskadaHelmRepoDir}/values.yaml" "${localKaskadaHelmRepoDir}/values.yaml.bak"
    cp -rf "${KASKADA_RTML_HOMEDIR}/deployment/kaskada-helm/values.yaml" "${localKaskadaHelmRepoDir}/values.yaml"

    helm -n ${k8sNamespace} install ${helmName} ${localKaskadaHelmRepoDir}
    echo

    echo ">> Wait for \"kaskada-canary\" deployment to be completely available ..."
    kubectl wait -n ${k8sNamespace} deployment "${helmName}-kaskada-canary" --timeout=600s --for condition=Available=True
    echo
fi

kaskadaSvcName=$(kubectl -n ${k8sNamespace} get svc -l=app.kubernetes.io/name="kaskada-canary" -o name)
debugMsg "kaskadaSvcName=${kaskadaSvcName}"

echo ">> Forward the following Kaskada ports to localhost: ${KASKADA_PORTS[@]} ..."
if [[ -n "${kaskadaSvcName// }" ]]; then
    startK8sPortForward \
        ${k8sNamespace} \
        ${kaskadaSvcName} \
        "kaskada_port_forward.nohup" \
        "${KASKADA_PORTS[@]}"
else
    echo "   [WARN] Doesn't detect the corresponding Kaskada service, skip port forwarding!"
fi

echo
