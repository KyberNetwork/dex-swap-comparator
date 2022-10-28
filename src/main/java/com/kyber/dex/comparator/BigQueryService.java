package com.kyber.dex.comparator;

import com.google.api.gax.paging.Page;
import com.google.auth.oauth2.AccessToken;
import com.google.auth.oauth2.GoogleCredentials;
import com.google.auth.oauth2.ServiceAccountCredentials;
import com.google.cloud.bigquery.*;
import com.google.cloud.bigquery.InsertAllRequest.RowToInsert;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.FileInputStream;
import java.util.*;

import static com.kyber.dex.comparator.Constants.BigQuery.DATASET;
import static com.kyber.dex.comparator.Constants.BigQuery.TABLE_NAME;
import static com.kyber.dex.comparator.Constants.Table_Column.*;

public class BigQueryService {
    private final Logger log = LoggerFactory.getLogger(BigQueryService.class);

    private BigQuery getBigQuery() {
        try {
            return BigQueryOptions.newBuilder().setCredentials(
                            ServiceAccountCredentials.fromStream(new FileInputStream("/config/credentials.json"))
                    )
                    .build()
                    .getService();
        } catch (Exception e) {
            log.info("Get Default Bigquery");
            return BigQueryOptions.getDefaultInstance().getService();
        }
    }

    public void createTable(String datasetName, String tableName, Schema schema) {
        try {
            // Initialize client that will be used to send requests. This client only needs to be created
            // once, and can be reused for multiple requests.
            BigQuery bigquery = getBigQuery();

            TableId tableId = TableId.of(datasetName, tableName);
            TimePartitioning partitioning =
                    TimePartitioning.newBuilder(TimePartitioning.Type.DAY)
                            .setField(CREATED_AT) //  name of column to use for partitioning
                            .build();

            TableDefinition tableDefinition = StandardTableDefinition.newBuilder()
                    .setSchema(schema)
                    .setTimePartitioning(partitioning)
                    .build();

            TableInfo tableInfo = TableInfo.newBuilder(tableId, tableDefinition).build();

            bigquery.create(tableInfo);
            log.info("Table created successfully");
        } catch (BigQueryException e) {
            log.error("Table was not created. \n{}", e.getMessage());
        }
    }


    public void createTable() {
        BigQuery bigquery = getBigQuery();
        TableId tableId = TableId.of(DATASET, TABLE_NAME);

        Table tbl = bigquery.getTable(tableId);
        if (tbl == null) {
            Schema schema =
                    Schema.of(
                            Field.of(ID, StandardSQLTypeName.STRING),
                            Field.of(CHAIN_NAME, StandardSQLTypeName.STRING),
                            Field.of(PROVIDER, StandardSQLTypeName.STRING),
                            Field.of(AMOUNT_IN, StandardSQLTypeName.FLOAT64),
                            Field.of(TOKEN_IN, StandardSQLTypeName.STRING),
                            Field.of(AMOUNT_OUT, StandardSQLTypeName.FLOAT64),
                            Field.of(TOKEN_OUT, StandardSQLTypeName.STRING),
                            Field.of(SWAP_VALUE, StandardSQLTypeName.FLOAT64),
                            Field.of(RANK, StandardSQLTypeName.INT64),
                            Field.of(COMPARE, StandardSQLTypeName.FLOAT64),
                            Field.of(CREATED_AT, StandardSQLTypeName.TIMESTAMP),
                            Field.of(STATUS, StandardSQLTypeName.BOOL)
                    );

            createTable(DATASET, TABLE_NAME, schema);
        } else {
            log.info("Table {} are created, no more action!", tbl.getTableId().getTable());
        }
    }

    public void insertRows(List<DexSwapInfo> rows) {
        BigQuery bigquery = getBigQuery();
        TableId tableId = TableId.of(DATASET, TABLE_NAME);

        List<RowToInsert> rowToInsertList = new ArrayList<>();
        rows.forEach(r -> {
            Map<String, Object> rowContent = new HashMap<>();
            rowContent.put(ID, UUID.randomUUID().toString());
            rowContent.put(CHAIN_NAME, r.getChainName());
            rowContent.put(TOKEN_IN, r.getTokenIn());
            rowContent.put(AMOUNT_IN, r.getAmountIn());
            rowContent.put(TOKEN_OUT, r.getTokenOut());
            rowContent.put(AMOUNT_OUT, r.getAmountOut());
            rowContent.put(SWAP_VALUE, r.getSwapValue());
            rowContent.put(PROVIDER, r.getProvider());
            rowContent.put(RANK, r.getRank());
            rowContent.put(COMPARE, r.getCompareToKyber());
            rowContent.put(CREATED_AT, r.getCreatedAt().getTime() / 1000);
            rowContent.put(STATUS, r.getStatus());
            rowToInsertList.add(RowToInsert.of(UUID.randomUUID().toString(), rowContent));
        });


        try {
            InsertAllResponse response =
                    bigquery.insertAll(
                            InsertAllRequest.newBuilder(tableId)
                                    .setRows(rowToInsertList)
                                    .build());

            if (response.hasErrors()) {
                // If any of the insertions failed, this lets you inspect the errors
                for (Map.Entry<Long, List<BigQueryError>> entry : response.getInsertErrors().entrySet()) {
                    log.error("Response error: {}\n", entry.getValue());
                }
            } else {
                log.info("inserted data in bigQuery");
            }
        } catch (Exception e) {
            log.error("Data insert but got err: \t{}", rowToInsertList);
            log.error(e.getMessage(), e);
        }

    }
}
