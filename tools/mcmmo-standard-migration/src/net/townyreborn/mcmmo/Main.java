package net.townyreborn.mcmmo;

import java.awt.GraphicsEnvironment;
import java.io.*;
import java.nio.ByteBuffer;
import java.nio.charset.*;
import java.nio.file.*;
import java.security.MessageDigest;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.*;
import java.util.regex.*;
import java.util.zip.*;
import javax.swing.*;
import javax.swing.filechooser.FileNameExtensionFilter;

/** Offline archive conversion. Never opens or writes a live server directory. */
public final class Main {
    public static final String VERSION = "1.0.0";
    public static final String SUPPORTED_JAR_SHA256 = "02b4904f5b5370b9815e4a2fecb9d02e8dac9afd541d09f855a7d2b1fe1c16d0";
    private static final int LIMIT = 64 * 1024 * 1024;
    private static final String PREFIX = "plugins/mcMMO/";
    private static final String MARKER = "towny-standard-migration.properties";
    public static final List<String> CHANGED_FILES = List.of("config.yml", "experience.yml", "skillranks.yml", "flatfile/mcmmo.users");
    public record Skill(String name, int levelIndex, int xpIndex) {}
    public static final List<Skill> SKILLS = List.of(
        new Skill("Mining",1,4),new Skill("Woodcutting",5,6),new Skill("Repair",7,15),
        new Skill("Unarmed",8,16),new Skill("Herbalism",9,17),new Skill("Excavation",10,18),
        new Skill("Archery",11,19),new Skill("Swords",12,20),new Skill("Axes",13,21),
        new Skill("Acrobatics",14,22),new Skill("Taming",24,25),new Skill("Fishing",34,35),
        new Skill("Alchemy",39,40),new Skill("Crossbows",46,45),new Skill("Tridents",48,47),
        new Skill("Maces",53,52),new Skill("Spears",56,55));
    public record Progress(int level, int xp, long totalXp) {}
    public record Result(Map<String, byte[]> before, Map<String, byte[]> after, String skillsReport,
                         String playersReport, int profiles, int startRequirements, long preservedXp) {}

    public static void main(String[] args) {
        try {
            if (args.length == 0 && !GraphicsEnvironment.isHeadless()) { gui(); return; }
            if (args.length == 3 && args[0].equals("--verify")) {
                verify(Path.of(args[1]), Path.of(args[2]));
                System.out.println("MATCH: all four affected files match the pre-conversion snapshot.");
                System.out.println("The server must remain stopped until installation is complete.");
                return;
            }
            if (args.length < 2 || args.length > 3) {
                System.out.println("Towny Reborn mcMMO Standard Converter " + VERSION);
                System.out.println("java -jar Converter.jar mcMMO.zip mcMMO.jar [new-package.zip]");
                System.out.println("java -jar Converter.jar --verify package.zip fresh-mcMMO.zip");
                System.out.println("Export mcMMO.zip only after a clean, complete server stop. Keep it stopped.");
                if (args.length != 0) throw new IllegalArgumentException("Unsupported arguments.");
                return;
            }
            Path input = Path.of(args[0]);
            Path output = args.length == 3 ? Path.of(args[2]) : outputFor(input);
            Result result = prepare(input, Path.of(args[1]), output);
            System.out.println("Created: " + output.toAbsolutePath());
            System.out.println("Profiles: " + result.profiles + "; reconstructed XP preserved exactly: " + result.preservedXp);
            System.out.println("Read README-DE.txt. Install all four files together while the server is stopped.");
        } catch (Exception error) {
            System.err.println("ABORTED: " + error.getMessage());
            System.err.println("The source archive and server files have not been changed.");
            System.exit(1);
        }
    }

    private static Path outputFor(Path input) {
        String date = LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyyMMdd-HHmmss"));
        return input.toAbsolutePath().getParent().resolve("mcMMO-Standard-Migration-" + date + ".zip");
    }

    private static void gui() {
        try {
            UIManager.setLookAndFeel(UIManager.getSystemLookAndFeelClassName());
            JFileChooser chooser = new JFileChooser();
            chooser.setDialogTitle("Towny Reborn: select the mcMMO ZIP exported AFTER stopping the server");
            chooser.setFileFilter(new FileNameExtensionFilter("mcMMO folder archive (*.zip)", "zip"));
            if (chooser.showOpenDialog(null) != JFileChooser.APPROVE_OPTION) return;
            Path input = chooser.getSelectedFile().toPath();
            chooser.setDialogTitle("Select the installed mcMMO.jar (supported build: 2.3.001)");
            chooser.setFileFilter(new FileNameExtensionFilter("mcMMO plugin (*.jar)", "jar"));
            if (chooser.showOpenDialog(null) != JFileChooser.APPROVE_OPTION) return;
            Path output = outputFor(input);
            Result result = prepare(input, chooser.getSelectedFile().toPath(), output);
            JOptionPane.showMessageDialog(null, "Created package:\n" + output + "\n\n" + result.profiles
                + " profiles converted; reconstructed XP preserved exactly.\nRead README-DE.txt inside the package.\nKeep the server stopped until all four files are installed.",
                "Towny Reborn | mcMMO", JOptionPane.INFORMATION_MESSAGE);
        } catch (Exception error) {
            JOptionPane.showMessageDialog(null, "Conversion aborted:\n" + error.getMessage()
                + "\n\nYour original ZIP and server files were not changed.", "Towny Reborn | mcMMO", JOptionPane.ERROR_MESSAGE);
        }
    }

    public static Result prepare(Path input, Path jar, Path output) throws Exception {
        require(Files.isRegularFile(input), "Input ZIP does not exist.");
        require(Files.isRegularFile(jar), "mcMMO.jar does not exist.");
        require(Files.size(input) <= LIMIT && Files.size(jar) <= LIMIT, "Input is too large for this reviewed migration.");
        require(sha(Files.readAllBytes(jar)).equals(SUPPORTED_JAR_SHA256), "This mcMMO.jar is not the reviewed 2.3.001 build. Do not migrate another build with this tool.");
        Path destination = output.toAbsolutePath();
        require(!Files.exists(destination), "Output already exists; choose a new filename.");
        byte[] original = Files.readAllBytes(input);
        Map<String, byte[]> entries = archive(input);
        Result result = convert(entries);
        Properties proof = new Properties();
        proof.setProperty("tool.version", VERSION);
        proof.setProperty("mcmmo.version", "2.3.001");
        proof.setProperty("mcmmo.jar.sha256", SUPPORTED_JAR_SHA256);
        proof.setProperty("source.archive.sha256", sha(original));
        proof.setProperty("profiles", Integer.toString(result.profiles));
        proof.setProperty("preserved.total.xp", Long.toString(result.preservedXp));
        proof.setProperty("initial.requirements.adjusted", Integer.toString(result.startRequirements));
        proof.setProperty("source.mode", "RETRO");
        proof.setProperty("target.mode", "STANDARD");
        for (String file : CHANGED_FILES) {
            proof.setProperty("source." + file + ".sha256", sha(result.before.get(file)));
            proof.setProperty("target." + file + ".sha256", sha(result.after.get(file)));
        }
        ByteArrayOutputStream metadata = new ByteArrayOutputStream();
        proof.store(metadata, "Towny Reborn mcMMO migration; offline snapshot only");
        byte[] readme;
        try (InputStream stream = Main.class.getResourceAsStream("/README-DE.txt")) {
            require(stream != null, "Migration instructions are missing from the tool.");
            readme = stream.readAllBytes();
        }
        // CREATE_NEW prevents replacing either a previous package or the input file.
        OutputStream raw = Files.newOutputStream(destination, StandardOpenOption.CREATE_NEW);
        try (ZipOutputStream zip = new ZipOutputStream(raw, StandardCharsets.UTF_8)) {
            for (String file : CHANGED_FILES) {
                put(zip, "INSTALL/" + PREFIX + file, result.after.get(file));
                put(zip, "ROLLBACK/" + PREFIX + file, result.before.get(file));
            }
            put(zip, "INSTALL/" + PREFIX + MARKER, metadata.toByteArray());
            put(zip, "REPORT/migration.properties", metadata.toByteArray());
            put(zip, "REPORT/skills.csv", result.skillsReport.getBytes(StandardCharsets.UTF_8));
            put(zip, "REPORT/players.csv", result.playersReport.getBytes(StandardCharsets.UTF_8));
            put(zip, "BACKUP/mcMMO-before-migration.zip", original);
            put(zip, "README-DE.txt", readme);
            Path self = Path.of(Main.class.getProtectionDomain().getCodeSource().getLocation().toURI());
            if (Files.isRegularFile(self)) {
                put(zip, "Werkzeug/TownyReborn-mcMMO-Standard-Converter.jar", Files.readAllBytes(self));
                put(zip, "Werkzeug/Start-Windows.cmd", ("@echo off\r\njava -jar \"%~dp0TownyReborn-mcMMO-Standard-Converter.jar\" %*\r\nif errorlevel 1 pause\r\n").getBytes(StandardCharsets.UTF_8));
                put(zip, "Werkzeug/Start-Linux.sh", ("#!/bin/sh\nset -eu\nexec java -jar \"$(dirname \"$0\")/TownyReborn-mcMMO-Standard-Converter.jar\" \"$@\"\n").getBytes(StandardCharsets.UTF_8));
            }
        } catch (Exception error) {
            Files.deleteIfExists(destination);
            throw error;
        }
        return result;
    }

    public static Result convert(Map<String, byte[]> files) {
        require(!files.containsKey(MARKER), "This snapshot already contains a migration marker.");
        LinkedHashMap<String, byte[]> before = new LinkedHashMap<>();
        for (String file : CHANGED_FILES) {
            require(files.containsKey(file), "Required file is missing: " + file);
            before.put(file, files.get(file));
        }
        Yaml config = new Yaml(text(before.get("config.yml")));
        Yaml experience = new Yaml(text(before.get("experience.yml")));
        Yaml ranks = new Yaml(text(before.get("skillranks.yml")));
        require(config.value("General.RetroMode.Enabled").equals("true"), "RetroMode is not true. Already converted data must not be divided again.");
        require(config.value("MySQL.Enabled").equals("false"), "MySQL storage is unsupported; this tool only converts the active flatfile.");
        require(config.number("General.Power_Level_Cap") == 0, "A custom power-level cap requires a separate review.");
        for (Skill skill : SKILLS) require(config.number("Skills." + skill.name + ".Level_Cap") == 0, "A custom skill cap requires a separate review: " + skill.name);
        require(config.number("Skills.Salvage.Level_Cap") == 0 && config.number("Skills.Smelting.Level_Cap") == 0, "Custom child-skill caps require a separate review.");
        require(experience.value("Experience_Formula.Curve").equals("LINEAR"), "Only the reviewed LINEAR formula is supported.");
        require(experience.value("Experience_Formula.Cumulative_Curve").equals("false"), "Cumulative curves require a separate review.");
        int base = experience.number("Experience_Formula.Linear_Values.base");
        int multiplier = experience.number("Experience_Formula.Linear_Values.multiplier");
        require(base == 10200 && multiplier == 200, "XP curve differs from the reviewed 10200 + 200 * level configuration.");
        if (files.containsKey("flatfile/formula.yml")) {
            String formula = new Yaml(text(files.get("flatfile/formula.yml"))).value("Previous_Formula");
            require(formula.equals("UNKNOWN") || formula.equals("LINEAR"), "Previous XP formula requires a separate review.");
        }
        if (files.containsKey("level_up_commands.yml")) {
            Yaml commands = new Yaml(text(files.get("level_up_commands.yml")));
            for (String path : commands.paths()) if (path.endsWith(".enabled"))
                require(commands.value(path).equals("false"), "Enabled level-up commands need their thresholds reviewed first: " + path);
        }
        config.replace("General.RetroMode.Enabled", "false");
        for (String path : List.of("General.Level_Up_Chat_Broadcasts.Milestone_Interval", "General.Level_Up_Chat_Broadcasts.Broadcast_Powerlevels.Milestone_Interval")) {
            int value = config.number(path);
            require(value > 0 && value % 10 == 0, "Broadcast milestone is not divisible by ten: " + path);
            config.replace(path, Integer.toString(value / 10));
        }
        // Native Standard sums (10*n+1)..(10*n+10). Subtracting b from a
        // makes it equal the OLD Retro block (10*n)..(10*n+9), exactly.
        experience.replace("Experience_Formula.Linear_Values.base", Integer.toString(base - multiplier));
        int initial = 0;
        for (String path : List.copyOf(ranks.paths())) {
            if (!path.contains(".RetroMode.")) continue;
            String other = path.replace(".RetroMode.", ".Standard.");
            require(ranks.has(other), "Missing Standard counterpart: " + path);
            int oldRequirement = ranks.number(path), standard = ranks.number(other);
            if (path.endsWith(".Rank_1") && oldRequirement == 1 && standard == 1) {
                ranks.replace(other, "0");
                initial++;
            } else {
                require(oldRequirement == standard * 10L, "Unexpected rank scaling requires review: " + path);
            }
        }
        StringBuilder converted = new StringBuilder();
        StringBuilder skills = new StringBuilder("player,skill,retro_level,retro_rest_xp,standard_level,standard_rest_xp,total_xp_before,total_xp_after\n");
        StringBuilder players = new StringBuilder("player,retro_powerlevel,standard_powerlevel,reconstructed_total_xp\n");
        Set<UUID> identities = new HashSet<>();
        Set<String> names = new HashSet<>();
        int profiles = 0;
        long totalXp = 0;
        Matcher lines = Pattern.compile("([^\\r\\n]*)(\\r\\n|\\n|\\r|$)").matcher(text(before.get("flatfile/mcmmo.users")));
        while (lines.find() && lines.start() != lines.end()) {
            String line = lines.group(1), newline = lines.group(2);
            String content = line.startsWith("\ufeff") ? line.substring(1) : line;
            if (content.isBlank() || content.stripLeading().startsWith("#")) { converted.append(line).append(newline); continue; }
            String[] fields = line.split(":", -1);
            require(fields.length == 59, "Unknown player-row layout at record " + (profiles + 1) + ": expected 59 fields.");
            require(!fields[0].isBlank(), "Player name is empty.");
            UUID identity;
            try { identity = UUID.fromString(fields[41]); }
            catch (IllegalArgumentException error) { throw new IllegalArgumentException("Invalid UUID at record " + (profiles + 1)); }
            require(identity.toString().equalsIgnoreCase(fields[41]), "Noncanonical UUID at record " + (profiles + 1));
            require(identities.add(identity), "Duplicate UUID in the player file.");
            require(names.add(fields[0].toLowerCase(Locale.ROOT)), "Duplicate player name in the player file.");
            long oldPower = 0, newPower = 0, playerXp = 0;
            Map<String,Integer> oldLevels = new HashMap<>(), newLevels = new HashMap<>();
            for (Skill skill : SKILLS) {
                int level = integer(fields[skill.levelIndex], "skill level");
                int xp = integer(fields[skill.xpIndex], "rest XP");
                Progress next = scale(level, xp, base, multiplier);
                oldLevels.put(skill.name,level); newLevels.put(skill.name,next.level);
                oldPower += level; newPower += next.level; playerXp = Math.addExact(playerXp, next.totalXp);
                skills.append(csv(fields[0])).append(',').append(skill.name).append(',').append(level).append(',').append(xp).append(',')
                    .append(next.level).append(',').append(next.xp).append(',').append(next.totalXp).append(',').append(next.totalXp).append('\n');
                fields[skill.levelIndex] = Integer.toString(next.level);
                fields[skill.xpIndex] = Integer.toString(next.xp);
            }
            for (Map<String,Integer> levels : List.of(oldLevels,newLevels)) {
                levels.put("Salvage", (levels.get("Repair") + levels.get("Fishing")) / 2);
                levels.put("Smelting", (levels.get("Mining") + levels.get("Repair")) / 2);
            }
            for (String path : ranks.paths()) {
                if (!path.contains(".RetroMode.")) continue;
                String parent = path.substring(0,path.indexOf('.'));
                require(oldLevels.containsKey(parent), "Unknown skill rank parent: " + path);
                int oldRequirement = ranks.number(path);
                int newRequirement = ranks.number(path.replace(".RetroMode.", ".Standard."));
                require(oldLevels.get(parent) < oldRequirement || newLevels.get(parent) >= newRequirement,
                    "Standard rounding would relock " + path.replace(".RetroMode.", ".")
                    + " at profile " + (profiles + 1) + ". This threshold needs a separate review.");
            }
            players.append(csv(fields[0])).append(',').append(oldPower).append(',').append(newPower).append(',').append(playerXp).append('\n');
            totalXp = Math.addExact(totalXp, playerXp);
            converted.append(String.join(":", fields)).append(newline);
            profiles++;
        }
        require(profiles > 0, "No player profiles found; check the selected snapshot.");
        Map<String, byte[]> after = new LinkedHashMap<>();
        after.put("config.yml", config.render().getBytes(StandardCharsets.UTF_8));
        after.put("experience.yml", experience.render().getBytes(StandardCharsets.UTF_8));
        after.put("skillranks.yml", ranks.render().getBytes(StandardCharsets.UTF_8));
        after.put("flatfile/mcmmo.users", converted.toString().getBytes(StandardCharsets.UTF_8));
        return new Result(before, after, skills.toString(), players.toString(), profiles, initial, totalXp);
    }

    public static Progress scale(int level, int xp, int base, int multiplier) {
        require(level >= 0 && xp >= 0, "Negative player progression requires review.");
        long oldNext = (long) base + (long) multiplier * level;
        require(xp < oldNext, "Stored rest XP exceeds the next Retro level; manual review required.");
        int newLevel = level / 10;
        long anchor = 10L * newLevel;
        long rest = Math.addExact(xp, Math.subtractExact(cumulative(level,base,multiplier), cumulative(anchor,base,multiplier)));
        long nextCost = 10L * base + multiplier * (100L * newLevel + 45L);
        require(nextCost <= Integer.MAX_VALUE, "Native Standard XP requirement would overflow; manual review required.");
        require(rest < nextCost && rest <= 16_777_216L, "Rest XP cannot be represented exactly by mcMMO's float storage; manual review required.");
        long total = Math.addExact(cumulative(level,base,multiplier),xp);
        long rebuilt = Math.addExact(cumulative(anchor,base,multiplier),rest);
        require(total == rebuilt, "XP conservation check failed.");
        return new Progress(newLevel, (int)rest, total);
    }

    private static long cumulative(long level, int base, int multiplier) {
        return Math.addExact(Math.multiplyExact(level,base), Math.multiplyExact(Math.multiplyExact(level,level-1)/2,multiplier));
    }

    public static void verify(Path packageFile, Path freshInput) throws IOException {
        Map<String, byte[]> fresh = archive(freshInput);
        try (ZipFile zip = new ZipFile(packageFile.toFile(), StandardCharsets.UTF_8)) {
            for (String file : CHANGED_FILES) {
                ZipEntry original = zip.getEntry("ROLLBACK/" + PREFIX + file);
                require(original != null && fresh.containsKey(file), "Missing file for snapshot verification: " + file);
                require(MessageDigest.isEqual(readLimited(zip.getInputStream(original)), fresh.get(file)), "Snapshot changed: " + file + ". Create a new package from the fresh stopped-server export.");
            }
        }
    }

    public static Map<String, byte[]> archive(Path path) throws IOException {
        require(Files.size(path) <= LIMIT, "Archive is too large for this reviewed migration.");
        Map<String, byte[]> entries = new LinkedHashMap<>();
        try (ZipFile zip = new ZipFile(path.toFile(), StandardCharsets.UTF_8)) {
            Set<String> roots = new HashSet<>();
            Set<String> seen = new HashSet<>();
            for (ZipEntry entry : Collections.list(zip.entries())) {
                String name = entry.getName();
                require(!name.startsWith("/") && !name.contains("\\") && !Arrays.asList(name.split("/")).contains(".."), "Unsafe archive entry.");
                require(seen.add(name), "Duplicate ZIP entry: " + name);
                if (!entry.isDirectory() && name.endsWith("flatfile/mcmmo.users")) roots.add(name.substring(0,name.length()-"flatfile/mcmmo.users".length()));
            }
            require(roots.size() == 1, "ZIP must contain exactly one mcMMO folder, with flatfile/mcmmo.users.");
            String root = roots.iterator().next();
            long expanded = 0;
            for (ZipEntry entry : Collections.list(zip.entries())) {
                String name = entry.getName();
                if (!entry.isDirectory() && name.startsWith(root)) {
                    String relative = name.substring(root.length());
                    if (relative.startsWith("backup/") || !relative.endsWith(".yml") && !relative.equals("flatfile/mcmmo.users") && !relative.equals(MARKER)) continue;
                    byte[] bytes = readLimited(zip.getInputStream(entry));
                    expanded += bytes.length;
                    require(expanded <= 2L * LIMIT, "Expanded configuration is too large.");
                    entries.put(relative,bytes);
                }
            }
        }
        return entries;
    }

    private static byte[] readLimited(InputStream stream) throws IOException {
        try (stream) {
            byte[] bytes = stream.readNBytes(LIMIT + 1);
            require(bytes.length <= LIMIT, "ZIP entry exceeds the size limit.");
            return bytes;
        }
    }
    private static void put(ZipOutputStream zip, String name, byte[] bytes) throws IOException {
        zip.putNextEntry(new ZipEntry(name)); zip.write(bytes); zip.closeEntry();
    }
    private static String sha(byte[] bytes) throws Exception { return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(bytes)); }
    private static String text(byte[] bytes) {
        try { return StandardCharsets.UTF_8.newDecoder().onMalformedInput(CodingErrorAction.REPORT).decode(ByteBuffer.wrap(bytes)).toString(); }
        catch (CharacterCodingException error) { throw new IllegalArgumentException("Non-UTF-8 data requires manual review."); }
    }
    private static String csv(String value) { return "\"" + value.replace("\"", "\"\"") + "\""; }
    private static int integer(String value, String description) {
        require(value.matches("[0-9]+"), "Invalid " + description + "; refusing to guess.");
        try { return Integer.parseInt(value); }
        catch (NumberFormatException error) { throw new IllegalArgumentException(description + " is outside the supported integer range."); }
    }
    private static void require(boolean condition, String message) { if (!condition) throw new IllegalArgumentException(message); }

    /** Only plain mapping paths are edited. Unknown YAML syntax never gets guessed. */
    static final class Yaml {
        private record Parent(int indent, String path) {}
        private record Cell(int start, int end, String value) {}
        private final String original;
        private final Map<String, Cell> fields = new LinkedHashMap<>();
        private final Map<String, String> replacements = new LinkedHashMap<>();
        Yaml(String text) {
            original = text;
            ArrayDeque<Parent> parents = new ArrayDeque<>();
            Matcher lines = Pattern.compile("(?m)^([ ]*)([A-Za-z0-9_-]+):([ ]*)([^\\r\\n]*)").matcher(text);
            Set<String> paths = new HashSet<>();
            while (lines.find()) {
                int indent = lines.group(1).length();
                while (!parents.isEmpty() && parents.peek().indent >= indent) parents.pop();
                String path = (parents.isEmpty() ? "" : parents.peek().path + ".") + lines.group(2);
                require(paths.add(path), "Duplicate YAML path: " + path);
                String raw = lines.group(4);
                String value = raw.replaceFirst("\\s+#.*$", "").stripTrailing();
                if (value.isEmpty() || value.startsWith("#")) { parents.push(new Parent(indent,path)); continue; }
                fields.put(path,new Cell(lines.start(4),lines.start(4)+value.length(),value));
            }
        }
        Set<String> paths() { return fields.keySet(); }
        boolean has(String path) { return fields.containsKey(path); }
        String value(String path) { require(has(path), "Missing or unsupported YAML field: " + path); return replacements.getOrDefault(path, fields.get(path).value); }
        int number(String path) { return integer(value(path), path); }
        void replace(String path, String value) { value(path); replacements.put(path,value); }
        String render() {
            StringBuilder output = new StringBuilder(original);
            replacements.entrySet().stream().sorted(Comparator.comparingInt((Map.Entry<String,String> e) -> fields.get(e.getKey()).start).reversed())
                .forEach(entry -> { Cell cell = fields.get(entry.getKey()); output.replace(cell.start,cell.end,entry.getValue()); });
            return output.toString();
        }
    }
}
