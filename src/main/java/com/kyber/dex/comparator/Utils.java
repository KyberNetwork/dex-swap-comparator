package com.kyber.dex.comparator;

import okhttp3.Call;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.Response;

import java.io.IOException;
import java.math.BigDecimal;
import java.math.MathContext;
import java.text.DecimalFormat;
import java.util.List;
import java.util.function.Function;

public class Utils {

    public static String amountWithDecimal(Double amount, int decimal) {
        DecimalFormat df = new DecimalFormat("#");
        df.setMaximumFractionDigits(decimal);
        String s = df.format(Math.round(amount));
        return s.contains(".") ? s.substring(0, s.indexOf(".")) : s;
    }

    public static double toAmountWithoutDecimal(String amount, int decimal) {
        if (amount == null || "".equals(amount)) return 0d;
        BigDecimal bigDecimal = new BigDecimal(amount);
        return bigDecimal.divide(BigDecimal.valueOf(Math.pow(10, decimal))).doubleValue();
    }

    public static double compareRate(double x, double y) {
        return (x * 100 / y) - 100;
    }

    public static String getChainById(String chainId) {
        switch (chainId) {
            case "1": return "ethereum";
            case "137": return "polygon";
            case "43114": return "avalanche";
            default: return "";
        }
    }

    public static List<DexSwapInfo> rankingByAmount(List<DexSwapInfo> sortedSwaps) {
        int rank = 1;
        sortedSwaps.get(0).setRank(rank);
        DexSwapInfo _2nd = sortedSwaps.get(1);
        double tmp = _2nd.getAmountOut();
        rank++;
        _2nd.setRank(rank);
        for (int i = 2; i < sortedSwaps.size(); i++) {
            DexSwapInfo dsi = sortedSwaps.get(i);
            if (dsi.getAmountOut() < tmp) {
                rank++;
                tmp = dsi.getAmountOut();
            }
            dsi.setRank(rank);
        }
        return sortedSwaps;
    }
    public static Request makeBaseRequest(String url) {
        return new Request.Builder()
                .url(url)
                .build();
    }

    public static void doCallHttp(OkHttpClient client, Request request, Function<Response, Void> func) throws IOException {
        Call call = client.newCall(request);
        Response response = call.execute();
        func.apply(response);
    }
}
