#! /usr/local/bin/bash

CUR_SCRIPT_FOLDER=$( cd -- "$( dirname -- "${BASH_SOURCE[0]}" )" &> /dev/null && pwd )
KASKADA_RTML_HOMEDIR=$( cd -- "${CUR_SCRIPT_FOLDER}/../.." &> /dev/null && pwd )

source "${KASKADA_RTML_HOMEDIR}/../_bash_/utilities.sh"

##
# Show usage info 
#
usage() {   
   echo
   echo "Usage: deploy_kaskada_cluster.sh.sh [-h]" 
   echo "                                    [-portForwardOnly]"
   echo "                                    [-localHemRepo <local_helm_repo_name>]"
   echo "       -h  : Show usage info"
   echo "       -portForwardOnly : (Optional) Only do port forwarding when specified."
   echo "       -localHelmRepo   : (Optional) The Kaskada helm repo local folder. Default to \"./kaskada/charts/kaskada-canary\"."
   echo
}

if [[ $# -gt 3 ]]; then
   usage
   echo "Incorrect input parametere count!"
   exit 10
fi

portForwardOnly=0
localKaskadaHelmRepoDir="./kaskada/charts/kaskada-canary"
while [[ "$#" -gt 0 ]]; do
   case $1 in
      -h) usage; exit 0 ;;
      -portForwardOnly) portForwardOnly=1; ;;
      -localHelmRepo) localKaskadaHelmRepoDir=$2; shift ;;
      *) echo "Unknown input parameter passed: $1"; exit 20 ;;
   esac
   shift
done
debugMsg "portForwardOnly=${portForwardOnly}"
debugMsg "localKaskadaHelmRepoDir=${localKaskadaHelmRepoDir}"

k8sNamespace=kaskada
helmName=kaskada

if [[ ${portForwardOnly} -eq 0 ]]; then
   if ! [[ -d "${localKaskadaHelmRepoDir}" && 
           -f "${localKaskadaHelmRepoDir}/Chart.yaml" && 
           -f "${localKaskadaHelmRepoDir}/values.yaml" ]]; then
      errExit 30 "Can't find the required Kaskada helm repo in the specified loca directory: \"${localKaskadaHelmRepoDir}\"."
   fi

   echo "Creating K8s namespace ${k8sNamespace} if doesn't exist ..."
   existingK8sNs=$(kubectl get namespace | grep ${k8sNamespace})
   if [[ -z "${existingK8sNs}" ]]; then
      kubectl create namespace ${k8sNamespace}
   fi
   echo

   echo "Deploy Kaskada resources ..."
   
   # Use the provided Kaska helm chart values.yaml file provided in this repo
   cp -rf "${localKaskadaHelmRepoDir}/values.yaml" "${localKaskadaHelmRepoDir}/values.yaml.bak"
   cp -rf "${KASKADA_RTML_HOMEDIR}/deployment/kaskada-helm/values.yaml" "${localKaskadaHelmRepoDir}/values.yaml"

   helm -n ${k8sNamespace} install ${helmName} ${localKaskadaHelmRepoDir}
   echo

   echo "Wait for \"kaskada-canary\" deployment to be completely available ..."
   kubectl wait -n ${k8sNamespace} deployment "${helmName}-kaskada-canary" --timeout=600s --for condition=Available=True
   echo
fi

kaskadaSvcName=$(kubectl -n ${k8sNamespace} get svc -l=app.kubernetes.io/name="kaskada-canary" -o name)
debugMsg "kaskadaSvcName=${kaskadaSvcName}"

if [[ -n "${kaskadaSvcName// }" ]]; then
   echo "Forward the following Kaskada ports to localhost: ${KASKADA_PORTS[@]} ..."
   source ${KASKADA_RTML_HOMEDIR}/bash/infra/forward_kaskada_port.sh \
      -namespace "${k8sNamespace}" \
      -svcName "${kaskadaSvcName}" \
      -act "start"
fi

echo
