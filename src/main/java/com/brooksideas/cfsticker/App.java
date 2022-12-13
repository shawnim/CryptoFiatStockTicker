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
 * Desktop ticker for cryptocurrencies, fiat/forex and stocks.
 *
 * Cryptocurrency data provided by coinmarketcap.com.
 * Stock Data provided for free by IEX. View IEX’s Terms of Use.
 * iextrading.com iexcloud.io
 *
 * Copyright 2019 Shawn McMurdo
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
    // XXX add configuration for people to set their own keys
    // XXX To get cmc api key sign up at
    // XXX https://pro.coinmarketcap.com/signup/?plan=0
    private static final String cmcApiKey = "5c25d932-696d-4113-8fbf-36848aa95d61";
    //private static final String ccaApiKey = "a3a76b1a829d048c0701";
    private static final String ccaApiKey = "a5af653c83bc54ceeb0e";
    private static final String iexApiKey = "pk_23b0042f655a4a729318ddac580d182b";
    private static final String cmcTickerUrl = "https://pro-api.coinmarketcap.com/v1/cryptocurrency/quotes/latest?symbol=";
    // XXX Should use header X-CMC_PRO_API_KEY instead of query arg
    private static final String cmcTailUrl = "&convert=USD&aux=cmc_rank,market_cap_by_total_supply&CMC_PRO_API_KEY=" + cmcApiKey;
    private static final String ccaBaseUrl = "https://free.currconv.com/api/v7/convert?q=";
    private static final String ccaTailUrl = "_USD&compact=ultra&apiKey=" + ccaApiKey;
    private static final String iexBaseUrl = "https://cloud.iexapis.com/v1/stock/";
    private static final String iexTailUrl = "/quote/?token=" + iexApiKey;
    private static final String cryptoRadioLabel = "Crypto";
    private static final String fiatRadioLabel = "Fiat";
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
    private TreeMap<String, CryptoQuote> cryptoQuotes;
    private TreeMap<String, FiatQuote> fiatQuotes;
    private TreeMap<String, StockQuote> stockQuotes;
    private PlaceholderTextField symbolTextField;
    private HashMap<String, JPanel> tickerPanels;
    private HashMap<String, JLabel> symbolLabels;
    private HashMap<String, JLabel> priceLabels;
    private Color upColor;
    private Color downColor;
    private boolean isCrypto;
    private boolean isFiat;
    private boolean isStock;
    private int gridx;
    private int updateIntervalSeconds;

    public static void main(String[] args) {
        App app = new App();
        app.run();
    }

    public void run() {
        String symbol;
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
            cryptoQuotes.put(symbol, new CryptoQuote(symbol));
        }

        // Initial fiats
        fiatQuotes = new TreeMap<String, FiatQuote>();
        ArrayList<String> fiats = config.getFiats();

        for (String fiat : fiats) {
            symbol = fiat.toUpperCase();
            fiatQuotes.put(symbol, new FiatQuote(symbol));
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
        isCrypto = true;
        isFiat = false;
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

        // Fiat button
        text = "<html><span style=\"font-size: 0.8em\">" + fiatRadioLabel + "</span></html>";
        JRadioButton fiatButton = new JRadioButton(text);
        fiatButton.setForeground(Color.white);
        fiatButton.setBackground(Color.black);
        fiatButton.setActionCommand(fiatRadioLabel);
        fiatButton.addActionListener(radioListener);
        actionGbc.gridx = 0;
        actionGbc.gridy = 2;
        actionGbc.gridwidth = 1;
        actionGbc.gridheight = 1;
        actionPanel.add(fiatButton, actionGbc);

        // Stock button
        text = "<html><span style=\"font-size: 0.8em\">" + stockRadioLabel + "</span></html>";
        JRadioButton stockButton = new JRadioButton(text);
        stockButton.setForeground(Color.white);
        stockButton.setBackground(Color.black);
        stockButton.setActionCommand(stockRadioLabel);
        stockButton.addActionListener(radioListener);
        actionGbc.gridx = 1;
        actionGbc.gridy = 2;
        actionGbc.gridwidth = 1;
        actionGbc.gridheight = 1;
        actionPanel.add(stockButton, actionGbc);

        // Radio button group
        ButtonGroup radioGroup = new ButtonGroup();
        radioGroup.add(cryptoButton);
        radioGroup.add(fiatButton);
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
        mainGbc.gridheight = 3;
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

        // Fiat tickers
        for (Map.Entry<String, FiatQuote> entry : fiatQuotes.entrySet()) {
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

        // Focus
        symbolTextField.requestFocusInWindow();

        // Interval update
        Timer timer = new Timer(updateIntervalSeconds * 1000, updateListener);
        timer.setRepeats(true);
        timer.start();
    }

    public void updateAll() {
        getCryptoQuotes();
        getFiatQuotes();
        getStockQuotes();
        updateTickers();
        updateWindow();
    }

    public void getCryptoQuotes() {
        URL url = null;
        String symbol = "";
        CryptoQuote cryptoQuote = null;
        Map<String, Object> map = null;
        int errorCode = 0;
        String name = "";
        int rank = 0;
        double price = 0.0;
        double marketCap = 0.0;
        double percentChange24h = 0.0;

        for (Map.Entry<String, CryptoQuote> entry : cryptoQuotes.entrySet()) {
            symbol = entry.getKey();
            cryptoQuote = entry.getValue();

            // XXX Build comma separated list and get all with one url
            try {
                url = new URL(cmcTickerUrl + symbol + cmcTailUrl);
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

            errorCode = getCryptoErrorCode(map);

            if (errorCode != 0) {
                System.err.println("Crypto quote request returned error code: " + errorCode);
                continue;
            }

            name = getCryptoName(map, symbol);
            cryptoQuote.setName(name);

            rank = getCryptoRank(map, symbol);
            cryptoQuote.setRank(rank);

            price = getCryptoPrice(map, symbol);
            cryptoQuote.setPrice(price);

            marketCap = getCryptoMarketCap(map, symbol);
            cryptoQuote.setMarketCap(marketCap);

            percentChange24h = getCryptoPercentChange24h(map, symbol);
            cryptoQuote.setPercentChange24h(percentChange24h);
        }
    }

    public void getFiatQuotes() {
        URL url = null;
        String symbol = "";
        FiatQuote fiatQuote = null;
        Map<String, Object> map = null;
        String name = "";
        double price = 0.0;
        double marketCap = 0.0;
        double percentChange24h = 0.0;

        for (Map.Entry<String, FiatQuote> entry : fiatQuotes.entrySet()) {
            symbol = entry.getKey();
            fiatQuote = entry.getValue();

            try {
                String ccaUrl = ccaBaseUrl + symbol + ccaTailUrl;
                url = new URL(ccaUrl);
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

            fiatQuote.setName(name);
            String quoteName = symbol + "_USD";
            price = getFiatPrice(map, quoteName);
            fiatQuote.setPrice(price);
            fiatQuote.setMarketCap(marketCap);
            fiatQuote.setPercentChange24h(percentChange24h);
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
                String iexUrl = iexBaseUrl + symbol + iexTailUrl;
                url = new URL(iexUrl);
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
        FiatQuote fiatQuote;
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

        // Fiats
        for (Map.Entry<String, FiatQuote> entry : fiatQuotes.entrySet()) {
            symbol = entry.getKey();
            fiatQuote = entry.getValue();
            name = fiatQuote.getName();
            price = fiatQuote.getPrice();
            sPrice = formatPrice(price);
            percentChange24h = fiatQuote.getPercentChange24h();

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
            text = "<html><p style=\"text-align: center\"><span style=\"font-size: 1.3em\"><b>" + sPrice + "</b></span><br/><span style=\"font-size: 0.8em\">-</span></p></html>";
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
        int tickerCount = cryptoQuotes.size() + fiatQuotes.size() + stockQuotes.size();
        window.setSize(150 + (tickerCount * 65), 110);
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

    private int getCryptoErrorCode(Map<String, Object> map) {
        Map<String, Object> status = (Map<String, Object>)map.get("status");
        Integer iErrorCode = (Integer)status.get("error_code");
        int errorCode = iErrorCode.intValue();
        return errorCode;
    }

    private String getCryptoName(Map<String, Object> map, String symbol) {
        Map<String, Object> data = (Map<String, Object>)map.get("data");
        Map<String, Object> symbolData = (Map<String, Object>)data.get(symbol);

        if (symbolData == null) {
            return "";
        }

        String name = (String)symbolData.get("name");
        return name;
    }

    private int getCryptoRank(Map<String, Object> map, String symbol) {
        Map<String, Object> data = (Map<String, Object>)map.get("data");
        Map<String, Object> symbolData = (Map<String, Object>)data.get(symbol);
        int rank = 0;

        if (symbolData == null) {
            return rank;
        }

        Integer iRank = (Integer)symbolData.get("cmc_rank");

        if (iRank != null) {
            rank = iRank.intValue();
        }

        return rank;
    }

    private double getCryptoPrice(Map<String, Object> map, String symbol) {
        double rounded = 0.0d;
        Map<String, Object> data = (Map<String, Object>)map.get("data");
        Map<String, Object> symbolData = (Map<String, Object>)data.get(symbol);

        if (symbolData == null) {
            return rounded;
        }

        Map<String, Object> quote = (Map<String, Object>)symbolData.get("quote");

        if (quote == null) {
            return rounded;
        }

        Map<String, Object> usd = (Map<String, Object>)quote.get("USD");

        if (usd == null) {
            return rounded;
        }

        Double dPrice = Double.valueOf("" + (usd.get("price")).toString());
        double price = dPrice.doubleValue();

        if (price >= 100.0) {
            rounded = Math.round(price);
        } else if (price >= 1.0) {
            rounded = Math.round(price * 100.0) / 100.0;
        } else {
            rounded = Math.round(price * 1000.0) / 1000.0;
        }

        return rounded;
    }

    /**
     * Returns market cap in millions with 2 decimal places.
     */
    private double getCryptoMarketCap(Map<String, Object> map, String symbol) {
        double roundedM = 0.0d;
        Map<String, Object> data = (Map<String, Object>)map.get("data");

        if (data == null) {
            return roundedM;
        }

        Map<String, Object> symbolData = (Map<String, Object>)data.get(symbol);

        if (symbolData == null) {
            return roundedM;
        }

        Map<String, Object> quote = (Map<String, Object>)symbolData.get("quote");

        if (quote == null) {
            return roundedM;
        }

        Map<String, Object> usd = (Map<String, Object>)quote.get("USD");

        if (usd == null) {
            return roundedM;
        }

        Object oMarketCap = usd.get("market_cap");

        if (oMarketCap == null) {
            return roundedM;
        }

        Double dMarketCap = Double.valueOf("" + oMarketCap.toString());

        if (dMarketCap == null) {
            return roundedM;
        }

        double marketCap = dMarketCap.doubleValue();
        roundedM = Math.round(marketCap / 10000.0) / 100.0;
        return roundedM;
    }

    private double getCryptoPercentChange24h(Map<String, Object> map, String symbol) {
        double percentChange24h = 0.0d;
        Map<String, Object> data = (Map<String, Object>)map.get("data");
        Map<String, Object> symbolData = (Map<String, Object>)data.get(symbol);

        if (symbolData == null) {
            return percentChange24h;
        }

        Map<String, Object> quote = (Map<String, Object>)symbolData.get("quote");
        Map<String, Object> usd = (Map<String, Object>)quote.get("USD");
        Object oPercentChange24h = usd.get("percent_change_24h");

        if (oPercentChange24h == null) {
            return percentChange24h;
        }

        Double dPercentChange24h = Double.valueOf("" + oPercentChange24h.toString());

        if (dPercentChange24h == null) {
            return percentChange24h;
        }

        // cmc 5% = 5.0
        percentChange24h = dPercentChange24h.doubleValue();
        return percentChange24h;
    }

    private double getFiatPrice(Map<String, Object> map, String quoteName) {
        Double dPrice = Double.valueOf("" + (map.get(quoteName)).toString());
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

    private String getStockName(Map<String, Object> map) {
        String name = (String)map.get("companyName");
        return name;
    }

    private double getStockPrice(Map<String, Object> map) {
        Object oPrice = map.get("latestPrice");
        double dPrice = 0.0;
        double rounded = 0.0;

        if (oPrice instanceof Integer) {
            Integer iiPrice = (Integer)oPrice;
            int iPrice = iiPrice.intValue();
            dPrice = (double)iPrice;
        } else if (oPrice instanceof Double) {
            Double ddPrice = (Double)oPrice;
            dPrice = ddPrice.doubleValue();
        } else {
            System.err.println("Unknown type for stock price");
        }

        if (dPrice >= 100.0) {
            rounded = Math.round(dPrice);
        } else if (dPrice >= 1.0) {
            rounded = Math.round(dPrice * 100.0) / 100.0;
        } else {
            rounded = Math.round(dPrice * 1000.0) / 1000.0;
        }

        return rounded;
    }

    private double getStockMarketCap(Map<String, Object> map) {
        Object oMarketCap = map.get("marketCap");
        double roundedM = 0.0;

        if (oMarketCap == null) {
            return roundedM;
        }

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

    public String getSymbol() {
        return symbolTextField.getText().trim().toUpperCase();
    }

    public static boolean empty(final String s) {
        return s == null || s.trim().isEmpty();
    }

    public void addSymbol(String symbol) {
        symbolTextField.setText("");

        if (empty(symbol)) {
            System.err.println("Attempt to add empty symbol: " + symbol);
            return;
        }

        if (isStock) {
            stockQuotes.put(symbol, new StockQuote(symbol));
            config.addStock(symbol);
        } else if (isFiat) {
            fiatQuotes.put(symbol, new FiatQuote(symbol));
            config.addFiat(symbol);
        } else if (isCrypto) {
            cryptoQuotes.put(symbol, new CryptoQuote(symbol));
            config.addCrypto(symbol);
        }

        JPanel tickerPanel = createTicker(symbol);
        mainGbc.gridx = gridx;
        mainPanel.add(tickerPanel, mainGbc);
        gridx++;
        updateAll();
    }

    public void removeSymbol(String symbol) {
        symbolTextField.setText("");

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
        } else if (isFiat) {
            if (fiatQuotes.containsKey(symbol)) {
                fiatQuotes.remove(symbol);
                config.removeFiat(symbol);
            } else {
                System.err.println("Cannot remove fiat symbol: " + symbol);
                return;
            }
        } else if (isCrypto) {
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

            symbolTextField.requestFocusInWindow();
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
            } else if (isFiat) {
                if (fiatQuotes.containsKey(symbol)) {
                    // Remove fiat
                    removeSymbol(symbol);
                } else {
                    // Add fiat
                    addSymbol(symbol);
                }
            } else if (isCrypto) {
                if (cryptoQuotes.containsKey(symbol)) {
                    // Remove crypto
                    removeSymbol(symbol);
                } else {
                    // Add crypto
                    addSymbol(symbol);
                }
            }

            symbolTextField.requestFocusInWindow();
        }
    }

    private class RadioListener implements ActionListener {
        public void actionPerformed(ActionEvent e) {
            String radio = e.getActionCommand();
            if (stockRadioLabel.equals(radio)) {
                isStock = true;
                isFiat = false;
                isCrypto = false;
            } else if (fiatRadioLabel.equals(radio)) {
                isStock = false;
                isFiat = true;
                isCrypto = false;
            } else if (cryptoRadioLabel.equals(radio)) {
                isStock = false;
                isFiat = false;
                isCrypto = true;
            }

            symbolTextField.requestFocusInWindow();
        }
    }

    private class CryptoQuote {
        protected String symbol;
        protected String name;
        private int rank;
        private double price;
        private double marketCap;
        private double percentChange24h;

        public CryptoQuote() {
        }

        public CryptoQuote(String symbol) {
            this.symbol = symbol;
        }

        public String getSymbol() { return symbol; }
        public void setSymbol(String x) { symbol = x; }

        public String getName() { return name; }
        public void setName(String x) { name = x; }

        public int getRank() { return rank; }
        public void setRank(int x) { rank = x; }

        public double getPrice() { return price; }
        public void setPrice(double x) { price = x; }

        public double getMarketCap() { return marketCap; }
        public void setMarketCap(double x) { marketCap = x; }

        public double getPercentChange24h() { return percentChange24h; }
        public void setPercentChange24h(double x) { percentChange24h = x; }
    }

    private class FiatQuote {
        private String symbol;
        private String name;
        private double price;
        private double marketCap;
        private double percentChange24h;

        public FiatQuote() {
        }

        public FiatQuote(String symbol) {
            this.symbol = symbol;
        }

        public String getSymbol() { return symbol; }
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

        public String getSymbol() { return symbol; }
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

/*

// This example uses the Apache HTTPComponents library. 

import org.apache.http.HttpEntity;
import org.apache.http.HttpHeaders;
import org.apache.http.NameValuePair;
import org.apache.http.client.methods.CloseableHttpResponse;
import org.apache.http.client.methods.HttpGet;
import org.apache.http.client.utils.URIBuilder;
import org.apache.http.impl.client.CloseableHttpClient;
import org.apache.http.impl.client.HttpClients;
import org.apache.http.message.BasicNameValuePair;
import org.apache.http.util.EntityUtils;

import java.io.IOException;
import java.net.URISyntaxException;
import java.util.ArrayList;
import java.util.List;

public class JavaExample {

  private static String apiKey = "b54bcf4d-1bca-4e8e-9a24-22ff2c3d462c";

  public static void main(String[] args) {
    String uri = "https://pro-api.coinmarketcap.com/v1/cryptocurrency/listings/latest";
    List<NameValuePair> paratmers = new ArrayList<NameValuePair>();
    paratmers.add(new BasicNameValuePair("start","1"));
    paratmers.add(new BasicNameValuePair("limit","5000"));
    paratmers.add(new BasicNameValuePair("convert","USD"));

    try {
      String result = makeAPICall(uri, paratmers);
      System.out.println(result);
    } catch (IOException e) {
      System.out.println("Error: cannont access content - " + e.toString());
    } catch (URISyntaxException e) {
      System.out.println("Error: Invalid URL " + e.toString());
    }
  }

  public static String makeAPICall(String uri, List<NameValuePair> parameters)
      throws URISyntaxException, IOException {
    String response_content = "";

    URIBuilder query = new URIBuilder(uri);
    query.addParameters(parameters);

    CloseableHttpClient client = HttpClients.createDefault();
    HttpGet request = new HttpGet(query.build());

    request.setHeader(HttpHeaders.ACCEPT, "application/json");
    request.addHeader("X-CMC_PRO_API_KEY", apiKey);

    CloseableHttpResponse response = client.execute(request);

    try {
      System.out.println(response.getStatusLine());
      HttpEntity entity = response.getEntity();
      response_content = EntityUtils.toString(entity);
      EntityUtils.consume(entity);
    } finally {
      response.close();
    }

    return response_content;
  }

*/
}

/*
coinmarketcap crypto quote sample data

{
    "status": {
        "timestamp": "2019-08-25T03:52:39.810Z",
        "error_code": 0,
        "error_message": null,
        "elapsed": 7,
        "credit_count": 1
    },
    "data": {
        "ADA": {
            "id": 2010,
            "name": "Cardano",
            "symbol": "ADA",
            "slug": "cardano",
            "num_market_pairs": 102,
            "date_added": "2017-10-01T00:00:00.000Z",
            "tags": [
                "mineable"
            ],
            "max_supply": 45000000000,
            "circulating_supply": 25927070538,
            "total_supply": 31112483745,
            "is_market_cap_included_in_calc": 1,
            "platform": null,
            "cmc_rank": 11,
            "last_updated": "2019-08-25T03:52:05.000Z",
            "quote": {
                "USD": {
                    "price": 0.0525694308162,
                    "volume_24h": 101258629.075106,
                    "percent_change_1h": 2.79196,
                    "percent_change_24h": 6.04116,
                    "percent_change_7d": 11.2303,
                    "market_cap": 1362971340.9141283,
                    "last_updated": "2019-08-25T03:52:05.000Z"
                }
            }
        },
        "BTC": {
            "id": 1,
            "name": "Bitcoin",
            "symbol": "BTC",
            "slug": "bitcoin",
            "num_market_pairs": 7910,
            "date_added": "2013-04-28T00:00:00.000Z",
            "tags": [
                "mineable"
            ],
            "max_supply": 21000000,
            "circulating_supply": 17895425,
            "total_supply": 17895425,
            "is_market_cap_included_in_calc": 1,
            "platform": null,
            "cmc_rank": 1,
            "last_updated": "2019-08-25T03:52:26.000Z",
            "quote": {
                "USD": {
                    "price": 10127.994658,
                    "volume_24h": 15163849995.5894,
                    "percent_change_1h": -0.0913643,
                    "percent_change_24h": -2.20153,
                    "percent_change_7d": -0.510527,
                    "market_cap": 181244768802.63965,
                    "last_updated": "2019-08-25T03:52:26.000Z"
                }
            }
        }
    }
}

*/

/*
currencyconverterapi fiat quote sample data

{"EUR_USD":{"val":1.16672}}

*/

/*
iexcloud.io stock quote sample data

https://cloud.iexapis.com/v1/stock/qqq/quote/?token=pk_23b0042f655a4a729318ddac580d182b

{"symbol":"QQQ",
"companyName":"Invesco QQQ Trust",
"calculationPrice":"close",
"open":186.66,
"openTime":1560951000327,
"close":187.11,
"closeTime":1560974400350,
"high":187.53,
"low":185.57,
"latestPrice":187.11,
"latestSource":"Close",
"latestTime":"June 19,
 2019",
"latestUpdate":1560974400350,
"latestVolume":31665176,
"iexRealtimePrice":187.12,
"iexRealtimeSize":400,
"iexLastUpdated":1560974399579,
"delayedPrice":187.11,
"delayedPriceTime":1560974400350,
"extendedPrice":187.8,
"extendedChange":0.69,
"extendedChangePercent":0.00369,
"extendedPriceTime":1560987863464,
"previousClose":186.41,
"change":0.7,
"changePercent":0.00376,
"iexMarketPercent":0.01111770229857557,
"iexVolume":352044,
"avgTotalVolume":40442117,
"iexBidPrice":0,
"iexBidSize":0,
"iexAskPrice":0,
"iexAskSize":0,
"marketCap":0,
"peRatio":null,
"week52High":191.32,
"week52Low":143.46,
"ytdChange":0.20733700000000002}

*/

