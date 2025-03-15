package org.cloudbus.cloudsim.serverless;

import org.cloudbus.cloudsim.Cloudlet;
import org.cloudbus.cloudsim.Consts;
import org.cloudbus.cloudsim.Log;
import org.cloudbus.cloudsim.ResCloudlet;
import org.cloudbus.cloudsim.core.CloudSim;

import java.util.ArrayList;
import java.util.List;

public class MaasServerlessRequestScheduler extends ServerlessRequestScheduler {

    private double longestRunTimeContainer = 0;
    private double containerQueueTime = 0;

    public MaasServerlessRequestScheduler(double mips, int numberOfPes) {
        super(mips, numberOfPes);
    }

    @Override
    public boolean isSuitableForRequest(ServerlessRequest cl, ServerlessContainer cont) {
        Log.printLine(String.format("Current allocated ram of cont #%s is #%s and requested ram of cl #%s is %s", cont.getId(), getTotalCurrentAllocatedRamForRequests(), cl.getCloudletId(), cl.getContainerMemory()*cl.getUtilizationOfRam()));
        return (cl.getContainerMemory() * cl.getUtilizationOfRam() <= (cont.getRam() - getTotalCurrentAllocatedRamForRequests())) && (cl.getNumberOfPes() <= getNumberOfPes());
    }

    public double getTotalCurrentMipsShare() {
        double total = 0;
        for (double mips: getCurrentMipsShare()) {
            total += mips;
        }
        return total;
    }

    @Override
    public double updateContainerProcessing(double currentTime, List<Double> mipsShare, ServerlessInvoker vm) {
        setCurrentMipsShare(mipsShare);

        int cpus=0;

        for (Double mips : mipsShare) { // count the CPUs available to the VMM
            if (mips > 0) {
                cpus++;
            }
        }

        currentCpus = cpus;
        double timeSpan = currentTime - getPreviousTime();
        double nextEvent = Double.MAX_VALUE;
        List<ResCloudlet> requestsToFinish = new ArrayList<>();

        for (ResCloudlet rcl : getCloudletExecList()) {
            rcl.updateCloudletFinishedSoFar((long) (timeSpan
                    * rcl.getCloudlet().getNumberOfPes()*((ServerlessRequest)(rcl.getCloudlet())).getUtilizationOfCpu()*getTotalCurrentMipsShare()* Consts.MILLION));

        }
        if (getCloudletExecList().size() == 0 && getCloudletWaitingList().size() == 0) {

            setPreviousTime(currentTime);
            return 0.0;
        }

        int finished = 0;
        int pesFreed = 0;
        for (ResCloudlet rcl : getCloudletExecList()) {
            // finished anyway, rounding issue...
            if (rcl.getRemainingCloudletLength() == 0) { // finished: remove from the list
                requestsToFinish.add(rcl);
                finished++;
                pesFreed+=rcl.getNumberOfPes();
            }
        }
        usedPes -=pesFreed;

        for (ResCloudlet rgl : requestsToFinish) {
            getCloudletExecList().remove(rgl);
            cloudletFinish(rgl);
        }

        List<ResCloudlet> toRemove = new ArrayList<ResCloudlet>();
        if (!getCloudletWaitingList().isEmpty()) {
            for (int i = 0; i < finished; i++) {
                toRemove.clear();
                for (ResCloudlet rcl : getCloudletWaitingList()) {
                    if ((currentCpus - usedPes) >= rcl.getNumberOfPes()) {
//                        if(rcl.getCloudlet().getCloudletId()==815){
//                            System.out.println(CloudSim.clock()+" request #815 running: Debug");
//                        }
                        rcl.setCloudletStatus(Cloudlet.INEXEC);
                        //vm.getRunningrequestStack().push((ServerlessRequest) rcl.getrequest());
                        boolean added = false;
                        for(int x=0; x< vm.getRunningRequestList().size(); x++){
                            if((((ServerlessRequest) rcl.getCloudlet()).getArrivalTime()+((ServerlessRequest) rcl.getCloudlet()).getMaxExecTime()<=vm.getRunningRequestList().get(x).getArrivalTime()+vm.getRunningRequestList().get(x).getMaxExecTime())){
                                vm.getRunningRequestList().add(x,((ServerlessRequest) rcl.getCloudlet()));
                                added = true;
                                break;
                            }
                        }
                        if(added == false){
                            vm.getRunningRequestList(). add((ServerlessRequest) rcl.getCloudlet());
                        }
                        for (int k = 0; k < rcl.getNumberOfPes(); k++) {
                            rcl.setMachineAndPeId(0, i);
                        }
                        getCloudletExecList().add(rcl);

                        /** To enable average latency of application */
                        vm.addToVmTaskExecutionMap((ServerlessRequest)rcl.getCloudlet(),vm);
                        usedPes += rcl.getNumberOfPes();
                        toRemove.add(rcl);
                        break;
                    }
                }
                getCloudletWaitingList().removeAll(toRemove);
            }
        }

        for (ResCloudlet rcl : getCloudletExecList()) {
            double estimatedFinishTime = getEstimatedFinishTime(rcl, currentTime);
            if (estimatedFinishTime < nextEvent) {
                nextEvent = estimatedFinishTime;
            }

            ServerlessRequest task = (ServerlessRequest)(rcl.getCloudlet());
            /** Record the longest remaining execution time of the container*/
            containerQueueTime += task.getMaxExecTime()+ task.getArrivalTime()- CloudSim.clock();
            if (task.getMaxExecTime()+ task.getArrivalTime()-CloudSim.clock()> longestRunTimeContainer) {
                longestRunTimeContainer = task.getMaxExecTime()+ task.getArrivalTime()-CloudSim.clock();
            }
        }

        for (ResCloudlet rcl : getCloudletWaitingList()) {
            ServerlessRequest task = (ServerlessRequest)(rcl.getCloudlet());
            containerQueueTime += task.getMaxExecTime();
            /** Record the longest remaining execution time of the container*/
            if (task.getMaxExecTime()+ task.getArrivalTime()-CloudSim.clock()> longestRunTimeContainer) {
                longestRunTimeContainer = task.getMaxExecTime()+ task.getArrivalTime()-CloudSim.clock();
            }

        }

        setPreviousTime(currentTime);
        requestsToFinish.clear();
        return nextEvent;
    }
}
