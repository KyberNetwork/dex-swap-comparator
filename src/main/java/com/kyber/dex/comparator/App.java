package com.kyber.dex.comparator;

import okhttp3.ConnectionPool;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.Response;
import org.json.JSONArray;
import org.json.JSONObject;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.yaml.snakeyaml.Yaml;
import org.yaml.snakeyaml.constructor.Constructor;

import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.time.ZoneId;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.TimeUnit;
import java.util.function.Function;
import java.util.stream.Collectors;

import static com.kyber.dex.comparator.Utils.*;


public class App {
    static {
        System.setProperty("user.timezone", ZoneId.of("Asia/Ho_Chi_Minh").getId());
    }

    private static final Logger log = LoggerFactory.getLogger(App.class);
    private static Date currentTimestamp = new Date();

    private final static String _1INCH_API = "https://api.1inch.io/v4.0/";
    private final static String PARASWAPS_API = "https://apiv5.paraswap.io";
    private final static Map<String, String> _0X_API = Map.of(
            "1", "https://api.0x.org/swap/v1",
            "137", "https://polygon.api.0x.org/swap/v1",
            "43114", "https://avalanche.api.0x.org/swap/v1"
    );

    private final static String KYBER_API = "https://aggregator-api.kyberswap.com/";

    private static String getKyberPriceAPI(String chainId) {
        return String.format("https://price.kyberswap.com/%s/api/v1/prices", chainId);
    }

    private static final BigQueryService bigQueryService = new BigQueryService();
    private final static OkHttpClient client = new OkHttpClient.Builder()
            .readTimeout(10, TimeUnit.SECONDS)
            .connectionPool(new ConnectionPool(8, 20, TimeUnit.SECONDS))
            .build();

    private static Token addPriceToToken(List<Integer> cases, Token t, Response response) {
        if (response.isSuccessful()) {
            try {
                String jsonData = response.body().string();
                JSONObject js = new JSONObject(jsonData);
                JSONObject datas = js.getJSONObject("data");
                JSONArray data = datas.getJSONArray("prices");
                JSONObject price = data.getJSONObject(0);
                t.setPrice(price.getDouble("marketPrice"));
                t.setAmounts(cases.stream().map(i -> i / t.getPrice()).collect(Collectors.toList()));
            } catch (IOException e) {
                log.error(e.getMessage(), e);
            }
            response.body().close();
        }
        return t;
    }


    private static void getPriceAndAmounts(Map<String, Token> maps, Token from, Token to) {
        from.setPrice(to.getPrice());
        from.setAmounts(to.getAmounts());
        maps.put(from.getName(), from);
    }

    private static void runPriceCompair() throws FileNotFoundException {
        Map<String, Token> tokenPriceMap = new ConcurrentHashMap<>();
        currentTimestamp = new Date();
        String configFromENV = System.getenv().getOrDefault("chains", "~/config.yml");
        String pathToConfigFile = configFromENV.isEmpty() ? App.class.getClassLoader().getResource("config.yml").getPath() : configFromENV;
//        log.info("Path to param config: {}", configFromENV);
//        log.info("Path to config: {}", pathToConfigFile);
        Path configPath = Paths.get(pathToConfigFile);
        Yaml yaml = new Yaml(new Constructor(Config.class));
        Config cfg = yaml.load(new FileInputStream(configPath.toFile()));

        Map<String, Map<String, Object>> chains = cfg.getChains();
        List<Integer> cases = cfg.getCases();


        chains.forEach((k, v) -> {
            List<DexSwapInfo> swaps = new ArrayList<>();
            List<Map<String, Object>> tokens = (List<Map<String, Object>>) v.get("tokens");
            tokens.forEach(t -> {
                Token token = new Token();
                token.setName(t.get("name").toString());
                token.setAddress(t.get("address").toString());
                token.setChain(k);
                token.setDecimals((int) t.get("decimals"));
                switch (token.getName()) {
                    case "ETH":
                        getPriceAndAmounts(tokenPriceMap, token, tokenPriceMap.get("WETH"));
                        return;

                    case "MATIC":
                        getPriceAndAmounts(tokenPriceMap, token, tokenPriceMap.get("WMATIC"));
                        return;

                    default:
                        Request getPrice = makeBaseRequest(getKyberPriceAPI(k) + "?ids=" + token.getAddress());
                        try {
                            doCallHttp(client, getPrice, response -> {
                                tokenPriceMap.put(token.getName(), addPriceToToken(cases, token, response));
                                return null;
                            });
                        } catch (IOException ex) {
                            log.error(ex.getMessage(), ex);
                        }
                }

            });

            if (tokenPriceMap.isEmpty()) {
                log.info("Cannot fetch current prices\nExit");
                System.exit(0);
            }

            String[] pairs = ((String) v.get("pairs")).split(",");
            for (String pair : pairs) {
                String[] p = pair.split("-");
                Token srcToken = tokenPriceMap.get(p[0]);
                Token destToken = tokenPriceMap.get(p[1]);

                srcToken.getAmounts().forEach(i -> {
                    log.info("Chain: {}\tPairs: {}\tAmount: {}", k, pair, i);
                    List<DexSwapInfo> tmp = new ArrayList<>();
                    Map<String, Object> requestVariable = Map.of(
                            "chain", k,
                            "chainId", v.get("id"),
                            "fromToken", srcToken,
                            "toToken", destToken
                    );
                    kyberSwapCheck(requestVariable, i, tmp);
                    _1InchSwapCheck(requestVariable, i, tmp);
                    zeroXSwapCheck(requestVariable, i, tmp);
                    paraSwapCheck(requestVariable, i, tmp);
                    tmp.sort((o1, o2) -> {
                        if (o1.getAmountOut() > o2.getAmountOut()) return -1;
                        if (o1.getAmountOut() < o2.getAmountOut()) return 1;
                        return 0;
                    });
                    swaps.addAll(rankingByAmount(tmp));
//                    swaps.addAll(tmp);
                });
            }
            log.info("data to be inserted: {}", swaps.size());
            bigQueryService.insertRows(swaps);
        });
    }

    public static void main(String[] args) throws InterruptedException {
        bigQueryService.createTable();
//        run();
        while (true) {
            Runnable runnable = () -> {
                try {
                    runPriceCompair();
                    log.info("Round done!");
                } catch (FileNotFoundException e) {
                    log.error(e.getMessage(), e);
                }

            };
            Thread t = new Thread(runnable);
            t.start();

            Thread.sleep(1000 * 60 * 5);
        }
    }


    private static void paraSwapCheck(Map<String, Object> requestVariable, double amount, List<DexSwapInfo> swaps) {
        DexSwapInfo swapInfo = new DexSwapInfo();
        Token fromToken = (Token) requestVariable.get("fromToken");
        Token toToken = (Token) requestVariable.get("toToken");
        String chain = (String) requestVariable.get("chain");
        String chainId = (String) requestVariable.get("chainId");
        swapInfo.setTokenIn(fromToken.getName());
        swapInfo.setTokenOut(toToken.getName());
        swapInfo.setChainName(chain);
        swapInfo.setProvider("paraswap");
        swapInfo.setCompareToKyber(compareRate(swapInfo.getAmountOut(), swaps.get(0).getAmountOut()));

        Function<Response, Void> func = response -> {
            try {
                if (response.isSuccessful()) {
                    String jsonData = response.body().string();
                    JSONObject js = new JSONObject(jsonData).getJSONObject("priceRoute");
                    swapInfo.setAmountIn(toAmountWithoutDecimal(js.getString("srcAmount"), fromToken.getDecimals()));
                    swapInfo.setAmountOut(toAmountWithoutDecimal(js.getString("destAmount"), toToken.getDecimals()));
                    swapInfo.setCompareToKyber(compareRate(swapInfo.getAmountOut(), swaps.get(0).getAmountOut()));
                    swapInfo.setStatus(true);
                } else {
                    swapInfo.setAmountIn(0d);
                    swapInfo.setAmountOut(0d);
                    swapInfo.setCompareToKyber(0d);
                    swapInfo.setStatus(false);
                    log.info("Call api \t{}\nnot success with response: \n{}", response.request().url().url(), response.body().string());
                }
            } catch (IOException e) {
                swapInfo.setAmountIn(0d);
                swapInfo.setAmountOut(0d);
                swapInfo.setCompareToKyber(0d);
                swapInfo.setStatus(false);
                log.error(e.getMessage(), e);
            }

            swaps.add(swapInfo);
            response.body().close();
            return null;
        };

        double amountDecimals = amount * Math.pow(10, fromToken.getDecimals().doubleValue());
        swapInfo.setSwapValue(amount * fromToken.getPrice());
        swapInfo.setCreatedAt(currentTimestamp);
        Request req = makeBaseRequest(PARASWAPS_API +
                "/prices?amount=" + amountWithDecimal(amountDecimals, fromToken.getDecimals()) + "&side=SELL&srcToken=" + fromToken.getAddress() + "&srcDecimals=" + fromToken.getDecimals() +
                "&destToken=" + toToken.getAddress() + "&destDecimals=" + toToken.getDecimals() + "&network=" + chainId);
//        log.info("Req: {}", req.url());
        try {
            doCallHttp(client, req, func);
        } catch (IOException e) {
            log.error(e.getMessage(), e);
        }
    }

    private static void zeroXSwapCheck(Map<String, Object> requestVariable, double amount, List<DexSwapInfo> swaps) {
        DexSwapInfo swapInfo = new DexSwapInfo();
        Token srcToken = (Token) requestVariable.get("fromToken");
        Token destToken = (Token) requestVariable.get("toToken");
        String chain = (String) requestVariable.get("chain");
        String chainId = (String) requestVariable.get("chainId");
        swapInfo.setTokenIn(srcToken.getName());
        swapInfo.setTokenOut(destToken.getName());
        swapInfo.setChainName(chain);
        swapInfo.setProvider("0x");

        Function<Response, Void> func = response -> {
            try {
                if (response.isSuccessful()) {
                    String jsonData = response.body().string();
                    JSONObject js = new JSONObject(jsonData);
                    swapInfo.setAmountIn(toAmountWithoutDecimal(js.getString("sellAmount"), srcToken.getDecimals()));
                    swapInfo.setAmountOut(toAmountWithoutDecimal(js.getString("buyAmount"), destToken.getDecimals()));
                    swapInfo.setCompareToKyber(compareRate(swapInfo.getAmountOut(), swaps.get(0).getAmountOut()));
                    swapInfo.setStatus(true);
                } else {
                    swapInfo.setAmountIn(0d);
                    swapInfo.setAmountOut(0d);
                    swapInfo.setCompareToKyber(0d);
                    swapInfo.setStatus(false);
                    log.info("Call api \t{}\nnot success with response: \n{}", response.request().url().url(), response.body().string());
                }
            } catch (IOException e) {
                swapInfo.setAmountIn(0d);
                swapInfo.setAmountOut(0d);
                swapInfo.setCompareToKyber(0d);
                swapInfo.setStatus(false);
                log.error(e.getMessage(), e);
            }
            swaps.add(swapInfo);
            response.body().close();
            return null;
        };

        double amountDecimals = amount * Math.pow(10, srcToken.getDecimals().doubleValue());
        swapInfo.setSwapValue(amount * srcToken.getPrice());
        swapInfo.setCreatedAt(currentTimestamp);
        Request req = makeBaseRequest(_0X_API.get(chainId) +
                "/price?sellToken=" + srcToken.getAddress() + "&buyToken=" + destToken.getAddress() + "&sellAmount=" + amountWithDecimal(amountDecimals, srcToken.getDecimals()));
//        log.info("Req: {}", req.url());
        try {
            doCallHttp(client, req, func);
        } catch (IOException e) {
            log.error(e.getMessage(), e);
        }
    }

    private static void _1InchSwapCheck(Map<String, Object> requestVariable, double amount, List<DexSwapInfo> swaps) {
        DexSwapInfo swapInfo = new DexSwapInfo();
        Token srcToken = (Token) requestVariable.get("fromToken");
        Token destToken = (Token) requestVariable.get("toToken");
        String chain = (String) requestVariable.get("chain");
        String chainId = (String) requestVariable.get("chainId");
        swapInfo.setTokenIn(srcToken.getName());
        swapInfo.setTokenOut(destToken.getName());
        swapInfo.setChainName(chain);
        swapInfo.setProvider("1Inch");

        Function<Response, Void> func = response -> {
            try {
                if (response.isSuccessful()) {
                    String jsonData = response.body().string();
                    JSONObject js = new JSONObject(jsonData);
                    swapInfo.setAmountIn(toAmountWithoutDecimal(js.getString("fromTokenAmount"), srcToken.getDecimals()));
                    swapInfo.setAmountOut(toAmountWithoutDecimal(js.getString("toTokenAmount"), destToken.getDecimals()));
                    swapInfo.setCompareToKyber(compareRate(swapInfo.getAmountOut(), swaps.get(0).getAmountOut()));
                    swapInfo.setStatus(true);
                } else {
                    swapInfo.setAmountIn(0d);
                    swapInfo.setAmountOut(0d);
                    swapInfo.setCompareToKyber(0d);
                    swapInfo.setStatus(false);
                    log.info("Call api \t{}\nnot success with response: \n{}", response.request().url().url(), response.body().string());
                }
            } catch (IOException e) {
                swapInfo.setAmountIn(0d);
                swapInfo.setAmountOut(0d);
                swapInfo.setCompareToKyber(0d);
                swapInfo.setStatus(false);
                log.error(e.getMessage(), e);
            }

            swaps.add(swapInfo);
            response.body().close();
            return null;
        };


        double amountDecimals = amount * Math.pow(10, srcToken.getDecimals().doubleValue());
        swapInfo.setSwapValue(amount * srcToken.getPrice());
        swapInfo.setCreatedAt(currentTimestamp);
        Request req = makeBaseRequest(_1INCH_API + chainId +
                "/quote?fromTokenAddress=" + srcToken.getAddress() + "&toTokenAddress=" + destToken.getAddress() + "&amount=" + amountWithDecimal(amountDecimals, srcToken.getDecimals()));
//        log.info("Req: {}", req.url());
        try {
            doCallHttp(client, req, func);
        } catch (IOException e) {
            log.error(e.getMessage(), e);
        }
    }

    private static void kyberSwapCheck(Map<String, Object> requestVariable, double amount, List<DexSwapInfo> swaps) {
        DexSwapInfo swapInfo = new DexSwapInfo();
        Token srcToken = (Token) requestVariable.get("fromToken");
        Token destToken = (Token) requestVariable.get("toToken");
        String chain = (String) requestVariable.get("chain");
        String chainId = (String) requestVariable.get("chainId");
        swapInfo.setTokenIn(srcToken.getName());
        swapInfo.setTokenOut(destToken.getName());
        swapInfo.setChainName(chain);
        swapInfo.setProvider("kyber");

        Function<Response, Void> func = response -> {
            try {
                //{"code":40020,"message":"could not find route","errorEntities":[],"details":[{"requestId":"6609788e-31c5-4da5-bdba-88ed8dc6364f"}]}
                if (response.isSuccessful()) {
                    String jsonData = response.body().string();
                    JSONObject js = new JSONObject(jsonData);
                    swapInfo.setAmountIn(toAmountWithoutDecimal(js.getString("inputAmount"), srcToken.getDecimals()));
                    swapInfo.setAmountOut(toAmountWithoutDecimal(js.getString("outputAmount"), destToken.getDecimals()));
                    swapInfo.setStatus(true);
                } else {
                    swapInfo.setAmountIn(0d);
                    swapInfo.setAmountOut(0d);
                    swapInfo.setStatus(false);

                    log.info("Call api \t{}\nnot success with response: \n{}", response.request().url().url(), response);
                }

            } catch (IOException e) {
                log.error(e.getMessage(), e);
            }
            swaps.add(swapInfo);
            response.body().close();
            return null;
        };


        double amountDecimals = amount * Math.pow(10, srcToken.getDecimals().doubleValue());
        swapInfo.setSwapValue(amount * srcToken.getPrice());
        swapInfo.setCompareToKyber(0d);
        swapInfo.setCreatedAt(currentTimestamp);
        Request req = makeBaseRequest(KYBER_API + getChainById(chainId) +
                "/route/encode/?tokenIn=" + srcToken.getAddress() + "&tokenOut=" + destToken.getAddress() + "&amountIn=" + amountWithDecimal(amountDecimals, srcToken.getDecimals())
                + "&to=0xdac17f958d2ee523a2206206994597c13d831ec7&gasInclude=true");
        try {
//            log.info("Req: {}", req.url());
            doCallHttp(client, req, func);
        } catch (IOException e) {
            log.error(e.getMessage(), e);
        }
    }
}