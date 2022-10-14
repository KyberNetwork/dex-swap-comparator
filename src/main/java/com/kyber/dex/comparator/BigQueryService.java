package com.kyber.dex.comparator;

import com.google.api.gax.paging.Page;
import com.google.cloud.bigquery.*;
import com.google.cloud.bigquery.InsertAllRequest.RowToInsert;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.*;

import static com.kyber.dex.comparator.Constants.BigQuery.DATASET;
import static com.kyber.dex.comparator.Constants.BigQuery.TABLE_NAME;
import static com.kyber.dex.comparator.Constants.Table_Column.*;

public class BigQueryService {
    private final Logger log = LoggerFactory.getLogger(BigQueryService.class);

    public void insertRows(List<DexSwapInfo> rows) {
        BigQuery bigquery = BigQueryOptions.getDefaultInstance().getService();
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
            rowContent.put(CREATED_AT, r.getCreatedAt().getTime()/1000);
            rowToInsertList.add(RowToInsert.of(UUID.randomUUID().toString(), rowContent));
        });



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

    }
}
