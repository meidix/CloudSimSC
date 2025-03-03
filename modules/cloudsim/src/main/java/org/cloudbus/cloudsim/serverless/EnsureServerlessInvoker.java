package org.cloudbus.cloudsim.serverless;

import org.cloudbus.cloudsim.container.containerProvisioners.ContainerBwProvisioner;
import org.cloudbus.cloudsim.container.containerProvisioners.ContainerPe;
import org.cloudbus.cloudsim.container.containerProvisioners.ContainerRamProvisioner;
import org.cloudbus.cloudsim.container.core.Container;
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
        List<Container> containers = getFunctionContainerMap().get(request.getRequestFunctionId());
        ArrayList<ServerlessContainer> warmContainers = new ArrayList<>();
        for (Container container : containers) {
            if (((ServerlessContainer) container).getIdling()) { warmContainers.add((ServerlessContainer) container); }
        }
        return warmContainers;
    }

    public int getFunctionCapacity(ServerlessRequest request) {
        List<Container> containers = getFunctionContainerMap().get(request.getRequestFunctionId());
        int capacity = Math.max(getPeList().size() - containers.size(), 0);
        int state = getState();
        if (state == Constants.ENSURE_STATE_WARNING) {
            return Math.min(capacity, 1);
        } else if (state == Constants.ENSURE_STATE_UNSAFE) { return 0;}
        else {
            return capacity;
        }
    }
}
