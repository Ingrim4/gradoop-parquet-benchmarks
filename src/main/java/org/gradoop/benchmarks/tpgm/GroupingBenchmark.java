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
import java.time.temporal.ChronoField;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.TimeUnit;

import org.apache.commons.cli.CommandLine;
import org.apache.flink.api.java.ExecutionEnvironment;
import org.gradoop.benchmarks.AbstractBenchmark;
import org.gradoop.flink.model.api.functions.AggregateFunction;
import org.gradoop.flink.model.api.functions.KeyFunction;
import org.gradoop.flink.model.impl.operators.aggregation.functions.count.Count;
import org.gradoop.flink.model.impl.operators.keyedgrouping.GroupingKeys;
import org.gradoop.flink.model.impl.operators.keyedgrouping.KeyedGrouping;
import org.gradoop.temporal.model.api.TimeDimension;
import org.gradoop.temporal.model.impl.TemporalGraph;
import org.gradoop.temporal.model.impl.TemporalGraphCollection;
import org.gradoop.temporal.model.impl.operators.aggregation.functions.MaxTime;
import org.gradoop.temporal.model.impl.operators.aggregation.functions.MinTime;
import org.gradoop.temporal.model.impl.operators.keyedgrouping.TemporalGroupingKeys;
import org.gradoop.temporal.model.impl.pojo.TemporalEdge;
import org.gradoop.temporal.model.impl.pojo.TemporalGraphHead;
import org.gradoop.temporal.model.impl.pojo.TemporalVertex;

/**
 * A dedicated program for the evaluation of the {@link KeyedGrouping} operator
 * on TPGM graphs.
 */
public class GroupingBenchmark extends AbstractBenchmark {

	/**
	 * Option to specify keyed grouping config
	 */
	private static final String OPTION_CONFIG = "g";

	/**
	 * Index of the selected config
	 */
	private static int SELECTED_CONFIG;

	/**
	 * {@link TimeDimension} that is considered in the context of this benchmark.
	 */
	private static final TimeDimension DIMENSION = TimeDimension.VALID_TIME;

	static {
		OPTIONS.addRequiredOption(OPTION_CONFIG, "config", true, "Select predefined configuration");
	}

	/**
	 * Main program to run the benchmark.
	 * <p>
	 * Example:
	 * {@code $ /path/to/flink run -c org.gradoop.benchmarks.grouping.KeyedGroupingBenchmark
	 * /path/to/gradoop-benchmarks.jar -i hdfs:///graph -o hdfs:///output -c results.csv -g 1 --temporal}
	 *
	 * @param args program arguments
	 * @throws Exception in case of error
	 */
	public static void main(String[] args) throws Exception {
		CommandLine cmd = parseArguments(args, GroupingBenchmark.class.getName());
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
		TemporalGraph grouped = graph.callForGraph(getTPGMGroupingConfig(SELECTED_CONFIG));

		// write graph
		writeOrCountGraph(grouped);

		// execute and write job statistics
		env.execute(GroupingBenchmark.class.getSimpleName() + " - P: " + env.getParallelism());
		writeCSV(env);
	}

	/**
	 * Returns a specific {@link KeyedGrouping} object which will be applied on a
	 * {@link TemporalGraph}.
	 * <p>
	 * This method is meant to be easily extendable in order to provide multiple
	 * keyed grouping configurations.
	 *
	 * @param select the selected keyed grouping configuration
	 * @return the selected {@link KeyedGrouping} object
	 */
	private static KeyedGrouping<TemporalGraphHead, TemporalVertex, TemporalEdge, TemporalGraph, TemporalGraphCollection> getTPGMGroupingConfig(
			int select) {

		List<KeyFunction<TemporalVertex, ?>> vertexKeys;
		List<KeyFunction<TemporalEdge, ?>> edgeKeys;
		List<AggregateFunction> vertexAggregateFunctions;
		List<AggregateFunction> edgeAggregateFunctions;

		switch (select) {
		case 1:
			vertexKeys = Arrays.asList(GroupingKeys.label(), TemporalGroupingKeys.timeStamp(DIMENSION,
					TimeDimension.Field.FROM, ChronoField.ALIGNED_WEEK_OF_YEAR));

			edgeKeys = Arrays.asList(GroupingKeys.label(), TemporalGroupingKeys.timeStamp(DIMENSION,
					TimeDimension.Field.FROM, ChronoField.ALIGNED_WEEK_OF_YEAR));

			vertexAggregateFunctions = Collections.singletonList(new Count("count"));

			edgeAggregateFunctions = Collections.singletonList(new Count("count"));
			break;

		case 2:
			vertexKeys = Arrays.asList(GroupingKeys.label(), TemporalGroupingKeys.timeStamp(DIMENSION,
					TimeDimension.Field.FROM, ChronoField.ALIGNED_WEEK_OF_MONTH));

			edgeKeys = Arrays.asList(GroupingKeys.label(), TemporalGroupingKeys.timeStamp(DIMENSION,
					TimeDimension.Field.FROM, ChronoField.ALIGNED_WEEK_OF_MONTH));

			vertexAggregateFunctions = Arrays.asList(new Count("count"),
					new MinTime("minTime", DIMENSION, TimeDimension.Field.FROM));

			edgeAggregateFunctions = Collections.singletonList(new Count("count"));
			break;

		case 3:
			vertexKeys = Collections.singletonList(GroupingKeys.label());

			edgeKeys = Arrays.asList(GroupingKeys.label(), TemporalGroupingKeys.timeStamp(DIMENSION,
					TimeDimension.Field.FROM, ChronoField.ALIGNED_DAY_OF_WEEK_IN_MONTH));

			vertexAggregateFunctions = Collections.singletonList(new Count("count"));

			edgeAggregateFunctions = Arrays.asList(new Count("count"),
					new MaxTime("maxTime", DIMENSION, TimeDimension.Field.FROM));
			break;

		default:
			throw new IllegalArgumentException("Unsupported config: " + select);
		}

		return new KeyedGrouping<>(vertexKeys, vertexAggregateFunctions, edgeKeys, edgeAggregateFunctions);
	}

	/**
	 * Read values from the given {@link CommandLine} object
	 *
	 * @param cmd the {@link CommandLine} object
	 */
	private static void readCMDArguments(CommandLine cmd) {
		SELECTED_CONFIG = Integer.parseInt(cmd.getOptionValue(OPTION_CONFIG));
	}

	/**
	 * Method to create and add lines to a csv file which contains all necessary
	 * statistics about the {@link KeyedGrouping} operator.
	 *
	 * @param env given {@link ExecutionEnvironment}
	 * @throws IOException exception during file writing
	 */
	private static void writeCSV(ExecutionEnvironment env) throws IOException {
		String head = String.format("%s|%s|%s|%s", "Parallelism", "dataset", "config", "Runtime(s)");

		String tail = String.format("%s|%s|%s|%s", env.getParallelism(), INPUT_PATH, SELECTED_CONFIG,
				env.getLastJobExecutionResult().getNetRuntime(TimeUnit.SECONDS));

		writeToCSVFile(head, tail);
	}
}
