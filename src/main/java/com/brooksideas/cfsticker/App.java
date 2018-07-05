package com.brooksideas.cfsticker;

import java.awt.*;
import java.awt.event.*;
import java.awt.image.BufferedImage;
import java.io.FileWriter;
import java.io.IOException;
import java.io.StringWriter;
import java.net.MalformedURLException;
import java.net.URL;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.Map;
import java.util.TreeMap;
import javax.imageio.ImageIO;
import javax.swing.*;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;

/**
 * CryptoFiatStockTicker
 *
 * Desktop ticker for cryptocurrencies and stocks.
 *
 * Cryptocurrency data provided by coinmarketcap.com.
 * Stock Data provided for free by IEX. View IEX’s Terms of Use.
 * iextrading.com
 *
 * Copyright 2018 Shawn McMurdo
 *
 * Redistribution and use in source and binary forms, with or without modification, are permitted provided that the following conditions are met:
 *
 * 1. Redistributions of source code must retain the above copyright notice, this list of conditions and the following disclaimer.
 *
 * 2. Redistributions in binary form must reproduce the above copyright notice, this list of conditions and the following disclaimer in the documentation and/or other materials provided with the distribution.
 *
 * THIS SOFTWARE IS PROVIDED BY THE COPYRIGHT HOLDERS AND CONTRIBUTORS "AS IS" AND ANY EXPRESS OR IMPLIED WARRANTIES, INCLUDING, BUT NOT LIMITED TO, THE IMPLIED WARRANTIES OF MERCHANTABILITY AND FITNESS FOR A PARTICULAR PURPOSE ARE DISCLAIMED. IN NO EVENT SHALL THE COPYRIGHT HOLDER OR CONTRIBUTORS BE LIABLE FOR ANY DIRECT, INDIRECT, INCIDENTAL, SPECIAL, EXEMPLARY, OR CONSEQUENTIAL DAMAGES (INCLUDING, BUT NOT LIMITED TO, PROCUREMENT OF SUBSTITUTE GOODS OR SERVICES; LOSS OF USE, DATA, OR PROFITS; OR BUSINESS INTERRUPTION) HOWEVER CAUSED AND ON ANY THEORY OF LIABILITY, WHETHER IN CONTRACT, STRICT LIABILITY, OR TORT (INCLUDING NEGLIGENCE OR OTHERWISE) ARISING IN ANY WAY OUT OF THE USE OF THIS SOFTWARE, EVEN IF ADVISED OF THE POSSIBILITY OF SUCH DAMAGE.
 *
 */
public class App {
    private static final String cmcTickerUrl = "https://api.coinmarketcap.com/v2/ticker/";
    private static final String cmcListingsUrl = "https://api.coinmarketcap.com/v2/listings";
    private static final String iexBaseUrl = "https://api.iextrading.com/1.0/stock/";
    private static final String cryptoRadioLabel = "Crypto";
    private static final String stockRadioLabel = "Stock";
    private static final String addButtonLabel = "Add";
    private static final String removeButtonLabel = "Remove";
    private static final String updateButtonLabel = "Update";
    private static final String saveButtonLabel = "Save";
    private static final String closeButtonLabel = "Close";
    private static final String configFileName = "cfsticker.json";
    private static final String addImageFileName = "/images/Add16.gif";
    private static final String removeImageFileName = "/images/Delete16.gif";
    private static final String updateImageFileName = "/images/Refresh16.gif";
    private static final String saveImageFileName = "/images/Save16.gif";
    private static final int minUpdateIntervalSeconds = 60;

    private JFrame window;
    private JPanel mainPanel;
    private GridBagConstraints mainGbc;
    private ObjectMapper mapper;
    private Config config;
    private TreeMap<String, CryptoListing> cryptoListings;
    private TreeMap<String, CryptoQuote> cryptoQuotes;
    private TreeMap<String, StockQuote> stockQuotes;
    private PlaceholderTextField symbolTextField;
    private HashMap<String, JPanel> tickerPanels;
    private HashMap<String, JLabel> symbolLabels;
    private HashMap<String, JLabel> priceLabels;
    private Color upColor;
    private Color downColor;
    private boolean isStock;
    private int gridx;
    private int updateIntervalSeconds;

    public static void main(String[] args) {
        App app = new App();
        app.run();
    }

    public void run() {
        String symbol;
        String cmcId;
        String text;

        // JSON response mapper
        mapper = new ObjectMapper();

        // Read config file
        try {
            byte[] configJson = Files.readAllBytes(Paths.get(configFileName));
            config = mapper.readValue(configJson, Config.class);
        } catch (IOException ioe) {
            System.err.println(ioe);
            System.exit(1);
        }

        // CryptoListings
        cryptoListings = new TreeMap<String, CryptoListing>();
        getCryptoListings();

        // Colors
        upColor = new Color(0x00ff00);
        downColor = new Color(0xff0000);

        // Update quotes interval in seconds
        int secs = config.getUpdateIntervalSeconds();

        if (secs < minUpdateIntervalSeconds) {
            updateIntervalSeconds = minUpdateIntervalSeconds;
        } else {
            updateIntervalSeconds = secs;
        }

        // Initial cryptos
        cryptoQuotes = new TreeMap<String, CryptoQuote>();
        ArrayList<String> cryptos = config.getCryptos();

        for (String crypto : cryptos) {
            symbol = crypto.toUpperCase();
            cmcId = getCmcId(symbol);

            if (cmcId != null) {
                cryptoQuotes.put(symbol, new CryptoQuote(symbol, cmcId));
            }
        }

        // Initial stocks
        stockQuotes = new TreeMap<String, StockQuote>();
        ArrayList<String> stocks = config.getStocks();

        for (String stock : stocks) {
            symbol = stock.toUpperCase();
            stockQuotes.put(symbol, new StockQuote(symbol));
        }

        // Component references
        tickerPanels = new HashMap<String, JPanel>();
        symbolLabels = new HashMap<String, JLabel>();
        priceLabels = new HashMap<String, JLabel>();

        // Main panel
        mainPanel = new JPanel();
        GridBagLayout layout = new GridBagLayout();
        mainPanel.setLayout(layout);
        mainPanel.setBackground(Color.black);
        mainGbc = new GridBagConstraints();
        mainGbc.fill = GridBagConstraints.HORIZONTAL;
        mainGbc.ipadx = 10;
        mainGbc.ipady = 5;

        // Action subpanel
        JPanel actionPanel = new JPanel();
        GridBagLayout actionLayout = new GridBagLayout();
        actionPanel.setLayout(actionLayout);
        actionPanel.setBackground(Color.black);
        GridBagConstraints actionGbc = new GridBagConstraints();
        actionGbc.fill = GridBagConstraints.HORIZONTAL;
        actionGbc.ipadx = 2;
        actionGbc.ipady = 2;

        // Symbol text field
        symbolTextField = new PlaceholderTextField();
        symbolTextField.setColumns(6);
        symbolTextField.setForeground(Color.white);
        symbolTextField.setBackground(Color.black);
        symbolTextField.setPlaceholder("Symbol");
        SymbolListener symbolListener = new SymbolListener();
        symbolTextField.addActionListener(symbolListener);
        actionGbc.gridx = 0;
        actionGbc.gridy = 0;
        actionGbc.gridwidth = 1;
        actionGbc.gridheight = 1;
        actionPanel.add(symbolTextField, actionGbc);

        // Radio buttons
        RadioListener radioListener = new RadioListener();
        isStock = false;

        // Crypto button
        text = "<html><span style=\"font-size: 0.8em\">" + cryptoRadioLabel + "</span></html>";
        JRadioButton cryptoButton = new JRadioButton(text);
        cryptoButton.setForeground(Color.white);
        cryptoButton.setBackground(Color.black);
        cryptoButton.setMnemonic(KeyEvent.VK_B);
        cryptoButton.setActionCommand(cryptoRadioLabel);
        cryptoButton.setSelected(true);
        cryptoButton.addActionListener(radioListener);
        actionGbc.gridx = 0;
        actionGbc.gridy = 1;
        actionGbc.gridwidth = 1;
        actionGbc.gridheight = 1;
        actionPanel.add(cryptoButton, actionGbc);

        // Stock button
        text = "<html><span style=\"font-size: 0.8em\">" + stockRadioLabel + "</span></html>";
        JRadioButton stockButton = new JRadioButton(text);
        stockButton.setForeground(Color.white);
        stockButton.setBackground(Color.black);
        stockButton.setActionCommand(stockRadioLabel);
        stockButton.addActionListener(radioListener);
        actionGbc.gridx = 0;
        actionGbc.gridy = 2;
        actionGbc.gridwidth = 1;
        actionGbc.gridheight = 1;
        actionPanel.add(stockButton, actionGbc);

        // Radio button group
        ButtonGroup radioGroup = new ButtonGroup();
        radioGroup.add(cryptoButton);
        radioGroup.add(stockButton);

        // Action buttons
        ButtonListener buttonListener = new ButtonListener();

        // Add button
        JButton addButton = new JButton();
        BufferedImage addImage = null;

        try {
            addImage = ImageIO.read(getClass().getResource(addImageFileName));
        } catch (IOException ioe) {
            System.err.println(ioe);
            System.exit(1);
        }

        ImageIcon addIcon = new ImageIcon(addImage, addButtonLabel);
        addButton.setIcon(addIcon);
        addButton.setToolTipText(addButtonLabel);
        addButton.setForeground(Color.white);
        addButton.setBackground(Color.black);
        addButton.setActionCommand(addButtonLabel);
        addButton.addActionListener(buttonListener);
        actionGbc.gridx = 1;
        actionGbc.gridy = 0;
        actionGbc.gridwidth = 1;
        actionGbc.gridheight = 1;
        actionPanel.add(addButton, actionGbc);

        // Remove button
        JButton removeButton = new JButton();
        BufferedImage removeImage = null;

        try {
            removeImage = ImageIO.read(getClass().getResource(removeImageFileName));
        } catch (IOException ioe) {
            System.err.println(ioe);
            System.exit(1);
        }

        ImageIcon removeIcon = new ImageIcon(removeImage, removeButtonLabel);
        removeButton.setIcon(removeIcon);
        removeButton.setToolTipText(removeButtonLabel);
        removeButton.setForeground(Color.white);
        removeButton.setBackground(Color.black);
        removeButton.setActionCommand(removeButtonLabel);
        removeButton.addActionListener(buttonListener);
        actionGbc.gridx = 1;
        actionGbc.gridy = 1;
        actionGbc.gridwidth = 1;
        actionGbc.gridheight = 1;
        actionPanel.add(removeButton, actionGbc);

        // Update button
        JButton updateButton = new JButton();
        BufferedImage updateImage = null;

        try {
            updateImage = ImageIO.read(getClass().getResource(updateImageFileName));
        } catch (IOException ioe) {
            System.err.println(ioe);
            System.exit(1);
        }

        ImageIcon updateIcon = new ImageIcon(updateImage, updateButtonLabel);
        updateButton.setIcon(updateIcon);
        updateButton.setToolTipText(updateButtonLabel);
        updateButton.setForeground(Color.white);
        updateButton.setBackground(Color.black);
        updateButton.setActionCommand(updateButtonLabel);
        UpdateListener updateListener = new UpdateListener();
        updateButton.addActionListener(updateListener);
        actionGbc.gridx = 2;
        actionGbc.gridy = 0;
        actionGbc.gridwidth = 1;
        actionGbc.gridheight = 1;
        actionPanel.add(updateButton, actionGbc);

        // Save button
        JButton saveButton = new JButton();
        BufferedImage saveImage = null;

        try {
            saveImage = ImageIO.read(getClass().getResource(saveImageFileName));
        } catch (IOException ioe) {
            System.err.println(ioe);
            System.exit(1);
        }

        ImageIcon saveIcon = new ImageIcon(saveImage, saveButtonLabel);
        saveButton.setIcon(saveIcon);
        saveButton.setToolTipText(saveButtonLabel);
        saveButton.setForeground(Color.white);
        saveButton.setBackground(Color.black);
        saveButton.setActionCommand(saveButtonLabel);
        saveButton.addActionListener(buttonListener);
        actionGbc.gridx = 2;
        actionGbc.gridy = 1;
        actionGbc.gridwidth = 1;
        actionGbc.gridheight = 1;
        actionPanel.add(saveButton, actionGbc);

        // Close button
        Icon closeIcon = UIManager.getIcon("InternalFrame.closeIcon");
        JButton closeButton = new JButton();
        closeButton.setIcon(closeIcon);
        closeButton.setToolTipText(closeButtonLabel);
        closeButton.setForeground(Color.white);
        closeButton.setBackground(Color.black);
        closeButton.setActionCommand(closeButtonLabel);
        closeButton.addActionListener(buttonListener);
        actionGbc.gridx = 2;
        actionGbc.gridy = 2;
        actionGbc.gridwidth = 1;
        actionGbc.gridheight = 1;
        actionPanel.add(closeButton, actionGbc);

        // Action subpanel
        gridx = 0;
        mainGbc.gridx = gridx;
        mainGbc.gridy = 0;
        mainGbc.gridwidth = 2;
        mainGbc.gridheight = 2;
        mainPanel.add(actionPanel, mainGbc);

        // Tickers
        JPanel tickerPanel;
        gridx = 2;
        mainGbc.gridy = 0;
        mainGbc.gridwidth = 1;
        mainGbc.gridheight = 2;

        // Crypto tickers
        for (Map.Entry<String, CryptoQuote> entry : cryptoQuotes.entrySet()) {
            symbol = entry.getKey();
            tickerPanel = createTicker(symbol);
            mainGbc.gridx = gridx;
            mainPanel.add(tickerPanel, mainGbc);
            gridx++;
        }

        // Stock tickers
        for (Map.Entry<String, StockQuote> entry : stockQuotes.entrySet()) {
            symbol = entry.getKey();
            tickerPanel = createTicker(symbol);
            mainGbc.gridx = gridx;
            mainPanel.add(tickerPanel, mainGbc);
            gridx++;
        }

        // Window frame
        window = new JFrame();
        window.setContentPane(mainPanel);
        window.setLocation(0, 0);
        window.setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);
        updateWindow();
        window.setVisible(true);

        // Initial update
        updateAll();

        // Update every 10 minutes
        Timer timer = new Timer(updateIntervalSeconds * 1000, updateListener);
        timer.setRepeats(true);
        timer.start();
    }

    public void updateAll() {
        getCryptoQuotes();
        getStockQuotes();
        updateTickers();
        updateWindow();
    }

    public void getCryptoListings() {
        Map<String, Object> map = null;
        URL url = null;

        try {
            url = new URL(cmcListingsUrl);
        } catch (MalformedURLException mfue) {
            System.err.println(mfue);
            System.exit(1);
        }

        try {
            map = mapper.readValue(url, Map.class);
        } catch (IOException ioe) {
            System.err.println(ioe);
            System.exit(1);
        }

        ArrayList<Map<String, Object>> data = (ArrayList<Map<String, Object>>)map.get("data");
        String symbol;
        String cmcId;
        String name;
        CryptoListing listing;

        for (Map<String, Object> d : data) {
            symbol = (String)d.get("symbol");
            cmcId = "" + (Integer)d.get("id");
            name = (String)d.get("name");
            listing = new CryptoListing(symbol, cmcId, name);
            cryptoListings.put(symbol, listing);
        }
    }

    public void getCryptoQuotes() {
        URL url = null;
        String symbol = "";
        String cmcId = "";
        CryptoQuote cryptoQuote = null;
        Map<String, Object> map = null;
        String name = "";
        int rank = 0;
        double price = 0.0;
        double marketCap = 0.0;
        double percentChange24h = 0.0;

        for (Map.Entry<String, CryptoQuote> entry : cryptoQuotes.entrySet()) {
            symbol = entry.getKey();
            cryptoQuote = entry.getValue();
            cmcId = cryptoQuote.getCmcId();

            try {
                url = new URL(cmcTickerUrl + cmcId);
            } catch (MalformedURLException mfue) {
                System.err.println(mfue);
                continue;
            }

            try {
                map = mapper.readValue(url, Map.class);
            } catch (IOException ioe) {
                System.err.println(ioe);
                continue;
            }

            name = getCryptoName(map);
            cryptoQuote.setName(name);

            rank = getCryptoRank(map);
            cryptoQuote.setRank(rank);

            price = getCryptoPrice(map);
            cryptoQuote.setPrice(price);

            marketCap = getCryptoMarketCap(map);
            cryptoQuote.setMarketCap(marketCap);

            percentChange24h = getCryptoPercentChange24h(map);
            cryptoQuote.setPercentChange24h(percentChange24h);
        }
    }

    public void getStockQuotes() {
        URL url = null;
        String symbol = "";
        StockQuote stockQuote = null;
        Map<String, Object> map = null;
        String name = "";
        double price = 0.0;
        double marketCap = 0.0;
        double percentChange24h = 0.0;

        for (Map.Entry<String, StockQuote> entry : stockQuotes.entrySet()) {
            symbol = entry.getKey();
            stockQuote = entry.getValue();

            try {
                url = new URL(iexBaseUrl + symbol + "/quote");
            } catch (MalformedURLException mfue) {
                System.err.println(mfue);
                continue;
            }

            try {
                map = mapper.readValue(url, Map.class);
            } catch (IOException ioe) {
                System.err.println(ioe);
                continue;
            }

            name = getStockName(map);
            stockQuote.setName(name);

            price = getStockPrice(map);
            stockQuote.setPrice(price);

            marketCap = getStockMarketCap(map);
            stockQuote.setMarketCap(marketCap);

            percentChange24h = getStockPercentChange24h(map);
            stockQuote.setPercentChange24h(percentChange24h);
        }
    }

    public void updateTickers() {
        String symbol;
        CryptoQuote cryptoQuote;
        StockQuote stockQuote;
        String name;
        int rank;
        double price;
        String sPrice;
        double marketCap;
        String sMarketCap;
        double percentChange24h;
        String text;

        // Cryptos
        for (Map.Entry<String, CryptoQuote> entry : cryptoQuotes.entrySet()) {
            symbol = entry.getKey();
            cryptoQuote = entry.getValue();
            name = cryptoQuote.getName();
            rank = cryptoQuote.getRank();
            price = cryptoQuote.getPrice();
            sPrice = formatPrice(price);
            marketCap = cryptoQuote.getMarketCap();
            sMarketCap = formatMarketCap(marketCap);
            percentChange24h = cryptoQuote.getPercentChange24h();

            // Symbol label
            text = "<html><b>" + symbol + "</b><sup>" + rank + "</sup></html>";
            JLabel symbolLabel = symbolLabels.get(symbol);

            if (symbolLabel != null) {
                symbolLabel.setText(text);
            } else {
                System.err.println("Could not find symbolLabel for symbol: " + symbol);
                continue;
            }

            // Price label
            text = "<html><p style=\"text-align: center\"><span style=\"font-size: 1.3em\"><b>" + sPrice + "</b></span><br/><span style=\"font-size: 0.8em\">" + sMarketCap + "</span></p></html>";
            JLabel priceLabel = priceLabels.get(symbol);

            if (priceLabel != null) {
                priceLabel.setText(text);
            } else {
                System.err.println("Could not find priceLabel for symbol: " + symbol);
                continue;
            }

            if (percentChange24h > 0.5) {
                priceLabel.setForeground(upColor);
            } else if (percentChange24h < -0.5) {
                priceLabel.setForeground(downColor);
            } else {
                priceLabel.setForeground(Color.white);
            }
        }

        // Stocks
        for (Map.Entry<String, StockQuote> entry : stockQuotes.entrySet()) {
            symbol = entry.getKey();
            stockQuote = entry.getValue();
            name = stockQuote.getName();
            price = stockQuote.getPrice();
            sPrice = formatPrice(price);
            marketCap = stockQuote.getMarketCap();
            sMarketCap = formatMarketCap(marketCap);
            percentChange24h = stockQuote.getPercentChange24h();

            // Symbol label
            text = "<html><b>" + symbol + "</b></html>";
            JLabel symbolLabel = symbolLabels.get(symbol);

            if (symbolLabel != null) {
                symbolLabel.setText(text);
            } else {
                System.err.println("Could not find symbolLabel for symbol: " + symbol);
                continue;
            }

            // Price label
            text = "<html><p style=\"text-align: center\"><span style=\"font-size: 1.3em\"><b>" + sPrice + "</b></span><br/><span style=\"font-size: 0.8em\">" + sMarketCap + "</span></p></html>";
            JLabel priceLabel = priceLabels.get(symbol);

            if (priceLabel != null) {
                priceLabel.setText(text);
            } else {
                System.err.println("Could not find priceLabel for symbol: " + symbol);
                continue;
            }

            if (percentChange24h > 0.5) {
                priceLabel.setForeground(upColor);
            } else if (percentChange24h < -0.5) {
                priceLabel.setForeground(downColor);
            } else {
                priceLabel.setForeground(Color.white);
            }
        }
    }

    public void updateWindow() {
        int tickerCount = cryptoQuotes.size() + stockQuotes.size();
        window.setSize(150 + (tickerCount * 65), 80);
        LocalDateTime now = LocalDateTime.now();
        int m = now.getMinute();
        String ms;

        if (m < 10) {
            ms = "0" + m;
        } else {
            ms = "" + m;
        }

        window.setTitle("CryptoFiatStockTicker " + now.getHour() + ":" + ms);
    }

    public void saveConfig() {
        mapper.configure(SerializationFeature.INDENT_OUTPUT, true);

        try {
            FileWriter out = new FileWriter(configFileName);
            mapper.writeValue(out, config);
        } catch (IOException ioe) {
            System.err.println(ioe);
            return;
        }
    }

    private JPanel createTicker(String symbol) {
        // Ticker subpanel
        JPanel tickerPanel = new JPanel();
        tickerPanels.put(symbol, tickerPanel);
        GridBagLayout layout = new GridBagLayout();
        tickerPanel.setLayout(layout);
        tickerPanel.setBackground(Color.black);
        GridBagConstraints tickerGbc = new GridBagConstraints();
        tickerGbc.fill = GridBagConstraints.HORIZONTAL;
        tickerGbc.ipadx = 5;
        tickerGbc.ipady = 5;

        // Symbol label
        tickerGbc.gridx = 0;
        tickerGbc.gridy = 0;
        String text = "<html><b>" + symbol + "</b></html>";
        JLabel symbolLabel = new JLabel(text, JLabel.CENTER);
        symbolLabel.setForeground(Color.white);
        symbolLabels.put(symbol, symbolLabel);
        tickerPanel.add(symbolLabel, tickerGbc);

        // Price label
        tickerGbc.gridy = 1;
        text = "<html><p style=\"text-align: center\"><span style=\"font-size: 1.3em\"><b>0.000</b></span><br/><span style=\"font-size: 0.8em\">0m</span></p></html>";
        JLabel priceLabel = new JLabel(text, JLabel.CENTER);
        priceLabel.setForeground(Color.white);
        symbolLabel.setLabelFor(priceLabel);
        priceLabels.put(symbol, priceLabel);
        tickerPanel.add(priceLabel, tickerGbc);

        return tickerPanel;
    }

    private String getCryptoName(Map<String, Object> map) {
        Map<String, Object> data = (Map<String, Object>)map.get("data");
        String name = (String)data.get("name");
        return name;
    }

    private int getCryptoRank(Map<String, Object> map) {
        Map<String, Object> data = (Map<String, Object>)map.get("data");
        Integer iRank = (Integer)data.get("rank");
        int rank = iRank.intValue();
        return rank;
    }

    private double getCryptoPrice(Map<String, Object> map) {
        Map<String, Object> data = (Map<String, Object>)map.get("data");
        Map<String, Object> quotes = (Map<String, Object>)data.get("quotes");
        Map<String, Object> usd = (Map<String, Object>)quotes.get("USD");
        Double dPrice = (Double)usd.get("price");
        double price = dPrice.doubleValue();
        double rounded = 0.0;

        if (price >= 100.0) {
            rounded = Math.round(price);
        } else if (price >= 1.0) {
            rounded = Math.round(price * 100.0) / 100.0;
        } else {
            rounded = Math.round(price * 1000.0) / 1000.0;
        }

        return rounded;
    }

    private double getCryptoMarketCap(Map<String, Object> map) {
        Map<String, Object> data = (Map<String, Object>)map.get("data");
        Map<String, Object> quotes = (Map<String, Object>)data.get("quotes");
        Map<String, Object> usd = (Map<String, Object>)quotes.get("USD");
        Double dMarketCap = (Double)usd.get("market_cap");
        double marketCap = dMarketCap.doubleValue();
        double roundedM = Math.round(marketCap / 1000000.0);
        return roundedM;
    }

    private double getCryptoPercentChange24h(Map<String, Object> map) {
        Map<String, Object> data = (Map<String, Object>)map.get("data");
        Map<String, Object> quotes = (Map<String, Object>)data.get("quotes");
        Map<String, Object> usd = (Map<String, Object>)quotes.get("USD");
        Double dPercentChange24h = (Double)usd.get("percent_change_24h");
        // cmc 5% = 5.0
        double percentChange24h = dPercentChange24h.doubleValue();
        return percentChange24h;
    }

    private String getStockName(Map<String, Object> map) {
        String name = (String)map.get("companyName");
        return name;
    }

    private double getStockPrice(Map<String, Object> map) {
        Double dPrice = (Double)map.get("latestPrice");
        double price = dPrice.doubleValue();
        double rounded = 0.0;

        if (price >= 100.0) {
            rounded = Math.round(price);
        } else if (price >= 1.0) {
            rounded = Math.round(price * 100.0) / 100.0;
        } else {
            rounded = Math.round(price * 1000.0) / 1000.0;
        }

        return rounded;
    }

    private double getStockMarketCap(Map<String, Object> map) {
        Object oMarketCap = map.get("marketCap");
        double roundedM = 0.0;

        if (oMarketCap instanceof Long) {
            Long llMarketCap = (Long)oMarketCap;
            long lMarketCap = llMarketCap.longValue();
            roundedM = Math.round(lMarketCap / 1000000.0);
        } else if (oMarketCap instanceof Integer) {
            Integer iiMarketCap = (Integer)oMarketCap;
            int iMarketCap = iiMarketCap.intValue();
            roundedM = Math.round(iMarketCap / 1000000.0);
        } else {
            System.err.println("Unknown type for stock marketCap");
            roundedM = 0.0;
        }

        return roundedM;
    }

    private double getStockPercentChange24h(Map<String, Object> map) {
        // iex 5% = 0.05
        Object cp = map.get("changePercent");
        double percentChange24h = 0.0;
        if (cp instanceof Double) {
            Double dPercentChange24h = (Double)map.get("changePercent");
            percentChange24h = dPercentChange24h.doubleValue() * 100.0;
        } else if (cp instanceof Integer) {
            Integer iPercentChange24h = (Integer)map.get("changePercent");
            percentChange24h = (double)iPercentChange24h.intValue() * 100.0;
        }

        return percentChange24h;
    }

    private String formatPrice(double price) {
        String sPrice;

        if (price >= 100.0) {
            sPrice = String.format("%.0f", price);
        } else if (price >= 1.0) {
            sPrice = String.format("%.2f", price);
        } else {
            sPrice = String.format("%.3f", price);
        }

        return sPrice;
    }

    private String formatMarketCap(double marketCap) {
        String sMarketCap;
        double bMarketCap;

        if (marketCap >= 100000.0) {
            bMarketCap = marketCap / 1000.0;
            sMarketCap = String.format("%.0f", bMarketCap) + "b";
        } else if (marketCap >= 10000.0) {
            bMarketCap = marketCap / 1000.0;
            sMarketCap = String.format("%.1f", bMarketCap) + "b";
        } else if (marketCap >= 1000.0) {
            bMarketCap = marketCap / 1000.0;
            sMarketCap = String.format("%.2f", bMarketCap) + "b";
        } else if (marketCap >= 100.0) {
            sMarketCap = String.format("%.0f", marketCap) + "m";
        } else if (marketCap >= 10.0) {
            sMarketCap = String.format("%.1f", marketCap) + "m";
        } else {
            sMarketCap = String.format("%.2f", marketCap) + "m";
        }

        return sMarketCap;
    }

    private String getCmcId(String symbol) {
        if (empty(symbol)) {
            return null;
        }

        // XXX Look up in listing
        CryptoListing listing = cryptoListings.get(symbol);

        if (listing != null) {
            return listing.getCmcId();
        }

        return null;
    }

    public String getSymbol() {
        return symbolTextField.getText().trim().toUpperCase();
    }

    public static boolean empty(final String s) {
        return s == null || s.trim().isEmpty();
    }

    public void addSymbol(String symbol) {
        if (empty(symbol)) {
            System.err.println("Attempt to add empty symbol: " + symbol);
            return;
        }

        if (isStock) {
            stockQuotes.put(symbol, new StockQuote(symbol));
            config.addStock(symbol);
        } else {
            String cmcId = getCmcId(symbol);

            if (cmcId != null) {
                cryptoQuotes.put(symbol, new CryptoQuote(symbol, cmcId));
                config.addCrypto(symbol);
            } else {
                System.err.println("No id found in listings for crypto symbol: " + symbol);
                return;
            }
        }

        JPanel tickerPanel = createTicker(symbol);
        mainGbc.gridx = gridx;
        mainPanel.add(tickerPanel, mainGbc);
        gridx++;
        updateAll();
    }

    public void removeSymbol(String symbol) {
        if (empty(symbol)) {
            System.err.println("Attempt to remove empty symbol: " + symbol);
            return;
        }

        if (isStock) {
            if (stockQuotes.containsKey(symbol)) {
                stockQuotes.remove(symbol);
                config.removeStock(symbol);
            } else {
                System.err.println("Cannot remove stock symbol: " + symbol);
                return;
            }
        } else {
            if (cryptoQuotes.containsKey(symbol)) {
                cryptoQuotes.remove(symbol);
                config.removeCrypto(symbol);
            } else {
                System.err.println("Cannot remove crypto symbol: " + symbol);
                return;
            }
        }

        JPanel tickerPanel = tickerPanels.get(symbol);

        if (tickerPanel != null) {
            tickerPanels.remove(symbol);
            mainPanel.remove(tickerPanel);
        }

        JLabel symbolLabel = symbolLabels.get(symbol);

        if (symbolLabel != null) {
            symbolLabels.remove(symbol);
        }

        JLabel priceLabel = priceLabels.get(symbol);

        if (priceLabel != null) {
            priceLabels.remove(symbol);
        }

        updateAll();
    }

    private class UpdateListener implements ActionListener {
        public void actionPerformed(ActionEvent e) {
            updateAll();
        }
    }

    private class ButtonListener implements ActionListener {
        public void actionPerformed(ActionEvent e) {
            String button = e.getActionCommand();
            String symbol = getSymbol();

            if (addButtonLabel.equals(button)) {
                // Add
                addSymbol(symbol);
            } else if (removeButtonLabel.equals(button)) {
                // Remove
                removeSymbol(symbol);
            } else if (saveButtonLabel.equals(button)) {
                // Save
                saveConfig();
            } else if (closeButtonLabel.equals(button)) {
                // Close
                System.exit(0);
            }
        }
    }

    private class SymbolListener implements ActionListener {
        public void actionPerformed(ActionEvent e) {
            String symbol = getSymbol();

            if (isStock) {
                if (stockQuotes.containsKey(symbol)) {
                    // Remove stock
                    removeSymbol(symbol);
                } else {
                    // Add stock
                    addSymbol(symbol);
                }
            } else {
                if (cryptoQuotes.containsKey(symbol)) {
                    // Remove crypto
                    removeSymbol(symbol);
                } else {
                    // Add crypto
                    addSymbol(symbol);
                }
            }
        }
    }

    private class RadioListener implements ActionListener {
        public void actionPerformed(ActionEvent e) {
            String radio = e.getActionCommand();
            if (stockRadioLabel.equals(radio)) {
                isStock = true;
            } else {
                isStock = false;
            }
        }
    }

    private class CryptoListing {
        protected String symbol;
        protected String cmcId;
        protected String name;

        public CryptoListing() {
        }

        public CryptoListing(String symbol, String cmcId, String name) {
            this.symbol = symbol;
            this.cmcId = cmcId;
            this.name = name;
        }

        public String getSymbol() { return symbol; }
        public void setSymbol(String x) { symbol = x; }

        public String getCmcId() { return cmcId; }
        public void setCmcId(String x) { cmcId = x; }

        public String getName() { return name; }
        public void setName(String x) { name = x; }
    }

    private class CryptoQuote extends CryptoListing {
        private int rank;
        private double price;
        private double marketCap;
        private double percentChange24h;

        public CryptoQuote() {
            super();
        }

        public CryptoQuote(String symbol, String cmcId) {
            super(symbol, cmcId, "");
        }

        public CryptoQuote(CryptoListing listing) {
            super(listing.getSymbol(), listing.getCmcId(), listing.getName());
        }

        public int getRank() { return rank; }
        public void setRank(int x) { rank = x; }

        public double getPrice() { return price; }
        public void setPrice(double x) { price = x; }

        public double getMarketCap() { return marketCap; }
        public void setMarketCap(double x) { marketCap = x; }

        public double getPercentChange24h() { return percentChange24h; }
        public void setPercentChange24h(double x) { percentChange24h = x; }
    }

    private class StockQuote {
        private String symbol;
        private String name;
        private double price;
        private double marketCap;
        private double percentChange24h;

        public StockQuote() {
        }

        public StockQuote(String symbol) {
            this.symbol = symbol;
        }

        public String getSybol() { return symbol; }
        public void setSymbol(String x) { symbol = x; }

        public String getName() { return name; }
        public void setName(String x) { name = x; }

        public double getPrice() { return price; }
        public void setPrice(double x) { price = x; }

        public double getMarketCap() { return marketCap; }
        public void setMarketCap(double x) { marketCap = x; }

        public double getPercentChange24h() { return percentChange24h; }
        public void setPercentChange24h(double x) { percentChange24h = x; }
    }
}

/*
coinmarketcap crypto listings sample data

{
    "data": [
        {
            "id": 1, 
            "name": "Bitcoin", 
            "symbol": "BTC", 
            "website_slug": "bitcoin"
        }, 
...
        {
            "id": 2835, 
            "name": "Endor Protocol", 
            "symbol": "EDR", 
            "website_slug": "endor-protocol"
        }
    ], 
    "metadata": {
        "timestamp": 1528046889, 
        "num_cryptocurrencies": 1639, 
        "error": null
    }
}

*/

/*
coinmarketcap crypto quote sample data

{
    "data": {
        "id": 2010, 
        "name": "Cardano", 
        "symbol": "ADA", 
        "website_slug": "cardano", 
        "rank": 7, 
        "circulating_supply": 25927070538.0, 
        "total_supply": 31112483745.0, 
        "max_supply": 45000000000.0, 
        "quotes": {
            "USD": {
                "price": 0.22743, 
                "volume_24h": 203214000.0, 
                "market_cap": 5896593652.0, 
                "percent_change_1h": -0.26, 
                "percent_change_24h": 11.36, 
                "percent_change_7d": 10.55
            }
        }, 
        "last_updated": 1527792565
    }, 
    "metadata": {
        "timestamp": 1527792374, 
        "error": null
    }
}

*/

/*
iextrading stock quote sample data

{"symbol":"IMGN",
"companyName":"ImmunoGen Inc.",
"primaryExchange":"Nasdaq Global Select",
"sector":"Healthcare",
"calculationPrice":"close",
"open":10.43,
"openTime":1529069400456,
"close":10.01,
"closeTime":1529092800695,
"high":10.76,
"low":9.93,
"latestPrice":10.01,
"latestSource":"Close",
"latestTime":"June 15,
 2018",
"latestUpdate":1529092800695,
"latestVolume":5373333,
"iexRealtimePrice":10.01,
"iexRealtimeSize":121,
"iexLastUpdated":1529092799533,
"delayedPrice":10.01,
"delayedPriceTime":1529092800695,
"extendedPrice":10.15,
"extendedChange":0.14,
"extendedChangePercent":0.01399,
"extendedPriceTime":1529096388244,
"previousClose":10.49,
"change":-0.48,
"changePercent":-0.04576,
"iexMarketPercent":0.0108,
"iexVolume":58032,
"avgTotalVolume":2606606,
"iexBidPrice":0,
"iexBidSize":0,
"iexAskPrice":0,
"iexAskSize":0,
"marketCap":1468836829,
"peRatio":-8.63,
"week52High":13.41,
"week52Low":4.355,
"ytdChange":0.4549553075822603}

*/

