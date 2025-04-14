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

    protected List<Double> vmUsageRecords = new ArrayList<>();
    protected List<Double> averageWorkloadUsageRecords = new ArrayList<>();


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

        double utilization = 0;
        int vmCount = 0;
        double sum=0;

        for(int x=0; x< getVmsCreatedList().size(); x++){
            utilization   = 1 - getVmsCreatedList().get(x).getAvailableMips() / getVmsCreatedList().get(x).getTotalMips();
            if(utilization>0){
                ((ServerlessInvoker)getVmsCreatedList().get(x)).used = true;
                vmCount++;
                sum += utilization;
            }
        }
        if(sum>0){
            vmUsageRecords.add(sum);
            averageVmUsageRecords.add(sum/vmCount);
            vmCountList.add(vmCount);
        }

        double sumOfAverage = 0;
        double sumOfVmCount = 0;
        double sumOfWorkloadUsage = 0;
        if(averageVmUsageRecords.size()==Constants.CPU_HISTORY_LENGTH){
            for(int x=0; x<Constants.CPU_HISTORY_LENGTH; x++){
                sumOfAverage += averageVmUsageRecords.get(x);
                sumOfVmCount += vmCountList.get(x);
                sumOfWorkloadUsage += vmUsageRecords.get(x);

            }
            meanAverageVmUsageRecords.add(sumOfAverage/Constants.CPU_HISTORY_LENGTH);
            meanSumOfVmCount.add(sumOfVmCount/Constants.CPU_HISTORY_LENGTH);
            recordTimes.add(CloudSim.clock());
            averageWorkloadUsageRecords.add(sumOfWorkloadUsage/Constants.CPU_HISTORY_LENGTH);
            averageVmUsageRecords.clear();
            vmCountList.clear();
            vmUsageRecords.clear();
        }

        send(this.getId(), Constants.CPU_USAGE_MONITORING_INTERVAL, CloudSimSCTags.RECORD_CPU_USAGE);
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

    public int getNumberofColdStarts() {
        int count = 0;
        List<ServerlessContainer> containersList = getContainerList();
        for (ServerlessContainer container : containersList) {
            if (container.getfinishedTasks().size() > 0) {
                count++;
            }
        }
        return count;
    }

    public ArrayList<ServerlessRequest> getColdStartRequests() {
        ArrayList<ServerlessRequest> requestList = new ArrayList<>();
        for (Container container: getContainerList()) {
            if (!((ServerlessContainer) container).getfinishedTasks().isEmpty()) {
                ServerlessRequest request = ((ServerlessContainer) container).getfinishedTasks().get(0);
                requestList.add(request);
            }
        }
        return requestList;
    }

    public List<Double> getWorkloadUsageRecords() {
        return averageWorkloadUsageRecords;
    }

    public double getWorkloadAverageUsage() {
        double sum = 0;
        for (double vmUsage: averageVmUsageRecords) {
            sum += vmUsage;
        }
        return sum/averageWorkloadUsageRecords.size();
    }
}
