// Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
// SPDX-License-Identifier: Apache-2.0

package com.amazonaws.c3r.io.schema;

import com.amazonaws.c3r.io.ParquetRowReader;
import com.amazonaws.c3r.utils.FileUtil;
import lombok.Builder;
import lombok.NonNull;

/**
 * Used to generate a schema file for a specific Parquet file. User can ask for either a simple, autogenerated schema or be walked through
 * the entire schema creation process.
 */
public final class ParquetSchemaGenerator extends SchemaGenerator {
    /**
     * Set up for schema generation and validate settings.
     *
     * @param inputParquetFile Parquet file to read header information from
     * @param targetJsonFile   Where to save the schema
     * @param overwrite        Whether the {@code targetJsonFile} should be overwritten (if it exists)
     */
    @Builder
    private ParquetSchemaGenerator(@NonNull final String inputParquetFile,
                                   @NonNull final String targetJsonFile,
                                   @NonNull final Boolean overwrite) {
        super(inputParquetFile, targetJsonFile, overwrite);
        FileUtil.verifyReadableFile(inputParquetFile);
        final var reader = new ParquetRowReader(inputParquetFile);
        sourceHeaders = reader.getHeaders();
        sourceColumnTypes = reader.getParquetSchema().getColumnClientDataTypes();
    }

}
