package de.gnmyt.mcdash.panel.routes.backups;

import java.io.File;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import org.apache.commons.io.FileUtils;
import org.bukkit.Bukkit;
import org.bukkit.World;
import org.bukkit.scheduler.BukkitRunnable;

import de.gnmyt.mcdash.MinecraftDashboard;
import de.gnmyt.mcdash.api.controller.BackupController;
import de.gnmyt.mcdash.api.entities.BackupMode;
import de.gnmyt.mcdash.api.handler.DefaultHandler;
import de.gnmyt.mcdash.api.http.ContentType;
import de.gnmyt.mcdash.api.http.Request;
import de.gnmyt.mcdash.api.http.ResponseController;
import de.gnmyt.mcdash.api.json.ArrayBuilder;
import de.gnmyt.mcdash.api.json.NodeBuilder;

public class BackupRoute extends DefaultHandler {

    /**
     * Gets a list of all directories that should be backed up
     * @param mode The mode of the backup
     * @return the list of all directories that should be backed up
     */
    public static ArrayList<File> getBackupDirectories(String mode) {
        // 1) Valider et dédupliquer les “modes”
        Set<Character> seen = new HashSet<>();
        List<Character> modes = new ArrayList<>();
        for (char c : mode.toCharArray()) {
            if (!Character.isDigit(c)) return new ArrayList<>();  // refuse les chars non numériques
            if (!seen.add(c)) return new ArrayList<>();           // doublon → retour vide (comportement original)
            modes.add(c);
        }

        ArrayList<File> directories = new ArrayList<>();

        for (char currentModeChar : modes) {
            BackupMode backupMode = BackupMode.fromMode(Character.digit(currentModeChar, 10));
            if (backupMode == null) return new ArrayList<>();

            if (backupMode == BackupMode.SERVER) {
                File[] serverFolder = new File(".").listFiles();
                directories.addAll(Arrays.asList(serverFolder != null ? serverFolder : new File[0]));
                break; // comportement original : si SERVER choisi, on s’arrête
            }

            if (backupMode == BackupMode.WORLDS) {
                // Sauvegarder proprement chaque monde sur le thread principal
                for (World world : Bukkit.getWorlds()) {
                    new BukkitRunnable() {
                        @Override public void run() {
                            world.save();
                        }
                    }.runTask(MinecraftDashboard.getInstance());
                    directories.add(world.getWorldFolder());
                }
            }

            if (backupMode == BackupMode.PLUGINS) {
                directories.add(new File("plugins"));
            }

            if (backupMode == BackupMode.CONFIGS) {
                // rassemble tous les .yml, .properties, .json à la racine
                Collection<File> cfgs = FileUtils.listFiles(new File("."), new String[]{"yml", "properties", "json"}, false);
                directories.addAll(cfgs);
            }

            if (backupMode == BackupMode.LOGS) {
                directories.addAll(Arrays.asList(new File("logs"), new File("crash-reports")));
            }
        }

        return directories;
    }


    private final BackupController controller = MinecraftDashboard.getBackupController();

    /**
     * Gets a list of all backups
     * @param request The request object from the HttpExchange
     * @param response The response controller from the HttpExchange
     * @throws Exception An exception that can occur while executing the code
     */
    @Override
    public void get(Request request, ResponseController response) throws Exception {
        ArrayBuilder backups = new ArrayBuilder();

        try {
            for (File backup : controller.getBackups())
                new NodeBuilder(backups)
                        .add("id", Long.parseLong(backup.getName().replace(".zip", "").split("-")[0]))
                        .add("modes", backup.getName().replace(".zip", "").split("-")[1].split(""))
                        .add("size", backup.length())
                        .register();
        } catch (Exception e) {
            response.code(500).message("The backups are not well formatted. Please check your backups folder");
            return;
        }

        response.type(ContentType.JSON).text(backups.toJSON());
    }

    /**
     * Creates a new backup
     * @param request The request object from the HttpExchange
     * @param response The response controller from the HttpExchange
     * @throws Exception An exception that can occur while executing the code
     */
    @Override
    public void put(Request request, ResponseController response) throws Exception {
        if (!isIntegerInBody(request, response, "mode")) return;

        String mode = getStringFromBody(request, "mode").contains("0") ? "0" : getStringFromBody(request, "mode");

        ArrayList<File> directories = getBackupDirectories(mode);

        if (directories.isEmpty()) {
            response.code(400).message("Invalid backup mode string");
            return;
        }

        controller.createBackup(mode, directories.toArray(new File[0]));

        response.message("Backup created");
    }

    /**
     * Deletes a backup
     * @param request The request object from the HttpExchange
     * @param response The response controller from the HttpExchange
     * @throws Exception An exception that can occur while executing the code
     */
    @Override
    public void delete(Request request, ResponseController response) throws Exception {
        if (!isStringInBody(request, response, "backup_id")) return;

        String backupId = getStringFromBody(request, "backup_id");

        if (!controller.backupExists(backupId)) {
            response.code(404).message("Backup not found");
            return;
        }

        controller.deleteBackup(backupId);
        response.message("Backup deleted");
    }
}
