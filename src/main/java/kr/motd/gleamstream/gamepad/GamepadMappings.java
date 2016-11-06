package kr.motd.gleamstream.gamepad;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.util.Iterator;
import java.util.List;
import java.util.Map;

import org.lwjgl.system.Platform;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.core.util.DefaultIndenter;
import com.fasterxml.jackson.core.util.DefaultPrettyPrinter;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.fasterxml.jackson.databind.annotation.JsonSerialize;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableMap;

@JsonSerialize(as = Iterable.class, contentAs = GamepadMapping.class)
public class GamepadMappings implements Iterable<GamepadMapping> {

    private static final ObjectMapper jackson = new ObjectMapper();

    static {
        jackson.enable(SerializationFeature.INDENT_OUTPUT);
        jackson.setDefaultPrettyPrinter(new DefaultPrettyPrinter() {
            private static final long serialVersionUID = 1822065996401935750L;
            {
                _arrayIndenter = DefaultIndenter.SYSTEM_LINEFEED_INSTANCE;
            }
        });
    }

    public static GamepadMappings load(InputStream in) throws IOException {
        return new GamepadMappings(jackson.readValue(in, new TypeReference<List<GamepadMapping>>() {}));
    }

    public static void save(OutputStream out, Iterable<GamepadMapping> mappings) throws IOException {
        jackson.writeValue(out, mappings);
    }

    private final List<GamepadMapping> nonDefaultMappings;
    private final Map<Platform, GamepadMapping> defaultMappings;
    private final List<GamepadMapping> allMappings;

    private GamepadMappings(List<GamepadMapping> mappings) {
        final ImmutableList.Builder<GamepadMapping> nonDefaultMappingsBuilder = ImmutableList.builder();
        final ImmutableMap.Builder<Platform, GamepadMapping> defaultMappingsBuilder = ImmutableMap.builder();
        for (GamepadMapping m : mappings) {
            if ("default".equals(m.id())) {
                m.platforms().forEach(p -> defaultMappingsBuilder.put(p, m));
            } else {
                nonDefaultMappingsBuilder.add(m);
            }
        }
        nonDefaultMappings = nonDefaultMappingsBuilder.build();
        defaultMappings = defaultMappingsBuilder.build();
        allMappings = ImmutableList.copyOf(mappings);
    }

    public GamepadMapping find(String name) {
        for (GamepadMapping m : nonDefaultMappings) {
            if (m.platforms().contains(Platform.get()) &&
                m.names().contains(name)) {
                return m;
            }
        }
        return defaultMappings.get(Platform.get());
    }

    @Override
    public Iterator<GamepadMapping> iterator() {
        return allMappings.iterator();
    }
}
