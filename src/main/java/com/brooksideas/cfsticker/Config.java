package com.brooksideas.cfsticker;

import java.util.ArrayList;

/**
 * CryptoFiatStockTicker config
 */
public class Config {
    private int updateIntervalSeconds;
    private ArrayList<String> cryptos;
    private ArrayList<String> fiats;
    private ArrayList<String> stocks;

    public int getUpdateIntervalSeconds() { return updateIntervalSeconds; }
    public void setUpdateIntervalSeconds(int x) { updateIntervalSeconds = x; }

    public ArrayList<String> getCryptos() {
        if (cryptos == null) {
            cryptos = new ArrayList<String>();
        }

        return cryptos;
    }

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

    public ArrayList<String> getFiats() {
        if (fiats == null) {
            fiats = new ArrayList<String>();
        }

        return fiats;
    }

    public void setFiats(ArrayList<String> x) {
        fiats = x;
    }

    public void addFiat(String x) {
        fiats.add(x);
    }

    public void removeFiat(String x) {
        int i = fiats.indexOf(x);

        if (i >= 0) {
            fiats.remove(i);
        }
    }

    public ArrayList<String> getStocks() {
        if (stocks == null) {
            stocks = new ArrayList<String>();
        }

        return stocks;
    }

    public void setStocks(ArrayList<String> x) {
        stocks = x;
    }

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

