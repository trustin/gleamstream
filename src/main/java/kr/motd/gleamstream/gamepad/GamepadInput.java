package kr.motd.gleamstream.gamepad;

import java.io.IOException;

import com.fasterxml.jackson.core.JsonGenerator;
import com.fasterxml.jackson.core.JsonParser;
import com.fasterxml.jackson.databind.DeserializationContext;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.SerializerProvider;
import com.fasterxml.jackson.databind.annotation.JsonDeserialize;
import com.fasterxml.jackson.databind.annotation.JsonSerialize;
import com.fasterxml.jackson.databind.deser.std.StdDeserializer;
import com.fasterxml.jackson.databind.ser.std.StdSerializer;
import com.google.common.math.DoubleMath;

@JsonSerialize(using = GamepadInput.Serializer.class)
@JsonDeserialize(using = GamepadInput.Deserializer.class)
public final class GamepadInput {

    private final int id;
    private final boolean isAxis;
    private final float start;
    private final float end;
    private final float outputStart;
    private final float outputEnd;

    GamepadInput(int id, boolean isAxis, float start, float end, float outputStart, float outputEnd) {
        this.id = id;
        this.isAxis = isAxis;
        this.start = start;
        this.end = end;
        this.outputStart = outputStart;
        this.outputEnd = outputEnd;
    }

    public int id() {
        return id;
    }

    public boolean isAxis() {
        return isAxis;
    }

    public float start() {
        return start;
    }

    public float end() {
        return end;
    }

    public float outputStart() {
        return outputStart;
    }

    public float outputEnd() {
        return outputEnd;
    }

    static final class Serializer extends StdSerializer<GamepadInput> {
        private static final long serialVersionUID = 3091427135590960169L;

        Serializer() {
            super(GamepadInput.class);
        }

        @Override
        public void serialize(GamepadInput value, JsonGenerator gen, SerializerProvider provider)
                throws IOException {
            gen.writeStartObject();
            gen.writeNumberField("id", value.id());
            if (value.isAxis()) {
                gen.writeStringField("type", "AXIS");
                if (value.start() != 0.0) {
                    gen.writeNumberField("start", value.start());
                }
                gen.writeNumberField("end", value.end());
                if (!DoubleMath.fuzzyEquals(value.outputStart(), value.start(), 0.001)) {
                    gen.writeNumberField("outputStart", value.outputStart());
                }
                if (!DoubleMath.fuzzyEquals(value.outputEnd(), value.end(), 0.001)) {
                    gen.writeNumberField("outputEnd", value.outputEnd());
                }
            } else {
                gen.writeStringField("type", "BUTTON");
            }
            gen.writeEndObject();
        }
    }

    static final class Deserializer extends StdDeserializer<GamepadInput> {

        private static final long serialVersionUID = -7787173477999829706L;

        Deserializer() {
            super(GamepadInput.class);
        }

        @Override
        public GamepadInput deserialize(JsonParser p, DeserializationContext ctx) throws IOException {
            final JsonNode node = p.getCodec().readTree(p);
            final JsonNode idNode = node.get("id");
            if (idNode == null) {
                throw new IllegalStateException("missing 'id' field in a gamepad mapping");
            }
            final JsonNode typeNode = node.get("type");
            if (typeNode == null) {
                throw new IllegalStateException("missing 'type' field in a gamepad mapping");
            }
            switch (typeNode.asText()) {
                case "AXIS":
                    final float start = getOptionalFloat(node, "start", 0.0f);
                    final float end = getOptionalFloat(node, "end", 1.0f);
                    final float outputStart = getOptionalFloat(node, "outputStart", start);
                    final float outputEnd = getOptionalFloat(node, "outputEnd", end);
                    return new GamepadInput(idNode.intValue(), true, start, end, outputStart, outputEnd);
                case "BUTTON":
                    return new GamepadInput(idNode.intValue(), false, 0, 0, 0, 0);
                default:
                    throw new IllegalStateException(
                            "invalid 'type' field in a gamepad mapping: " + typeNode.asText());
            }
        }

        private static float getOptionalFloat(JsonNode node, String fieldName, float defaultValue) {
            final JsonNode valueNode = node.get(fieldName);
            if (valueNode == null) {
                return defaultValue;
            }
            return (float) valueNode.asDouble(defaultValue);
        }
    }
}
