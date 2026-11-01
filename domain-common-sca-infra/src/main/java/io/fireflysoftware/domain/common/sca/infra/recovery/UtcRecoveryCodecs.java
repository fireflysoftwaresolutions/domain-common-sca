/*
 * Copyright 2025 Firefly Software Foundation
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */


package io.fireflysoftware.domain.common.sca.infra.recovery;

import com.fasterxml.jackson.core.*;
import com.fasterxml.jackson.databind.*;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import org.springframework.http.codec.json.Jackson2JsonDecoder;
import org.springframework.http.codec.json.Jackson2JsonEncoder;
import org.springframework.web.reactive.function.client.ExchangeStrategies;
import java.io.IOException;
import java.time.*;

/** Original SDK LocalDateTime fields represent Core Instant schemas on this dedicated recovery transport. */
public final class UtcRecoveryCodecs {
    private UtcRecoveryCodecs(){ }
    public static ObjectMapper mapper(){
        JavaTimeModule time=new JavaTimeModule();
        time.addSerializer(LocalDateTime.class,new JsonSerializer<>(){
            @Override public void serialize(LocalDateTime value,JsonGenerator output,SerializerProvider provider)throws IOException{
                output.writeString(value.toInstant(ZoneOffset.UTC).toString());
            }
        });
        time.addDeserializer(LocalDateTime.class,new JsonDeserializer<>(){
            @Override public LocalDateTime deserialize(JsonParser input,DeserializationContext context)throws IOException{
                try{return LocalDateTime.ofInstant(Instant.parse(input.getValueAsString()),ZoneOffset.UTC);}
                catch(RuntimeException failure){throw context.weirdStringException("[redacted]",LocalDateTime.class,"RFC3339 recovery instant required");}
            }
        });
        return new ObjectMapper().registerModule(time).disable(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS);
    }
    public static ExchangeStrategies strategies(){
        var mapper=mapper();
        return ExchangeStrategies.builder().codecs(codecs->{codecs.defaultCodecs().maxInMemorySize(65536);
            codecs.defaultCodecs().jackson2JsonEncoder(new Jackson2JsonEncoder(mapper));
            codecs.defaultCodecs().jackson2JsonDecoder(new Jackson2JsonDecoder(mapper));}).build();
    }
}
