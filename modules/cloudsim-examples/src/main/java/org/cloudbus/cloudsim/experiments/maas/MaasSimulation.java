package org.cloudbus.cloudsim.experiments.maas;

import com.opencsv.CSVWriter;
import org.cloudbus.cloudsim.Log;
import org.cloudbus.cloudsim.Storage;
import org.cloudbus.cloudsim.UtilizationModelFull;
import org.cloudbus.cloudsim.container.containerProvisioners.ContainerBwProvisionerSimple;
import org.cloudbus.cloudsim.container.containerProvisioners.ContainerPe;
import org.cloudbus.cloudsim.container.containerProvisioners.CotainerPeProvisionerSimple;
import org.cloudbus.cloudsim.container.containerVmProvisioners.ContainerVmBwProvisionerSimple;
import org.cloudbus.cloudsim.container.containerVmProvisioners.ContainerVmPe;
import org.cloudbus.cloudsim.container.containerVmProvisioners.ContainerVmPeProvisionerSimple;
import org.cloudbus.cloudsim.container.containerVmProvisioners.ContainerVmRamProvisionerSimple;
import org.cloudbus.cloudsim.container.core.*;
import org.cloudbus.cloudsim.container.resourceAllocators.ContainerVmAllocationPolicy;
import org.cloudbus.cloudsim.container.resourceAllocators.PowerContainerVmAllocationSimple;
import org.cloudbus.cloudsim.container.schedulers.ContainerVmSchedulerTimeSharedOverSubscription;
import org.cloudbus.cloudsim.container.utils.IDs;
import org.cloudbus.cloudsim.core.CloudSim;
import org.cloudbus.cloudsim.serverless.*;

import java.io.*;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.text.DecimalFormat;
import java.util.*;

public class MaasSimulation {

    /** The vmlist. */
    private static List<MaasServerlessInvoker> vmList;
    private static String csvResultFilePath;

    private int overBookingfactor = 80;
    private static int controllerId;

    private static RequestLoadBalancer loadBalancer;

    private static ServerlessDatacenter DC;

    private static ServerlessController controller;

    /**
     * Creates main() to run this experiment
     */
    public static void main(String[] args) {

        Log.printLine("Starting MaasSimulation...");

        try {
            // First step: Initialize the CloudSim package. It should be called
            // before creating any entities.
            int num_user = 1; // number of cloud users
            Calendar calendar = Calendar.getInstance();
            boolean trace_flag = false; // mean trace events
            csvResultFilePath = "Experiments/Simulation/";

            // Initialize the CloudSim library
            CloudSim.init(num_user, calendar, trace_flag);

            controller = createBroker();
            controllerId = controller.getId();

            // Second step: Create Datacenters
            // Datacenters are the resource providers in CloudSim. We need at least one of
            // them to run a CloudSim simulation
            DC = createDatacenter("datacenter");

            // Third step: Create the virtual machines
            vmList = createVmList(controllerId);

            // Fourth step: submit vm list to the broker
            controller.submitVmList(vmList);

            // Fifth step: Create a load balancer
            loadBalancer = new RequestLoadBalancer(controller, DC);
            controller.setLoadBalancer(loadBalancer);
            controller.setServerlessDatacenter(DC);

            // Sixth step: Create the request workload
            createRequests();

            // the time at which the simulation has to be terminated.
            CloudSim.terminateSimulation(3000.00);

            // Starting the simualtion
            CloudSim.startSimulation();

            // Stopping the simualtion.
            CloudSim.stopSimulation();

            // Printing the results when the simulation is finished.
            List<ContainerCloudlet> finishedRequests = controller.getCloudletReceivedList();

            saveResultsAsCSV();
            saveContainersAsCSV();
            saveUtilizationSummary();
            saveResourceUsageAsCSV();
            saveColdStartRequestsAsCSV();
            // printRequestList(finishedRequests);
            printContainerList(controller.getContainerList());
            Log.printLine(controller.getContainerList().size());
            Log.printLine(controller.getContainersDestroyedList().size());
            // printContainerList(containerList);
            if (Constants.MONITORING) {
//        printVmUpDownTime();
                printVmUtilization();
                System.out.println("Number of Finished Requests: " +  finishedRequests.size());
            }

            // Writing the results to a file when the simulation is finished.
//       writeDataLineByLine(finishedRequests);
            Log.printLine("MaasSimulation finished!");
        } catch (Exception e) {
            e.printStackTrace();
            Log.printLine("Unwanted errors happen");
        }
    }

    private static void saveColdStartRequestsAsCSV() {
        String path = csvResultFilePath + "MaasSimulation/cold-starts.csv";
        List<ServerlessRequest> requestList = ((MaasServerlessController) controller).getColdStartRequests();
        try {
            // Ensure all directories in the path exist
            File file = new File(path);
            Files.createDirectories(Paths.get(file.getParent()));

            try (CSVWriter writer = new CSVWriter(new FileWriter(path))) {
                // Define CSV Header
                String[] header = {"Request ID", "Function ID", "Arrival Time", "Start Time",  "Finish Time", "ExecutionTime", "Response Time"};
                writer.writeNext(header);

                DecimalFormat dft = new DecimalFormat("####.##");

                for (ServerlessRequest request : requestList) {
                    if (!request.getSuccess()) {continue;}
                    // Extract relevant request data
                    int requestId = request.getCloudletId();
                    String functionId =  request.getRequestFunctionId();
                    double startTime = request.getExecStartTime();
                    double arrivalTime = request.getArrivalTime();
                    double finishTime = request.getFinishTime();
                    double executionTime = request.getFinishTime() - request.getExecStartTime();
                    double responseTime = finishTime -  request.getArrivalTime();

                    // Format values for clarity
                    String[] data = {
                            String.valueOf(requestId),
                            functionId,
                            dft.format(arrivalTime),
                            dft.format(startTime),
                            dft.format(finishTime),
                            dft.format(executionTime),
                            dft.format(responseTime)
                    };

                    writer.writeNext(data);
                }

                System.out.println("✅ Simulation results saved to: " + path);

            } catch (IOException e) {
                e.printStackTrace();
                System.err.println("❌ Error writing to CSV file: " + path);
            }
        }
        catch (IOException e) {
            e.printStackTrace();
            System.err.println("❌ Error Creating the Path: " + path);
        }

    }

    private static void saveUtilizationSummary() {
        String path = csvResultFilePath + "MaasSimulation/summary.txt";
        try {
            File file = new File(path);
            Files.createDirectories(Paths.get(file.getParent()));
            try (BufferedWriter bw = new BufferedWriter(new FileWriter(file))) {
                bw.write("Summary of : " + Constants.FUNCTION_REQUESTS_FILENAME + " worklaod\r\n" );
                bw.write("=========== CONTAINER OUTPUT ==========\r\n");
                bw.write("Container ID\tVM ID\tStart Time\tFinish Time\tNumber of Requests Served\r\n");
                DecimalFormat dft = new DecimalFormat("###.##");
                for (Container container : controller.getContainerList()) {
                    ServerlessContainer cont = (ServerlessContainer) container;
                    bw.write("\t\t" + cont.getId() + "\t\t" + cont.getVm().getId() + "\t\t" +
                            dft.format(cont.getStartTime()) + "\t\t" + dft.format(cont.getFinishTime()) + "\t\t" +
                            cont.getfinishedTasks().size() + "\r\n"
                    );
                }

                bw.write("=========== CONTAINER OUTPUT ==========\r\n");
                bw.write("Number of Containers Created: " +  controller.getContainerList().size() + "\r\n");
                bw.write("Number of Containers Destroyed: " +  controller.getContainersDestroyedList().size() + "\r\n");


                bw.write("Number of Requests Served: " +  controller.getCloudletReceivedList().size() + "\r\n");
                bw.write("Average Number of Vms under Load: " + controller.getAverageVmCount() + "\r\n");
                bw.write("Total Number of Vms used: " + ((MaasServerlessController) controller).getMaximumVmCount() + "\r\n");
                bw.write("Average CPU Utilization of Vms: " + controller.getAverageResourceUtilization() + "\r\n");
                bw.write("Average Workload Usage: " + ((MaasServerlessController) controller).getWorkloadAverageUsage() + "\r\n");
                bw.write("Number of SLO Violations: " + ((MaasServerlessController) controller).getSloViolationCount(getFunctionsMetadata()) + "\r\n");
                bw.write("Number of Cold Start Executions: " + ((MaasServerlessController) controller).getColdStartRequests().size() + "\r\n");

            } catch (IOException e) {
                e.printStackTrace();
                System.err.println("❌ Error writing to summary file: " + path);
            }

        } catch (IOException err) {
            err.printStackTrace();
            System.err.println("❌ Error writing to summary file: " + path);
        }

    }

    private static HashMap<String, Double> getFunctionsMetadata() {
        HashMap<String, Double> functionsMetadata = new HashMap<>();

        try {
            BufferedReader br = new BufferedReader(new FileReader(Constants.FUNCTION_METADATA_FILENAME));
            String line = null;
            String csvSplitBy = ",";

            while ((line = br.readLine()) != null) {
                String[] data = line.split(csvSplitBy);
                if (String.valueOf(data[0]).equals("Function ID")) { continue; }
                functionsMetadata.put(data[0], Double.parseDouble(data[1]));
            }
        } catch (IOException e) {
            e.printStackTrace();
            System.exit(1);
        }

        return functionsMetadata;

    }

    private static String getCSVResultsFilePath() {
        return csvResultFilePath + "MaasSimulation/results.csv";
    }

    private static void saveResourceUsageAsCSV() {
        String path = csvResultFilePath + "MaasSimulation/resources.csv";
        List<Double> vmUtilizations = ((MaasServerlessController) controller).getMeanAverageVmUsageRecords();
        List<Double> vmCounts = ((MaasServerlessController) controller).getMeanSumOfVmCount();
        List<Double> recordTimes = ((MaasServerlessController) controller).getRecordTimes();
        List<Double> workloadUsageRecords = ((MaasServerlessController) controller).getWorkloadUsageRecords();

        String[] header = {"clock", "utilization", "count", "workload usage"};
        DecimalFormat dft = new DecimalFormat("###.##");
        DecimalFormat bdft = new DecimalFormat("#####.####");
        try (CSVWriter writer = new CSVWriter(new FileWriter(path))) {
            writer.writeNext(header);
            for (int i = 0; i < recordTimes.size(); i++) {
                String[] data = {
                        dft.format(recordTimes.get(i)),
                        dft.format(vmUtilizations.get(i)),
                        dft.format(vmCounts.get(i)),
                        bdft.format(workloadUsageRecords.get(i))
                };
                writer.writeNext(data);
            }
            System.out.println("Saved Resource Data to " + path);

        } catch (IOException e) {
            e.printStackTrace();
            System.err.println("❌ Error writing to CSV file: " + path);
        }
    }

    private static void saveContainersAsCSV() {
        String path = csvResultFilePath + "MaasSimulation/containers.csv";
        List<ServerlessContainer> containers = controller.getContainerList();
        try {
            File file = new File(path);
            Files.createDirectories(Paths.get(file.getParent()));

            try (CSVWriter writer = new CSVWriter(new FileWriter(path))) {
                String[] header = {"Container ID", "VM ID", "Function ID", "Start Time", "Finish Time", "Finished Request Count"};
                writer.writeNext(header);
                DecimalFormat dft = new DecimalFormat("####.##");
                for (ServerlessContainer container : containers) {
                   String[] data = {
                           String.valueOf(container.getId()),
                           String.valueOf(container.getVm().getId()),
                           container.getType(),
                           dft.format(container.getStartTime()),
                           dft.format(container.getFinishTime()),
                           String.valueOf(container.getfinishedTasks().size())
                   };
                   writer.writeNext(data);
                }
                System.out.println("Saved Container Data to " + path);
            } catch (IOException e) {
                e.printStackTrace();
                System.err.println("❌ Error writing to CSV file: " + path);
            }

        }
        catch (IOException e) {
            e.printStackTrace();
            System.err.println("❌ Error Creating the Path: " + path);
        }
    }

    private static void saveResultsAsCSV() {
        String path = getCSVResultsFilePath();
        List<ServerlessRequest> requestList = controller.getCloudletReceivedList();
        try {
            // Ensure all directories in the path exist
            File file = new File(path);
            Files.createDirectories(Paths.get(file.getParent()));

            try (CSVWriter writer = new CSVWriter(new FileWriter(path))) {
                // Define CSV Header
                String[] header = {"Request ID", "Function ID", "Arrival Time", "Start Time",  "Finish Time", "ExecutionTime", "Response Time"};
                writer.writeNext(header);

                DecimalFormat dft = new DecimalFormat("####.##");

                for (ServerlessRequest request : requestList) {
                    if (!request.getSuccess()) {continue;}
                    // Extract relevant request data
                    int requestId = request.getCloudletId();
                    String functionId =  request.getRequestFunctionId();
                    double startTime = request.getExecStartTime();
                    double arrivalTime = request.getArrivalTime();
                    double finishTime = request.getFinishTime();
                    double executionTime = request.getFinishTime() - request.getExecStartTime();
                    double responseTime = finishTime -  request.getArrivalTime();

                    // Format values for clarity
                    String[] data = {
                            String.valueOf(requestId),
                            functionId,
                            dft.format(arrivalTime),
                            dft.format(startTime),
                            dft.format(finishTime),
                            dft.format(executionTime),
                            dft.format(responseTime)
                    };

                    writer.writeNext(data);
                }

                System.out.println("✅ Simulation results saved to: " + path);

            } catch (IOException e) {
                e.printStackTrace();
                System.err.println("❌ Error writing to CSV file: " + path);
            }
        }
        catch (IOException e) {
            e.printStackTrace();
            System.err.println("❌ Error Creating the Path: " + path);
        }
    }

    private static void createRequests() throws IOException {

        BufferedReader br = new BufferedReader(new FileReader(Constants.FUNCTION_REQUESTS_FILENAME));
        String line = null;
        String cvsSplitBy = ",";

        // concurrency is enabled
        UtilizationModelPartial utilizationModelPar = new UtilizationModelPartial();
        UtilizationModelFull utilizationModel = new UtilizationModelFull();

        long fileSize = 10L;
        long outputSize = 10L;
        double cpuShareReq = 1.0d;
        double memShareReq = 1.0d;
        double arrivalTime;
        String functionID;
        long requestLength;
        int pesNumber;
        int containerMemory;
        long containerMips;


        while ((line = br.readLine()) != null) {
            String[] data = line.split(cvsSplitBy);
            int createdRequests = 0;

            ServerlessRequest request = null;
            arrivalTime = Double.parseDouble(data[0]);
            functionID = String.valueOf(data[1]);
            requestLength = Long.parseLong(data[2]);
            pesNumber = Integer.parseInt(data[3]);
            containerMemory = Integer.parseInt(data[4]);
            containerMips = Math.min(Long.parseLong(data[5]), (long) Constants.VM_MIPS[0] * pesNumber);
            try {
                request = new ServerlessRequest(
                        IDs.pollId(ServerlessRequest.class),
                        arrivalTime, functionID, requestLength, pesNumber, containerMemory,
                        containerMips, cpuShareReq, memShareReq, fileSize, outputSize, utilizationModelPar,
                        utilizationModelPar, utilizationModel, 0, true);
                System.out.println("request No " + request.getCloudletId());
            } catch (Exception e) {
                e.printStackTrace();
                br.close();
                System.exit(0);
            }
            request.setUserId(controller.getId());
            controller.noOfTasks++;
            System.out
                    .println(CloudSim.clock() + " request created. This request arrival time is :" + arrivalTime);
            controller.requestArrivalTime.add(arrivalTime + Constants.FUNCTION_SCHEDULING_DELAY);
            controller.requestQueue.add(request);
            createdRequests += 1;
        }
        br.close();
    }

    private static ArrayList<MaasServerlessInvoker> createVmList(int brokerId) {
        ArrayList<MaasServerlessInvoker> containerVms = new ArrayList<MaasServerlessInvoker>();
        HashMap<String, Double> functionsMetadata = new HashMap<>();

        try {
            BufferedReader br = new BufferedReader(new FileReader(Constants.FUNCTION_METADATA_FILENAME));
            String line = null;
            String csvSplitBy = ",";

            while ((line = br.readLine()) != null) {
                String[] data = line.split(csvSplitBy);
                if (String.valueOf(data[0]).equals("Function ID")) { continue; }
                functionsMetadata.put(data[0], Double.parseDouble(data[1]));
            }
        } catch (IOException e) {
            e.printStackTrace();
            System.exit(1);
        }

        for (int i = 0; i < Constants.NUMBER_VMS; ++i) {
            ArrayList<ContainerPe> peList = new ArrayList<ContainerPe>();
            Random rand = new Random();
            int vmType = rand.nextInt(4);
            for (int j = 0; j < Constants.VM_PES[vmType]; ++j) {
                peList.add(new ContainerPe(j,
                        new CotainerPeProvisionerSimple((double) Constants.VM_MIPS[vmType])));
            }
            containerVms.add(new MaasServerlessInvoker(IDs.pollId(ContainerVm.class), brokerId,
                    (double) Constants.VM_MIPS[vmType], (float) Constants.VM_RAM[vmType],
                    Constants.VM_BW, Constants.VM_SIZE, "Xen",
                    new ServerlessContainerScheduler(peList),
                    new ServerlessContainerRamProvisioner(Constants.VM_RAM[vmType]),
                    new ContainerBwProvisionerSimple(Constants.VM_BW),
                    peList, Constants.SCHEDULING_INTERVAL, functionsMetadata));

        }

        return containerVms;
    }

    private static ServerlessController createBroker() {

        MaasServerlessController controller = null;
        int overBookingFactor = 80;

        try {
            controller = new MaasServerlessController("Broker", overBookingFactor);
        } catch (Exception var2) {
            var2.printStackTrace();
            System.exit(0);
        }

        return controller;
    }

    public static ServerlessDatacenter createDatacenter(String name) throws Exception {
        String arch = "x86";
        String os = "Linux";
        String vmm = "Xen";
        String logAddress = "Experiments/simulation1/Results";
        double time_zone = 10.0D;
        double cost = 3.0D;
        double costPerMem = 0.05D;
        double costPerStorage = 0.001D;
        double costPerBw = 0.0D;

        List<ContainerHost> hostList = createHostList(Constants.NUMBER_HOSTS);
        ContainerVmAllocationPolicy vmAllocationPolicy = new PowerContainerVmAllocationSimple(hostList);
//     Allocating vms to container
        FunctionScheduler containerAllocationPolicy = new MaaSFunctionScheduler();

        ContainerDatacenterCharacteristics characteristics = new ContainerDatacenterCharacteristics(arch, os, vmm, hostList,
                time_zone, cost, costPerMem, costPerStorage,
                costPerBw);
        /** Set datacenter monitoring to true if metrics monitoring is required **/
        EnsureServerlessDatacenter datacenter = new EnsureServerlessDatacenter(name, characteristics, vmAllocationPolicy,
                containerAllocationPolicy, new LinkedList<Storage>(), Constants.SCHEDULING_INTERVAL,
                getExperimentName("SimTest1", String.valueOf(80)), logAddress,
                Constants.VM_STARTTUP_DELAY, Constants.CONTAINER_STARTTUP_DELAY, Constants.MONITORING);

        return datacenter;
    }

    private static String getExperimentName(String... args) {
        StringBuilder experimentName = new StringBuilder();

        for (int i = 0; i < args.length; ++i) {
            if (!args[i].isEmpty()) {
                if (i != 0) {
                    experimentName.append("_");
                }

                experimentName.append(args[i]);
            }
        }

        return experimentName.toString();
    }

    public static List<ContainerHost> createHostList(int hostsNumber) {
        ArrayList<ContainerHost> hostList = new ArrayList<ContainerHost>();
        for (int i = 0; i < hostsNumber; ++i) {
            int hostType = i / (int) Math.ceil((double) hostsNumber / 3.0D);
            // System.out.println("Host type is: "+ hostType);
            ArrayList<ContainerVmPe> peList = new ArrayList<ContainerVmPe>();
            for (int j = 0; j < Constants.HOST_PES[hostType]; ++j) {
                peList.add(new ContainerVmPe(j,
                        new ContainerVmPeProvisionerSimple((double) Constants.HOST_MIPS[hostType])));
            }

            hostList.add(new PowerContainerHostUtilizationHistory(IDs.pollId(ContainerHost.class),
                    new ContainerVmRamProvisionerSimple(Constants.HOST_RAM[hostType]),
                    new ContainerVmBwProvisionerSimple(Constants.HOST_BW), Constants.HOST_STORAGE, peList,
                    new ContainerVmSchedulerTimeSharedOverSubscription(peList),
                    Constants.HOST_POWER[hostType]));
        }

        for (ContainerHost host : hostList) {
            System.out.println(String.valueOf(host));
        }

        return hostList;
    }

    private static void printContainerList(List<ServerlessContainer> list) {
        int size = list.size();
        ServerlessContainer container;
        int deadlineMetStat = 0;

        String indent = "    ";
        Log.printLine();
        Log.printLine("========== OUTPUT ==========");
        Log.printLine("Container ID" + indent + "VM ID" + indent + "Start Time" + indent
                + "Finish Time" + indent + "Finished Requests List");

        DecimalFormat dft = new DecimalFormat("###.##");
        for (int i = 0; i < size; i++) {
            container = list.get(i);
            Log.print(indent + container.getId() + indent + indent);

            Log.printLine(indent + (container.getVm()).getId() + indent + indent + (dft.format(container.getStartTime()))
                    + indent + indent + indent + indent + dft.format(container.getFinishTime())
                    + indent + indent + indent + indent
                    + container.getfinishedTasks());

        }

        Log.printLine("Deadline met no: " + deadlineMetStat);

    }

    private static void printVmUtilization() {
        System.out.println("Average CPU utilization of vms: " + controller.getAverageResourceUtilization());
        System.out.println("Average vm count: " + Math.ceil(controller.getAverageVmCount()));

    }
}
