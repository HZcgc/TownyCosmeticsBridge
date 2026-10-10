package com.townyreborn.petshops;

import static org.junit.jupiter.api.Assertions.*;
import java.util.*;
import org.junit.jupiter.api.Test;
import net.kyori.adventure.text.format.TextDecoration;
import net.kyori.adventure.text.serializer.legacy.LegacyComponentSerializer;
import net.kyori.adventure.text.serializer.plain.PlainTextComponentSerializer;

class PetNameFormatTest {
    private String plain(PetNameFormat.Name name) { return PlainTextComponentSerializer.plainText().serialize(name.display()); }
    @Test void birdflopHexAndExpandedFormatsKeepEveryCharactersExactColor() {
        String expected="§dH§x§a§a§f§f§5§5i"; // #ff55ff is exactly Minecraft light purple.
        for (String input:List.of("&#ff55ffH&#aaff55i","&x&f&f&5&5&f&fH&x&a&a&f&f&5&5i",
                expected,"<#ff55ff>H<#aaff55>i")) {
            var name=PetNameFormat.parse(input,16);
            assertEquals("Hi",plain(name)); assertEquals(expected,name.legacy());
            assertEquals("Hi",PlainTextComponentSerializer.plainText().serialize(LegacyComponentSerializer.legacySection().deserialize(name.legacy())));
        }
    }
    @Test void rgbirdflopDefaultOutputDoesNotLeaveTheAmpersandsSeenInNativeMcpetsChatRenames() {
        var name=PetNameFormat.parse("&#54DAF4H&#54C8EBZ&#54B7E2c&#54A5D9g&#5493D1c",16);
        assertEquals("HZcgc",plain(name)); assertEquals("HZcgc",org.bukkit.ChatColor.stripColor(name.legacy()));
        assertFalse(name.legacy().contains("&"));
        assertEquals("§x§5§4§d§a§f§4H§x§5§4§c§8§e§bZ§x§5§4§b§7§e§2c§x§5§4§a§5§d§9g§x§5§4§9§3§d§1c",name.legacy());
        var rendered=LegacyComponentSerializer.legacySection().deserialize(name.legacy());
        assertEquals("HZcgc",PlainTextComponentSerializer.plainText().serialize(rendered));
    }
    @Test void miniMessageGradientsWorkOnOldMCPetsAndFormattingSurvives() {
        var name=PetNameFormat.parse("<gradient:#ff55ff:#aaff55><bold>Hi</bold></gradient>",16);
        assertEquals("Hi",plain(name));
        assertEquals("§d§lH§x§a§a§f§f§5§5§li",name.legacy());
        assertTrue(name.legacy().contains("§l"));
        var legacy=PetNameFormat.parse("&a&lBuddy",16); assertEquals("§a§lBuddy",legacy.legacy());
        assertEquals(TextDecoration.State.TRUE,legacy.display().decoration(TextDecoration.BOLD));
    }
    @Test void longRgbirdflopOutputCountsVisibleCharactersAndFitsTheDialog() {
        String text="abcdefghijklmnop";
        String input=text.chars().mapToObj(c->"&x&f&f&5&5&f&f&l"+(char)c).reduce("",String::concat);
        assertTrue(input.length()>256);
        assertEquals(text,plain(PetNameFormat.parse(input,16)));
        assertThrows(IllegalArgumentException.class,()->PetNameFormat.parse(input+"q",16));
        assertThrows(IllegalArgumentException.class,()->PetNameFormat.parse(input,15));
    }
    @Test void invalidOrInvisibleNamesCannotCorruptNativeStorage() {
        for (String input:List.of("", "   ", "&a", "<gradient:#ff55ff:#aaff55></gradient>","two\nlines",
                "nul\u0000name","Fox's Pet","/nick &#ff55ffBuddy","x".repeat(4097)))
            assertThrows(IllegalArgumentException.class,()->PetNameFormat.parse(input,16),input);
    }
    @Test void namesContainOnlyTextAndFormattingRatherThanClickableActions() {
        var name=PetNameFormat.parse("<click:run_command:/op x>X</click>",128);
        var queue=new ArrayDeque<>(List.of(name.display()));
        while (!queue.isEmpty()) {
            var component=queue.removeFirst(); assertNull(component.clickEvent()); assertNull(component.hoverEvent());
            queue.addAll(component.children());
        }
        assertFalse(name.legacy().contains("§k"));
    }
}
