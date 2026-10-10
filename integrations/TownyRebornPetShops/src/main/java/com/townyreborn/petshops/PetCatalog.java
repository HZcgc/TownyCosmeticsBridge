package com.townyreborn.petshops;

import java.io.IOException;
import java.net.URI;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.util.*;
import java.util.regex.Pattern;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.YamlConfiguration;
import org.yaml.snakeyaml.LoaderOptions;
import org.yaml.snakeyaml.Yaml;
import org.yaml.snakeyaml.constructor.SafeConstructor;

final class PetCatalog {
    record Definition(String id, String permission, String name, String model, String iconModel) {}
    record Pet(String id, String permission, String name, String model, String iconModel,
               boolean enabled, double price, double storePrice, String storeUrl,
               double hologramHeight, float width, float height, String idle) {
        Pet {
            validId(id);
            if (permission == null || !permission.matches("[A-Za-z0-9_.-]{1,200}") || permission.startsWith("ultracosmetics."))
                throw new IllegalArgumentException("A single MCPets permission is required: " + id);
            if (name == null || name.isBlank() || model == null || !model.matches("[A-Za-z0-9_:-]{1,128}"))
                throw new IllegalArgumentException("Invalid pet name/model: " + id);
            PetCatalog.price(price); PetCatalog.price(storePrice); validUrl(storeUrl);
            if (!Double.isFinite(hologramHeight) || Math.abs(hologramHeight)>32 || !Float.isFinite(width)
                    || !Float.isFinite(height) || width<=0 || height<=0 || width>16 || height>16)
                throw new IllegalArgumentException("Invalid hologram/hitbox size: " + id);
        }
    }
    private final Path file, definitionsFolder;
    private final ConfigurationSection defaults;
    private Map<String, Definition> definitions = Map.of();
    private Map<String, Pet> pets = Map.of();
    PetCatalog(Path file, Path definitionsFolder, ConfigurationSection defaults) throws IOException {
        this.file=file; this.definitionsFolder=definitionsFolder; this.defaults=defaults; reload();
    }
    Map<String, Pet> pets() { return pets; }
    Set<String> availableIds() { return definitions.keySet(); }
    Pet get(String id) { return pets.get(id); }
    boolean existsInMCPets(Pet pet) {
        Definition d=definitions.get(pet.id());
        return d!=null && d.permission().equals(pet.permission());
    }
    void reload() throws IOException {
        var found=new TreeMap<String, Definition>();
        if (Files.isDirectory(definitionsFolder)) {
            try (var stream=Files.walk(definitionsFolder)) {
                for (Path p:stream.filter(Files::isRegularFile).filter(p->p.toString().endsWith(".yml")).sorted().toList()) {
                    Definition d=parseDefinition(Files.readString(p));
                    if (d!=null && found.putIfAbsent(d.id(),d)!=null) throw new IOException("Duplicate MCPets ID: "+d.id());
                }
            } catch (RuntimeException e) { throw new IOException("Cannot read MCPets definitions", e); }
        }
        var loaded=new LinkedHashMap<String, Pet>();
        ConfigurationSection root=FilesIO.read(file).getConfigurationSection("pets");
        if (root!=null) for (String id:root.getKeys(false)) {
            ConfigurationSection s=root.getConfigurationSection(id);
            if (s==null) throw new IOException("Invalid shop entry: "+id);
            try {
                Definition d=found.get(id);
                // The permission comes only from MCPets, never from a shop command.
                String perm=d==null ? s.getString("permission", "") : d.permission();
                if (d!=null && s.contains("permission") && !perm.equals(s.getString("permission")))
                    throw new IllegalArgumentException("MCPets permission mismatch: "+id);
                loaded.put(id,new Pet(id,perm,s.getString("name",d==null?id:d.name()),
                    s.getString("model",d==null?id:d.model()),s.getString("icon-item-model",d==null?"":d.iconModel()),
                    s.getBoolean("enabled",true),s.getDouble("ingame-price",defaults.getDouble("ingame-price",50000)),
                    s.getDouble("store-price",defaults.getDouble("store-price",10.99)),
                    s.getString("store-url",defaults.getString("store-url","")),
                    s.getDouble("hologram-height",defaults.getDouble("hologram-height",2.5)),
                    (float)s.getDouble("hitbox-width",defaults.getDouble("hitbox-width",1.8)),
                    (float)s.getDouble("hitbox-height",defaults.getDouble("hitbox-height",2)),s.getString("idle-animation","idle")));
            } catch (RuntimeException e) { throw new IOException("Invalid shop entry: "+id,e); }
        }
        definitions=Collections.unmodifiableMap(found); pets=Collections.unmodifiableMap(loaded);
    }
    Pet add(String id) throws IOException {
        validId(id);
        if (pets.containsKey(id)) return pets.get(id);
        Definition d=definitions.get(id);
        if (d==null) throw new IllegalArgumentException("Unknown MCPets ID: "+id);
        var y=FilesIO.read(file);
        var s=y.createSection("pets."+id);
        s.set("enabled",true); s.set("name",d.name()); s.set("permission",d.permission());
        s.set("model",d.model()); s.set("icon-item-model",d.iconModel());
        s.set("ingame-price",defaults.getDouble("ingame-price",50000));
        s.set("store-price",defaults.getDouble("store-price",10.99));
        s.set("store-url",defaults.getString("store-url",""));
        s.set("hologram-height",defaults.getDouble("hologram-height",2.5));
        s.set("hitbox-width",defaults.getDouble("hitbox-width",1.8));
        s.set("hitbox-height",defaults.getDouble("hitbox-height",2)); s.set("idle-animation","idle");
        FilesIO.write(file,y); reload(); return get(id);
    }
    void set(String id, String field, Object value) throws IOException {
        if (!pets.containsKey(id)) throw new IllegalArgumentException("Unknown shop pet: "+id);
        var y=FilesIO.read(file); y.set("pets."+id+"."+field,value);
        // Validate a temporary complete catalog before replacing the live file.
        Path tmp=Files.createTempFile(file.toAbsolutePath().getParent(),"catalog-",".yml");
        try {
            FilesIO.write(tmp,y); new PetCatalog(tmp,definitionsFolder,defaults);
            FilesIO.write(file,y); reload();
        } finally { Files.deleteIfExists(tmp); }
    }
    static void validId(String id) {
        if (id==null || !id.matches("[A-Za-z0-9_-]{1,128}")) throw new IllegalArgumentException("Invalid pet ID");
    }
    static double price(double v) {
        if (!Double.isFinite(v) || v<=0 || v>1_000_000_000 || Math.abs(v*100-Math.rint(v*100))>0.00001)
            throw new IllegalArgumentException("Price must be positive with at most two decimal places");
        return v;
    }
    static void validUrl(String url) {
        if (url==null || url.isBlank()) return;
        try {
            URI u=URI.create(url.replace("{player}","Player").replace("{uuid}","00000000-0000-0000-0000-000000000000"));
            if (!"https".equalsIgnoreCase(u.getScheme()) || u.getHost()==null || u.getUserInfo()!=null)
                throw new IllegalArgumentException();
        } catch (RuntimeException e) { throw new IllegalArgumentException("Store link must be a valid HTTPS URL",e); }
    }
    static String urlFor(Pet pet, String player, UUID uuid) {
        return pet.storeUrl().replace("{player}",URLEncoder.encode(player,StandardCharsets.UTF_8))
                .replace("{uuid}",uuid.toString());
    }
    @SuppressWarnings("unchecked") static Definition parseDefinition(String text) {
        Object parsed=new Yaml(new SafeConstructor(new LoaderOptions())).load(text);
        if (!(parsed instanceof Map<?,?> d) || !d.containsKey("Id")) return null;
        String id=Objects.toString(d.get("Id"),""); validId(id);
        String perm=Objects.toString(d.get("Permission"),"");
        if (!perm.matches("[A-Za-z0-9_.-]{1,200}") || perm.startsWith("ultracosmetics."))
            throw new IllegalArgumentException("Invalid MCPets permission: "+id);
        String name=id.replace('_',' '), itemModel="";
        if (d.get("Icon") instanceof Map<?,?> icon) {
            if (icon.get("Name") instanceof String n) name=n;
            if (icon.get("Raw") instanceof Map<?,?> raw && raw.get("components") instanceof Map<?,?> components) {
                itemModel=unquote(Objects.toString(components.get("minecraft:item_model"),""));
                String custom=Objects.toString(components.get("minecraft:custom_name"),"");
                var match=Pattern.compile("[\"']?text[\"']?\\s*:\\s*\"((?:\\\\.|[^\"])*)\"").matcher(custom);
                if (match.find()) name=match.group(1).replace("\\\"","\"");
            }
        }
        return new Definition(id,perm,name,id,itemModel);
    }
    private static String unquote(String s) {
        return s.length()>1 && s.startsWith("\"") && s.endsWith("\"") ? s.substring(1,s.length()-1) : s;
    }
}
