package com.github.javaparser.serialization;

import com.github.javaparser.ast.Modifier;
import com.github.javaparser.ast.Node;
import com.github.javaparser.ast.NodeList;
import com.github.javaparser.metamodel.BaseNodeMetaModel;
import com.github.javaparser.metamodel.PropertyMetaModel;
import com.github.javaparser.utils.Log;

import javax.json.JsonArray;
import javax.json.JsonObject;
import javax.json.JsonReader;
import javax.json.JsonString;
import java.util.EnumSet;
import java.util.HashMap;
import java.util.Map;
import java.util.stream.Collectors;

import static com.github.javaparser.ast.NodeList.toNodeList;
import static com.github.javaparser.metamodel.JavaParserMetaModel.getNodeMetaModel;

/**
 * Deserializes the JSON file that was built by {@link JavaParserJsonSerializer}.
 */
public class JavaParserJsonDeserializer {

    public Node deserializeObject(JsonReader reader) {
        Log.info("Deserializing JSON to Node.");
        JsonObject jsonObject = reader.readObject();
        return deserializeObject(jsonObject);
    }

    private Node deserializeObject(JsonObject nodeJson) {
        try {
            String serializedNodeType = nodeJson.getString("!");
            BaseNodeMetaModel nodeMetaModel = getNodeMetaModel(Class.forName(serializedNodeType))
                    .orElseThrow(() -> new IllegalStateException("Trying to deserialize an unknown node type: " + serializedNodeType));
            Map<String, Object> parameters = new HashMap<>();
            for (String name : nodeJson.keySet()) {
                if (name.equals("!")) {
                    continue;
                }

                PropertyMetaModel propertyMetaModel = nodeMetaModel.getAllPropertyMetaModels().stream()
                        .filter(mm -> mm.getName().equals(name))
                        .findFirst().orElseThrow(() -> new IllegalStateException("Unknown property: " + nodeMetaModel.getQualifiedClassName() + "." + name));

                if (propertyMetaModel.isNodeList()) {
                    JsonArray nodeListJson = nodeJson.getJsonArray(name);
                    parameters.put(name, deserializeNodeList(nodeListJson));
                } else if (propertyMetaModel.isEnumSet()) {
                    JsonArray enumSetJson = nodeJson.getJsonArray(name);
                    parameters.put(name, deserializeEnumSet(enumSetJson));
                } else if (propertyMetaModel.isNode()) {
                    parameters.put(name, deserializeObject(nodeJson.getJsonObject(name)));
                } else {
                    Class<?> type = propertyMetaModel.getType();
                    if (type == String.class) {
                        parameters.put(name, nodeJson.getString(name));
                    } else if (type == boolean.class) {
                        parameters.put(name, Boolean.parseBoolean(nodeJson.getString(name)));
                    } else {
                        throw new IllegalStateException("Don't know how to convert: " + type);
                    }
                }
            }

            return nodeMetaModel.construct(parameters);
        } catch (ClassNotFoundException e) {
            throw new RuntimeException(e);
        }
    }

    private EnumSet<?> deserializeEnumSet(JsonArray enumSetJson) {
        return enumSetJson.stream().map(v -> (JsonString) v).map(s -> Modifier.valueOf(s.getString())).collect(Collectors.toCollection(() -> EnumSet.noneOf(Modifier.class)));
    }

    private NodeList<?> deserializeNodeList(JsonArray nodeListJson) {
        return nodeListJson.stream().map(nodeJson -> deserializeObject((JsonObject) nodeJson)).collect(toNodeList());
    }
}
