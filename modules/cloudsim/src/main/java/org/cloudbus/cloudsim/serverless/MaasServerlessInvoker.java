package org.cloudbus.cloudsim.serverless;

import org.cloudbus.cloudsim.container.containerProvisioners.ContainerBwProvisioner;
import org.cloudbus.cloudsim.container.containerProvisioners.ContainerPe;
import org.cloudbus.cloudsim.container.containerProvisioners.ContainerRamProvisioner;
import org.cloudbus.cloudsim.container.core.Container;
import org.cloudbus.cloudsim.container.schedulers.ContainerScheduler;
import org.cloudbus.cloudsim.core.CloudSim;

import java.util.*;
import java.util.stream.Collectors;

public class MaasServerlessInvoker  extends ServerlessInvokerRequestAware {

    private HashMap<String, Double> isoResponseTimes;
    private double lastRecordTime;

    public MaasServerlessInvoker(int id, int userId, double mips, float ram, long bw, long size, String vmm, ContainerScheduler containerScheduler, ContainerRamProvisioner containerRamProvisioner, ContainerBwProvisioner containerBwProvisioner, List<? extends ContainerPe> peList, double schedulingInterval, HashMap<String, Double> isoResponseTimes) {
        super(id, userId, mips, ram, bw, size, vmm, containerScheduler, containerRamProvisioner, containerBwProvisioner, peList, schedulingInterval);
        setIsoResponseTimes(isoResponseTimes);
        setLastRecordTime(0.0);
    }

    protected void setLastRecordTime(double lastRecordTime) { this.lastRecordTime = lastRecordTime; }

    protected void setIsoResponseTimes(HashMap<String, Double> isoResponseTimes) {
        this.isoResponseTimes = isoResponseTimes;
    }

    protected List<ServerlessRequest> getCurrentWindowRequests(String functionId) {
       ArrayList<ServerlessRequest> requests =  finishedTasksMap.get(functionId);
       if (requests == null) {
           return null;
       }
       int start = Math.max(requests.size() - Constants.WINDOW_SIZE, 0);
       return requests
               .subList(start, requests.size())
               .stream()
               .filter(req -> req.getFinishTime() > lastRecordTime - Constants.MAAS_TIME_SLOT )
               .collect(Collectors.toList());
    }

    public double getEMA(String functionId) {
        List<ServerlessRequest> requests = getCurrentWindowRequests(functionId);
        if (requests == null || requests.isEmpty()) {
            return isoResponseTimes.get(functionId); // or a default value if no requests are present
        }

        double ema = 0;
        ema = requests.get(0).getFinishTime() - requests.get(0).getExecStartTime();
        double alpha = Constants.MAAS_ALPHA;

        // Process the requests in chronological order (oldest to newest within the window)
        for (int i = 1; i < requests.size(); i++) {
            ServerlessRequest request = requests.get(i);
            double latest = request.getFinishTime() - request.getExecStartTime();
            ema = alpha * latest + (1 - alpha) * ema;
        }
        setLastRecordTime(CloudSim.clock());
        return ema;
    }

    public int getNormalizedEMA(String functionId) {
        double ema = getEMA(functionId);
        Double isolationTime = isoResponseTimes.get(functionId);
        if (isolationTime == null || isolationTime <= 0) {
            // Fallback normalization if no isolation time is provided.
            return 0;
        }
        return normalizeValue(isolationTime, ema);
    }

    public double getNormalizedEMA() {
        ArrayList<Integer> normalizedValues = new ArrayList<>();
        for (String functionId : finishedTasksMap.keySet()) {
            normalizedValues.add(getNormalizedEMA(functionId));
        }
        return calculateMedian(normalizedValues);
    }

    public double getSMA(String functionId) {
        List<ServerlessRequest> requests = getCurrentWindowRequests(functionId);
        if (requests == null || requests.isEmpty()) {
            return isoResponseTimes.get(functionId);
        }

        double sum = 0;
        for (int i = 0; i < requests.size(); i++) {
            ServerlessRequest request = requests.get(i);
            sum += request.getFinishTime() - request.getExecStartTime();
        }
        setLastRecordTime(CloudSim.clock());
        return sum / requests.size();
    }

    public int getNormalizedSMA(String functionId) {
        double sma = getSMA(functionId);
        Double isolationTime = isoResponseTimes.get(functionId);
        if (isolationTime == null || isolationTime <= 0) {
            return 0;
        }
        return normalizeValue(isolationTime, sma);
    }

    public double getNormalizedSMA() {
        ArrayList<Integer> normalizedValues = new ArrayList<>();
        for (String functionId : finishedTasksMap.keySet()) {
            normalizedValues.add(getNormalizedSMA(functionId));
        }
        return calculateMedian(normalizedValues);
    }

    private double calculateMedian(List<Integer> values) {
        if (values == null || values.isEmpty()) {
            return 1;
        }
        Collections.sort(values);
        int size = values.size();
        if (size % 2 == 0) {
            return (values.get(size/2 - 1) + values.get(size/2)) / 2.0;
        } else {
            return values.get(size/2);
        }
    }

    private int normalizeValue(double isoResponseTime, double value) {
        double latencyRange = isoResponseTime * (Constants.MAAS_SLO - 1);
        if (value <= (latencyRange / 3) + isoResponseTime) {
            return 1;
        } else if (value <= ( 2 * (latencyRange / 3)) + isoResponseTime) {
            return 2;
        } else if (value <= latencyRange + isoResponseTime) {
            return 3;
        } else {
            return 4;
        }
    }

    protected int getMipsAllocatedSoFar() {
        int mips = 0;
        for (Container containers: getContainerList()) {
            mips += containers.getMips();
        }
        return mips;
    }

    @Override
    public boolean isSuitableForContainer(Container container, ServerlessInvoker vm) {
        int ema = ((MaasServerlessInvoker) vm).getNormalizedEMA(((ServerlessContainer) container).getType());
        boolean hasCpuCapacity = container.getMips() <= ( 1.05 * vm.getTotalMips()) -  getMipsAllocatedSoFar();
        return ( ema < 3 &&  hasCpuCapacity && getContainerRamProvisioner().isSuitableForContainer(container, container.getCurrentRequestedRam()) && getContainerBwProvisioner()
                .isSuitableForContainer(container, container.getCurrentRequestedBw()));
    }

}
