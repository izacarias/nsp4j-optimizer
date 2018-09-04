
package learning;

import filemanager.Parameters;
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
import utils.Auxiliary;

import java.util.*;

public class DeepQ {

    private static final Logger log = LoggerFactory.getLogger(DeepQ.class);
    private Agent agent;
    private final double THRESHOLD = 1.0;
    private Parameters pm;
    private double uX[];
    private double uL[];
    private boolean fXSVD[][][][];
    private boolean fXSV[][][];
    private boolean tSP[][];
    private boolean tSPD[][][];
    private double mPSV[][][];

    DeepQ(Parameters pm) {
        this.pm = pm;
        int inputLength = pm.getServers().size() * pm.getServices().size() * pm.getServiceLengthAux();
        int outputLength = pm.getServers().size() * pm.getServices().size() * pm.getServiceLengthAux();
        int hiddenLayerOut = 150;
        MultiLayerConfiguration conf = new NeuralNetConfiguration.Builder()
                .seed(123)
                .iterations(1)
                .optimizationAlgo(OptimizationAlgorithm.STOCHASTIC_GRADIENT_DESCENT)
                .learningRate(0.0025)
                .updater(Updater.NESTEROVS)
                .list()
                .layer(0, new DenseLayer.Builder()
                        .nIn(inputLength)
                        .nOut(hiddenLayerOut)
                        .weightInit(WeightInit.XAVIER)
                        .activation(Activation.RELU)
                        .build())
                .layer(1, new OutputLayer.Builder(LossFunctions.LossFunction.MSE)
                        .nIn(hiddenLayerOut)
                        .nOut(outputLength)
                        .weightInit(WeightInit.XAVIER)
                        .activation(Activation.IDENTITY)
                        .build())
                .pretrain(false)
                .backprop(true)
                .build();
        agent = new Agent(conf, 100000, .99f, 1024, 100, 1024, inputLength);
    }

    void learn(float[] input, int[] environment, double minCost, int iteration) {
        int[] localEnvironment = environment.clone();
        INDArray inputIndArray = Nd4j.create(input);
        boolean optimal = false;
        int timeStep = 0;
        while (!optimal) {
            int action = agent.getAction(inputIndArray, 1);
            modifyEnvironment(false, localEnvironment, action);
            double reward = computeReward();
            timeStep++;
            input[input.length - 1] = timeStep;
            if (reward >= (1 - minCost) * THRESHOLD) {
                agent.observeReward(inputIndArray, null, reward);
                optimal = true;
            } else
                agent.observeReward(inputIndArray, Nd4j.create(input), reward);
        }
        log.info("iteration " + iteration + " -> " + timeStep + " steps");
    }

    double reason(float[] input, int[] environment, double minCost, double epsilon) {
        int[] localEnvironment = environment.clone();
        INDArray inputIndArray = Nd4j.create(input);
        boolean optimal = false;
        int timeStep = 0;
        double reward = 0;
        while (!optimal) {
            int action = agent.getAction(inputIndArray, epsilon);
            modifyEnvironment(true, localEnvironment, action);
            reward = computeReward();
            timeStep++;
            if (reward >= (1 - minCost) * THRESHOLD)
                optimal = true;
            else {
                agent.observeReward(inputIndArray, Nd4j.create(input), reward);
                if (timeStep > 15)
                    break;
            }
        }
        computeFunctionsServers();
        log.info("reasoning in -> " + timeStep + " steps");
        return 1 - reward;
    }

    private void modifyEnvironment(boolean isReasoning, int[] environment, int action) {
        if (environment[action] == 1)
            environment[action] = 0;
        else environment[action] = 1;
        Map<Integer, List<Path>> servicesAdmissiblePaths = getServicesAdmissiblePaths(environment);
        chooseServersPerDemand(servicesAdmissiblePaths, environment);
        calculateServerUtilization(environment);
        calculateLinkUtilization(environment);
        if (isReasoning) {
            computePaths();
            calculateReroutingTraffic();
        }
    }

    private Map<Integer, List<Path>> getServicesAdmissiblePaths(int[] environment) {
        Map<Integer, List<Path>> servicesAdmissiblePaths = new HashMap<>();
        for (int s = 0; s < pm.getServices().size(); s++)
            servicesAdmissiblePaths.put(s, computeAdmissiblePaths(s, environment));
        return servicesAdmissiblePaths;
    }

    private List<Path> computeAdmissiblePaths(int s, int[] environment) {
        List<Path> admissiblePaths = new ArrayList<>();
        for (int d = 0; d < pm.getServices().get(s).getTrafficFlow().getTrafficDemands().size(); d++) {
            for (int p = 0; p < pm.getServices().get(s).getTrafficFlow().getAdmissiblePaths().size(); p++) {
                for (int v = 0; v < pm.getServices().get(s).getFunctions().size(); v++) {
                    boolean activatedInPath = false;
                    outerLoop:
                    for (int n = 0; n < pm.getServices().get(s).getTrafficFlow().getAdmissiblePaths().get(p).getNodePath().size(); n++) {
                        Node node = pm.getServices().get(s).getTrafficFlow().getAdmissiblePaths().get(p).getNodePath().get(n);
                        for (int x = 0; x < pm.getServers().size(); x++) {
                            if (pm.getServers().get(x).getNodeParent().equals(node)) {
                                if (environment[x * pm.getServices().get(s).getFunctions().size() + v] == 1) {
                                    activatedInPath = true;
                                    break outerLoop;
                                }
                            }
                        }
                    }
                    if (!activatedInPath)
                        break;
                }
                admissiblePaths.add(pm.getPaths().get(p));
            }
        }
        return admissiblePaths;
    }

    private void chooseServersPerDemand(Map<Integer, List<Path>> tSP, int[] environment) {
        Random rnd = new Random();
        fXSVD = new boolean[pm.getServers().size()][pm.getServices().size()][pm.getServiceLengthAux()][pm.getDemandsPerTrafficFlowAux()];
        for (int s = 0; s < pm.getServices().size(); s++)
            for (int d = 0; d < pm.getServices().get(s).getTrafficFlow().getTrafficDemands().size(); d++) {
                Path path = tSP.get(s).get(rnd.nextInt(tSP.get(s).size()));
                for (int v = 0; v < pm.getServices().get(s).getFunctions().size(); v++) {
                    outerLoop:
                    for (int n = 0; n < path.getNodePath().size(); n++)
                        for (int x = 0; x < pm.getServers().size(); x++)
                            if (pm.getServers().get(x).getNodeParent().equals(path.getNodePath().get(n)))
                                if (environment[x * pm.getServices().get(s).getFunctions().size() + v] == 1) {
                                    fXSVD[x][s][v][d] = true;
                                    break outerLoop;
                                }
                }
            }
    }

    private void calculateServerUtilization(int[] environment) {
        uX = new double[pm.getServers().size()];
        for (int x = 0; x < pm.getServers().size(); x++) {
            for (int s = 0; s < pm.getServices().size(); s++)
                for (int v = 0; v < pm.getServices().get(s).getFunctions().size(); v++) {
                    double demands = 0;
                    for (int d = 0; d < pm.getServices().get(s).getTrafficFlow().getTrafficDemands().size(); d++) {
                        if (fXSVD[x][s][v][d]) {
                            demands += pm.getServices().get(s).getTrafficFlow().getTrafficDemands().get(d);
                        }
                    }
                    if (environment[x * pm.getServices().get(s).getFunctions().size() + v] == 1)
                        uX[x] += ((demands * pm.getServices().get(s).getFunctions().get(v).getLoad())
                                + (pm.getServices().get(s).getFunctions().get(v).getLoad() * pm.getAux()[0]))
                                / pm.getServers().get(x).getCapacity();
                }
        }
    }

    private void calculateLinkUtilization(int[] environment) {
        uL = new double[pm.getLinks().size()];
        // TODO
    }

    private double computeReward() {
        double cost, totalCost = 0;
        for (Double serverUtilization : uX) {
            cost = 0;
            for (int f = 0; f < Auxiliary.linearCostFunctions.getValues().size(); f++) {
                double value = Auxiliary.linearCostFunctions.getValues().get(f)[0] * serverUtilization
                        + Auxiliary.linearCostFunctions.getValues().get(f)[1];
                if (value > cost)
                    cost = value;
            }
            totalCost += cost;
        }
        return 1 - (totalCost / pm.getServers().size());
    }

    private void computeFunctionsServers() {
        fXSV = new boolean[pm.getServers().size()][pm.getServices().size()][pm.getServiceLengthAux()];
        for (int x = 0; x < pm.getServers().size(); x++)
            for (int s = 0; s < pm.getServices().size(); s++)
                for (int v = 0; v < pm.getServices().get(s).getFunctions().size(); v++)
                    for (int d = 0; d < pm.getServices().get(s).getTrafficFlow().getTrafficDemands().size(); d++)
                        if (fXSVD[x][s][v][d])
                            fXSV[x][s][v] = true;
    }

    private void computePaths() {
        tSP = new boolean[pm.getServices().size()][pm.getPathsPerTrafficFlowAux()];
        tSPD = new boolean[pm.getServices().size()][pm.getPathsPerTrafficFlowAux()][pm.getDemandsPerTrafficFlowAux()];
        // TODO
    }

    private void calculateReroutingTraffic() {
        mPSV = new double[pm.getPaths().size()][pm.getServices().size()][pm.getServiceLengthAux()];
        // TODO
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

    public boolean[][] gettSP() {
        return tSP;
    }

    public boolean[][][] gettSPD() {
        return tSPD;
    }

    public double[][][] getmPSV() {
        return mPSV;
    }
}
