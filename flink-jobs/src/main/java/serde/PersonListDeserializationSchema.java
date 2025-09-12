package serde;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.apache.flink.api.common.serialization.DeserializationSchema;
import org.apache.flink.api.common.typeinfo.TypeInformation;
import java.io.IOException;
import java.util.List;

public class PersonListDeserializationSchema implements DeserializationSchema<List<Person>> {

    ObjectMapper objectMapper = new ObjectMapper();
    @Override
    public List<Person> deserialize(byte[] bytes) throws IOException {
        return objectMapper.readValue(bytes, new TypeReference<List<Person>>() {});
    }

    @Override
    public boolean isEndOfStream(List<Person> people) {
        return false;
    }

    @Override
    public TypeInformation<List<Person>> getProducedType() {
        return null;
    }
}
