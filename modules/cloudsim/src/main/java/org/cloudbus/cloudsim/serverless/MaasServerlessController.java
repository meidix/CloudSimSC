package org.cloudbus.cloudsim.serverless;

import org.cloudbus.cloudsim.Log;
import org.cloudbus.cloudsim.container.core.Container;
import org.cloudbus.cloudsim.container.core.containerCloudSimTags;
import org.cloudbus.cloudsim.core.CloudSim;
import org.cloudbus.cloudsim.core.SimEvent;

import java.util.*;

public class MaasServerlessController extends ServerlessController {

    ArrayList<Double> recordTimes;

    public MaasServerlessController(String name, int overBookingfactor) throws Exception {
        super(name, overBookingfactor);
        recordTimes = new ArrayList<>();
    }

    @Override
    protected void createContainer(ServerlessRequest cl, String requestId, int brokerId) {
        ServerlessContainer container = new ServerlessContainer(containerId, brokerId, requestId, cl.getContainerMIPS(), cl.getNumberOfPes(), cl.getContainerMemory(), Constants.CONTAINER_BW, Constants.CONTAINER_SIZE,"Xen", new MaasServerlessRequestScheduler(cl.getContainerMIPS(), cl.getNumberOfPes()), Constants.SCHEDULING_INTERVAL, true, false, false, 0, 0, 0);
        getContainerList().add(container);
        if (!(cl ==null)){
            cl.setContainerId(containerId);
        }

        submitContainer(cl, container);
        containerId++;
    }

    @Override
    protected void processScaledContainer(SimEvent ev){
        String[] data = (String[]) ev.getData();
        int brokerId = Integer.parseInt(data[0]);
        String requestId = data[1];
        double containerMips = Double.parseDouble(data[2]);
        int containerRAM = (int)Double.parseDouble(data[3]);
        int containerPES = (int)Double.parseDouble(data[4]);
        ServerlessContainer container = new ServerlessContainer(containerId, brokerId, requestId, containerMips, containerPES, containerRAM, Constants.CONTAINER_BW, Constants.CONTAINER_SIZE,"Xen", new MaasServerlessRequestScheduler(containerMips, containerPES), Constants.SCHEDULING_INTERVAL, true, false, false, 0, 0, 0);
        getContainerList().add(container);
        container.setWorkloadMips(container.getMips());
        sendNow(getDatacenterIdsList().get(0), containerCloudSimTags.CONTAINER_SUBMIT, container);
        Log.printLine(String.format("clock %s Creating scaled container: container #%s", CloudSim.clock(), container.getId()));

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

    public int getSloViolationCount(HashMap<String, Double> isoResponseTimes) {
        int violations = 0;
        double responseThreshold = 0;
        for (Container container : getContainerList()) {
            ServerlessContainer cont = (ServerlessContainer) container;
            if (isoResponseTimes.containsKey(cont.getType())) {
                responseThreshold = isoResponseTimes.get(cont.getType()) * Constants.MAAS_SLO;
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

}
