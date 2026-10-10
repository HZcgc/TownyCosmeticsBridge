package com.ghostchu.quickshop.util.matcher.item;

import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.persistence.PersistentDataContainer;
import org.bukkit.persistence.PersistentDataType;
import net.kyori.adventure.text.serializer.legacy.LegacyComponentSerializer;
import java.io.*;
import java.util.*;
import java.util.zip.GZIPOutputStream;
import static org.mockito.Mockito.*;
import static com.ghostchu.quickshop.util.matcher.item.AnimalNbt.*;

/** In-memory items use real binary NBT and independent inventory state, without a live server. */
public final class AnimalTestItems {
    public static final List<String> TEMPLATES = List.of(
        "&eENTITY TYPE: &a{ENTITY}", "&eThe Catcher: &a{PLAYER}",
        "&eCapture Time: &a{TIME}", "&eCapture Location: &a{LOCATION}");

    public static SavedItem bucket(String variant, String entityData) {
        return item(Material.AXOLOTL_BUCKET, "{DataVersion:4903,id:\"minecraft:axolotl_bucket\",count:1,"
            + "components:{\"minecraft:axolotl/variant\":" + quote(variant)
            + ",\"minecraft:bucket_entity_data\":" + entityData + "}}", null);
    }

    public static SavedItem captured(String type, String entityData, String uuid, String player, String time, String extra) {
        List<String> lines = List.of("\u00a7eENTITY TYPE: \u00a7a" + type, "\u00a7eThe Catcher: \u00a7a" + player,
            "\u00a7eCapture Time: \u00a7a" + time, "\u00a7eCapture Location: \u00a7aworld, 123, 64, 321");
        String lore = String.join(",", lines.stream().map(s -> "{text:" + quote(s) + "}").toList());
        String root = "{DataVersion:4903,id:\"minecraft:player_head\",count:1,components:{"
            + "\"minecraft:custom_name\":{text:" + quote(type) + "},"
            + "\"minecraft:lore\":[" + lore + "],"
            + "\"minecraft:profile\":{id:[I;" + uuid.hashCode() + ",1,2,3],name:\"catchball\","
            + "properties:[{name:\"textures\",value:\"species-" + type + "\"}]},"
            + "\"minecraft:custom_data\":{PublicBukkitValues:{"
            + "\"catchball:entitytype\":" + quote(type) + ",\"catchball:entity\":" + quote(entityData)
            + ",\"catchball:skulldata\":" + quote(uuid) + extra + "}}}}";
        ItemMeta meta = mock(ItemMeta.class);
        var pdc = mock(PersistentDataContainer.class);
        when(meta.getPersistentDataContainer()).thenReturn(pdc);
        when(pdc.has(new NamespacedKey("catchball", "entity"), PersistentDataType.STRING)).thenReturn(true);
        when(pdc.has(new NamespacedKey("catchball", "entitytype"), PersistentDataType.STRING)).thenReturn(true);
        when(meta.lore()).thenReturn(lines.stream().<net.kyori.adventure.text.Component>map(LegacyComponentSerializer.legacySection()::deserialize).toList());
        return item(Material.PLAYER_HEAD, root, meta);
    }

    public static SavedItem item(Material material, String snbt, ItemMeta meta) {
        return new SavedItem(material, encode(parse(snbt)), meta, 1);
    }
    public static String quote(String value) { return "\"" + value.replace("\\", "\\\\").replace("\"", "\\\"") + "\""; }

    static byte[] encode(Tag tag) {
        try {
            var bytes = new ByteArrayOutputStream();
            try (var stream = new DataOutputStream(new GZIPOutputStream(bytes))) {
                stream.writeByte(10); stream.writeUTF(""); write(stream, tag);
            }
            return bytes.toByteArray();
        } catch (IOException ex) { throw new UncheckedIOException(ex); }
    }
    private static void write(DataOutputStream out, Tag tag) throws IOException {
        switch (tag.type()) {
            case 1 -> out.writeByte(((Number)tag.value()).byteValue());
            case 2 -> out.writeShort(((Number)tag.value()).shortValue());
            case 3 -> out.writeInt(((Number)tag.value()).intValue());
            case 4 -> out.writeLong(((Number)tag.value()).longValue());
            case 5 -> out.writeFloat(((Number)tag.value()).floatValue());
            case 6 -> out.writeDouble(((Number)tag.value()).doubleValue());
            case 8 -> out.writeUTF((String)tag.value());
            case 7, 11, 12 -> {
                var values = (List<?>)tag.value();
                out.writeInt(values.size());
                for (Object value : values) {
                    Number n = (Number)value;
                    if (tag.type() == 7) out.writeByte(n.byteValue());
                    else if (tag.type() == 11) out.writeInt(n.intValue()); else out.writeLong(n.longValue());
                }
            }
            case 9 -> {
                var list = (TagList)tag.value();
                out.writeByte(list.elementType()); out.writeInt(list.values().size());
                for (Tag value : list.values()) write(out, value);
            }
            case 10 -> {
                for (var entry : compound(tag).entrySet()) {
                    out.writeByte(entry.getValue().type()); out.writeUTF(entry.getKey()); write(out, entry.getValue());
                }
                out.writeByte(0);
            }
            default -> throw new IOException("Unsupported fixture type");
        }
    }

    public static final class SavedItem extends ItemStack {
        private final Material material;
        private final byte[] bytes;
        private final ItemMeta meta;
        private int amount;
        public SavedItem(Material material, byte[] bytes, ItemMeta meta, int amount) {
            super(); this.material=material; this.bytes=bytes.clone(); this.meta=meta; this.amount=amount;
        }
        @Override public Material getType() { return material; }
        @Override public int getAmount() { return amount; }
        @Override public void setAmount(int amount) { this.amount = amount; }
        @Override public int getMaxStackSize() { return material == Material.AXOLOTL_BUCKET ? 1 : 64; }
        @Override public ItemMeta getItemMeta() { return meta; }
        @Override public boolean hasItemMeta() { return meta != null; }
        @Override public byte[] serializeAsBytes() { return bytes.clone(); }
        @Override public SavedItem clone() { return new SavedItem(material, bytes, meta, amount); }
        @Override public boolean isSimilar(ItemStack other) {
            return other != null && material == other.getType() && Arrays.equals(bytes, other.serializeAsBytes());
        }
        @Override public ItemStack add(int count) { amount += count; return this; }
        @Override public String toString() { return material + " x" + amount; }
    }
}
