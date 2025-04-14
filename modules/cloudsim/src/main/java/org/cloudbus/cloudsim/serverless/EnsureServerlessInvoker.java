package org.cloudbus.cloudsim.serverless;

import org.cloudbus.cloudsim.container.containerProvisioners.ContainerBwProvisioner;
import org.cloudbus.cloudsim.container.containerProvisioners.ContainerPe;
import org.cloudbus.cloudsim.container.containerProvisioners.ContainerRamProvisioner;
import org.cloudbus.cloudsim.container.core.Container;
import org.cloudbus.cloudsim.container.lists.ContainerList;
import org.cloudbus.cloudsim.container.schedulers.ContainerScheduler;
import org.cloudbus.cloudsim.core.CloudSim;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;


public class EnsureServerlessInvoker  extends ServerlessInvokerRequestAware {

    private HashMap<String, Double> functionIsoResponseTimes;
    private double lastRecordedStateTime;


    public EnsureServerlessInvoker(int id, int userId, double mips, float ram, long bw, long size, String vmm, ContainerScheduler containerScheduler, ContainerRamProvisioner containerRamProvisioner, ContainerBwProvisioner containerBwProvisioner, List<? extends ContainerPe> peList, double schedulingInterval, HashMap<String, Double> functionIsoResponseTimes) {
        super(id, userId, mips, ram, bw, size, vmm, containerScheduler, containerRamProvisioner, containerBwProvisioner, peList, schedulingInterval);
        setFunctionIsoResponseTimes(functionIsoResponseTimes);
        lastRecordedStateTime = 0;
    }

    protected void setLastRecordedStateTime(double time) { lastRecordedStateTime = time; }

    public void setFunctionIsoResponseTimes(HashMap<String, Double> functionIsoResponseTimes) { this.functionIsoResponseTimes = functionIsoResponseTimes;}


    public double getFunctionLatencyMovingAverage(String functionId) {
        ArrayList<ServerlessRequest> finishedRequests = getFinishedTaskMap().get(functionId);
        if (finishedRequests == null) {
            return functionIsoResponseTimes.get(functionId);
        }
        int start = Math.max(finishedRequests.size() - Constants.ENSURE_RESPONSE_TIME_WINDOW_SIZE, 0);
        return finishedRequests
                .subList(start, finishedRequests.size())
                .stream()
                .mapToDouble(req -> req.getFinishTime() - req.getExecStartTime())
                .average()
                .orElse(0.0);
    }

    public double getFunctionAllowableLatency(String functionId) {
        return functionIsoResponseTimes.get(functionId) * Constants.ENSURE_LATENCY_WARNING_THRESHOLD;
    }

    public int getState() {
        int current = Constants.ENSURE_STATE_SAFE;
        int newState;
        for (Map.Entry<String, ArrayList<ServerlessRequest>> entry: getFinishedTaskMap().entrySet()) {
            double allowableRange = functionIsoResponseTimes.get(entry.getKey()) * (Constants.ENSURE_LATENCY_THRESHOLD - 1);
            int start = Math.max(entry.getValue().size() - Constants.ENSURE_RESPONSE_TIME_WINDOW_SIZE, 0);
            double movingAverage = entry
                    .getValue()
                    .subList(start, entry.getValue().size())
                    .stream()
                    .filter(req -> req.getFinishTime() > lastRecordedStateTime - Constants.ENSURE_STATE_TIME_WINDOW_SIZE)
                    .mapToDouble(req -> req.getFinishTime() - req.getExecStartTime())
                    .average()
                    .orElse(0.0);
            if (movingAverage <= (allowableRange / 4) + functionIsoResponseTimes.get(entry.getKey())) {
                newState =  Constants.ENSURE_STATE_SAFE;
            } else if (movingAverage <= (allowableRange / 2) + functionIsoResponseTimes.get(entry.getKey())) {
                newState =  Constants.ENSURE_STATE_PRE_WARMING;
            } else if (movingAverage <= ((allowableRange / 4) * 3) + functionIsoResponseTimes.get(entry.getKey())) {
                newState =  Constants.ENSURE_STATE_WARNING;
            } else {
                newState =  Constants.ENSURE_STATE_UNSAFE;
            }
            if (newState > current) {
                current = newState;
            }
        }
        setLastRecordedStateTime(CloudSim.clock());
        return current;
    }

    public ArrayList<ServerlessContainer> getWarmContainers(ServerlessRequest request) {
        ArrayList<ServerlessContainer> warmContainers = new ArrayList<>();
        List<Container> vmContainers = getContainerList();
        for (Container container : vmContainers) {
            if (
                    ((ServerlessContainer) container).getType().equals(request.getRequestFunctionId()) &&
                            ((ServerlessContainer) container).getIdling() ) {
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
        boolean hasEnoughRam = (getContainerRamProvisioner().isSuitableForContainer(container, container.getCurrentRequestedRam()) && getContainerBwProvisioner()
                .isSuitableForContainer(container, container.getCurrentRequestedBw()));
        return ((EnsureServerlessInvoker) vm).getFunctionCapacity(((ServerlessContainer) container).getType()) > 0 && hasEnoughRam;
    }
}
