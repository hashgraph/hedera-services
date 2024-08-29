package com.hedera.node.app.statedumpers;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.ObjectWriter;

import java.io.File;
import java.io.IOException;
import java.util.Map;

public class JsonWriter {
    public void write(Map<?, ?> map, String filePath) {
        ObjectMapper mapper = new ObjectMapper();
        ObjectWriter writer = mapper.writerWithDefaultPrettyPrinter();

        try {
            writer.writeValue(new File(filePath), map);
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    public void write(Object[] map, String filePath) {
        ObjectMapper mapper = new ObjectMapper();
        ObjectWriter writer = mapper.writerWithDefaultPrettyPrinter();

        try {
            writer.writeValue(new File(filePath), map);
        } catch (IOException e) {
            e.printStackTrace();
        }
    }
}
