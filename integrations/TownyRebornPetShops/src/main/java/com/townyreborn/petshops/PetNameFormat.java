package com.townyreborn.petshops;

import java.util.regex.Pattern;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.minimessage.MiniMessage;
import net.kyori.adventure.text.minimessage.tag.resolver.TagResolver;
import net.kyori.adventure.text.minimessage.tag.standard.StandardTags;
import net.kyori.adventure.text.serializer.legacy.LegacyComponentSerializer;
import net.kyori.adventure.text.serializer.plain.PlainTextComponentSerializer;

/** Normalize RGBirdflop output to the legacy hex format understood by MCPets 4.1.5+. */
final class PetNameFormat {
    record Name(String legacy, Component display) {}
    static final int MAX_INPUT=4096;
    private static final Pattern EXPANDED=Pattern.compile("(?i)&x((?:&[0-9a-f]){6})");
    private static final Pattern HEX=Pattern.compile("(?i)&#([0-9a-f]{6})");
    private static final Pattern LEGACY=Pattern.compile("(?i)&([0-9a-fk-or])");
    private static final String[] TAGS={"black","dark_blue","dark_green","dark_aqua","dark_red","dark_purple",
        "gold","gray","dark_gray","blue","green","aqua","red","light_purple","yellow","white"};
    private static final MiniMessage COLORS=MiniMessage.builder().tags(TagResolver.resolver(StandardTags.color(),
        StandardTags.decorations(),StandardTags.gradient(),StandardTags.rainbow(),StandardTags.reset())).build();
    private static final LegacyComponentSerializer NATIVE=LegacyComponentSerializer.builder().character('§')
        .hexColors().useUnusualXRepeatedCharacterHexFormat().build();

    private PetNameFormat() {}

    static Name parse(String input, int maxLength) {
        if (input==null || input.isBlank()) throw new IllegalArgumentException("Please enter a pet name.");
        if (input.length()>MAX_INPUT) throw new IllegalArgumentException("The pasted name is too long.");
        if (input.codePoints().anyMatch(Character::isISOControl)) throw new IllegalArgumentException("Pet names must fit on one line.");
        if (input.indexOf('\'')>=0) throw new IllegalArgumentException("Please use a name without apostrophes.");
        if (input.stripLeading().startsWith("/")) throw new IllegalArgumentException("Paste only the name and color codes, without /nick.");
        String text=input.trim().replace('§','&');
        text=EXPANDED.matcher(text).replaceAll(m->"<#"+m.group(1).replace("&","")+">");
        text=HEX.matcher(text).replaceAll(m->"<#"+m.group(1)+">");
        text=LEGACY.matcher(text).replaceAll(m-> {
            char code=Character.toLowerCase(m.group(1).charAt(0));
            int color=Character.digit(code,16);
            if (color>=0) return "<"+TAGS[color]+">";
            return switch(code) {
                case 'k'->"<obfuscated>"; case 'l'->"<bold>"; case 'm'->"<strikethrough>";
                case 'n'->"<underlined>"; case 'o'->"<italic>"; case 'r'->"<reset>";
                default->throw new IllegalArgumentException("Unsupported name format");
            };
        });
        Component component;
        try { component=COLORS.deserialize(text); }
        catch (RuntimeException e) { throw new IllegalArgumentException("Please check the color codes or gradient tags.",e); }
        String visible=PlainTextComponentSerializer.plainText().serialize(component);
        if (visible.isBlank()) throw new IllegalArgumentException("Please enter a visible pet name.");
        if (visible.length()>maxLength) throw new IllegalArgumentException("Use at most "+maxLength+" visible characters; color codes do not count.");
        return new Name(NATIVE.serialize(component),component);
    }
}
