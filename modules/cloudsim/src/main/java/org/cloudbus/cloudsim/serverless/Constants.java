package org.cloudbus.cloudsim.serverless;

import org.cloudbus.cloudsim.power.models.PowerModel;
import org.cloudbus.cloudsim.power.models.PowerModelSpecPowerHpProLiantMl110G4Xeon3040;
import org.cloudbus.cloudsim.power.models.PowerModelSpecPowerHpProLiantMl110G5Xeon3075;
import org.cloudbus.cloudsim.power.models.PowerModelSpecPowerIbmX3550XeonX5670;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.Map;
import java.util.*;

public class Constants {


    /**
     * Simulation  parameters including the interval and limit
     */
    public static final boolean ENABLE_OUTPUT = true;
    public static final boolean OUTPUT_CSV = false;
    public static final double SCHEDULING_INTERVAL = 300.0D;
    public static final double SIMULATION_LIMIT = 87400.0D;
    /**
     * request specs
     */
    public static final int CLOUDLET_LENGTH = 30;
    public static final int CLOUDLET_PES = 1;
    public static final double[] FUNCTION_PE_SHARE = new double[]{ 0.25, 0.25, 0.25, 0.25};
    public static final double DEADLINE_CHECKPOINT_LOW = 0.65;
    public static final double DEADLINE_CHECKPOINT_HIGH = 0.85;
    public static final double DEADLINE_CHECKPOINT = 0.75;

    /**
     * Startup delay for VMs and the containers are mentioned here.
     */
    public static final double CONTAINER_STARTTUP_DELAY = 2.0;//the amount is in seconds
    public static final double VM_STARTTUP_DELAY = 100;//the amoun is in seconds
    /**
     * Initial scheduling delay for a function request.
     */
    public static final double FUNCTION_SCHEDULING_DELAY = 0.020;
    /**
     * Scheduling retry  gap for a function request.
     */
    public static final double FUNCTION_SCHEDULING_RETRY_DELAY = 2.0;

    public static final double VM_CPU_USAGE_THRESHOLD = 0.9;
    public static final int WINDOW_SIZE = 10;
    public static final double LATENCY_THRESHOLD = 0.10;
    public static final double SAFE_ZONE = 0.5;
    public static final double WARNING_ZONE = 0.75;

    public static final int NUM_TASKS = 50;
    public static final double CPU_USAGE_MONITORING_INTERVAL = 0.01;

    public static final double AUTO_SCALING_INTERVAL = 5;
    public static final double FUNCTION_PLACEMENT_TIME = 0.002;
    public static final double MINIMUM_INTERVAL_BETWEEN_TWO_CLOUDLET_SUBMISSIONS = 0.001;
    public static final double CLOUDLET_CREATING_INTERVAL = 50.0;

    /**
     * The available virtual machine types along with the specs.
     */

    public static final int VM_TYPES = 4;
    public static final double[] VM_MIPS = new double[]{ 8000, 8000, 8000, 8000};
    public static final int[] VM_PES = new int[]{4, 4, 4, 4};
    public static final float[] VM_RAM = new float[] {(float)16384, (float) 16384, (float) 16384, (float) 16384};//**MB*
    public static final int VM_BW = 200000;
    public static final int VM_SIZE = 30000;
    public static final int CPU_HISTORY_LENGTH = 1000;


    /**
     * The available types of container along with the specs.
     */

    public static final int CONTAINER_TYPES = 3;
//    public static final int[] CONTAINER_MIPS = new int[]{4658, 9320, 18636};
    public static final double[] CONTAINER_MIPS = new double[]{4658, 9320, 18636};
    public static final int[] CONTAINER_MIPS_INCREMENT = new int[]{-932, -466, -233, 0, 233, 466, 932};
    public static final int[] CONTAINER_RAM = new int[]{512, 512, 512};
    public static final int[] CONTAINER_RAM_INCREMENT = new int[]{-1024, -512, -128, 0, 128, 512, 1024};
    public static final int MIN_CONTAINER_MIPS = 466;
    public static final int MIN_CONTAINER_RAM = 128;
    public static final int MAX_CONTAINER_MIPS = 11600;
    public static final int MAX_CONTAINER_RAM = 3072;

    public static final int[] CONTAINER_PES = new int[]{1, 1, 1};
    public static final int[] CONTAINER_CPU_SHARE = new int[]{128, 128, 128};

    public static final int CONTAINER_BW = 2500;
    public static final int CONTAINER_SIZE = 512;
    public static final int RAM_INCREMENT = 256;
    public static final double CPU_QUOTA_INCREMENT_LOW = 0.2;
    public static final double CPU_QUOTA_INCREMENT_HIGH = 0.4;
    public static final int CPU_SHARES_STEP = 128;


    /**
     * The available types of hosts along with the specs.
     */

    public static final int HOST_TYPES = 3;
    public static final int[] HOST_MIPS = new int[]{8000, 8000, 8000};
    public static final int[] HOST_PES = new int[]{32, 32, 32};
    public static final int[] HOST_RAM = new int[]{131072, 131072, 131072};
    public static final int HOST_BW = 100000000;
    public static final int HOST_STORAGE = 10000000;
    public static final PowerModel[] HOST_POWER = new PowerModel[]{new PowerModelSpecPowerHpProLiantMl110G4Xeon3040(),
            new PowerModelSpecPowerHpProLiantMl110G5Xeon3075(), new PowerModelSpecPowerIbmX3550XeonX5670()};

    /**
     * The population of hosts, containers, and VMs are specified.
     * The containers population is equal to the requests population as each request is mapped to each container.
     * However, depending on the simualtion scenario the container's population can also be different from request's
     * population.
     */


    public static final int NUMBER_HOSTS = 4;
    public static final int NUMBER_VMS = 32;
    public static final int NUMBER_requestS = 5;

    /**
     * Name of the file containing function requests list.
     */

//    public static final String FUNCTION_REQUESTS_FILENAME = "modules/cloudsim/src/main/java/org/cloudbus/cloudsim/serverless/Real_azure_workload2.csv";
    public static final String FUNCTION_REQUESTS_FILENAME = "modules/cloudsim-examples/src/main/java/org/cloudbus/cloudsim/experiments/workloads/azure-3.csv";

    /**
     * Algorithm specific parameters
     */
    public static final double OVER_UTILIZATION_THRESHOLD = 0.80;
    public static final double UNDER_UTILIZATION_THRESHOLD = 0.70;
    public static final double CONTAINER_SCALE_CPU_THRESHOLD = 0.40;
    public static final boolean CONTAINER_CONCURRENCY = false;
    public static final boolean FUNCTION_AUTOSCALING = false;
    public static final boolean FUNCTION_HORIZONTAL_AUTOSCALING = false;
    public static final boolean FUNCTION_VERTICAL_AUTOSCALING = false;
    public static final boolean SCALE_PER_REQUEST = false;
    public static final String VM_SELECTION_ALGO = "RR";
    public static final String HOR_SCALING_LOGIC = "cpuThreshold";
    public static final String SCALING_TRIGGER_LOGIC = "cpuThreshold";
    public static final String CONTAINER_SELECTION_ALGO = "FF";
    public static final int MAX_RESCHEDULE_TRIES = 100;
    public static final boolean MONITORING = true;
    public static final int MAX_REPLICAS = 50;

    public static final boolean CONTAINER_IDLING_ENABLED = true;

    public static final int CONTAINER_IDLING_TIME = 10;

    public static final double LATENCY_SLO = 1.50;


}
