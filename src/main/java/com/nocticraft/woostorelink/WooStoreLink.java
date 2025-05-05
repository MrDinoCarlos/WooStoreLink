package com.nocticraft.woostorelink;

import com.nocticraft.woostorelink.commands.EntregasCommand;
import org.bukkit.Bukkit;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.entity.Player;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.EventHandler;
import org.bukkit.plugin.java.JavaPlugin;

import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.sql.*;
import java.text.SimpleDateFormat;
import java.util.Date;

public class WooStoreLink extends JavaPlugin implements Listener {

    private Connection connection;

    @Override
    public void onEnable() {
        saveDefaultConfig();
        saveDefaultConfig();
        limpiarLogsAntiguos();
        connectToDatabase();
        Bukkit.getPluginManager().registerEvents(this, this);
        getCommand("entregas").setExecutor(new EntregasCommand(this));
        logEntrega("WooStoreLink activado correctamente.");

// ⏱ Programar chequeo automático DESPUÉS de todo
        Bukkit.getScheduler().runTaskLater(this, () -> {
            int minutos = getConfig().getInt("check-interval-minutes", 5);
            long ticks = minutos * 60L * 20L;

            Bukkit.getScheduler().runTaskTimer(this, () -> {
                logEntrega("⏰ Comprobando entregas pendientes para jugadores online...");
                for (Player player : Bukkit.getOnlinePlayers()) {
                    procesarEntregasPendientes(player);
                }
            }, ticks, ticks);
        }, 20L); // esperar 1 segundo tras arranque
    }

    @Override
    public void onDisable() {
        try {
            if (connection != null && !connection.isClosed()) connection.close();
        } catch (SQLException e) {
            logEntrega("❌ Error cerrando conexión: " + e.getMessage());
        }
    }

    public void connectToDatabase() {
        FileConfiguration config = getConfig();
        String host = config.getString("mysql.host");
        int port = config.getInt("mysql.port");
        String db = config.getString("mysql.database");
        String user = config.getString("mysql.user");
        String pass = config.getString("mysql.password");

        String url = "jdbc:mysql://" + host + ":" + port + "/" + db + "?autoReconnect=true&useSSL=false";

        try {
            connection = DriverManager.getConnection(url, user, pass);
            logEntrega("✅ Conexión MySQL establecida.");
        } catch (SQLException e) {
            connection = null;
            logEntrega("❌ Error de conexión MySQL: " + e.getMessage());
        }
    }

    @EventHandler
    public void onPlayerJoin(PlayerJoinEvent event) {
        procesarEntregasPendientes(event.getPlayer());
    }

    public void procesarEntregasPendientes(Player player) {
        try {
            if (connection == null || connection.isClosed()) {
                logEntrega("⚠ Reestableciendo conexión MySQL...");
                connectToDatabase();
            }
        } catch (SQLException e) {
            logEntrega("❌ Error comprobando conexión: " + e.getMessage());
            connectToDatabase();
        }

        if (connection == null) {
            logEntrega("❌ No se pudo entregar a " + player.getName() + " por fallo de conexión.");
            player.sendMessage("§cNo se pudo comprobar tus entregas por un error de conexión.");
            return;
        }

        try {
            PreparedStatement stmt = connection.prepareStatement(
                    "SELECT id, item, cantidad FROM entregas_pendientes WHERE jugador = ? AND entregado = 0");
            stmt.setString(1, player.getName());
            ResultSet rs = stmt.executeQuery();

            boolean entregado = false;

            while (rs.next()) {
                int id = rs.getInt("id");
                String productoNombre = rs.getString("item").toLowerCase();
                String item = getConfig().getString("productos." + productoNombre);

                if (item == null || item.isEmpty()) {
                    logEntrega("❌ Producto no configurado: " + productoNombre);
                    player.sendMessage("§cEl producto §e" + productoNombre + "§c no está configurado en el servidor.");
                    continue;
                }
                int cantidad = rs.getInt("cantidad");

                Bukkit.dispatchCommand(Bukkit.getConsoleSender(),
                        "give " + player.getName() + " " + item + " " + cantidad);

                PreparedStatement update = connection.prepareStatement(
                        "UPDATE entregas_pendientes SET entregado = 1 WHERE id = ?");
                update.setInt(1, id);
                update.executeUpdate();

                logEntrega("✔ Entregado a " + player.getName() + ": " + item + " x" + cantidad);
                entregado = true;
            }

            if (entregado) {
                player.sendMessage("§aHas recibido tu(s) entrega(s) pendiente(s) de la tienda.");
            }

        } catch (SQLException e) {
            logEntrega("❌ Error entregando a " + player.getName() + ": " + e.getMessage());
        }
    }

    public void logEntrega(String mensaje) {
        String fecha = new SimpleDateFormat("yyyy-MM-dd").format(new Date());
        File logsDir = new File(getDataFolder(), "transaction-logs");
        File logFile = new File(logsDir, fecha + ".log");

        try {
            if (!logsDir.exists()) logsDir.mkdirs();
            if (!logFile.exists()) logFile.createNewFile();

            try (FileWriter fw = new FileWriter(logFile, true)) {
                String hora = new SimpleDateFormat("HH:mm:ss").format(new Date());
                fw.write("[" + hora + "] " + mensaje + "\n");
            }
        } catch (IOException e) {
            getLogger().warning("❌ No se pudo escribir en el log de transacciones: " + e.getMessage());
        }
    }

    public void limpiarLogsAntiguos() {
        int diasRetencion = getConfig().getInt("log-retention-days", 365);
        File logsDir = new File(getDataFolder(), "transaction-logs");

        if (!logsDir.exists()) return;

        File[] archivos = logsDir.listFiles();
        if (archivos == null) return;

        long ahora = System.currentTimeMillis();
        long limite = ahora - (diasRetencion * 24L * 60 * 60 * 1000);

        for (File archivo : archivos) {
            if (archivo.isFile() && archivo.getName().endsWith(".log")) {
                if (archivo.lastModified() < limite) {
                    if (archivo.delete()) {
                        getLogger().info("🗑️ Log antiguo eliminado: " + archivo.getName());
                    }
                }
            }
        }
    }

    public Connection getConnection() {
        return connection;
    }
}
