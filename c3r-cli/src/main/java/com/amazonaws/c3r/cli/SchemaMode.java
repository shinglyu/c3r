// Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
// SPDX-License-Identifier: Apache-2.0

package com.amazonaws.c3r.cli;

import com.amazonaws.c3r.cleanrooms.CleanRoomsDao;
import com.amazonaws.c3r.config.ClientSettings;
import com.amazonaws.c3r.exception.C3rIllegalArgumentException;
import com.amazonaws.c3r.io.FileFormat;
import com.amazonaws.c3r.io.schema.CsvSchemaGenerator;
import com.amazonaws.c3r.io.schema.ParquetSchemaGenerator;
import lombok.Getter;
import lombok.extern.slf4j.Slf4j;
import picocli.CommandLine;

import java.io.File;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.Callable;

import static com.amazonaws.c3r.cli.Main.generateCommandLine;

/**
 * Command line utility to help create a schema for a data file. Walks the user through each column in the input to see if/how it should be
 * transformed during encryption for upload to a collaboration.
 */
@Slf4j
@Getter
@CommandLine.Command(name = "schema",
        mixinStandardHelpOptions = true,
        version = CliDescriptions.VERSION,
        descriptionHeading = "%nDescription:%n",
        description = "Generate an encryption schema for a tabular file.")
public class SchemaMode implements Callable<Integer> {

    /**
     * Required command line arguments.
     */
    @Getter
    static class RequiredArgs {
        /**
         * {@value CliDescriptions#INPUT_DESCRIPTION_SCHEMA}.
         */
        @CommandLine.Parameters(
                description = CliDescriptions.INPUT_DESCRIPTION_SCHEMA,
                paramLabel = "<input>")
        private String input = null;
    }


    /**
     * Required values as specified by the user.
     */
    @CommandLine.ArgGroup(validate = false, heading = "%nRequired parameters:%n")
    private RequiredArgs requiredArgs = new RequiredArgs();

    /**
     * Class for the different modes of scheme generation.
     */
    @Getter
    public static class SubMode {
        /**
         * Create a simple schema automatically.
         */
        @CommandLine.Option(
                names = {"--template", "-t"},
                required = true,
                description = {"Create template schema file for <input>.",
                        "NOTE: user needs to edit schema file before use."})
        private boolean templateMode = false;

        /**
         * Walk user through entire schema creation process.
         */
        @CommandLine.Option(
                names = {"--interactive", "-i"},
                required = true,
                description = "Create a schema file interactively for <input>.")
        private boolean interactiveMode = false;
    }

    /**
     * Which generation mode to use for execution.
     */
    @CommandLine.ArgGroup(multiplicity = "1", heading = "%nGeneration mode (specify one of these):%n")
    private SubMode subMode = new SubMode();

    /**
     * Optional command line arguments.
     */
    @Getter
    static class OptionalArgs {
        /**
         * For description see {@link CliDescriptions#ID_DESCRIPTION}.
         */
        @CommandLine.Option(names = {"--id"},
                description = CliDescriptions.ID_DESCRIPTION,
                paramLabel = "<value>")
        private UUID id = null;

        /**
         * If this input file has headers.
         *
         * <p>
         * Note: Using a default value of {@code true} means when the flag {@code --noHeaders}
         * is passed, @{code hasHeaders} is set to {@code false}.
         */
        @CommandLine.Option(names = {"--noHeaders", "-p"},
                description = "Indicates <input> has no column headers (CSV only).")
        private boolean hasHeaders = true;

        /**
         * {@value CliDescriptions#FILE_FORMAT_DESCRIPTION}.
         */
        @CommandLine.Option(names = {"--fileFormat", "-e"},
                description = CliDescriptions.FILE_FORMAT_DESCRIPTION,
                paramLabel = "<format>")
        private FileFormat fileFormat = null;

        /**
         * {@value CliDescriptions#OUTPUT_DESCRIPTION_SCHEMA}.
         */
        @CommandLine.Option(names = {"--output", "-o"},
                description = CliDescriptions.OUTPUT_DESCRIPTION_SCHEMA,
                paramLabel = "<file>")
        private String output = null;

        /**
         * {@value CliDescriptions#OVERWRITE_DESCRIPTION}.
         */
        @CommandLine.Option(names = {"--overwrite", "-f"},
                description = CliDescriptions.OVERWRITE_DESCRIPTION)
        private boolean overwrite = false;

        /**
         * {@value CliDescriptions#ENABLE_STACKTRACE_DESCRIPTION}.
         */
        @CommandLine.Option(names = {"--enableStackTraces", "-v"},
                description = CliDescriptions.ENABLE_STACKTRACE_DESCRIPTION)
        private boolean enableStackTraces = false;
    }

    /**
     * Optional values as specified by the user.
     */
    @CommandLine.ArgGroup(validate = false, heading = "%nOptional parameters:%n")
    private OptionalArgs optionalArgs = new OptionalArgs();

    /** DAO for interacting with AWS Clean Rooms. */
    private CleanRoomsDao cleanRoomsDao;

    /**
     * Return a CLI instance for schema generation with no specified AWS Clean Rooms collaboration.
     *
     * <p>
     * Note: {@link #getApp} is the intended method for manually creating this class
     * with the appropriate CLI settings.
     */
    SchemaMode() {
        this.cleanRoomsDao = null;
    }

    /**
     * Return a CLI instance for schema generation configured for a particular AWS Clean Rooms collaboration.
     *
     * <p>
     * Note: {@link #getApp} is the intended method for manually creating this class
     * with the appropriate CLI settings.
     *
     * @param cleanRoomsDao Object containing information on the cryptographic settings for the collaboration
     *                      if provided, else {@code null}.
     */
    SchemaMode(final CleanRoomsDao cleanRoomsDao) {
        this.cleanRoomsDao = cleanRoomsDao;
    }

    /**
     * Get the schema mode command line application with a custom CleanRoomsDao.
     *
     * @param cleanRoomsDao AWS Clean Rooms data access object to use for schema mode.
     * @return CommandLine interface for `schema` with customized AWS Clean Rooms access.
     */
    static CommandLine getApp(final CleanRoomsDao cleanRoomsDao) {
        return generateCommandLine(new SchemaMode(cleanRoomsDao));
    }

    /**
     * Get the settings from AWS Clean Rooms for this collaboration.
     *
     * @return Cryptographic computing rules for collaboration, else {@code null} if not applicable.
     */
    public ClientSettings getClientSettings() {
        if (optionalArgs.id == null) {
            return null;
        }
        final ClientSettings settings;
        if (cleanRoomsDao == null) {
            settings = new CleanRoomsDao().getCollaborationDataEncryptionMetadata(optionalArgs.id.toString());
        } else {
            settings = cleanRoomsDao.getCollaborationDataEncryptionMetadata(optionalArgs.id.toString());
        }
        return settings;
    }

    /**
     * Validates that required information is specified.
     *
     * @throws C3rIllegalArgumentException If user input is invalid
     */
    private void validate() {
        if (requiredArgs.getInput().isBlank()) {
            throw new C3rIllegalArgumentException("Specified input file name is blank.");
        }
        if (optionalArgs.output != null && optionalArgs.output.isBlank()) {
            throw new C3rIllegalArgumentException("Specified output file name is blank.");
        }
    }

    /**
     * Execute schema generation help utility.
     *
     * @return {@value Main#SUCCESS} if no errors encountered else {@value Main#FAILURE}
     */
    @Override
    public Integer call() {
        try {
            validate();
            final File file = new File(requiredArgs.getInput());
            final String fileNameNoPath = file.getName();
            final String outFile = Objects.requireNonNullElse(optionalArgs.output, fileNameNoPath + ".json");

            final FileFormat fileFormat = Optional.ofNullable(optionalArgs.fileFormat).orElseGet(() ->
                    FileFormat.fromFileName(requiredArgs.getInput()));
            if (fileFormat == null) {
                throw new C3rIllegalArgumentException("Unknown file format (consider using the --format flag): " + requiredArgs.getInput());
            }
            switch (fileFormat) {
                case CSV:
                    final var csvSchemaGenerator = CsvSchemaGenerator.builder()
                            .inputCsvFile(requiredArgs.getInput())
                            .hasHeaders(optionalArgs.hasHeaders)
                            .targetJsonFile(outFile)
                            .overwrite(optionalArgs.overwrite)
                            .clientSettings(getClientSettings())
                            .build();
                    csvSchemaGenerator.generateSchema(subMode);
                    break;
                case PARQUET:
                    if (!optionalArgs.hasHeaders) {
                        throw new C3rIllegalArgumentException("--noHeaders is not applicable for Parquet files.");
                    }
                    final var parquetSchemaGenerator = ParquetSchemaGenerator.builder()
                            .inputParquetFile(requiredArgs.getInput())
                            .targetJsonFile(outFile)
                            .overwrite(optionalArgs.overwrite)
                            .clientSettings(getClientSettings())
                            .build();
                    parquetSchemaGenerator.generateSchema(subMode);
                    break;
                default:
                    throw new C3rIllegalArgumentException("Unsupported file format for schema generation: " + fileFormat);
            }
        } catch (Exception e) {
            Main.handleException(e, optionalArgs.enableStackTraces);
            return Main.FAILURE;
        }
        return Main.SUCCESS;
    }
}
