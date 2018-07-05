package com.brooksideas.cfsticker;

import java.util.ArrayList;

/**
 * CryptoFiatStockTicker config
 */
public class Config {
    private int updateIntervalSeconds;
    private ArrayList<String> cryptos;
    private ArrayList<String> stocks;

    public int getUpdateIntervalSeconds() { return updateIntervalSeconds; }
    public void setUpdateIntervalSeconds(int x) { updateIntervalSeconds = x; }

    public ArrayList<String> getCryptos() { return cryptos; }
    public void setCryptos(ArrayList<String> x) { cryptos = x; }

    public void addCrypto(String x) {
        cryptos.add(x);
    }

    public void removeCrypto(String x) {
        int i = cryptos.indexOf(x);

        if (i >= 0) {
            cryptos.remove(i);
        }
    }

    public ArrayList<String> getStocks() { return stocks; }
    public void setStocks(ArrayList<String> x) { stocks = x; }

    public void addStock(String x) {
        stocks.add(x);
    }

    public void removeStock(String x) {
        int i = stocks.indexOf(x);

        if (i >= 0) {
            stocks.remove(i);
        }
    }
}

