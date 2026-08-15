package com.offertracker;

import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.boot.web.context.WebServerApplicationContext;
import org.springframework.context.ConfigurableApplicationContext;

import java.awt.AWTException;
import java.awt.BasicStroke;
import java.awt.Color;
import java.awt.Desktop;
import java.awt.Font;
import java.awt.Graphics2D;
import java.awt.Image;
import java.awt.MenuItem;
import java.awt.PopupMenu;
import java.awt.RenderingHints;
import java.awt.SystemTray;
import java.awt.TrayIcon;
import java.awt.event.ActionListener;
import java.awt.image.BufferedImage;
import java.io.IOException;
import java.net.URI;
import java.nio.channels.FileChannel;
import java.nio.channels.FileLock;
import java.nio.channels.OverlappingFileLockException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.time.LocalDateTime;
import java.util.LinkedHashMap;
import java.util.Locale;
import java.util.Map;

final class DesktopRuntime {

    private static final String ENABLED_PROPERTY = "offertracker.desktop";
    private static final String BROWSER_ENV = "OFFERTRACKER_OPEN_BROWSER";
    private static final String APP_DIRECTORY = "OfferTracker";
    private static final int EXISTING_INSTANCE_WAIT_ATTEMPTS = 40;
    private static final long EXISTING_INSTANCE_WAIT_MILLIS = 250L;

    private static Path appDirectory;
    private static Path portFile;
    private static Path launcherLog;
    private static FileChannel lockChannel;
    private static FileLock appLock;
    private static TrayIcon trayIcon;

    private DesktopRuntime() {
    }

    static boolean isEnabled() {
        return Boolean.getBoolean(ENABLED_PROPERTY);
    }

    static boolean prepare() {
        try {
            appDirectory = resolveAppDirectory();
            Files.createDirectories(appDirectory.resolve("data"));
            Files.createDirectories(appDirectory.resolve("logs"));
            Files.createDirectories(appDirectory.resolve("runtime"));
            portFile = appDirectory.resolve("runtime").resolve("server.port");
            launcherLog = appDirectory.resolve("logs").resolve("launcher.log");

            lockChannel = FileChannel.open(
                    appDirectory.resolve("runtime").resolve("application.lock"),
                    StandardOpenOption.CREATE,
                    StandardOpenOption.WRITE);
            try {
                appLock = lockChannel.tryLock();
            } catch (OverlappingFileLockException ignored) {
                appLock = null;
            }
            if (appLock == null) {
                closeQuietly(lockChannel);
                openExistingInstance();
                return false;
            }

            Runtime.getRuntime().addShutdownHook(new Thread(DesktopRuntime::release, "offer-tracker-desktop-cleanup"));
            log("Desktop runtime acquired the application lock.");
            return true;
        } catch (IOException exception) {
            logFallback("Unable to initialize desktop mode", exception);
            return false;
        }
    }

    static void applySystemProperties() {
        defaultProperties().forEach((name, value) -> System.setProperty(name, value.toString()));
    }

    private static Map<String, Object> defaultProperties() {
        String databasePath = appDirectory.resolve("data").resolve("offer-tracker")
                .toAbsolutePath().toString().replace('\\', '/');
        String logPath = appDirectory.resolve("logs").resolve("offer-tracker.log")
                .toAbsolutePath().toString().replace('\\', '/');

        Map<String, Object> properties = new LinkedHashMap<>();
        properties.put("server.address", "127.0.0.1");
        properties.put("server.port", "0");
        properties.put("spring.datasource.url", "jdbc:h2:file:" + databasePath);
        properties.put("logging.file.name", logPath);
        return properties;
    }

    static void onReady(ApplicationReadyEvent event) {
        if (!(event.getApplicationContext() instanceof WebServerApplicationContext webContext)) {
            log("Application started without a web server context.");
            return;
        }

        int port = webContext.getWebServer().getPort();
        String url = "http://127.0.0.1:" + port;
        try {
            Files.writeString(portFile, Integer.toString(port), StandardCharsets.UTF_8,
                    StandardOpenOption.CREATE, StandardOpenOption.TRUNCATE_EXISTING);
        } catch (IOException exception) {
            log("Unable to persist the runtime port: " + exception.getMessage());
        }

        installTray(event.getApplicationContext(), url);
        if (shouldOpenBrowser()) {
            openBrowser(url);
        }
        log("Offer Tracker is ready at " + url);
    }

    private static Path resolveAppDirectory() {
        String localAppData = System.getenv("LOCALAPPDATA");
        if (localAppData != null && !localAppData.isBlank()) {
            return Path.of(localAppData, APP_DIRECTORY);
        }
        return Path.of(System.getProperty("user.home"), ".offer-tracker");
    }

    private static void openExistingInstance() {
        for (int attempt = 0; attempt < EXISTING_INSTANCE_WAIT_ATTEMPTS; attempt++) {
            try {
                if (Files.isRegularFile(portFile)) {
                    int port = Integer.parseInt(Files.readString(portFile, StandardCharsets.UTF_8).trim());
                    openBrowser("http://127.0.0.1:" + port);
                    return;
                }
                Thread.sleep(EXISTING_INSTANCE_WAIT_MILLIS);
            } catch (IOException | NumberFormatException exception) {
                log("Waiting for the running instance: " + exception.getMessage());
            } catch (InterruptedException exception) {
                Thread.currentThread().interrupt();
                return;
            }
        }
        log("A running instance was detected, but its port was not available.");
    }

    private static boolean shouldOpenBrowser() {
        return !"false".equalsIgnoreCase(System.getenv(BROWSER_ENV));
    }

    private static void openBrowser(String url) {
        if (!shouldOpenBrowser()) {
            return;
        }
        try {
            URI uri = URI.create(url);
            if (Desktop.isDesktopSupported() && Desktop.getDesktop().isSupported(Desktop.Action.BROWSE)) {
                Desktop.getDesktop().browse(uri);
                return;
            }
            if (System.getProperty("os.name", "").toLowerCase(Locale.ROOT).contains("win")) {
                new ProcessBuilder("rundll32", "url.dll,FileProtocolHandler", url).start();
                return;
            }
            log("No supported browser launcher was found for " + url);
        } catch (Exception exception) {
            log("Unable to open the browser: " + exception.getMessage());
        }
    }

    private static void installTray(ConfigurableApplicationContext context, String url) {
        if (!SystemTray.isSupported()) {
            log("System tray is not supported on this computer.");
            return;
        }
        try {
            PopupMenu menu = new PopupMenu();
            MenuItem openItem = new MenuItem("打开 Offer Tracker");
            MenuItem exitItem = new MenuItem("退出");
            ActionListener openAction = ignored -> openBrowser(url);
            openItem.addActionListener(openAction);
            exitItem.addActionListener(ignored -> {
                context.close();
                System.exit(0);
            });
            menu.add(openItem);
            menu.addSeparator();
            menu.add(exitItem);

            trayIcon = new TrayIcon(createTrayImage(), "Offer Tracker", menu);
            trayIcon.setImageAutoSize(true);
            trayIcon.addActionListener(openAction);
            SystemTray.getSystemTray().add(trayIcon);
            trayIcon.displayMessage("Offer Tracker", "程序已启动，可通过托盘图标重新打开或退出。",
                    TrayIcon.MessageType.INFO);
        } catch (AWTException exception) {
            log("Unable to install the tray icon: " + exception.getMessage());
        }
    }

    private static Image createTrayImage() {
        BufferedImage image = new BufferedImage(64, 64, BufferedImage.TYPE_INT_ARGB);
        Graphics2D graphics = image.createGraphics();
        try {
            graphics.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
            graphics.setColor(new Color(17, 24, 39));
            graphics.fillRoundRect(3, 3, 58, 58, 18, 18);
            graphics.setColor(new Color(110, 231, 183));
            graphics.setStroke(new BasicStroke(4, BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND));
            graphics.drawRoundRect(17, 24, 30, 23, 4, 4);
            graphics.drawLine(17, 31, 47, 31);
            graphics.drawRoundRect(25, 17, 14, 9, 4, 4);
            graphics.setFont(new Font(Font.SANS_SERIF, Font.BOLD, 1));
        } finally {
            graphics.dispose();
        }
        return image;
    }

    private static synchronized void release() {
        if (trayIcon != null && SystemTray.isSupported()) {
            SystemTray.getSystemTray().remove(trayIcon);
            trayIcon = null;
        }
        try {
            if (portFile != null) {
                Files.deleteIfExists(portFile);
            }
        } catch (IOException exception) {
            log("Unable to remove the runtime port file: " + exception.getMessage());
        }
        try {
            if (appLock != null && appLock.isValid()) {
                appLock.release();
            }
        } catch (IOException exception) {
            log("Unable to release the application lock: " + exception.getMessage());
        }
        closeQuietly(lockChannel);
    }

    private static void closeQuietly(FileChannel channel) {
        if (channel == null) {
            return;
        }
        try {
            channel.close();
        } catch (IOException ignored) {
            // The process is already exiting.
        }
    }

    private static synchronized void log(String message) {
        if (launcherLog == null) {
            return;
        }
        try {
            Files.writeString(launcherLog, LocalDateTime.now() + " " + message + System.lineSeparator(),
                    StandardCharsets.UTF_8, StandardOpenOption.CREATE, StandardOpenOption.APPEND);
        } catch (IOException ignored) {
            // Desktop mode must not fail solely because diagnostics cannot be written.
        }
    }

    private static void logFallback(String message, Exception exception) {
        System.err.println(message + ": " + exception.getMessage());
    }
}
