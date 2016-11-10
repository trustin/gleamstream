package kr.motd.gleamstream.gamepad;

import static java.util.Objects.requireNonNull;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Set;

import org.lwjgl.system.Platform;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.databind.annotation.JsonDeserialize;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableMap;
import com.google.common.collect.ImmutableSet;

import it.unimi.dsi.fastutil.ints.Int2ObjectMap;
import it.unimi.dsi.fastutil.ints.Int2ObjectOpenHashMap;

public class GamepadMapping {

    public static final Entry IGNORED = new Entry(null, null);
    public static final Entry MISSING = new Entry(null, null);

    private final Int2ObjectMap<Entry> buttons = new Int2ObjectOpenHashMap<>();
    private final Int2ObjectMap<List<Entry>> axes = new Int2ObjectOpenHashMap<>();

    private final String id;
    private final Set<Platform> platforms;
    private final Set<String> names;
    private final Map<GamepadOutput, GamepadInput> mappings;
    private final Set<Integer> ignoredButtons;
    private final Set<Integer> ignoredAxes;
    private final float leftStickDeadZone;
    private final float rightStickDeadZone;

    @JsonCreator
    public GamepadMapping(@JsonProperty("id") String id,
                          @JsonProperty("platforms") Set<Platform> platforms,
                          @JsonProperty("names") Set<String> names,
                          @JsonProperty("mappings")
                          @JsonDeserialize(keyAs = GamepadOutput.class, contentAs = GamepadInput.class)
                          Map<GamepadOutput, GamepadInput> mappings,
                          @JsonProperty("ignoredButtons") Set<Integer> ignoredButtons,
                          @JsonProperty("ignoredAxes") Set<Integer> ignoredAxes,
                          @JsonProperty("leftStickDeadZone") float leftStickDeadZone,
                          @JsonProperty("rightStickDeadZone") float rightStickDeadZone) {

        this.id = requireNonNull(id, "id");
        this.platforms = platforms != null ? ImmutableSet.copyOf(platforms)
                                           : ImmutableSet.of(Platform.LINUX, Platform.MACOSX, Platform.WINDOWS);
        this.names = ImmutableSet.copyOf(requireNonNull(names, "names"));
        this.mappings = mappings != null ? ImmutableMap.copyOf(mappings)
                                         : Collections.emptyMap();
        this.ignoredButtons = ignoredButtons != null ? ImmutableSet.copyOf(ignoredButtons)
                                                     : Collections.emptySet();
        this.ignoredAxes = ignoredAxes != null ? ImmutableSet.copyOf(ignoredAxes)
                                               : Collections.emptySet();
        this.leftStickDeadZone = leftStickDeadZone;
        this.rightStickDeadZone = rightStickDeadZone;

        final Set<Map.Entry<GamepadOutput, GamepadInput>> entries = mappings.entrySet();
        entries.stream().filter(e -> !e.getValue().isAxis()).forEach(e -> {
            final GamepadInput in = e.getValue();
            final GamepadOutput out = e.getKey();
            buttons.put(in.id(), new Entry(in, out));
        });
        entries.stream().filter(e -> e.getValue().isAxis()).forEach(e -> {
            final GamepadInput in = e.getValue();
            final GamepadOutput out = e.getKey();
            axes.computeIfAbsent(in.id(), unused -> new ArrayList<>())
                .add(new Entry(in, out));
        });

        ignoredButtons.forEach(button -> buttons.putIfAbsent(button, IGNORED));
        ignoredAxes.forEach(axis -> axes.putIfAbsent(axis, ImmutableList.of(IGNORED)));
    }

    @JsonProperty
    public String id() {
        return id;
    }

    @JsonProperty
    Set<Platform> platforms() {
        return platforms;
    }

    @JsonProperty
    public Set<String> names() {
        return names;
    }

    @JsonProperty
    public Map<GamepadOutput, GamepadInput> mappings() {
        return mappings;
    }

    @JsonProperty
    public Set<Integer> ignoredButtons() {
        return ignoredButtons;
    }

    @JsonProperty
    public Set<Integer> ignoredAxes() {
        return ignoredAxes;
    }

    @JsonProperty
    public float leftStickDeadZone() {
        return leftStickDeadZone;
    }

    @JsonProperty
    public float rightStickDeadZone() {
        return rightStickDeadZone;
    }

    public boolean matches(String name) {
        return platforms.contains(Platform.get()) && names.contains(name);
    }

    public Entry mapButton(int id) {
        final Entry entry = buttons.get(id);
        return entry != null ? entry : MISSING;
    }

    public Entry mapAxis(int id, float value) {
        final List<Entry> entries = axes.get(id);
        if (entries == null) {
            return MISSING;
        }

        for (Entry e : entries) {
            if (e == IGNORED) {
                return IGNORED;
            }

            final GamepadInput in = e.in();
            final float start = in.start();
            final float end = in.end();
            if (start < end) {
                if (value >= start && value <= end) {
                    return e;
                }
            } else {
                if (value <= start && value >= end) {
                    return e;
                }
            }
        }
        return IGNORED;
    }

    @Override
    public String toString() {
        return id;
    }

    public static final class Entry {

        private final GamepadInput in;
        private final GamepadOutput out;

        Entry(GamepadInput in, GamepadOutput out) {
            this.in = in;
            this.out = out;
        }

        public GamepadInput in() {
            return in;
        }

        public GamepadOutput out() {
            return out;
        }
    }
}
