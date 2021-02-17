package optimizer;

public class Definitions {
   // objective functions
   public static final String DIMEN = "DIMEN";
   public static final String NUM_SERVERS = "NUM_SERVERS";
   public static final String NUM_SERVERS_AND_UTIL_COSTS = "NUM_SERVERS_AND_UTIL_COSTS";
   public static final String UTIL_COSTS = "UTIL_COSTS";
   public static final String UTIL_COSTS_AND_MAX_UTIL = "UTIL_COSTS_AND_MAX_UTIL";
   public static final String UTILIZATION = "UTILIZATION";
   public static final String OPEX_SERVERS = "OPEX_SERVERS";
   public static final String FUNCTIONS_CHARGES = "FUNCTIONS_CHARGES";
   public static final String QOS_PENALTIES = "QOS_PENALTIES";
   public static final String ALL_MONETARY_COSTS = "ALL_MONETARY_COSTS";
   public static final String MGR = "MGR";
   public static final String REP = "REP";
   public static final String MGR_REP = "MGR_REP";

   // scenarios
   public static final String LP = "LP";
   public static final String FF = "FF";
   public static final String RF = "RF";
   public static final String GRD = "GRD";
   public static final String CUSTOM_1 = "CUSTOM_1";
   public static final String CUSTOM_2 = "CUSTOM_2";
   public static final String CUSTOM_3 = "CUSTOM_3";

   // general variables
   public static final String zSP = "zSP";
   public static final String zSPD = "zSPD";
   public static final String fX = "fX";
   public static final String fXSV = "fXSV";
   public static final String fXSVD = "fXSVD";
   public static final String uX = "uX";
   public static final String uL = "uL";

   // model specific variables
   public static final String xN = "xN";
   public static final String kL = "kL";
   public static final String kX = "kX";
   public static final String uMax = "uMax";
   public static final String oX = "oX";
   public static final String oSV = "oSV";
   public static final String qSDP = "qSDP";
   public static final String ySDP = "ySDP";

   // service delay variables
   public static final String dSVXD = "dSVXD";

   // synchronization traffic variables
   public static final String gSVXY = "gSVXY";
   public static final String hSVP = "hSVP";

   // general constraints
   public static final String RP1 = "RP1";
   public static final String RP2 = "RP2";
   public static final String PF1 = "PF1";
   public static final String PF2 = "PF2";
   public static final String PF3 = "PF3";
   public static final String FD1 = "FD1";
   public static final String FD2 = "FD2";
   public static final String FD3 = "FD3";

   // additional constraints
   public static final String SYNC_TRAFFIC = "sync_traffic";
   public static final String MAX_SERV_DELAY = "max_serv_delay";
   public static final String CLOUD_ONLY = "cloud_only";
   public static final String EDGE_ONLY = "edge_only";
   public static final String SINGLE_PATH = "single_path";
   public static final String SET_INIT_PLC = "set_init_plc";

   // other constraints
   public static final String FORCE_SRC_DST = "force_src_dst";
   public static final String CONST_REP = "const_rep";

   // service parameters
   public static final String SERVICE_MIN_PATHS = "min_paths";
   public static final String SERVICE_MAX_PATHS = "max_paths";
   public static final String SERVICE_DOWNTIME = "downtime";

   // function parameters
   public static final String FUNCTION_REPLICABLE = "replicable";
   public static final String FUNCTION_LOAD_RATIO = "load_ratio";
   public static final String FUNCTION_OVERHEAD_RATIO = "overhead_ratio";
   public static final String FUNCTION_SYNC_LOAD_RATIO = "sync_load";
   public static final String FUNCTION_PROCESS_TRAFFIC_DELAY = "process_traffic_delay";
   public static final String FUNCTION_MAX_DEM = "max_dem";
   public static final String FUNCTION_MAX_BW = "max_bw";
   public static final String FUNCTION_MAX_DELAY = "max_delay";
   public static final String FUNCTION_MIN_PROCESS_DELAY = "min_process_delay";
   public static final String FUNCTION_PROCESS_DELAY = "process_delay";
   public static final String FUNCTION_CHARGES = "charges";

   // node and server parameters
   public static final String NODE_CLOUD = "node_cloud";
   public static final String SERVER_DIMENSIONING_CAPACITY = "server_dimensioning_capacity";
   public static final String SERVER_IDLE_ENERGY_COST = "server_idle_energy_cost";
   public static final String SERVER_UTIL_ENERGY_COST = "server_util_energy_cost";
   public static final String OVERPROVISIONING_SERVER_CAPACITY = "overprovisioning_server_capacity";
   public static final String LONGITUDE_LABEL_1 = "Longitude";
   public static final String LATITUDE_LABEL_1 = "Latitude";
   public static final String LONGITUDE_LABEL_2 = "x";
   public static final String LATITUDE_LABEL_2 = "y";
   public static final String CLOUD_SERVER_CAPACITY = "cloud_server_capacity";

   // link parameters
   public static final String LINK_CAPACITY = "capacity";
   public static final String LINK_DELAY = "delay";
   public static final String LINK_DISTANCE = "distance";
   public static final String LINK_CLOUD = "link_cloud";

   // Aux parameters
   public static final String INITIAL_TRAFFIC_LOAD = "initial_traffic_load";
   public static final String dSPD = "dSPD";
   public static final String QOS_PENALTY_RATIO = "qos_penalty_ratio";
   public static final String LINKS_WEIGHT = "links_weight";
   public static final String SERVERS_WEIGHT = "servers_weight";
   public static final String MAXU_WEIGHT = "maxU_weight";
   public static final String DIRECTED_EDGES = "directed_edges";
   public static final String SCENARIOS_PATH = "scenarios";

   // GUI parameters
   public static final String NODE_COLOR = "Black";
   public static final String NODE_SHAPE = "ellipse";
   public static final String SERVER_COLOR = "Black";
   public static final String SERVER_SHAPE = "rectangle";
   public static final String LINK_COLOR = "Black";
   public static final String LINK_CLOUD_COLOR = "LightGray";
   public static final String X_SCALING = "x_scaling";
   public static final String Y_SCALING = "y_scaling";
   public static final int MAX_NUM_SERVERS = 24;
   public static final int PORT = 8082;

   // DRL parameters
   public static final int NUM_HIDDEN_LAYERS = 150;
   public static final int MEMORY_CAPACITY = 100000;
   public static final float DISCOUNT_FACTOR = 0.1f;
   public static final int BATCH_SIZE = 10;
   public static final int START_SIZE = 10;
   public static final int FREQUENCY = 100;
   public static final String ROUTING_MAX_REPETITIONS = "";
   public static final String PLACEMENT_MAX_REPETITIONS = "";
   public static final String ROUTING_EPSILON_DECREMENT = "";
   public static final String EPSILON_STEPPER = "epsilon_stepper";
   public static final String ROUTING_DRL_CONF_FILE = "routing_drl_conf";
   public static final String PLACEMENT_DRL_CONF_FILE = "placement_drl_conf";

   // logs and messages
   public static final String ERROR = "ERROR - ";
   public static final String INFO = "INFO - ";
   public static final String WARNING = "WARN - ";
}
