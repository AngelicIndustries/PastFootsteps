package org.fentanylsolutions.pastfootsteps.footprint;

import static org.junit.Assert.assertEquals;

import java.io.ByteArrayInputStream;
import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Set;

import net.minecraft.client.resources.IResource;
import net.minecraft.client.resources.IResourceManager;
import net.minecraft.client.resources.data.IMetadataSection;
import net.minecraft.util.ResourceLocation;

import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;

public class FootprintProfilesTest {

    private static final String LOWER = "{\"schemaVersion\":1,"
        + "\"entities\":{\"Player\":{\"style\":\"SHOE\",\"width\":0.2},"
        + "\"Cow\":{\"style\":\"SQUARE\",\"width\":0.3}},"
        + "\"soundTypes\":{\"sand\":{\"opacity\":0.2,\"lifetime\":10}},"
        + "\"blocks\":{\"minecraft:sand\":{\"opacity\":0.3,\"lifetime\":20}}}";

    @Rule
    public final TemporaryFolder temporary = new TemporaryFolder();

    @Test
    public void localRulesOverrideHigherResourcesWithoutDiscardingOtherKeys() throws IOException {
        Resources resources = new Resources();
        resources.add(LOWER);
        resources.add(
            "{\"schemaVersion\":1,\"entities\":{\"Player\":{\"style\":\"SHOE\",\"width\":0.4}},"
                + "\"soundTypes\":{\"sand\":{\"opacity\":0.4,\"lifetime\":30}}}");
        File local = localFile();
        write(
            local,
            "{\"schemaVersion\":1,\"entities\":{\"Player\":{\"style\":\"SHOE\",\"width\":0.6}},"
                + "\"soundTypes\":{\"sand\":{\"opacity\":0.8,\"lifetime\":40}},"
                + "\"blocks\":{\"minecraft:sand@1\":{\"opacity\":0.7,\"lifetime\":50}}}");

        FootprintProfiles profiles = new FootprintProfiles();
        profiles.reload(resources, temporary.getRoot());

        assertEquals(0.6, profiles.actor("Player", false).width, 0.0);
        assertEquals(0.3, profiles.actor("Cow", false).width, 0.0);
        assertEquals(0.7, profiles.surface("minecraft:sand", 1, "sand").opacity, 0.0);
        assertEquals(0.3, profiles.surface("minecraft:sand", 0, "sand").opacity, 0.0);
        assertEquals(0.8, profiles.surface("minecraft:other", 0, "sand").opacity, 0.0);
    }

    @Test
    public void invalidLocalEditRetainsLastValidLocalLayer() throws IOException {
        Resources resources = new Resources();
        resources.add(LOWER);
        File local = localFile();
        write(local, "{\"schemaVersion\":1,\"entities\":{\"Player\":{\"style\":\"SHOE\",\"width\":0.65}}}");
        FootprintProfiles profiles = new FootprintProfiles();
        profiles.reload(resources, temporary.getRoot());
        assertEquals(0.65, profiles.actor("Player", false).width, 0.0);

        write(local, "{\"schemaVersion\":1,\"entities\":{\"Player\":{\"style\":\"SHOE\",\"unknown\":1}}}");
        profiles.reload(resources, temporary.getRoot());

        assertEquals(0.65, profiles.actor("Player", false).width, 0.0);
    }

    @Test
    public void deletingLocalFileClearsItsCachedLayer() throws IOException {
        Resources resources = new Resources();
        resources.add(LOWER);
        File local = localFile();
        write(local, "{\"schemaVersion\":1,\"entities\":{\"Player\":{\"style\":\"SHOE\",\"width\":0.65}}}");
        FootprintProfiles profiles = new FootprintProfiles();
        profiles.reload(resources, temporary.getRoot());
        assertEquals(0.65, profiles.actor("Player", false).width, 0.0);

        Files.delete(local.toPath());
        profiles.reload(resources, temporary.getRoot());

        assertEquals(0.2, profiles.actor("Player", false).width, 0.0);
    }

    @Test
    public void invalidUpperResourceLayerIsAtomic() {
        Resources resources = new Resources();
        resources.add(LOWER);
        resources.add(
            "{\"schemaVersion\":1,\"entities\":{" + "\"Player\":{\"style\":\"SHOE\",\"width\":0.9},"
                + "\"Cow\":{\"style\":\"NOT_A_STYLE\",\"width\":0.9}}}");

        FootprintProfiles profiles = new FootprintProfiles();
        profiles.reload(resources, null);

        assertEquals(0.2, profiles.actor("Player", false).width, 0.0);
        assertEquals(0.3, profiles.actor("Cow", false).width, 0.0);
    }

    private File localFile() throws IOException {
        File directory = new File(temporary.getRoot(), "profiles");
        Files.createDirectories(directory.toPath());
        return new File(directory, "footprints.json");
    }

    private static void write(File file, String value) throws IOException {
        Files.write(file.toPath(), value.getBytes(StandardCharsets.UTF_8));
    }

    private static final class Resources implements IResourceManager {

        private final List<IResource> layers = new ArrayList<IResource>();

        void add(String json) {
            layers.add(new StringResource(json));
        }

        @Override
        public Set<String> getResourceDomains() {
            return Collections.singleton("pastfootsteps");
        }

        @Override
        public IResource getResource(ResourceLocation location) throws IOException {
            if (layers.isEmpty()) throw new IOException("missing " + location);
            return layers.get(layers.size() - 1);
        }

        @Override
        public List<IResource> getAllResources(ResourceLocation location) {
            return new ArrayList<IResource>(layers);
        }
    }

    private static final class StringResource implements IResource {

        private final byte[] bytes;

        StringResource(String value) {
            bytes = value.getBytes(StandardCharsets.UTF_8);
        }

        @Override
        public InputStream getInputStream() {
            return new ByteArrayInputStream(bytes);
        }

        @Override
        public boolean hasMetadata() {
            return false;
        }

        @Override
        public IMetadataSection getMetadata(String sectionName) {
            return null;
        }

    }
}
