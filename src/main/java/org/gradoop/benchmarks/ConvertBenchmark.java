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
package org.gradoop.benchmarks;

import org.apache.commons.cli.CommandLine;
import org.apache.flink.api.java.ExecutionEnvironment;
import org.gradoop.flink.io.api.DataSink;
import org.gradoop.flink.io.api.DataSource;
import org.gradoop.flink.io.impl.csv.CSVDataSource;
import org.gradoop.flink.io.impl.parquet.protobuf.ProtobufParquetDataSink;
import org.gradoop.flink.io.impl.parquet.raw.ParquetDataSink;
import org.gradoop.flink.model.impl.epgm.GraphCollection;
import org.gradoop.flink.util.GradoopFlinkConfig;
import org.gradoop.temporal.io.api.TemporalDataSink;
import org.gradoop.temporal.io.api.TemporalDataSource;
import org.gradoop.temporal.io.impl.csv.TemporalCSVDataSource;
import org.gradoop.temporal.io.impl.parquet.protobuf.TemporalProtobufParquetDataSink;
import org.gradoop.temporal.io.impl.parquet.raw.TemporalParquetDataSink;
import org.gradoop.temporal.model.impl.TemporalGraphCollection;
import org.gradoop.temporal.util.TemporalGradoopConfig;

public class ConvertBenchmark extends AbstractRunner {
	/**
	 * Option to declare path to input graph
	 */
	private static final String OPTION_INPUT_PATH = "i";
	/**
	 * Option to declare path to output graph
	 */
	private static final String OPTION_OUTPUT_PATH = "o";
	/**
	 * Option to declare input/output as temporal
	 */
	private static final String OPTION_TEMPORAL = "t";
	/**
	 * Option to declare input/output as temporal
	 */
	private static final String OPTION_PROTOBUF = "p";
	/**
	 * Used input path
	 */
	private static String INPUT_PATH;
	/**
	 * Used output path
	 */
	private static String OUTPUT_PATH;
	/**
	 * Used temporal flag
	 */
	private static boolean TEMPORAL;
	/**
	 * Used protobuf data sink
	 */
	private static boolean PROTOBUF;

	static {
		OPTIONS.addOption(OPTION_INPUT_PATH, "input", true, "Path to source files.");
		OPTIONS.addOption(OPTION_OUTPUT_PATH, "output", true, "Path to output files.");
		OPTIONS.addOption(OPTION_TEMPORAL, "temporal", false, "Is input/ouput temporal.");
		OPTIONS.addOption(OPTION_PROTOBUF, "protobuf", false, "Is output protobuf");
	}

	/**
	 * Main program to run the benchmark. Arguments are the available options.
	 *
	 * @param args program arguments
	 * @throws Exception in case of Error
	 */
	public static void main(String[] args) throws Exception {
		CommandLine cmd = parseArguments(args, ConvertBenchmark.class.getName());

		if (cmd == null) {
			return;
		}

		// test if minimum arguments are set
		performSanityCheck(cmd);

		// read cmd arguments
		readCMDArguments(cmd);

		// create gradoop config
		ExecutionEnvironment env = ExecutionEnvironment.getExecutionEnvironment();

		if (TEMPORAL) {
			TemporalGradoopConfig conf = TemporalGradoopConfig.createConfig(env);
			// read graph
			TemporalDataSource source = new TemporalCSVDataSource(INPUT_PATH, conf);
			TemporalGraphCollection collection = source.getTemporalGraphCollection();

			// write graph
			TemporalDataSink sink = PROTOBUF
					? new TemporalProtobufParquetDataSink(OUTPUT_PATH, conf)
					: new TemporalParquetDataSink(OUTPUT_PATH, conf);
			sink.write(collection, true);
		} else {
			GradoopFlinkConfig conf = GradoopFlinkConfig.createConfig(env);

			// read graph
			DataSource source = new CSVDataSource(INPUT_PATH, conf);
			GraphCollection collection = source.getGraphCollection();

			// write graph
			DataSink sink = PROTOBUF
					? new ProtobufParquetDataSink(OUTPUT_PATH, conf)
					: new ParquetDataSink(OUTPUT_PATH, conf);
			sink.write(collection, true);
		}

		// execute and write job statistics
		env.execute(String.format("ConvertBenchmark - in: %s, out: %s", INPUT_PATH, OUTPUT_PATH));
	}

	/**
	 * Reads the given arguments from command line
	 *
	 * @param cmd command line
	 */
	private static void readCMDArguments(CommandLine cmd) {
		INPUT_PATH = cmd.getOptionValue(OPTION_INPUT_PATH);
		OUTPUT_PATH = cmd.getOptionValue(OPTION_OUTPUT_PATH);
		TEMPORAL = cmd.hasOption(OPTION_TEMPORAL);
		PROTOBUF = cmd.hasOption(OPTION_PROTOBUF);
	}

	/**
	 * Checks if the minimum of arguments is provided
	 *
	 * @param cmd command line
	 */
	private static void performSanityCheck(CommandLine cmd) {
		if (!cmd.hasOption(OPTION_INPUT_PATH)) {
			throw new IllegalArgumentException("Define a graph input directory.");
		}
		if (!cmd.hasOption(OPTION_OUTPUT_PATH)) {
			throw new IllegalArgumentException("Define a graph output directory.");
		}
	}
}
