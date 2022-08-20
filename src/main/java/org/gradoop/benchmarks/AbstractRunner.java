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

import java.io.File;
import java.io.IOException;

import org.apache.commons.cli.CommandLine;
import org.apache.commons.cli.DefaultParser;
import org.apache.commons.cli.HelpFormatter;
import org.apache.commons.cli.Options;
import org.apache.commons.cli.ParseException;
import org.apache.flink.api.java.ExecutionEnvironment;
import org.gradoop.flink.io.api.DataSink;
import org.gradoop.flink.io.api.DataSource;
import org.gradoop.flink.io.impl.csv.CSVDataSink;
import org.gradoop.flink.io.impl.csv.CSVDataSource;
import org.gradoop.flink.io.impl.csv.indexed.IndexedCSVDataSink;
import org.gradoop.flink.io.impl.csv.indexed.IndexedCSVDataSource;
import org.gradoop.flink.io.impl.parquet.protobuf.ProtobufParquetDataSink;
import org.gradoop.flink.io.impl.parquet.protobuf.ProtobufParquetDataSource;
import org.gradoop.flink.io.impl.parquet.raw.ParquetDataSink;
import org.gradoop.flink.io.impl.parquet.raw.ParquetDataSource;
import org.gradoop.flink.model.impl.epgm.LogicalGraph;
import org.gradoop.flink.util.GradoopFlinkConfig;
import org.gradoop.temporal.io.api.TemporalDataSink;
import org.gradoop.temporal.io.api.TemporalDataSource;
import org.gradoop.temporal.io.impl.csv.TemporalCSVDataSink;
import org.gradoop.temporal.io.impl.csv.TemporalCSVDataSource;
import org.gradoop.temporal.io.impl.csv.indexed.TemporalIndexedCSVDataSink;
import org.gradoop.temporal.io.impl.csv.indexed.TemporalIndexedCSVDataSource;
import org.gradoop.temporal.io.impl.parquet.protobuf.TemporalProtobufParquetDataSink;
import org.gradoop.temporal.io.impl.parquet.protobuf.TemporalProtobufParquetDataSource;
import org.gradoop.temporal.io.impl.parquet.raw.TemporalParquetDataSink;
import org.gradoop.temporal.io.impl.parquet.raw.TemporalParquetDataSource;
import org.gradoop.temporal.model.impl.TemporalGraph;
import org.gradoop.temporal.util.TemporalGradoopConfig;

/**
 * Base class for benchmarks.
 */
public abstract class AbstractRunner {

	/**
	 * Command line options for the runner.
	 */
	protected static final Options OPTIONS = new Options();
	/**
	 * Flink execution environment.
	 */
	private static ExecutionEnvironment ENV;

	/**
	 * Parses the program arguments and performs sanity checks.
	 *
	 * @param args      program arguments
	 * @param className executing class name (for help display)
	 * @return command line which can be used in the program
	 * @throws ParseException on failure
	 */
	protected static CommandLine parseArguments(String[] args, String className) throws ParseException {
		if (args.length == 0) {
			HelpFormatter formatter = new HelpFormatter();
			formatter.printHelp(className, OPTIONS, true);
			return null;
		}
		return new DefaultParser().parse(OPTIONS, args);
	}

	/**
	 * Reads an EPGM database from a given directory.
	 *
	 * @param directory path to EPGM database
	 * @param format    format in which the graph is stored (csv, indexed)
	 * @return EPGM logical graph
	 * @throws IOException on failure
	 */
	protected static LogicalGraph readLogicalGraph(String directory, String format) throws IOException {
		return getDataSource(directory, format).getLogicalGraph();
	}

	/**
	 * Reads a TPGM graph from a given directory. Currently there are two supported
	 * formats: {@code csv} which uses a {@link TemporalCSVDataSource} and
	 * {@code indexed} which uses a {@link TemporalIndexedCSVDataSource}.
	 *
	 * @param directory path to the TPGM database
	 * @param format    format in which the graph is stored (csv, indexed, parquet)
	 * @return a TPGM graph instance
	 * @throws IOException in case of an error
	 */
	protected static TemporalGraph readTemporalGraph(String directory, String format) throws IOException {
		return getTemporalDataSource(directory, format).getTemporalGraph();
	}

	/**
	 * Writes a logical graph into a given directory.
	 *
	 * @param graph     logical graph
	 * @param directory output path
	 * @param format    output format (csv, indexed, parquet)
	 * @throws Exception on failure
	 */
	protected static void writeLogicalGraph(LogicalGraph graph, String directory, String format) throws Exception {
		graph.writeTo(getDataSink(directory, format, graph.getConfig()), true);
	}

	/**
	 * Writes a temporal graph into a given directory.
	 *
	 * @param graph     the temporal graph to write
	 * @param directory the target directory
	 * @param format    the output format (csv, indexed, parquet)
	 * @throws Exception in case of an error
	 */
	protected static void writeTemporalGraph(TemporalGraph graph, String directory, String format) throws Exception {
		graph.writeTo(getTemporalDataSink(directory, format, graph.getConfig()), true);
	}

	/**
	 * Returns a Flink execution environment.
	 *
	 * @return Flink execution environment
	 */
	protected static ExecutionEnvironment getExecutionEnvironment() {
		if (ENV == null) {
			ENV = ExecutionEnvironment.getExecutionEnvironment();
		}
		return ENV;
	}

	/**
	 * Appends a file separator to the given directory (if not already existing).
	 *
	 * @param directory directory
	 * @return directory with OS specific file separator
	 */
	protected static String appendSeparator(final String directory) {
		return directory.endsWith(File.separator) ? directory : directory + File.separator;
	}

	/**
	 * Converts the given DOT file into a PNG image. Note that this method requires
	 * the "dot" command to be available locally.
	 *
	 * @param dotFile path to DOT file
	 * @param pngFile path to PNG file
	 * @throws IOException on failure
	 */
	protected static void convertDotToPNG(String dotFile, String pngFile) throws IOException {
		ProcessBuilder pb = new ProcessBuilder("dot", "-Tpng", dotFile);
		File output = new File(pngFile);
		pb.redirectOutput(ProcessBuilder.Redirect.appendTo(output));
		pb.start();
	}

	/**
	 * Returns an EPGM DataSource for a given directory and format.
	 *
	 * @param directory input path
	 * @param format    format in which the data is stored (csv, indexed)
	 * @return DataSource for EPGM Data
	 */
	private static DataSource getDataSource(String directory, String format) {
		directory = appendSeparator(directory);
		GradoopFlinkConfig config = GradoopFlinkConfig.createConfig(getExecutionEnvironment());
		format = format.toLowerCase();

		switch (format) {
		case "csv":
			return new CSVDataSource(directory, config);
		case "indexed":
			return new IndexedCSVDataSource(directory, config);
		case "parquet":
			return new ParquetDataSource(directory, config);
		case "protobuf":
			return new ProtobufParquetDataSource(directory, config);
		default:
			throw new IllegalArgumentException("Unsupported format: " + format);
		}
	}

	/**
	 * Creates a TPGM data source for a given directory and format. The format
	 * string {@code csv} creates a {@link TemporalCSVDataSource} whereas
	 * {@code indexed} creates a {@link TemporalIndexedCSVDataSource}.
	 *
	 * @param directory the input path to the TPGM database
	 * @param format    the input format
	 * @return a data source instance
	 */
	private static TemporalDataSource getTemporalDataSource(String directory, String format) {
		directory = appendSeparator(directory);
		TemporalGradoopConfig config = TemporalGradoopConfig.createConfig(getExecutionEnvironment());
		format = format.toLowerCase();

		switch (format) {
		case "csv":
			return new TemporalCSVDataSource(directory, config);
		case "indexed":
			return new TemporalIndexedCSVDataSource(directory, config);
		case "parquet":
			return new TemporalParquetDataSource(directory, config);
		case "protobuf":
			return new TemporalProtobufParquetDataSource(directory, config);
		default:
			throw new IllegalArgumentException("Unsupported format: " + format);
		}
	}

	/**
	 * Returns an EPGM DataSink
	 *
	 * @param directory output path
	 * @param format    output format (csv, indexed)
	 * @param config    gradoop config
	 * @return DataSink for EPGM Data
	 */
	private static DataSink getDataSink(String directory, String format, GradoopFlinkConfig config) {
		directory = appendSeparator(directory);
		format = format.toLowerCase();

		switch (format) {
		case "csv":
			return new CSVDataSink(directory, config);
		case "indexed":
			return new IndexedCSVDataSink(directory, config);
		case "parquet":
			return new ParquetDataSink(directory, config);
		case "protobuf":
			return new ProtobufParquetDataSink(directory, config);
		default:
			throw new IllegalArgumentException("Unsupported format: " + format);
		}
	}

	/**
	 * Returns a TPGM data sink for a given directory and format.
	 *
	 * @param directory the directory where the graph will be stored
	 * @param format    the output format (csv, indexed)
	 * @param config    the temporal config
	 * @return a temporal data sink instance
	 */
	private static TemporalDataSink getTemporalDataSink(String directory, String format, TemporalGradoopConfig config) {
		directory = appendSeparator(directory);
		format = format.toLowerCase();

		switch (format) {
		case "csv":
			return new TemporalCSVDataSink(directory, config);
		case "indexed":
			return new TemporalIndexedCSVDataSink(directory, config);
		case "parquet":
			return new TemporalParquetDataSink(directory, config);
		case "protobuf":
			return new TemporalProtobufParquetDataSink(directory, config);
		default:
			throw new IllegalArgumentException("Unsupported format: " + format);
		}
	}
}
