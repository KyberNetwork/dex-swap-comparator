package com.kyber.dex.comparator;

import java.util.List;
import java.util.Map;

public class Config {
    private List<Token> tokens;
    private Map<String, Map<String, Object>> chains;

    private List<Integer> cases;

    public Config() {}

    public Config(List<Token> tokens, Map<String, Map<String, Object>> chains, List<Integer> cases) {
        this.tokens = tokens;
        this.chains = chains;
        this.cases = cases;
    }

    public List<Token> getTokens() {
        return tokens;
    }

    public void setTokens(List<Token> tokens) {
        this.tokens = tokens;
    }

    public Map<String, Map<String, Object>> getChains() {
        return chains;
    }

    public void setChains(Map<String, Map<String, Object>> chains) {
        this.chains = chains;
    }

    public List<Integer> getCases() {
        return cases;
    }

    public void setCases(List<Integer> cases) {
        this.cases = cases;
    }
}
