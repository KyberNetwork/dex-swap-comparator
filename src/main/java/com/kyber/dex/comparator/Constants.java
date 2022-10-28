package com.kyber.dex.comparator;

public class Constants {
    static class BigQuery {
        public static final String PROJECT_ID = "develop-339203";
        public static final String DATASET = "data_warehouse";
        public static final String TABLE_NAME = "dex_swap_info_2";
    }

    static class Table_Column {
        public static final String ID = "id";
        public static final String CHAIN_NAME = "chain_name";
        public static final String PROVIDER = "provider";
        public static final String AMOUNT_IN = "amount_in";
        public static final String TOKEN_IN = "token_in";
        public static final String AMOUNT_OUT = "amount_out";
        public static final String TOKEN_OUT = "token_out";
        public static final String SWAP_VALUE = "swap_value";
        public static final String RANK = "rank";
        public static final String COMPARE = "compare_to_kyber";
        public static final String CREATED_AT = "created_at";
        public static final String STATUS = "status";
    }
}
