package org.cloudbus.cloudsim.serverless;

import org.cloudbus.cloudsim.Log;
import org.cloudbus.cloudsim.container.core.Container;
import org.cloudbus.cloudsim.container.lists.ContainerVmList;
import org.cloudbus.cloudsim.core.CloudSim;

import java.util.*;


/**
 * Loadbalancer class for CloudSimSC extension.
 *
 * @author Anupama Mampage
 * Created on 6/25/2023
 */
public class RequestLoadBalancer {

    /**
     * The broker ID.
     */
    private ServerlessController broker;

    /**
     * The DC.
     */
    private ServerlessDatacenter DC;
    public RequestLoadBalancer(ServerlessController controller, ServerlessDatacenter dc){
        setBroker(controller);
        setServerlessDatacenter(dc);
    }

    public void setBroker(ServerlessController br) {
        this.broker = br;
    }

    public ServerlessController getBroker() {
        return broker;
    }

    public void setServerlessDatacenter(ServerlessDatacenter dc) {
        this.DC = dc;
    }

    public ServerlessDatacenter getServerlessDatacenter() {
        return DC;
    }

    public void routeRequest(ServerlessRequest request){
        if (request.retry > Constants.MAX_RESCHEDULE_TRIES){
            broker.getCloudletList().remove(request);
            request.setSuccess(false);
            broker.getCloudletReceivedList().add(request);
        }
        else if (Constants.SCALE_PER_REQUEST){
            broker.toSubmitOnContainerCreation.add(request);
            broker.createContainer(request, request.getRequestFunctionId(), request.getUserId());
            broker.requestSubmitClock = CloudSim.clock();
        }
        else{
            boolean containerSelected = selectContainer(request);
            if (!containerSelected) {
                broker.getCloudletList().remove(request);

            }
        }

    }

    protected boolean selectContainer(ServerlessRequest task){
//        boolean containerSelected = false ;
        boolean contTypeExists = false;
        int clusterEMA = 0;
        int clusterSMA = 0;
        switch (Constants.CONTAINER_SELECTION_ALGO) {
            /** Selecting container using FF method **/
            case "FF": {
                for (int x = 1; x <= broker.getVmsCreatedList().size(); x++) {
                    ServerlessInvoker vm = (ServerlessInvoker) (ContainerVmList.getById(broker.getVmsCreatedList(), x));
                    assert vm != null;
                    if (vm.getFunctionContainerMap().containsKey(task.getRequestFunctionId())) {
                        contTypeExists = true;
                        List<Container> contList = vm.getFunctionContainerMap().get(task.getRequestFunctionId());
                        int y = 0;
                        for (Container container : contList) {
                            ServerlessContainer cont = (ServerlessContainer) (container);
                            ServerlessRequestScheduler clScheduler = (ServerlessRequestScheduler) (container.getContainerCloudletScheduler());
                            if (clScheduler.isSuitableForRequest(task, cont)) {
                                clScheduler.setTotalCurrentAllocatedRamForRequests(task);
                                clScheduler.setTotalCurrentAllocatedMipsShareForRequests(task);
                                Log.printLine(String.format("Using idling container: container #%s", cont.getId()));

                                task.setContainerId(cont.getId());
                                broker.addToVmTaskMap(task, vm);
                                cont.setRunningTask(task);
                                cont.setIdling(false);
                                cont.setIdleStartTime(0);
//                                if(DC.getContainersToDestroy().contains(cont)){
//                                    ((ServerlessContainer)DC.getContainersToDestroy().get(y)).setIdleStartTime(0);
//                                }
                                broker.setFunctionVmMap(vm, task.getRequestFunctionId());
                                broker.requestSubmitClock = CloudSim.clock();
                                broker.submitRequestToDC(task, vm.getId(), 0, cont.getId());
                                return true;

                            }
                            y++;

                        }

                    }

                }
                break;
            }
            case "ENSURE": {
                for (int x = 1; x <= broker.getVmsCreatedList().size(); x++) {
                    EnsureServerlessInvoker vm = (EnsureServerlessInvoker) (ContainerVmList.getById(broker.getVmsCreatedList(), x));
                    assert vm != null;
                    vm.setFinishedTasksMap(broker.getContainerList());
                    if (vm.getState() == Constants.ENSURE_STATE_UNSAFE) continue;
                    if (vm.getFunctionContainerMap().containsKey(task.getRequestFunctionId())) {
                        contTypeExists = true;
                        for (Container container: vm.getFunctionContainerMap().get(task.getRequestFunctionId())) {
                            ServerlessContainer cont = (ServerlessContainer) (container);
                            ServerlessRequestScheduler clScheduler = (ServerlessRequestScheduler) (cont.getContainerCloudletScheduler());
                            if (clScheduler.isSuitableForRequest(task, cont)) {

                                Log.printLine(String.format("Using idling container: container #%s", cont.getId()));

                                task.setContainerId(cont.getId());
                                broker.addToVmTaskMap(task, vm);
                                cont.setRunningTask(task);
                                cont.setIdling(false);
                                cont.setIdleStartTime(0);
                                broker.setFunctionVmMap(vm, task.getRequestFunctionId());
                                broker.requestSubmitClock = CloudSim.clock();
                                broker.submitRequestToDC(task, x, 0, cont.getId());
                                return true;
                            }
                        }
                    }


                    int capacity = vm.getFunctionCapacity(task.getRequestFunctionId());
                    if (capacity > 0) {
                        broker.toSubmitOnContainerCreation.add(task);
                        ((EnsureServerlessController) broker).createContainer(task, task.getRequestFunctionId(), task.getUserId(), vm.getId());
                        broker.requestSubmitClock = CloudSim.clock();
                        return true;
                    }
                }
                if (task.retry < Constants.MAX_RESCHEDULE_TRIES) {
                    broker.sendFunctionRetryRequest(task);
                    task.retry++;
                }
                return false;
            }
            case "MAAS": {
                List<MaasServerlessInvoker> warmInvokers = new ArrayList<MaasServerlessInvoker>();
                boolean busyInvokers = false;
                List<Integer> EMAList = new ArrayList<>();
                List<Integer> SMAList = new ArrayList<>();
                for (int x = 1; x <= broker.getVmsCreatedList().size(); x++) {
                   MaasServerlessInvoker vm = ContainerVmList.getById(broker.getVmsCreatedList(), x);
                   vm.setFinishedTasksMap(broker.getContainerList());
                   assert vm != null;
                   EMAList.add(vm.getNormalizedEMA(task.getRequestFunctionId()));
                   SMAList.add(vm.getNormalizedSMA(task.getRequestFunctionId()));
                   if (vm.getFunctionContainerMap().containsKey(task.getRequestFunctionId())) {
                        for (Container container: vm.getFunctionContainerMap().get(task.getRequestFunctionId())) {
                            ServerlessContainer cont = (ServerlessContainer) container;
                            MaasServerlessRequestScheduler clScheduler = (MaasServerlessRequestScheduler) (cont.getContainerCloudletScheduler());
                            if (clScheduler.isSuitableForRequest(task, cont)) {
                                warmInvokers.add(vm);
                            } else {
                                busyInvokers = true;
                            }
                        }
                   }
                }
                /*
                * Cluster Level EMA Calculation
                * */
                Collections.sort(EMAList);
                if (!EMAList.isEmpty()) {
                    if (EMAList.size() % 2 == 0) {
                        clusterEMA = EMAList.get(EMAList.size() / 2);
                    } else {
                        clusterEMA = (EMAList.get(EMAList.size() / 2 - 1) + EMAList.get(EMAList.size() / 2)) / 2;
                    }
                }

                /*
                * Cluster Level SMA Calculation
                * */
                Collections.sort(SMAList);
                if (!SMAList.isEmpty()) {
                    if (SMAList.size() % 2 == 0) {
                        clusterSMA = SMAList.get(EMAList.size() / 2);
                    } else {
                        clusterSMA = (SMAList.get(EMAList.size() / 2 - 1) + SMAList.get(EMAList.size() / 2)) / 2;
                    }
                }

                MaasServerlessInvoker selectedVm = null;
                if (!warmInvokers.isEmpty()) {
                    warmInvokers.sort(new Comparator<MaasServerlessInvoker>() {

                        @Override
                        public int compare(MaasServerlessInvoker vm1, MaasServerlessInvoker vm2) {
                            double vm1Util = (1 - (vm1.getAvailableMips() / vm1.getTotalMips()));
                            double vm2Util = (1 - (vm2.getAvailableMips() / vm2.getTotalMips()));
                            return Double.compare(vm2Util, vm1Util);
                        }
                    });
                    Iterator<MaasServerlessInvoker> warmIterator = warmInvokers.iterator();
                    while (warmIterator.hasNext()) {
                        selectedVm = warmIterator.next();
                        if (selectedVm.getNormalizedEMA() < 3) {
                            for (Container container: selectedVm.getFunctionContainerMap().get(task.getRequestFunctionId())) {
                                MaasServerlessRequestScheduler clScheduler = (MaasServerlessRequestScheduler) (container.getContainerCloudletScheduler());
                                if (clScheduler.isSuitableForRequest(task, (ServerlessContainer) container)) {
                                    task.setContainerId(((ServerlessContainer) container).getId());
                                    broker.addToVmTaskMap(task, selectedVm);
                                    ((ServerlessContainer) container).setRunningTask(task);
                                    ((ServerlessContainer) container).setIdling(false);
                                    ((ServerlessContainer) container).setIdleStartTime(0.0);
                                    broker.setFunctionVmMap(selectedVm, task.getRequestFunctionId());
                                    broker.requestSubmitClock = CloudSim.clock();
                                    broker.submitRequestToDC(task, selectedVm.getId(), 0, ((ServerlessContainer) container).getId());
                                    return true;
                                }
                            }
                        }
                    }
                }
                if (busyInvokers && ((clusterEMA <= 1 && clusterSMA > 1 && clusterSMA < 4) || (clusterEMA == 2 && clusterSMA == 3) )) {
                    if (task.retry < Constants.MAX_RESCHEDULE_TRIES) {
                        broker.sendFunctionRetryRequest(task);
                        task.retry++;
                    }
                    return false;
                }
            }
            break;
        }

        if(Constants.CONTAINER_CONCURRENCY && Constants.FUNCTION_HORIZONTAL_AUTOSCALING){
            if (contTypeExists){
                broker.sendFunctionRetryRequest(task);
                Log.printLine(String.format("clock %s Container type exists so rescheduling", CloudSim.clock()));

                task.retry++;
                return false;
            }
            for (int x = 1; x <= broker.getVmsCreatedList().size(); x++) {
                ServerlessInvoker vm = (ServerlessInvoker) (ContainerVmList.getById(broker.getVmsCreatedList(), x));
                assert vm != null;
                if (vm.getFunctionContainerMapPending().containsKey(task.getRequestFunctionId())) {
                    Log.printLine(String.format("clock %s Pending Container of type exists so rescheduling", CloudSim.clock()));

                    broker.sendFunctionRetryRequest(task);
                    task.retry++;
                    return false;
                }

            }
            Log.printLine(String.format("clock %s Container type does not exist so creating new", CloudSim.clock()));

            broker.createContainer(task, task.getRequestFunctionId(), task.getUserId());
            broker.sendFunctionRetryRequest(task);
            task.retry++;

            return false;
        }
        else {
            broker.toSubmitOnContainerCreation.add(task);
            broker.createContainer(task, task.getRequestFunctionId(), task.getUserId());
            broker.requestSubmitClock = CloudSim.clock();

            return true;
        }
    }

}
