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
import java.util.Collection;

import com.graphhopper.jsprit.analysis.toolbox.AlgorithmSearchProgressChartListener;
import com.graphhopper.jsprit.core.algorithm.VehicleRoutingAlgorithm;
import com.graphhopper.jsprit.core.algorithm.VehicleRoutingAlgorithmBuilder;
import com.graphhopper.jsprit.core.algorithm.state.InternalStates;
import com.graphhopper.jsprit.core.algorithm.state.StateManager;
import com.graphhopper.jsprit.core.algorithm.termination.IterationWithoutImprovementTermination;
import com.graphhopper.jsprit.core.algorithm.termination.TimeTermination;
import com.graphhopper.jsprit.core.algorithm.termination.VariationCoefficientTermination;
import com.graphhopper.jsprit.core.problem.VehicleRoutingProblem;
import com.graphhopper.jsprit.core.problem.constraint.ConstraintManager;
import com.graphhopper.jsprit.core.problem.io.VrpXMLReader;
import com.graphhopper.jsprit.core.problem.io.VrpXMLWriter;
import com.graphhopper.jsprit.core.problem.job.Job;
import com.graphhopper.jsprit.core.problem.solution.SolutionCostCalculator;
import com.graphhopper.jsprit.core.problem.solution.VehicleRoutingProblemSolution;
import com.graphhopper.jsprit.core.problem.solution.route.VehicleRoute;
import com.graphhopper.jsprit.core.problem.solution.route.activity.TourActivity;
import com.graphhopper.jsprit.core.problem.solution.route.activity.TourActivity.JobActivity;
import com.graphhopper.jsprit.core.reporting.SolutionPrinter;
import com.graphhopper.jsprit.core.util.Solutions;
import com.graphhopper.jsprit.core.util.VehicleRoutingTransportCostsMatrix;

import joptsimple.OptionException;
import joptsimple.OptionParser;
import joptsimple.OptionSet;
import joptsimple.OptionSpec;

public class Run {

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
		OptionSpec<Integer> optionTimeLimit = parser.accepts("ms").withRequiredArg().ofType(Integer.class);
		OptionSpec<Integer> optionIterationLimit = parser.accepts("iterations").withRequiredArg().ofType(Integer.class);
		OptionSpec<Integer> optionWithoutVariationLimit = parser.accepts("stable_iterations").withRequiredArg().ofType(Integer.class);
		OptionSpec<Double> optionWithoutVariationCoefficient = parser.accepts("stable_coef").withRequiredArg().ofType(Double.class);
		OptionSpec<Integer> optionThreads = parser.accepts("threads").withRequiredArg().ofType(Integer.class)
				.defaultsTo(1);
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
		Integer solveDuration = options.valueOf(optionTimeLimit);
		Integer solveIteration = options.valueOf(optionIterationLimit);
		Integer solveIterationWithoutVariation = options.valueOf(optionWithoutVariationLimit);
		Double solveCoefficientWithoutVariation = options.valueOf(optionWithoutVariationCoefficient);
		Integer threads = options.valueOf(optionThreads);
		boolean debug = options.has("debug");
		String debugGraphFile = options.valueOf(optionDebugGraph);

		new Run(algorithmFile, solutionFile, timeMatrixFile, distanceMatrixFile, instanceFile, solveDuration, solveIteration, solveIterationWithoutVariation, solveCoefficientWithoutVariation, threads, debug,
				debugGraphFile);
	}

	public Run(String algorithmFile, String solutionFile, String timeMatrixFile, String distanceMatrixFile,
			String instanceFile, Integer algorithmDuration, Integer algorithmIteration, Integer algorithmStableIteration, Double algorithmStableCoef, Integer threads, boolean debug, String debugGraphFile) throws IOException {
		VehicleRoutingTransportCostsMatrix.Builder costMatrixBuilder = VehicleRoutingTransportCostsMatrix.Builder
				.newInstance(true);
		if (timeMatrixFile != null) {
			readTimeFile(costMatrixBuilder, timeMatrixFile);
		}
		if (distanceMatrixFile != null) {
			readDistanceFile(costMatrixBuilder, distanceMatrixFile);
		}
		run(algorithmFile, instanceFile, costMatrixBuilder.build(), algorithmDuration, algorithmIteration, algorithmStableIteration, algorithmStableCoef, solutionFile, threads, debug, debugGraphFile);
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

	private void run(String algorithmFile, String instanceFile, VehicleRoutingTransportCostsMatrix costMatrix,
			Integer algorithmDuration, Integer algorithmIteration, Integer algorithmStableIteration, Double algorithmStableCoef, String solutionFile, Integer threads, boolean debug, String debugGraphFile) {

		VehicleRoutingProblem.Builder vrpBuilder = VehicleRoutingProblem.Builder.newInstance();
		vrpBuilder.setRoutingCost(costMatrix);

		new VrpXMLReader(vrpBuilder).read(instanceFile);

		VehicleRoutingProblem problem = vrpBuilder.build();
		VehicleRoutingAlgorithmBuilder vraBuilder = new VehicleRoutingAlgorithmBuilder(problem, algorithmFile);
		vraBuilder.setNuOfThreads(threads);
		vraBuilder.addDefaultCostCalculators();
		vraBuilder.addCoreConstraints();
		final StateManager stateManager = new StateManager(problem);

		ConstraintManager constraintManager = new ConstraintManager(problem, stateManager);

		vraBuilder.setStateAndConstraintManager(stateManager, constraintManager);
		vraBuilder.setObjectiveFunction(new SolutionCostCalculator() {
			public double getCosts(VehicleRoutingProblemSolution solution) {
				double c = 0;
				for (VehicleRoute r : solution.getRoutes()) {
					c += stateManager.getRouteState(r, InternalStates.COSTS, Double.class);
					c += r.getVehicle().getType().getVehicleCostParams().fix;
				}
				c += solution.getUnassignedJobs().size() * (1 + c) * 0.1;
				return c;
			}
		});

		VehicleRoutingAlgorithm algorithm = vraBuilder.build();

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

		if(algorithmIteration != null)
			algorithm.addTerminationCriterion(new IterationWithoutImprovementTermination(algorithmIteration));

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
