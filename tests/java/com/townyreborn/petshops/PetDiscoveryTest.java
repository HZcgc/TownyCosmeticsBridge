package com.townyreborn.petshops;

import static org.junit.jupiter.api.Assertions.*;
import java.io.IOException;
import java.nio.file.*;
import java.util.*;
import org.bukkit.configuration.file.YamlConfiguration;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class PetDiscoveryTest {
    @TempDir Path folder;
    private Path definitions() { return folder.resolve("MCPets/Pets"); }
    private Path legacy() { return folder.resolve("PetShops/pets.yml"); }
    private void install(String id,String permission) throws Exception {
        Path path=definitions().resolve("future-pack/"+id+".yaml"); Files.createDirectories(path.getParent());
        Files.writeString(path,"Id: "+id+"\nPermission: "+permission+"\nMythicMob: "+id+"\nIcon:\n  Name: Future Pet\n");
    }
    private PetCatalog catalog() throws Exception {
        var defaults=new YamlConfiguration(); defaults.set("ingame-price",75000); defaults.set("store-price",10.99);
        defaults.set("store-url","https://shop.example.com");
        return new PetCatalog(legacy(),definitions(),defaults);
    }
    private void legacyOffer(String id,double price) throws Exception {
        var y=FilesIO.read(legacy()); y.set("pets."+id+".permission","pet."+id);
        y.set("pets."+id+".ingame-price",price); y.set("pets."+id+".store-price",17.99);
        y.set("pets."+id+".store-url","https://shop.example.com/package/custom");
        y.set("pets."+id+".hologram-height",3.2); FilesIO.write(legacy(),y);
    }
    @Test void aNewNestedDefinitionIsDiscoveredWithoutAutomaticallySellingIt() throws Exception {
        var catalog=catalog(); install("future_fox","pet.future_fox");
        var scanned=catalog.scanDefinitions();
        assertTrue(catalog.availableIds().isEmpty()); // Background reads never mutate the live state.
        assertFalse(catalog.updateDefinitions(scanned));
        assertEquals(Set.of("future_fox"),catalog.availableIds());
        assertTrue(catalog.pets().isEmpty()); assertFalse(Files.exists(catalog.shopFile("future_fox")));
    }
    @Test void addingImmediatelyAfterInstallationCreatesACompleteTebexFileAndKeepsCustomSettings() throws Exception {
        var catalog=catalog(); install("future_fox","pet.future_fox");
        var pet=catalog.add("future_fox");
        Path file=catalog.shopFile(pet.id()); var data=FilesIO.read(file);
        assertEquals("pet.future_fox",data.getString("permission"));
        assertEquals(75000,data.getDouble("ingame-price")); assertEquals(10.99,data.getDouble("store-price"));
        assertEquals("auto",data.getString("interaction-animation")); assertEquals(2,data.getInt("offer-version"));
        assertEquals("trpetshop grant {id} future_fox {transaction}",data.getString("tebex.command"));
        assertEquals("SERVER_CONSOLE",data.getString("tebex.execution")); assertEquals("PERMANENT",data.getString("tebex.unlock"));
        catalog.set(pet.id(),"ingame-price",75000.0); catalog.set(pet.id(),"store-url","https://shop.example.com/package/987");
        data=FilesIO.read(file); data.set("admin-note","Keep my note"); FilesIO.write(file,data);
        byte[] before=Files.readAllBytes(file); catalog.add(pet.id());
        assertArrayEquals(before,Files.readAllBytes(file));
        assertEquals(75000,catalog.get(pet.id()).price());
        assertEquals("https://shop.example.com/package/987",catalog.get(pet.id()).storeUrl());
    }
    @Test void legacyMigrationPreservesPricesLinksAndAlreadyCreatedIndividualFiles() throws Exception {
        install("future_fox","pet.future_fox"); legacyOffer("future_fox",65000);
        var catalog=catalog(); var file=catalog.shopFile("future_fox");
        assertEquals(65000,catalog.get("future_fox").price()); assertEquals(17.99,catalog.get("future_fox").storePrice());
        assertEquals(3.2,catalog.get("future_fox").hologramHeight());
        assertTrue(FilesIO.read(legacy()).getBoolean("shops-migrated"));
        assertEquals(65000,FilesIO.read(legacy()).getDouble("pets.future_fox.ingame-price")); // History retained.
        catalog.set("future_fox","ingame-price",80000.0);
        var legacy=FilesIO.read(legacy()); legacy.set("shops-migrated",false); FilesIO.write(legacy(),legacy);
        assertEquals(80000,catalog().get("future_fox").price());
        Files.delete(file); assertTrue(catalog().pets().isEmpty()); // A removed offer is not resurrected.
    }
    @Test void legacyFiftyThousandOffersUpgradeOnceWithoutChangingTheirStorePackage() throws Exception {
        install("future_fox","pet.future_fox"); legacyOffer("future_fox",50000);
        var catalog=catalog();
        assertEquals(75000,catalog.get("future_fox").price()); assertEquals(17.99,catalog.get("future_fox").storePrice());
        assertEquals("https://shop.example.com/package/custom",catalog.get("future_fox").storeUrl());
        assertEquals(2,FilesIO.read(catalog.shopFile("future_fox")).getInt("offer-version"));
        catalog.set("future_fox","ingame-price",50000.0);
        catalog.set("future_fox","interaction-animation","wag_tail");
        var reloaded=catalog(); assertEquals(50000,reloaded.get("future_fox").price());
        assertEquals("wag_tail",reloaded.get("future_fox").interaction());
        var data=FilesIO.read(reloaded.shopFile("future_fox")); data.set("interaction-animation","bad animation name");
        FilesIO.write(reloaded.shopFile("future_fox"),data);
        assertThrows(IOException.class,reloaded::reload);
        assertEquals("wag_tail",reloaded.get("future_fox").interaction());
    }
    @Test void anInvalidLegacyOfferStopsMigrationBeforeAnyOfferIsWritten() throws Exception {
        install("future_fox","pet.future_fox"); install("future_cat","pet.future_cat");
        legacyOffer("future_fox",65000); legacyOffer("future_cat",-1);
        assertThrows(IOException.class,this::catalog);
        assertFalse(Files.isDirectory(legacy().getParent().resolve("shops")));
        assertFalse(FilesIO.read(legacy()).getBoolean("shops-migrated"));
    }
    @Test void aBadEditedFileDoesNotReplaceTheLastValidCatalogOrGetSilentlyRepaired() throws Exception {
        install("future_fox","pet.future_fox"); var catalog=catalog(); catalog.add("future_fox");
        Path file=catalog.shopFile("future_fox"); var data=FilesIO.read(file); data.set("ingame-price",-1);
        FilesIO.write(file,data); byte[] bad=Files.readAllBytes(file);
        assertThrows(IOException.class,catalog::reload);
        assertEquals(75000,catalog.get("future_fox").price()); assertArrayEquals(bad,Files.readAllBytes(file));
    }
    @Test void nativePermissionChangesRefreshOnlyGeneratedDataAndNeverResetThePrice() throws Exception {
        install("future_fox","pet.future_fox"); var catalog=catalog(); catalog.add("future_fox");
        catalog.set("future_fox","ingame-price",72000.0); install("future_fox","pet.future_fox_v2");
        assertTrue(catalog.updateDefinitions(catalog.scanDefinitions()));
        assertEquals("pet.future_fox_v2",catalog.get("future_fox").permission());
        assertEquals(72000,catalog.get("future_fox").price());
        assertEquals("pet.future_fox_v2",FilesIO.read(catalog.shopFile("future_fox")).getString("permission"));
    }
    @Test void aCommandEditedInTheSetupFileCannotChangeTheGrantedPermissionOrRunArbitraryCommands() throws Exception {
        install("future_fox","pet.future_fox"); var catalog=catalog(); catalog.add("future_fox");
        var data=FilesIO.read(catalog.shopFile("future_fox"));
        data.set("permission","*"); data.set("tebex.command","op {username}");
        data.set("admin-note","Package 987"); FilesIO.write(catalog.shopFile("future_fox"),data);
        catalog.reload(); data=FilesIO.read(catalog.shopFile("future_fox"));
        assertEquals("pet.future_fox",catalog.get("future_fox").permission());
        assertEquals(PetCatalog.tebexCommand("future_fox"),data.getString("tebex.command"));
        assertEquals("Package 987",data.getString("admin-note"));
        assertThrows(IllegalArgumentException.class,()->catalog.shopFile("../outside"));
    }
    @Test void duplicateOrBrokenNewDefinitionsRetainThePreviousValidSnapshot() throws Exception {
        install("future_fox","pet.future_fox"); var catalog=catalog(); catalog.add("future_fox");
        Files.writeString(definitions().resolve("duplicate.yml"),"Id: future_fox\nPermission: pet.other\n");
        assertThrows(IOException.class,catalog::scanDefinitions);
        assertEquals("pet.future_fox",catalog.get("future_fox").permission());
        assertEquals(Set.of("future_fox"),catalog.availableIds());
    }
}
