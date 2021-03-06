/***
 * Copyright © Mapotempo, 2016
 *
 * This file is part of Mapotempo.
 *
 * Mapotempo is free software. You can redistribute it and/or
 * modify since you respect the terms of the GNU Affero General
 * Public License as published by the Free Software Foundation,
 * either version 3 of the License, or (at your option) any later version.
 *
 * Mapotempo is distributed in the hope that it will be useful, but WITHOUT
 * ANY WARRANTY; without even the implied warranty of MERCHANTABILITY
 * or FITNESS FOR A PARTICULAR PURPOSE.  See the Licenses for more details.
 *
 * You should have received a copy of the GNU Affero General Public License
 * along with Mapotempo. If not, see:
 * <http://www.gnu.org/licenses/agpl.html>
***/
package com.mapotempo.optimizer.jsprit;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.Collection;

import com.graphhopper.jsprit.analysis.toolbox.AlgorithmSearchProgressChartListener;
import com.graphhopper.jsprit.core.algorithm.VehicleRoutingAlgorithm;
import com.graphhopper.jsprit.core.algorithm.listener.IterationEndsListener;
import com.graphhopper.jsprit.core.algorithm.recreate.BreakScheduling;
import com.graphhopper.jsprit.core.algorithm.state.InternalStates;
import com.graphhopper.jsprit.core.algorithm.state.StateManager;
import com.graphhopper.jsprit.core.algorithm.termination.TimeTermination;
import com.graphhopper.jsprit.core.algorithm.termination.VariationCoefficientTermination;
import com.graphhopper.jsprit.core.problem.Location;
import com.graphhopper.jsprit.core.problem.VehicleRoutingProblem;
import com.graphhopper.jsprit.core.problem.constraint.ConstraintManager;
import com.graphhopper.jsprit.core.problem.constraint.NoFirstANDSecondSkillConstraint;
import com.graphhopper.jsprit.core.problem.cost.AbstractForwardVehicleRoutingTransportCosts;
import com.graphhopper.jsprit.core.problem.cost.VehicleRoutingTransportCosts;
import com.graphhopper.jsprit.core.problem.driver.Driver;
import com.graphhopper.jsprit.core.problem.job.Job;
import com.graphhopper.jsprit.core.problem.solution.SolutionCostCalculator;
import com.graphhopper.jsprit.core.problem.solution.VehicleRoutingProblemSolution;
import com.graphhopper.jsprit.core.problem.solution.route.VehicleRoute;
import com.graphhopper.jsprit.core.problem.solution.route.activity.TourActivity;
import com.graphhopper.jsprit.core.problem.solution.route.activity.TourActivity.JobActivity;
import com.graphhopper.jsprit.core.problem.vehicle.Vehicle;
import com.graphhopper.jsprit.core.problem.vehicle.VehicleTypeImpl.VehicleCostParams;
import com.graphhopper.jsprit.core.reporting.SolutionPrinter;
import com.graphhopper.jsprit.core.util.Solutions;
import com.graphhopper.jsprit.core.util.VehicleRoutingTransportCostsMatrix;

import com.graphhopper.jsprit.io.algorithm.AlgorithmConfig;
import com.graphhopper.jsprit.io.algorithm.AlgorithmConfigXmlReader;
import com.graphhopper.jsprit.io.algorithm.VehicleRoutingAlgorithms;
import com.graphhopper.jsprit.io.problem.VrpXMLReader;
import com.graphhopper.jsprit.io.problem.VrpXMLWriter;

import com.mapotempo.optimizer.jsprit.CustomPrematureAlgorithmTermination.StrictIterationWithoutImprovementTermination;

import joptsimple.OptionException;
import joptsimple.OptionParser;
import joptsimple.OptionSet;
import joptsimple.OptionSpec;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class Run {

	final static Logger logger = LoggerFactory.getLogger(Run.class.getName());

	private VehicleRoutingProblemSolution bestCurrentSolution = null;

	public static void main(String[] args) throws IOException {
		OptionParser parser = new OptionParser();

		OptionSpec<String> optionTimeMatrix = parser.accepts("time_matrix").withRequiredArg().ofType(String.class);
		OptionSpec<String> optionsDistanceMatrix = parser.accepts("distance_matrix").requiredUnless(optionTimeMatrix)
				.withRequiredArg().ofType(String.class);
		OptionSpec<String> optionInstanceFile = parser.accepts("instance").withRequiredArg().ofType(String.class)
				.required();
		OptionSpec<String> optionAlgorithm = parser.accepts("algorithm").withOptionalArg().ofType(String.class)
				.defaultsTo("algorithmConfig.xml");
		OptionSpec<String> optionSolution = parser.accepts("solution").withOptionalArg().ofType(String.class)
				.defaultsTo("solution.xml");
		parser.accepts("minmax");
		OptionSpec<Integer> optionTimeLimit = parser.accepts("ms").withRequiredArg().ofType(Integer.class);
		OptionSpec<Integer> optionWithoutImprovementIterationLimit = parser.accepts("no_improvment_iterations").withRequiredArg().ofType(Integer.class);
		OptionSpec<Integer> optionWithoutVariationLimit = parser.accepts("stable_iterations").withRequiredArg().ofType(Integer.class);
		OptionSpec<Double> optionWithoutVariationCoefficient = parser.accepts("stable_coef").withRequiredArg().ofType(Double.class);
		OptionSpec<Integer> optionThreads = parser.accepts("threads").withRequiredArg().ofType(Integer.class)
				.defaultsTo(1);
		parser.accepts("nearby");
		parser.accepts("debug");
		OptionSpec<String> optionDebugGraph = parser.accepts("debug-graph").withOptionalArg().ofType(String.class);
		parser.accepts("help").forHelp();

		OptionSet options;
		try {
			options = parser.parse(args);
		} catch (OptionException e) {
			parser.printHelpOn(System.out);
			return;
		}

		if (options.has("help")) {
			parser.printHelpOn(System.out);
			return;
		}

		String algorithmFile = options.valueOf(optionAlgorithm);
		String solutionFile = options.valueOf(optionSolution);
		String timeMatrixFile = options.valueOf(optionTimeMatrix);
		String distanceMatrixFile = options.valueOf(optionsDistanceMatrix);
		String instanceFile = options.valueOf(optionInstanceFile);
		boolean minMax = options.has("minmax");
		Integer solveDuration = options.valueOf(optionTimeLimit);
		Integer solveIterationWithoutImprovement = options.valueOf(optionWithoutImprovementIterationLimit);
		Integer solveIterationWithoutVariation = options.valueOf(optionWithoutVariationLimit);
		Double solveCoefficientWithoutVariation = options.valueOf(optionWithoutVariationCoefficient);
		Integer threads = options.valueOf(optionThreads);
		boolean debug = options.has("debug");
		boolean nearby = options.has("nearby");
		String debugGraphFile = options.valueOf(optionDebugGraph);

		new Run(algorithmFile, solutionFile, timeMatrixFile, distanceMatrixFile, instanceFile, minMax, solveDuration, solveIterationWithoutImprovement, solveIterationWithoutVariation, solveCoefficientWithoutVariation, threads, debug, nearby,
				debugGraphFile);
	}

	public Run(String algorithmFile, String solutionFile, String timeMatrixFile, String distanceMatrixFile,
			String instanceFile, boolean minMax, Integer algorithmDuration, Integer algorithmNoImprovementIteration, Integer algorithmStableIteration, Double algorithmStableCoef, Integer threads, boolean debug, boolean nearby, String debugGraphFile) throws IOException {
		VehicleRoutingTransportCostsMatrix.Builder costMatrixBuilder = VehicleRoutingTransportCostsMatrix.Builder
				.newInstance(false);
		if (timeMatrixFile != null) {
			readTimeFile(costMatrixBuilder, timeMatrixFile);
		}
		if (distanceMatrixFile != null) {
			readDistanceFile(costMatrixBuilder, distanceMatrixFile);
		}
		run(algorithmFile, instanceFile, costMatrixBuilder.build(), minMax, algorithmDuration, algorithmNoImprovementIteration, algorithmStableIteration, algorithmStableCoef, solutionFile, threads, debug, nearby, debugGraphFile);
	}

	private void readTimeFile(VehicleRoutingTransportCostsMatrix.Builder costMatrixBuilder, String path)
			throws IOException {
		int n = 0;
		for (String line : Files.readAllLines(Paths.get(path))) {
			int nn = 0;
			for (String f : line.split(" ")) {
				costMatrixBuilder.addTransportTime(String.valueOf(n), String.valueOf(nn), Float.valueOf(f));
				nn++;
			}
			n++;
		}
	}

	private void readDistanceFile(VehicleRoutingTransportCostsMatrix.Builder costMatrixBuilder, String path)
			throws IOException {
		int n = 0;
		for (String line : Files.readAllLines(Paths.get(path))) {
			int nn = 0;
			for (String f : line.split(" ")) {
				costMatrixBuilder.addTransportDistance(String.valueOf(n), String.valueOf(nn), Float.valueOf(f));
				nn++;
			}
			n++;
		}
	}

	private String solutiontToString(VehicleRoutingProblemSolution solution) {
		String myRet = "";
		int i = 0;
		String previous = "";
		for (VehicleRoute route : solution.getRoutes()) {
			myRet += "\n" + route.getVehicle().getId() + ": " + route.getStart().getLocation().getId() + " ";
			for (TourActivity act : route.getActivities()) {
				String jobId;
				if (act instanceof JobActivity) {
					++i;
					jobId = ((JobActivity) act).getJob().getId();
				} else {
					jobId = "0";
				}
				if (jobId != previous) {
					myRet += jobId + " ";
				}
				previous = jobId;
			}
			myRet += route.getEnd().getLocation().getId();
		}
		myRet += "\nUnassigned : ";
		for (Job j : solution.getUnassignedJobs()) {
			myRet += j.getId() + " ";
		}
		return "Nb delivery : " + i + "\n" + myRet;
	}

	private void run(String algorithmFile, String instanceFile, final VehicleRoutingTransportCostsMatrix costMatrix, boolean minMax,
			Integer algorithmDuration, Integer algorithmNoImprovementIteration, Integer algorithmStableIteration, Double algorithmStableCoef, final String solutionFile, Integer threads, boolean debug, boolean nearby, String debugGraphFile) {

		VehicleRoutingProblem.Builder vrpBuilder = VehicleRoutingProblem.Builder.newInstance();
		if(nearby) {
			VehicleRoutingTransportCosts costEdit = new AbstractForwardVehicleRoutingTransportCosts() {

				@Override
				public double getTransportCost(Location from, Location to, double departureTime, double setupDuration, Driver driver, Vehicle vehicle) {
				if(from == null || to == null)
					return 0.0;
				if (vehicle == null) return costMatrix.getDistance(from.getId(), to.getId());
					VehicleCostParams costParams = vehicle.getType().getVehicleCostParams();
					double setupCost = 0.;
					if(!from.equals(to))
						setupCost = costParams.perSetupTimeUnit * setupDuration * vehicle.getCoefSetupTime();
					return costParams.perDistanceUnit * costMatrix.getDistance(from.getId(), to.getId()) + 20*Math.sqrt(costParams.perDistanceUnit * costMatrix.getDistance(from.getId(), to.getId()))
						+ costParams.perTransportTimeUnit * costMatrix.getTransportTime(from, to,departureTime, 0., driver, vehicle) + 20*Math.sqrt(costParams.perTransportTimeUnit * costMatrix.getTransportTime(from, to,departureTime, 0., driver, vehicle)) + setupCost;
				}

				@Override
				public double getTransportTime(Location from, Location to, double departureTime, double setupDuration, Driver driver, Vehicle vehicle) {
					if(from == null || to == null)
						return 0.0;
					if (from.getIndex() < 0 || to.getIndex() < 0)
						throw new IllegalArgumentException("index of from " + from + " to " + to + " < 0 ");
					return costMatrix.getTransportTime(from, to, departureTime, setupDuration, driver, vehicle);
				}
			};
			vrpBuilder.setRoutingCost(costEdit);
		}
		else {
			vrpBuilder.setRoutingCost(costMatrix);
		}

		new VrpXMLReader(vrpBuilder).read(instanceFile);

		VehicleRoutingProblem problem = vrpBuilder.build();
		final StateManager stateManager = new StateManager(problem);
		ConstraintManager constraintManager = new ConstraintManager(problem, stateManager);

		for(Vehicle vehc : problem.getVehicles())
			if(vehc.getAlternativeSkills().size() > 1) {
				constraintManager.addConstraint(new NoFirstANDSecondSkillConstraint(problem.getLinkedSkills(), stateManager), ConstraintManager.Priority.CRITICAL);
				break;
			}

		SolutionCostCalculator solCost;
		if(minMax) {
			solCost = new SolutionCostCalculator() {
				public double getCosts(VehicleRoutingProblemSolution solution) {
					double c = 0;
					for (VehicleRoute r : solution.getRoutes()) {
						double current = 0.;
						current += stateManager.getRouteState(r, InternalStates.COSTS, Double.class);
						current += r.getVehicle().getType().getVehicleCostParams().fix;
						c = Math.max(c, current);
					}
					c += solution.getUnassignedJobs().size() * (1 + c) * 0.5;
					return c;
				}
			};
		} else {
			solCost = new SolutionCostCalculator() {
				public double getCosts(VehicleRoutingProblemSolution solution) {
					double c = 0;
					for (VehicleRoute r : solution.getRoutes()) {
						c += stateManager.getRouteState(r, InternalStates.COSTS, Double.class);
						c += r.getVehicle().getType().getVehicleCostParams().fix;
					}
					c += solution.getUnassignedJobs().size() * (1 + c) * 0.5;
					return c;
				}
			};
		}
		AlgorithmConfig algo = new AlgorithmConfig();
		AlgorithmConfigXmlReader reader = new AlgorithmConfigXmlReader(algo);
		reader.read(algorithmFile);
		VehicleRoutingAlgorithm algorithm = VehicleRoutingAlgorithms.readAndCreateAlgorithm(problem, algo, threads, solCost, stateManager, constraintManager, true);

		IterationEndsListener displayBestScore = new IterationEndsListener() {
			@Override
			public void informIterationEnds(int i, VehicleRoutingProblem problem, Collection<VehicleRoutingProblemSolution> solutions) {
				if (bestCurrentSolution == null || Solutions.bestOf(solutions).getCost() < bestCurrentSolution.getCost()){
					bestCurrentSolution = Solutions.bestOf(solutions);
					Collection<VehicleRoutingProblemSolution> bestEver = new ArrayList<VehicleRoutingProblemSolution>();
					bestEver.add(bestCurrentSolution);
					new VrpXMLWriter(problem, bestEver, true).write(solutionFile);
					logger.info("Iteration : " + i + " Cost : " + bestCurrentSolution.getCost());
				}
			}
		};
		algorithm.addListener(displayBestScore);
		algorithm.addListener(new BreakScheduling(problem, stateManager, constraintManager));

		if(algorithmDuration != null) {
			TimeTermination prematureTermination = new TimeTermination((long)algorithmDuration);
			algorithm.addTerminationCriterion(prematureTermination);
			algorithm.addListener(prematureTermination);
		}

		if(algorithmStableIteration != null && algorithmStableCoef != null) {
			VariationCoefficientTermination variationCoef = new VariationCoefficientTermination(algorithmStableIteration,algorithmStableCoef);
			algorithm.addTerminationCriterion(variationCoef);
			algorithm.addListener(variationCoef);
		}

		if(algorithmNoImprovementIteration != null)
			algorithm.addTerminationCriterion(new StrictIterationWithoutImprovementTermination(algorithmNoImprovementIteration));

		if (debugGraphFile != null) {
			algorithm.addListener(new AlgorithmSearchProgressChartListener(debugGraphFile));
		}

		Collection<VehicleRoutingProblemSolution> solutions = algorithm.searchSolutions();
		VehicleRoutingProblemSolution bestSolution = Solutions.bestOf(solutions);

		if (debug) {
			System.out.println(solutiontToString(bestSolution));
			SolutionPrinter.print(problem, bestSolution, SolutionPrinter.Print.VERBOSE);
			System.out.println(bestSolution.getRoutes().iterator().next().getDepartureTime());
			System.out.println((int) bestSolution.getRoutes().iterator().next().getEnd().getArrTime() / 60 / 60);
		}

		new VrpXMLWriter(problem, solutions).write(solutionFile);
	}
}
