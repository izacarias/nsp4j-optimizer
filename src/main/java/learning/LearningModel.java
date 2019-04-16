package learning;

import gurobi.GRB;
import gurobi.GRBException;
import gurobi.GRBModel;
import manager.Parameters;
import manager.elements.Function;
import manager.elements.Service;
import org.deeplearning4j.nn.api.OptimizationAlgorithm;
import org.deeplearning4j.nn.conf.MultiLayerConfiguration;
import org.deeplearning4j.nn.conf.NeuralNetConfiguration;
import org.deeplearning4j.nn.conf.Updater;
import org.deeplearning4j.nn.conf.layers.DenseLayer;
import org.deeplearning4j.nn.conf.layers.OutputLayer;
import org.deeplearning4j.nn.weights.WeightInit;
import org.graphstream.graph.Node;
import org.graphstream.graph.Path;
import org.nd4j.linalg.activations.Activation;
import org.nd4j.linalg.api.ndarray.INDArray;
import org.nd4j.linalg.factory.Nd4j;
import org.nd4j.linalg.lossfunctions.LossFunctions;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import output.Auxiliary;
import output.Definitions;

import java.util.*;

import static output.Auxiliary.printLog;
import static output.Definitions.*;

public class LearningModel {

   private static final Logger log = LoggerFactory.getLogger(LearningModel.class);
   private Parameters pm;
   private DeepQ deepQ;
   private double objVal;

   private boolean[][][] zSPD;
   private boolean[][][] fXSV;
   private boolean[][][][] fXSVD;
   private double[] uL;
   private double[] uX;

   public LearningModel(Parameters pm) {
      this.pm = pm;
   }

   private void initializeModel(int inputLength, int outputLength) {
      MultiLayerConfiguration conf = new NeuralNetConfiguration.Builder()
              .seed(123)
              .iterations(1)
              .optimizationAlgo(OptimizationAlgorithm.STOCHASTIC_GRADIENT_DESCENT)
              .learningRate(0.1)
              .updater(Updater.NESTEROVS)
              .list()
              .layer(0, new DenseLayer.Builder()
                      .nIn(inputLength)
                      .nOut((int) pm.getAux(NUM_HIDDEN_LAYERS))
                      .weightInit(WeightInit.XAVIER)
                      .activation(Activation.RELU)
                      .build())
              .layer(1, new OutputLayer.Builder(LossFunctions.LossFunction.MSE)
                      .nIn((int) pm.getAux(NUM_HIDDEN_LAYERS))
                      .nOut(outputLength)
                      .weightInit(WeightInit.XAVIER)
                      .activation(Activation.IDENTITY)
                      .build())
              .pretrain(false)
              .backprop(true)
              .build();
      deepQ = new DeepQ(conf, 100000, .99f, 1024, 100, 1024, inputLength);
   }

   public void run(GRBModel initialPlacement, double objValTarget) throws GRBException {
      float[] environment = generateEnvironment(initialPlacement);
      initializeModel(environment.length, environment.length - 1);
      int timeSteps;
      for (int i = 0; i < (int) pm.getAux(TRAINING_ITERATIONS); i++) {
         timeSteps = run(environment, objValTarget, (double) pm.getAux(EPSILON));
         log.info("iteration " + i + " -> " + timeSteps + " steps");
      }
      timeSteps = run(environment, objValTarget, 0);
      log.info("reasoning in -> " + timeSteps + " steps");
      this.uL = calculateLinkUtilization(zSPD);
      this.fXSV = computeFunctionsServers(fXSVD);
      this.objVal = computeCost(uX);
      printLog(log, INFO, "finished [" + Auxiliary.roundDouble(objVal, 2) + "]");
   }

   private float[] generateEnvironment(GRBModel initialPlacement) throws GRBException {
      float[] environment = new float[pm.getServers().size() * pm.getTotalNumFunctions() + 1];
      for (int x = 0; x < pm.getServers().size(); x++)
         for (int s = 0; s < pm.getServices().size(); s++)
            for (int v = 0; v < pm.getServices().get(s).getFunctions().size(); v++) {
               int pointer = x * pm.getTotalNumFunctions() + s * pm.getServices().get(s).getFunctions().size() + v;
               if (initialPlacement.getVarByName(Definitions.fXSV + "[" + x + "][" + s + "][" + v + "]").get(GRB.DoubleAttr.X) == 1.0)
                  environment[pointer] = 1;
               else
                  environment[pointer] = 0;
            }
      return environment;
   }

   private int run(float[] environment, double objValTarget, double epsilon) {
      float[] localEnvironment = environment.clone();
      int timeStep = 0;
      int action = -1;
      boolean[][][] zSPD;
      boolean[][][][] fXSVD;
      double[] uX;
      double objectiveValue = objValTarget * (double) pm.getAux(THRESHOLD);
      while (true) {
         INDArray inputIndArray = Nd4j.create(localEnvironment);
         int[] actionMask = generateActionMask(localEnvironment, action);
         if (!checkActionMask(actionMask))
            break;
         action = deepQ.getAction(inputIndArray, actionMask, epsilon);
         modifyEnvironment(localEnvironment, action);
         int[] nextActionMask = generateActionMask(localEnvironment, action);
         double cost;
         Map<Integer, List<Path>> servicesAdmissiblePaths = getServicesAdmissiblePaths(localEnvironment);
         boolean isValid = false;
         zSPD = computezSPD(servicesAdmissiblePaths);
         if (zSPD != null)
            isValid = true;
         fXSVD = activateServersPerDemand(localEnvironment, zSPD);
         uX = calculateServerUtilization(localEnvironment, fXSVD);
         if (!isValid)
            cost = Double.MAX_VALUE;
         else
            cost = computeCost(uX);
         double reward = computeReward(cost, objectiveValue);
         timeStep++;
         localEnvironment[localEnvironment.length - 1] = timeStep;
         if (cost <= objectiveValue) {
            deepQ.observeReward(inputIndArray, null, reward, nextActionMask);
            this.zSPD = zSPD;
            this.fXSVD = fXSVD;
            this.uX = uX;
            break;
         } else {
            deepQ.observeReward(inputIndArray, Nd4j.create(localEnvironment), reward, nextActionMask);
            if (timeStep == (int) pm.getAux(LEARNING_STEPS))
               break;
         }
      }
      return timeStep;
   }

   private int[] generateActionMask(float[] environment, int pastAction) {
      int[] actionMask = new int[environment.length - 1];
      // to avoid doing the same action twice
      if (pastAction != -1)
         actionMask[pastAction] = 0;
      // calculate the admissible paths for all services
      Map<Integer, List<Path>> servicesAdmissiblePaths = getServicesAdmissiblePaths(environment);
      // check which servers can be used based on the admissible paths
      for (int x = 0; x < pm.getServers().size(); x++)
         for (int s = 0; s < pm.getServices().size(); s++) {
            for (int v = 0; v < pm.getServiceLength(); v++) {
               outerloop:
               if (environment[x * pm.getServiceLength() + v] == 0)
                  // check if it is possible to activate the server
                  for (int p = 0; p < servicesAdmissiblePaths.get(s).size(); p++)
                     for (int n = 0; n < servicesAdmissiblePaths.get(s).get(p).getNodePath().size(); n++)
                        if (pm.getServers().get(x).getParent().equals(servicesAdmissiblePaths.get(s).get(p).getNodePath().get(n))) {
                           actionMask[x * pm.getServiceLength() + v] = 1;
                           break outerloop;
                        }
            }
         }
      // find the maximum demand for the function with highest load
      double maxDemandLoad = 0;
      for (int s = 0; s < pm.getServices().size(); s++)
         for (int v = 0; v < pm.getServiceLength(); v++)
            for (int d = 0; d < pm.getServices().get(s).getTrafficFlow().getDemands().size(); d++) {
               double demandLoad = pm.getServices().get(s).getTrafficFlow().getDemands().get(d)
                       * (double) pm.getServices().get(s).getFunctions().get(v).getAttribute(FUNCTION_LOAD_RATIO);
               if (demandLoad > maxDemandLoad)
                  maxDemandLoad = demandLoad;
            }
      // check which servers can be used based on server capacity
      for (int x = 0; x < pm.getServers().size(); x++) {
         double totalTraffic = 0;
         for (int s = 0; s < pm.getServices().size(); s++) {
            int traffic = 0;
            for (int d = 0; d < pm.getServices().get(s).getTrafficFlow().getDemands().size(); d++)
               traffic += pm.getServices().get(s).getTrafficFlow().getDemands().get(d);
            for (int v = 0; v < pm.getServiceLength(); v++) {
               if (environment[x * pm.getServiceLength() + v] == 1)
                  totalTraffic += (double) pm.getServices().get(s).getFunctions().get(v).getAttribute(FUNCTION_LOAD_RATIO) * traffic;
            }
         }
         if (totalTraffic + maxDemandLoad >= pm.getServers().get(x).getCapacity())
            for (int s = 0; s < pm.getServices().size(); s++)
               for (int v = 0; v < pm.getServiceLength(); v++)
                  actionMask[x * pm.getServiceLength() + v] = 0;

      }
      return actionMask;
   }

   private boolean checkActionMask(int[] actionMask) {
      for (int i : actionMask)
         if (i != 0)
            return true;
      return false;
   }

   private void modifyEnvironment(float[] environment, int action) {
      outerloop:
      for (int s = 0; s < pm.getServices().size(); s++) {
         Service service = pm.getServices().get(s);
         for (int v = 0; v < pm.getServiceLength(); v++)
            for (int x = 0; x < pm.getServers().size(); x++) {
               if (action != x * pm.getServiceLength() + v) continue;
               if (service.getFunctions().size() <= v) continue;
               if (!(boolean) service.getFunctions().get(v).getAttribute(FUNCTION_REPLICABLE))
                  for (int y = 0; y < pm.getServers().size(); y++)
                     environment[y * pm.getServiceLength() + v] = 0;
               if (environment[action] == 1)
                  environment[action] = 0;
               else environment[action] = 1;
               break outerloop;
            }
      }
   }

   private Map<Integer, List<Path>> getServicesAdmissiblePaths(float[] environment) {
      Map<Integer, List<Path>> servicesAdmissiblePaths = new HashMap<>();
      for (int s = 0; s < pm.getServices().size(); s++)
         servicesAdmissiblePaths.put(s, computeAdmissiblePaths(s, environment));
      return servicesAdmissiblePaths;
   }

   private List<Path> computeAdmissiblePaths(int s, float[] environment) {
      List<Path> admissiblePaths = new ArrayList<>();
      for (int p = 0; p < pm.getServices().get(s).getTrafficFlow().getPaths().size(); p++) {
         boolean allFunctionsExist = true;
         for (int v = 0; v < pm.getServices().get(s).getFunctions().size(); v++) {
            boolean activatedInPath = false;
            outerLoop:
            for (int n = 0; n < pm.getServices().get(s).getTrafficFlow().getPaths().get(p).getNodePath().size(); n++) {
               Node node = pm.getServices().get(s).getTrafficFlow().getPaths().get(p).getNodePath().get(n);
               for (int x = 0; x < pm.getServers().size(); x++)
                  if (pm.getServers().get(x).getParent().equals(node)) {
                     int pointer = pm.getServices().size() * pm.getServices().get(s).getFunctions().size()
                             * x + s * pm.getServices().get(s).getFunctions().size() + v;
                     if (environment[pointer] == 1) {
                        activatedInPath = true;
                        break outerLoop;
                     }
                  }
            }
            if (!activatedInPath) {
               allFunctionsExist = false;
               break;
            }
         }
         if (allFunctionsExist)
            admissiblePaths.add(pm.getServices().get(s).getTrafficFlow().getPaths().get(p));
      }
      return admissiblePaths;
   }

   private boolean[][][] computezSPD(Map<Integer, List<Path>> tSP) {
      boolean[][][] zSPD = new boolean[pm.getServices().size()][pm.getPathsTrafficFlow()][pm.getDemandsTrafficFlow()];
      double[] uL = new double[pm.getLinks().size()];
      for (int s = 0; s < pm.getServices().size(); s++) {
         if (tSP.get(s).size() == 0) return null;
         for (int d = 0; d < pm.getServices().get(s).getTrafficFlow().getDemands().size(); d++) {
            int pathIndex = 0;
            int trafficDemand = pm.getServices().get(s).getTrafficFlow().getDemands().get(d);
            for (int i = 0; i < tSP.get(s).size(); i++) {
               pathIndex = i;
               uL = routeDemandOverLinks(tSP.get(s).get(pathIndex), trafficDemand, uL);
               if (uL != null)
                  break;
            }
            zSPD[s][pathIndex][d] = true;
         }
      }
      for (int s = 0; s < pm.getServices().size(); s++)
         for (int d = 0; d < pm.getServices().get(s).getTrafficFlow().getDemands().size(); d++) {
            boolean isPath = false;
            for (int p = 0; p < pm.getServices().get(s).getTrafficFlow().getPaths().size(); p++)
               if (zSPD[s][p][d]) {
                  isPath = true;
                  break;
               }
            if (!isPath)
               return null;
         }
      return zSPD;
   }

   private double[] routeDemandOverLinks(Path path, double trafficDemand, double[] uL) {
      double[] tempUL = uL.clone();
      for (int k = 0; k < path.getEdgePath().size(); k++)
         for (int l = 0; l < pm.getLinks().size(); l++)
            if (pm.getLinks().get(l).equals(path.getEdgePath().get(k))) {
               double partialUtilization = trafficDemand / (int) pm.getLinks().get(l).getAttribute(LINK_CAPACITY);
               if (tempUL[l] + partialUtilization > 1.0)
                  return null;
               else
                  tempUL[l] += partialUtilization;
               break;
            }
      return tempUL;
   }

   private boolean[][][][] activateServersPerDemand(float[] environment, boolean[][][] zSPD) {
      boolean[][][][] fXSVD = new boolean[pm.getServers().size()][pm.getServices().size()]
              [pm.getServiceLength()][pm.getDemandsTrafficFlow()];
      for (int s = 0; s < pm.getServices().size(); s++)
         for (int d = 0; d < pm.getServices().get(s).getTrafficFlow().getDemands().size(); d++) {
            Path path = null;
            for (int p = 0; p < pm.getServices().get(s).getTrafficFlow().getPaths().size(); p++)
               if (zSPD[s][p][d]) {
                  path = pm.getServices().get(s).getTrafficFlow().getPaths().get(p);
                  break;
               }
            for (int v = 0; v < pm.getServices().get(s).getFunctions().size(); v++) {
               outerLoop:
               for (int n = 0; n < path.getNodePath().size(); n++)
                  for (int x = 0; x < pm.getServers().size(); x++)
                     if (pm.getServers().get(x).getParent().equals(path.getNodePath().get(n)))
                        if (environment[x * pm.getServices().get(s).getFunctions().size() + v] == 1) {
                           fXSVD[x][s][v][d] = true;
                           break outerLoop;
                        }
            }
         }
      return fXSVD;
   }

   private double[] calculateServerUtilization(float[] environment, boolean[][][][] fXSVD) {
      double[] uX = new double[pm.getServers().size()];
      for (int x = 0; x < pm.getServers().size(); x++) {
         for (int s = 0; s < pm.getServices().size(); s++)
            for (int v = 0; v < pm.getServices().get(s).getFunctions().size(); v++) {
               double demands = 0;
               for (int d = 0; d < pm.getServices().get(s).getTrafficFlow().getDemands().size(); d++)
                  if (fXSVD[x][s][v][d])
                     demands += pm.getServices().get(s).getTrafficFlow().getDemands().get(d);
               if (environment[x * pm.getServices().get(s).getFunctions().size() + v] == 1) {
                  Function function = pm.getServices().get(s).getFunctions().get(v);
                  uX[x] += ((demands * (double) function.getAttribute(FUNCTION_LOAD_RATIO))
                          + ((double) function.getAttribute(FUNCTION_LOAD_RATIO)
                          * (int) function.getAttribute(FUNCTION_OVERHEAD)))
                          / pm.getServers().get(x).getCapacity();
               }
            }
      }
      return uX;
   }

   private double[] calculateLinkUtilization(boolean[][][] zSPD) {
      double[] uL = new double[pm.getLinks().size()];
      for (int l = 0; l < pm.getLinks().size(); l++) {
         for (int s = 0; s < pm.getServices().size(); s++)
            for (int p = 0; p < pm.getServices().get(s).getTrafficFlow().getPaths().size(); p++) {
               if (!pm.getServices().get(s).getTrafficFlow().getPaths().get(p).contains(pm.getLinks().get(l)))
                  continue;
               for (int d = 0; d < pm.getServices().get(s).getTrafficFlow().getDemands().size(); d++)
                  if (zSPD[s][p][d])
                     uL[l] += (double) pm.getServices().get(s).getTrafficFlow().getDemands().get(d)
                             / (int) pm.getLinks().get(l).getAttribute(LINK_CAPACITY);
            }
      }
      return uL;
   }

   private double computeCost(double[] uX) {
      double cost, totalCost = 0;
      for (Double serverUtilization : uX) {
         cost = 0;
         for (int y = 0; y < Auxiliary.costFunctions.getValues().size(); y++) {
            double value = Auxiliary.costFunctions.getValues().get(y)[0] * serverUtilization
                    + Auxiliary.costFunctions.getValues().get(y)[1];
            if (value > cost)
               cost = value;
         }
         totalCost += cost;
      }
      return Auxiliary.roundDouble(totalCost / pm.getServers().size(), 4);
   }

   private double computeReward(double cost, double objValTarget) {
      double reward;
      if (cost <= objValTarget)
         reward = 100;
      else reward = -1;
      return reward;
   }

   private boolean[][][] computeFunctionsServers(boolean[][][][] fXSVD) {
      boolean[][][] fXSV = new boolean[pm.getServers().size()][pm.getServices().size()][pm.getServiceLength()];
      for (int x = 0; x < pm.getServers().size(); x++)
         for (int s = 0; s < pm.getServices().size(); s++)
            for (int v = 0; v < pm.getServices().get(s).getFunctions().size(); v++)
               for (int d = 0; d < pm.getServices().get(s).getTrafficFlow().getDemands().size(); d++)
                  if (fXSVD[x][s][v][d])
                     fXSV[x][s][v] = true;
      return fXSV;
   }

   public double[] getuX() {
      return uX;
   }

   public double[] getuL() {
      return uL;
   }

   public boolean[][][][] getfXSVD() {
      return fXSVD;
   }

   public boolean[][][] getfXSV() {
      return fXSV;
   }

   public boolean[][][] getzSPD() {
      return zSPD;
   }

   public double getObjVal() {
      return objVal;
   }
}
