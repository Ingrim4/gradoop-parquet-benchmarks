/*
 * Copyright © 2014 - 2021 Leipzig University (Database Research Group)
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.gradoop.benchmarks.tpgm;

import java.io.IOException;
import java.util.concurrent.TimeUnit;

import org.apache.commons.cli.CommandLine;
import org.apache.flink.api.java.ExecutionEnvironment;
import org.gradoop.benchmarks.AbstractBenchmark;
import org.gradoop.temporal.model.impl.TemporalGraph;
import org.gradoop.temporal.model.impl.functions.predicates.AsOf;

/**
 * A dedicated program for parametrized TPGM graph evolution (diff) benchmark.
 * The temporal predicate function is fixed to AS OF for this benchmark. The two
 * query timestamps are specified during program arguments.
 */
public class DifferenceBenchmark extends AbstractBenchmark {
	/**
	 * Option to declare verification
	 */
	private static final String OPTION_VERIFICATION = "v";
	/**
	 * Option to declare the first query timestamp
	 */
	private static final String OPTION_QUERY_1 = "x";
	/**
	 * Option to declare the second query timestamp
	 */
	private static final String OPTION_QUERY_2 = "y";

	/**
	 * Used verification flag
	 */
	private static boolean VERIFICATION;
	/**
	 * Used first query timestamp in milliseconds
	 */
	private static long QUERY_FROM_1;
	/**
	 * Used second query timestamp in milliseconds
	 */
	private static long QUERY_FROM_2;

	static {
		OPTIONS.addRequiredOption(OPTION_QUERY_1, "queryts1", true, "Used first query timestamp [ms]");
		OPTIONS.addRequiredOption(OPTION_QUERY_2, "queryts2", true, "Used second query timestamp [ms]");
		OPTIONS.addOption(OPTION_VERIFICATION, "verification", false, "Verify Snapshot with join.");
	}

	/**
	 * Main program to run the benchmark. Arguments are the available options.
	 * Example:
	 * {@code /path/to/flink run -c org.gradoop.benchmarks.tpgm.DiffBenchmark
	 * path/to/gradoop-benchmarks.jar -i hdfs:///graph -o hdfs:///output -c results.csv
	 * -x 1287000000000 -y 1230000000000}
	 *
	 * @param args program arguments
	 * @throws Exception in case of error
	 */
	public static void main(String[] args) throws Exception {
		CommandLine cmd = parseArguments(args, DifferenceBenchmark.class.getName());
		if (cmd == null) {
			return;
		}

		// read cmd arguments
		readBaseCMDArguments(cmd);
		readCMDArguments(cmd);

		// create gradoop config
		ExecutionEnvironment env = getExecutionEnvironment();

		// read graph
		TemporalGraph graph = readTemporalGraph(INPUT_PATH, INPUT_FORMAT);

		// get the diff
		TemporalGraph diff = graph.diff(new AsOf(QUERY_FROM_1), new AsOf(QUERY_FROM_2));

		// apply optional verification
		if (VERIFICATION) {
			diff = diff.verify();
		}

		// write graph
		writeOrCountGraph(diff);

		// execute and write job statistics
		env.execute(DifferenceBenchmark.class.getSimpleName() + " - P: " + env.getParallelism());
		writeCSV(env);
	}

	/**
	 * Reads the given arguments from command line
	 *
	 * @param cmd command line
	 */
	private static void readCMDArguments(CommandLine cmd) {
		QUERY_FROM_1 = Long.parseLong(cmd.getOptionValue(OPTION_QUERY_1));
		QUERY_FROM_2 = Long.parseLong(cmd.getOptionValue(OPTION_QUERY_2));
		VERIFICATION = cmd.hasOption(OPTION_VERIFICATION);
	}

	/**
	 * Method to create and add lines to a csv-file
	 *
	 * @param env given ExecutionEnvironment
	 * @throws IOException exception during file writing
	 */
	private static void writeCSV(ExecutionEnvironment env) throws IOException {
		String head = String.format("%s|%s|%s|%s|%s|%s", "Parallelism", "dataset", "asOf1(ms)", "asOf2(ms)", "verify",
				"Runtime(s)");

		String tail = String.format("%s|%s|%s|%s|%s|%s", env.getParallelism(), INPUT_PATH, QUERY_FROM_1, QUERY_FROM_2,
				VERIFICATION, env.getLastJobExecutionResult().getNetRuntime(TimeUnit.SECONDS));

		writeToCSVFile(head, tail);
	}
}
