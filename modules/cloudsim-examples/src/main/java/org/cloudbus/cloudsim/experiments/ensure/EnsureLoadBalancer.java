package org.cloudbus.cloudsim.experiments.ensure;

import org.cloudbus.cloudsim.Log;
import org.cloudbus.cloudsim.container.core.Container;
import org.cloudbus.cloudsim.container.core.ContainerCloudlet;
import org.cloudbus.cloudsim.container.lists.ContainerVmList;
import org.cloudbus.cloudsim.serverless.*;

import java.util.*;

public class EnsureLoadBalancer  extends RequestLoadBalancer {

    private HashMap<String, Double> isoResponseTimes;

    public EnsureLoadBalancer(ServerlessController controller, ServerlessDatacenter dc, HashMap<String, Double> isoResponseTime) {
        super(controller, dc);
        setIsoResponseTimes(isoResponseTime);
    }

    public void setIsoResponseTimes(HashMap<String, Double> isoResponseTimes) {this.isoResponseTimes = isoResponseTimes;}
    public double getFunctionIsoResponseTime(String functionId) {
        return isoResponseTimes.get(functionId);
    }

    @Override
    protected boolean selectContainer(ServerlessRequest task) {
        ServerlessContainer container = getWarmContainer(task);
        if (container != null) {
            ServerlessRequestScheduler clScheduler = (ServerlessRequestScheduler) (container.getContainerCloudletScheduler());
            if (clScheduler.isSuitableForRequest(task, container)) {
                clScheduler.setTotalCurrentAllocatedRamForRequests(task);
                clScheduler.setTotalCurrentAllocatedMipsShareForRequests(task);
                Log.printLine(String.format("Using idling container: container #%s", container.getId()));

                task.setContainerId(container.getId());
            }
        }
        return true;
    }

    public ServerlessContainer getWarmContainer(ServerlessRequest request) {
        for (int x = 1; x <= getBroker().getVmsCreatedList().size(); x++) {
            ServerlessInvoker vm = (ServerlessInvoker) (ContainerVmList.getById(getBroker().getVmsCreatedList(), x));
            assert vm != null;
            int vmState = getVmState(vm);
            if (vmState < Constants.ENSURE_INVOKER_STATE_UNSAFE) {
                for (Container container: vm.getContainerList()) {
                    if (((ServerlessContainer) container).getIdling() ) {
                        return (ServerlessContainer) container;
                    }
                }
            }
        }
        return null;
    }

    public int getVmState(ServerlessInvoker vm) {
        HashMap<String, List<ServerlessRequest>> requestList = getProccessedVmRequestList(vm);
        int currentState = Constants.ENSURE_INVOKER_STATE_SAFE;
        int newState;
        for (String functionId : requestList.keySet()) {
            double avg = getFunctionMovingAverage(functionId, requestList.get(functionId));
            double latencyRange = (Constants.ENSURE_SLO_THRESHOLD - 1) * isoResponseTimes.get(functionId);
            if (avg < latencyRange * 0.25 ) newState =  Constants.ENSURE_INVOKER_STATE_SAFE;
            else if (avg < latencyRange * 0.5) newState =  Constants.ENSURE_INVOKER_STATE_PRE_WARMING;
            else if (avg < latencyRange * 0.75) newState =  Constants.ENSURE_INVOKER_STATE_WARNING;
            else newState =  Constants.ENSURE_INVOKER_STATE_UNSAFE;
            if (newState > currentState) currentState = newState;
        }
        return currentState;
    }

    public double getFunctionMovingAverage(String functionId, List<ServerlessRequest> requestList) {
        requestList.sort(Comparator.comparingDouble(ServerlessRequest::getFinishTime).reversed());
        List<ServerlessRequest> requests = requestList.subList(
                0, Math.min(requestList.size(), Constants.ENSURE_RESPONSE_TIME_WINDOW_SIZE));
        double sum = 0;
        for (ServerlessRequest request : requests) {
            sum += request.getFinishTime() - request.getArrivalTime();
        }
        return sum / requests.size();
    }

    HashMap<String, List<ServerlessRequest>> getProccessedVmRequestList(ServerlessInvoker vm) {
        HashMap<String, List<ServerlessRequest>> requestList = new HashMap<>();
        for (String functionId: isoResponseTimes.keySet()) {
            requestList.put(functionId, new ArrayList<>());
        }
        for (ContainerCloudlet cloudlet : getBroker().getCloudletReceivedList()) {
            if (((ServerlessRequest) cloudlet).getVmId() == vm.getId()) {
                requestList.get(((ServerlessRequest) cloudlet).getRequestFunctionId()).add((ServerlessRequest) cloudlet);
            }
        }
        return requestList;
    }
}
