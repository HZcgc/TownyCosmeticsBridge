package com.townyreborn.petshops;

import java.io.IOException;
import java.nio.file.*;
import java.util.*;
import java.util.function.BooleanSupplier;
import java.util.regex.*;
import org.bukkit.*;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.entity.Entity;
import org.bukkit.plugin.Plugin;
import org.yaml.snakeyaml.*;
import org.yaml.snakeyaml.constructor.SafeConstructor;

/** Reads cosmetic sound cues from native pet skills without executing those skills. */
final class PetSounds {
    private static final Pattern MECHANIC=Pattern.compile("^([a-z_:]+)\\s*(?:\\{([^}]*)})?",Pattern.CASE_INSENSITIVE);
    private static final Pattern DELAY=Pattern.compile("^delay\\s+(\\d+)\\s*$",Pattern.CASE_INSENSITIVE);
    private static final Pattern INTERACT=Pattern.compile("~onInteract\\b",Pattern.CASE_INSENSITIVE);
    record Cue(int tick, String sound, float volume, float pitch, SoundCategory category) {
        Cue {
            if (sound!=null && !sound.contains(":")) sound="minecraft:"+sound;
            if (sound==null || !sound.matches("[a-z0-9_.-]+:[a-z0-9_./-]{1,200}") || category==null
                    || tick<0 || tick>1200 || !Float.isFinite(volume) || volume<0 || volume>16
                    || !Float.isFinite(pitch) || pitch<0.01f || pitch>2)
                throw new IllegalArgumentException("Invalid pet sound, delay, volume or pitch");
        }
        Cue after(int offset) { return new Cue(Math.addExact(tick,offset),sound,volume,pitch,category); }
    }
    private PetSounds() {}

    static Map<String,List<Cue>> immutable(Map<String,List<Cue>> profiles) {
        var result=new TreeMap<String,List<Cue>>();
        for (var entry:profiles.entrySet()) {
            String clip=entry.getKey();
            if (!clip.equals("*") && !clip.matches("[A-Za-z0-9_.:-]{1,128}")) throw new IllegalArgumentException("Invalid sound animation: "+clip);
            if (entry.getValue().size()>128) throw new IllegalArgumentException("Too many pet sound cues: "+clip);
            result.put(clip,entry.getValue().stream().sorted(Comparator.comparingInt(Cue::tick)).toList());
        }
        return Collections.unmodifiableMap(result);
    }
    static Map<String,List<Cue>> configured(Object value, Map<String,List<Cue>> automatic) {
        if (value==null || "auto".equals(value)) return automatic;
        if ("off".equals(value)) return Map.of();
        if (value instanceof ConfigurationSection section) value=section.getValues(false);
        if (!(value instanceof Map<?,?> profiles)) throw new IllegalArgumentException("interaction-sounds must be auto, off or an animation map");
        var parsed=new TreeMap<String,List<Cue>>();
        for (var profile:profiles.entrySet()) {
            if (!(profile.getValue() instanceof List<?> list)) throw new IllegalArgumentException("Sound cues must be a list");
            var cues=new ArrayList<Cue>();
            for (Object entry:list) {
                if (!(entry instanceof Map<?,?> cue)) throw new IllegalArgumentException("Sound cue must be an object");
                double tick=number(cue,"delay-ticks",0);
                if (tick!=Math.rint(tick) || tick<0 || tick>1200) throw new IllegalArgumentException("Sound delay must be 0–1200 whole ticks");
                cues.add(new Cue((int)tick,Objects.toString(cue.get("sound"),""),(float)number(cue,"volume",1),
                    (float)number(cue,"pitch",1),category(Objects.toString(cue.get("category"),"MASTER"))));
            }
            parsed.put(Objects.toString(profile.getKey(),""),cues);
        }
        return immutable(parsed);
    }
    private static double number(Map<?,?> cue, String name, double fallback) {
        Object value=cue.get(name);
        if (value==null) return fallback;
        if (!(value instanceof Number n) || !Double.isFinite(n.doubleValue())) throw new IllegalArgumentException("Invalid sound "+name);
        return n.doubleValue();
    }
    private static SoundCategory category(String name) { return SoundCategory.valueOf(name.toUpperCase(Locale.ROOT)); }
    static List<Cue> forAnimation(Map<String,List<Cue>> profiles, String animation) {
        var cues=new ArrayList<>(profiles.getOrDefault("*",List.of()));
        cues.addAll(profiles.getOrDefault(animation,List.of()));
        return cues.stream().sorted(Comparator.comparingInt(Cue::tick)).toList();
    }
    static long duration(List<Cue> cues) { return cues.stream().mapToLong(c->(c.tick()+1L)*50).max().orElse(0); }
    static void play(Plugin plugin, Entity speaker, List<Cue> cues, BooleanSupplier current) {
        for (Cue cue:cues) {
            Runnable play=()-> {
                if (!current.getAsBoolean() || !speaker.isValid()) return;
                var location=speaker.getLocation();
                location.getWorld().playSound(location,cue.sound(),cue.category(),cue.volume(),cue.pitch());
            };
            if (cue.tick()==0) play.run();
            else Bukkit.getScheduler().runTaskLater(plugin,play,cue.tick());
        }
    }

    record NativeCatalog(Map<String,List<String>> mobs, Map<String,List<String>> skills) {
        Map<String,List<Cue>> forPet(String mob) {
            var profiles=new TreeMap<String,List<Cue>>();
            List<String> interactions=mobs.getOrDefault(mob,List.of()).stream().filter(line->INTERACT.matcher(line).find()).toList();
            if (interactions.isEmpty()) return Map.of();
            var direct=new ArrayList<String>();
            for (String line:interactions) {
                List<String> refs=references(line);
                if (refs.isEmpty()) direct.add(line);
                for (String ref:refs) add(profiles,trace(skills.getOrDefault(ref,List.of()),Set.of(ref),0));
            }
            add(profiles,trace(direct,Set.of(),0));
            return immutable(profiles);
        }
        private void add(Map<String,List<Cue>> profiles, Trace trace) {
            if (trace.cues().isEmpty()) return;
            for (String clip:trace.clips().isEmpty()?Set.of("*"):trace.clips())
                profiles.computeIfAbsent(clip,k->new ArrayList<>()).addAll(trace.cues());
        }
        private Trace trace(List<String> lines, Set<String> parents, int depth) {
            if (depth>12) return new Trace(Set.of(),List.of());
            int tick=0; var clips=new LinkedHashSet<String>(); var cues=new ArrayList<Cue>();
            for (String line:lines) {
                String text=line.strip(); var delay=DELAY.matcher(text);
                if (delay.matches()) { tick=Math.addExact(tick,Integer.parseInt(delay.group(1))); if(tick>1200)break; continue; }
                var mechanic=MECHANIC.matcher(text); if (!mechanic.find()) continue;
                String name=mechanic.group(1).toLowerCase(Locale.ROOT); var args=attributes(mechanic.group(2));
                if (name.equals("state")) {
                    String clip=attribute(args,"", "state","s");
                    if (clip.matches("[A-Za-z0-9_.:-]{1,128}")) clips.add(clip);
                } else if (Set.of("sound","s","effect:sound","e:sound","e:s").contains(name)) {
                    try {
                        cues.add(new Cue(tick,attribute(args,"entity.zombie.attack_iron_door","sound","s"),
                            Float.parseFloat(attribute(args,"1","volume","v")),Float.parseFloat(attribute(args,"1","pitch","p")),
                            category(attribute(args,"MASTER","soundcategory","category","sc"))));
                    } catch (IllegalArgumentException ignored) { /* Dynamic expressions require an explicit shop sound profile. */ }
                } else if (name.equals("skill")) {
                    for (String child:references(text)) if (!parents.contains(child)) {
                        var seen=new HashSet<>(parents); seen.add(child);
                        Trace nested=trace(skills.getOrDefault(child,List.of()),seen,depth+1);
                        clips.addAll(nested.clips()); for (Cue cue:nested.cues()) cues.add(cue.after(tick));
                    }
                }
                if (cues.size()>128) throw new IllegalArgumentException("Too many native pet sound cues");
            }
            return new Trace(clips,cues);
        }
    }
    private record Trace(Set<String> clips, List<Cue> cues) {}
    private static Map<String,String> attributes(String text) {
        var result=new HashMap<String,String>();
        if (text!=null) for (String part:text.split(";")) {
            int split=part.indexOf('=');
            if (split>0) result.put(part.substring(0,split).strip().toLowerCase(Locale.ROOT),part.substring(split+1).strip());
        }
        return result;
    }
    private static String attribute(Map<String,String> args, String fallback, String... aliases) {
        for (String alias:aliases) if(args.containsKey(alias))return args.get(alias);
        return fallback;
    }
    private static List<String> references(String line) {
        var mechanic=MECHANIC.matcher(line.strip());
        if (!mechanic.find() || !Set.of("skill","randomskill").contains(mechanic.group(1).toLowerCase(Locale.ROOT))) return List.of();
        String refs=attribute(attributes(mechanic.group(2)),"","skills","skill","s");
        return Arrays.stream(refs.split(",")).map(String::strip).map(ref->ref.split("\\s+",2)[0])
            .filter(ref->ref.matches("[A-Za-z0-9_.:-]{1,200}")).toList();
    }
    static NativeCatalog read(Path folder) throws IOException { return new NativeCatalog(readSections(folder,"mobs"),readSections(folder,"skills")); }
    private static Map<String,List<String>> readSections(Path folder, String type) throws IOException {
        var result=new TreeMap<String,List<String>>();
        if (!Files.isDirectory(folder)) return result;
        try (var directories=Files.list(folder)) {
            for (Path directory:directories.filter(Files::isDirectory).filter(p->p.getFileName().toString().equalsIgnoreCase(type)).sorted().toList())
                try (var files=Files.walk(directory)) {
                    for (Path file:files.filter(Files::isRegularFile).filter(p->p.toString().toLowerCase(Locale.ROOT).matches(".*\\.ya?ml$")).sorted().toList()) {
                        Object parsed=new Yaml(new SafeConstructor(new LoaderOptions())).load(Files.readString(file));
                        if (!(parsed instanceof Map<?,?> root)) continue;
                        for (var entry:root.entrySet()) if (entry.getValue() instanceof Map<?,?> section && section.get("Skills") instanceof List<?> lines) {
                            String id=Objects.toString(entry.getKey());
                            var list=lines.stream().filter(String.class::isInstance).map(String.class::cast).toList();
                            if (result.putIfAbsent(id,list)!=null) throw new IOException("Duplicate MythicMobs "+type+" ID: "+id);
                        }
                    }
                }
        } catch (RuntimeException e) { throw new IOException("Cannot read MythicMobs pet sounds",e); }
        return Collections.unmodifiableMap(result);
    }
}
