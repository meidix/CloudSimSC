package org.cloudbus.cloudsim.serverless;

import org.cloudbus.cloudsim.Cloudlet;
import org.cloudbus.cloudsim.container.core.Container;
import org.cloudbus.cloudsim.container.core.ContainerVm;
import org.cloudbus.cloudsim.container.lists.ContainerVmList;
import org.cloudbus.cloudsim.core.CloudSim;
import org.cloudbus.cloudsim.core.SimEvent;

import java.util.*;

public class EnsureServerlessController  extends ServerlessController {
    List<Double> recordTimes;


    public EnsureServerlessController(String name, int overBookingfactor) throws Exception {
        super(name, overBookingfactor);
        recordTimes = new ArrayList<>();
    }

    protected void createContainer(ServerlessRequest cl, String requestId, int brokerId, int vmId) {
        ServerlessContainer container = new ServerlessContainer(containerId, brokerId, requestId, cl.getContainerMIPS(), cl.getNumberOfPes(), cl.getContainerMemory(), Constants.CONTAINER_BW, Constants.CONTAINER_SIZE,"Xen", new ServerlessRequestScheduler(cl.getContainerMIPS(), cl.getNumberOfPes()), Constants.SCHEDULING_INTERVAL, true, false, false, 0, 0, 0);
        ContainerVm vm = ContainerVmList.getById(getVmsCreatedList(), vmId);
        assert vm != null;
        container.setVm(vm);
        getContainerList().add(container);
        if (!(cl ==null)){
            cl.setContainerId(containerId);
        }

        submitContainer(cl, container);
        containerId++;
    }

    public int getMaximumVmCount() {
        ArrayList<Integer> vmUsedList = new ArrayList<>();
        for (Container container : getContainerList()) {
            ServerlessContainer cont = (ServerlessContainer) container;
            vmUsedList.add(cont.getVm().getId());
        }

        Set<Integer> result = new HashSet<>(vmUsedList);
        return result.size();
    }

    public List<Double> getRecordTimes() { return recordTimes; }
    public List<Double> getMeanAverageVmUsageRecords() {return meanAverageVmUsageRecords;}
    public List<Double> getMeanSumOfVmCount() {return meanSumOfVmCount;}

    @Override
    public void processRecordCPUUsage(SimEvent ev){
        int vmUsageSize = meanAverageVmUsageRecords.size();
        super.processRecordCPUUsage(ev);
        if (vmUsageSize < meanAverageVmUsageRecords.size()) {
            recordTimes.add(CloudSim.clock());
        }
    }

    public int getSloViolationCount(HashMap<String, Double> isoResponseTimes) {
        int violations = 0;
        double responseThreshold = 0;
        for (Container container : getContainerList()) {
            ServerlessContainer cont = (ServerlessContainer) container;
            if (isoResponseTimes.containsKey(cont.getType())) {
                responseThreshold = isoResponseTimes.get(cont.getType()) * Constants.ENSURE_LATENCY_THRESHOLD;
            }
            if (responseThreshold > 0) {
                for (ServerlessRequest request: cont.getfinishedTasks()) {
                    if ((request.getFinishTime() - request.getArrivalTime()) > responseThreshold) {
                        violations++;
                    }
                }
            }
        }
        return violations;
    }
}
