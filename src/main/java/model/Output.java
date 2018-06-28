package model;

import filemanager.Parameters;
import gurobi.GRB;
import gurobi.GRBException;
import network.Server;
import org.graphstream.graph.Edge;
import results.Results;
import utils.Auxiliary;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class Output {

    private Variables variables;
    private Parameters parameters;
    private int numOfMigrations;
    private int numOfReplicas;

    public Output(Model model) {
        this.parameters = model.getParameters();
        this.variables = model.getVariables();
    }

    public void calculateNumberOfMigrations(Output initialPlacement) throws GRBException {
        numOfMigrations = 0;
        for (int x = 0; x < parameters.getServers().size(); x++)
            for (int s = 0; s < parameters.getServices().size(); s++)
                for (int v = 0; v < parameters.getServices().get(s).getFunctions().size(); v++)
                    if (initialPlacement.variables.fXSV[x][s][v].get(GRB.DoubleAttr.X) == 1 && variables.fXSV[x][s][v].get(GRB.DoubleAttr.X) == 0)
                        numOfMigrations++;
    }

    public void calculateNumberOfReplications() throws GRBException {
        numOfReplicas = 0;
        for (int s = 0; s < parameters.getServices().size(); s++)
            for (int v = 0; v < parameters.getServices().get(s).getFunctions().size(); v++) {
                int numOfReplicasPerFunction = 0;
                for (int x = 0; x < parameters.getServers().size(); x++)
                    if (variables.fXSV[x][s][v].get(GRB.DoubleAttr.X) == 1)
                        numOfReplicasPerFunction++;
                numOfReplicas += numOfReplicasPerFunction - 1;
            }
    }

    public double[][][] getUtilizationPerFunction() throws GRBException {
        double[][][] utilizationPerFunction = new double[parameters.getServers().size()][parameters.getServices().size()][parameters.getServiceLengthAux()];
        for (int x = 0; x < parameters.getServers().size(); x++)
            for (int s = 0; s < parameters.getServices().size(); s++)
                for (int v = 0; v < parameters.getServices().get(s).getFunctions().size(); v++) {
                    double functionUtilization = 0;
                    for (int r = 0; r < parameters.getServices().get(s).getTrafficFlow().getTrafficDemands().size(); r++)
                        if (variables.fXSVD[x][s][v][r].get(GRB.DoubleAttr.X) == 1)
                            functionUtilization += (parameters.getServices().get(s).getTrafficFlow().getTrafficDemands().get(r)
                                    * parameters.getServices().get(s).getFunctions().get(v).getLoad());
                    utilizationPerFunction[x][s][v] = functionUtilization;
                }
        return utilizationPerFunction;
    }

    public Results generate(double cost) throws GRBException {
        return new Results(linksMap(), serversMap(), functionsMap(), functionsStringMap(), parameters.getTotalTrafficAux()
                , trafficOnLinks(), avgPathLength(), Auxiliary.roundDouble(cost, 4), numOfMigrations, numOfReplicas, usedPathsPerDemand());
    }

    private Map<Edge, Double> linksMap() throws GRBException {
        Map<Edge, Double> linkMapResults = new HashMap<>();
        for (int l = 0; l < parameters.getLinks().size(); l++)
            linkMapResults.put(parameters.getLinks().get(l), Math.round(variables.uL[l].get(GRB.DoubleAttr.X) * 10000.0) / 10000.0);
        return linkMapResults;
    }

    private Map<Server, Double> serversMap() throws GRBException {
        Map<Server, Double> serverMapResults = new HashMap<>();
        for (int x = 0; x < parameters.getServers().size(); x++)
            serverMapResults.put(parameters.getServers().get(x), Math.round(variables.uX[x].get(GRB.DoubleAttr.X) * 10000.0) / 10000.0);
        return serverMapResults;
    }

    public Map<Server, List<Integer>> functionsMap() throws GRBException {
        Map<Server, List<Integer>> functionsMap = new HashMap<>();
        for (int x = 0; x < parameters.getServers().size(); x++) {
            List<Integer> functions = new ArrayList<>();
            for (int s = 0; s < parameters.getServices().size(); s++)
                for (int v = 0; v < parameters.getServices().get(s).getFunctions().size(); v++)
                    if (variables.fXSV[x][s][v].get(GRB.DoubleAttr.X) == 1.0)
                        functions.add(parameters.getServices().get(s).getFunctions().get(v).getId());
            functionsMap.put(parameters.getServers().get(x), functions);
        }
        return functionsMap;
    }

    public Map<Server, String> functionsStringMap() throws GRBException {
        Map<Server, String> functionsStringMap = new HashMap<>();
        for (int x = 0; x < parameters.getServers().size(); x++) {
            StringBuilder stringVnf = new StringBuilder();
            for (int s = 0; s < parameters.getServices().size(); s++)
                for (int v = 0; v < parameters.getServices().get(s).getFunctions().size(); v++)
                    if (variables.fXSV[x][s][v].get(GRB.DoubleAttr.X) == 1.0)
                        stringVnf.append("s").append(String.valueOf(s)).append("v").append(String.valueOf(v)).append("\n");
            functionsStringMap.put(parameters.getServers().get(x), stringVnf.toString());
        }
        return functionsStringMap;
    }

    private List<String> usedPaths() throws GRBException {
        List<String> usedPaths = new ArrayList<>();
        for (int s = 0; s < parameters.getServices().size(); s++)
            for (int p = 0; p < parameters.getServices().get(s).getTrafficFlow().getAdmissiblePaths().size(); p++)
                if (variables.tSP[s][p].get(GRB.DoubleAttr.X) == 1)
                    usedPaths.add("s" + s + " --> " + parameters.getServices().get(s).getTrafficFlow().getAdmissiblePaths().get(p).getNodePath());
        return usedPaths;
    }

    private List<String> usedPathsPerDemand() throws GRBException {
        List<String> usedPathsPerDemand = new ArrayList<>();
        for (int s = 0; s < parameters.getServices().size(); s++)
            for (int p = 0; p < parameters.getServices().get(s).getTrafficFlow().getAdmissiblePaths().size(); p++)
                for (int d = 0; d < parameters.getServices().get(s).getTrafficFlow().getTrafficDemands().size(); d++)
                    if (variables.tSPD[s][p][d].get(GRB.DoubleAttr.X) == 1)
                        usedPathsPerDemand.add("s" + s + "-d" + d + " --> " + parameters.getServices().get(s).getTrafficFlow().getAdmissiblePaths().get(p).getNodePath());
        return usedPathsPerDemand;
    }

    private List<String> usedServers() throws GRBException {
        List<String> usedServers = new ArrayList<>();
        for (int s = 0; s < parameters.getServices().size(); s++)
            for (int v = 0; v < parameters.getServices().get(s).getFunctions().size(); v++)
                for (int x = 0; x < parameters.getServers().size(); x++)
                    if (variables.fXSV[x][s][v].get(GRB.DoubleAttr.X) == 1)
                        usedServers.add("s" + s + "-v" + v + " --> " + parameters.getServers().get(x).getId());
        return usedServers;
    }

    private List<String> usedServersPerDemand() throws GRBException {
        List<String> usedServersPerDemand = new ArrayList<>();
        for (int x = 0; x < parameters.getServers().size(); x++)
            for (int s = 0; s < parameters.getServices().size(); s++)
                for (int v = 0; v < parameters.getServices().get(s).getFunctions().size(); v++)
                    for (int d = 0; d < parameters.getServices().get(s).getTrafficFlow().getTrafficDemands().size(); d++)
                        if (variables.fXSVD[x][s][v][d].get(GRB.DoubleAttr.X) == 1)
                            usedServersPerDemand.add("s" + s + "-v" + v + "-r" + d + " --> " + parameters.getServers().get(x).getId());
        return usedServersPerDemand;
    }

    private double avgPathLength() throws GRBException {
        double avgPathLength = 0;
        int usedPaths = 0;
        for (int s = 0; s < parameters.getServices().size(); s++)
            for (int p = 0; p < parameters.getServices().get(s).getTrafficFlow().getAdmissiblePaths().size(); p++)
                if (variables.tSP[s][p].get(GRB.DoubleAttr.X) == 1.0) {
                    avgPathLength += parameters.getServices().get(s).getTrafficFlow().getAdmissiblePaths().get(p).getEdgePath().size();
                    usedPaths++;
                }
        avgPathLength = avgPathLength / usedPaths;
        return avgPathLength;
    }

    private List<Integer> functionsPerServer() throws GRBException {
        List<Integer> fnv = new ArrayList<>();
        int counter;
        for (int x = 0; x < parameters.getServers().size(); x++) {
            counter = 0;
            for (int s = 0; s < parameters.getServices().size(); s++)
                for (int v = 0; v < parameters.getServices().get(s).getFunctions().size(); v++)
                    if (variables.fXSV[x][s][v].get(GRB.DoubleAttr.X) == 1.0)
                        counter++;
            fnv.add(counter);
        }
        return fnv;
    }

    private double trafficOnLinks() throws GRBException {
        double trafficOnLinks = 0;
        for (int l = 0; l < parameters.getLinks().size(); l++)
            trafficOnLinks += variables.uL[l].get(GRB.DoubleAttr.X) * (double) parameters.getLinks().get(l).getAttribute("capacity");
        return trafficOnLinks;
    }

    public Variables getVariables() {
        return variables;
    }
}
