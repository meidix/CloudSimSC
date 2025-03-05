package org.cloudbus.cloudsim.serverless;

import org.cloudbus.cloudsim.container.containerProvisioners.ContainerBwProvisioner;
import org.cloudbus.cloudsim.container.containerProvisioners.ContainerPe;
import org.cloudbus.cloudsim.container.containerProvisioners.ContainerRamProvisioner;
import org.cloudbus.cloudsim.container.core.Container;
import org.cloudbus.cloudsim.container.lists.ContainerList;
import org.cloudbus.cloudsim.container.schedulers.ContainerScheduler;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;


public class EnsureServerlessInvoker  extends ServerlessInvokerRequestAware {

    private HashMap<String, Double> functionIsoResponseTimes;


    public EnsureServerlessInvoker(int id, int userId, double mips, float ram, long bw, long size, String vmm, ContainerScheduler containerScheduler, ContainerRamProvisioner containerRamProvisioner, ContainerBwProvisioner containerBwProvisioner, List<? extends ContainerPe> peList, double schedulingInterval, HashMap<String, Double> functionIsoResponseTimes) {
        super(id, userId, mips, ram, bw, size, vmm, containerScheduler, containerRamProvisioner, containerBwProvisioner, peList, schedulingInterval);
        setFunctionIsoResponseTimes(functionIsoResponseTimes);
    }

    public void setFunctionIsoResponseTimes(HashMap<String, Double> functionIsoResponseTimes) { this.functionIsoResponseTimes = functionIsoResponseTimes;}

    public int getState() {
        int current = Constants.ENSURE_STATE_SAFE;
        int newState;
        for (Map.Entry<String, ArrayList<ServerlessRequest>> entry: getFinishedTaskMap().entrySet()) {
            double allowableRange = functionIsoResponseTimes.get(entry.getKey()) * (1 - Constants.ENSURE_LATENCY_THRESHOLD);
            int start = Math.max(entry.getValue().size() - Constants.ENSURE_RESPONSE_TIME_WINDOW_SIZE, 0);
            double movingAverage = entry
                    .getValue()
                    .subList(start, entry.getValue().size())
                    .stream()
                    .mapToDouble(req -> req.getFinishTime() - req.getArrivalTime())
                    .average()
                    .orElse(0.0);
            if (movingAverage <= (allowableRange / 4)) {
                newState =  Constants.ENSURE_STATE_SAFE;
            } else if (movingAverage <= (allowableRange / 2)) {
                newState =  Constants.ENSURE_STATE_PRE_WARMING;
            } else if (movingAverage <= (allowableRange / 4) * 3 ) {
                newState =  Constants.ENSURE_STATE_WARNING;
            } else {
                newState =  Constants.ENSURE_STATE_UNSAFE;
            }
            if (newState > current) {
                current = newState;
            }
        }
        return current;
    }

    public ArrayList<ServerlessContainer> getWarmContainers(ServerlessRequest request) {
        List<Container> vmContainers = getContainerList();
        ArrayList<ServerlessContainer> warmContainers = new ArrayList<>();
        for (Container container : vmContainers) {
            if (
                    ((ServerlessContainer) container).getType().equals(request.getRequestFunctionId()) &&
                            ((ServerlessContainer) container).getRunningTasks().size() == 0 &&
                            ((ServerlessContainer) container).getfinishedTasks().size() > 0 ) {
                warmContainers.add((ServerlessContainer) container);
            }
        }
        return warmContainers;
    }

    public int getFunctionCapacity(String functionId) {
        List<Container> readyContainers = getFunctionContainerMap().get(functionId);
        List<Container> pendingContainers = getFunctionContainerMapPending().get(functionId);
        int size  = 0;
        if (readyContainers != null) { size = readyContainers.size(); }
        if (pendingContainers != null) { size += pendingContainers.size(); }
        int capacity = Math.max(getPeList().size() - size, 0);
        int state = getState();
        if (state == Constants.ENSURE_STATE_WARNING) {
            return Math.min(capacity, 1);
        } else if (state == Constants.ENSURE_STATE_UNSAFE) { return 0;}
        else {
            return capacity;
        }
    }

    @Override
    public boolean isSuitableForContainer(Container container, ServerlessInvoker vm) {
        boolean isSuitable = super.isSuitableForContainer(container, vm);
        List<ServerlessRequest> tasks = ((ServerlessContainer) container).getRunningTasks();
        if (tasks == null) {
            return isSuitable && ((EnsureServerlessInvoker) vm).getState() != Constants.ENSURE_STATE_UNSAFE;
        }
        return isSuitable && ((EnsureServerlessInvoker) vm).getFunctionCapacity(((ServerlessContainer) container).getType()) > 0;
    }
}
