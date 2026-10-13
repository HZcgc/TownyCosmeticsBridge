package net.townyreborn.journey;

import java.util.Locale;
import me.clip.placeholderapi.expansion.PlaceholderExpansion;
import org.bukkit.entity.Player;

final class RebornExpansion extends PlaceholderExpansion {
   private final RebornPlugin plugin;
   private final String identifier;

   RebornExpansion(RebornPlugin var1, String var2) {
      this.plugin = var1;
      this.identifier = var2;
   }

   public String getIdentifier() {
      return this.identifier;
   }

   public String getAuthor() {
      return "TownyReborn";
   }

   public String getVersion() {
      return this.plugin.getPluginMeta().getVersion();
   }

   public boolean persist() {
      return true;
   }

   public String onPlaceholderRequest(Player var1, String var2) {
      if (var1 == null) {
         return "0";
      }

      if (var2.equalsIgnoreCase("access")) {
         return this.plugin.access(var1) ? "1" : "0";
      }

      if (!var2.startsWith("ready_") && !var2.equals("paused")) {
         try {
            State var3 = this.plugin.state(var1);
            int var4 = this.plugin.journey.level(var3.points);

            return switch (var2.toLowerCase(Locale.ROOT)) {
               case "level" -> Integer.toString(var4);
               case "title" -> this.plugin.journey.title(var4);
               case "points", "quest_points" -> Long.toString(var3.points);
               case "playtime_minutes" -> Long.toString(var3.minutes);
               case "playtime_hours" -> String.format(Locale.US, "%.1f", var3.minutes / 60.0);
               case "next_level" -> Integer.toString(Math.min(this.plugin.journey.steps, var4 + 1));
               case "next_title" -> this.plugin.journey.title(Math.min(this.plugin.journey.steps, var4 + 1));
               case "next_required" -> Long.toString(this.plugin.journey.threshold(Math.min(this.plugin.journey.steps, var4 + 1)));
               case "percent" -> Integer.toString(this.plugin.journey.percent(var3.points));
               default -> null;
            };
         } catch (Exception var7) {
            this.plugin.warnOnce("placeholder-" + var1.getUniqueId(), "Journey profile could not be read for " + var1.getUniqueId());
            return "0";
         }
      } else {
         return "0";
      }
   }
}
