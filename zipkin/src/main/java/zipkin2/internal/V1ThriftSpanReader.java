/*
 * Copyright 2015-2019 The OpenZipkin Authors
 *
 * Licensed under the Apache License, Version 2.0 (the "License"); you may not use this file except
 * in compliance with the License. You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software distributed under the License
 * is distributed on an "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express
 * or implied. See the License for the specific language governing permissions and limitations under
 * the License.
 */
package zipkin2.internal;

import zipkin2.Endpoint;
import zipkin2.v1.V1Span;

import static zipkin2.internal.JsonCodec.UTF_8;
import static zipkin2.internal.ThriftCodec.readListLength;
import static zipkin2.internal.ThriftCodec.skip;
import static zipkin2.internal.ThriftField.TYPE_I32;
import static zipkin2.internal.ThriftField.TYPE_I64;
import static zipkin2.internal.ThriftField.TYPE_STOP;
import static zipkin2.internal.ThriftField.TYPE_STRING;
import static zipkin2.internal.ThriftField.TYPE_STRUCT;
import static zipkin2.internal.V1ThriftSpanWriter.ANNOTATIONS;
import static zipkin2.internal.V1ThriftSpanWriter.BINARY_ANNOTATIONS;
import static zipkin2.internal.V1ThriftSpanWriter.DEBUG;
import static zipkin2.internal.V1ThriftSpanWriter.DURATION;
import static zipkin2.internal.V1ThriftSpanWriter.ID;
import static zipkin2.internal.V1ThriftSpanWriter.NAME;
import static zipkin2.internal.V1ThriftSpanWriter.PARENT_ID;
import static zipkin2.internal.V1ThriftSpanWriter.TIMESTAMP;
import static zipkin2.internal.V1ThriftSpanWriter.TRACE_ID;
import static zipkin2.internal.V1ThriftSpanWriter.TRACE_ID_HIGH;
import java.nio.charset.Charset;
import java.nio.ByteBuffer;
import java.util.Arrays;

public final class V1ThriftSpanReader {
  static final String ONE = Character.toString((char) 1);
  static final String ZERO = Character.toString((char) 0);


  public static V1ThriftSpanReader create() {
    return new V1ThriftSpanReader();
  }

  V1Span.Builder builder = V1Span.newBuilder();

  public V1Span read(ReadBuffer buffer) {
    if (builder == null) {
      builder = V1Span.newBuilder();
    } else {
      builder.clear();
    }

    ThriftField thriftField;

    while (true) {
      thriftField = ThriftField.read(buffer);
      if (thriftField.type == TYPE_STOP) break;

      if (thriftField.isEqualTo(TRACE_ID_HIGH)) {
        builder.traceIdHigh(buffer.readLong());
      } else if (thriftField.isEqualTo(TRACE_ID)) {
        builder.traceId(buffer.readLong());
      } else if (thriftField.isEqualTo(NAME)) {
        builder.name(buffer.readUtf8(buffer.readInt()));
      } else if (thriftField.isEqualTo(ID)) {
        builder.id(buffer.readLong());
      } else if (thriftField.isEqualTo(PARENT_ID)) {
        builder.parentId(buffer.readLong());
      } else if (thriftField.isEqualTo(ANNOTATIONS)) {
        int length = readListLength(buffer);
        for (int i = 0; i < length; i++) {
          AnnotationReader.read(buffer, builder);
        }
      } else if (thriftField.isEqualTo(BINARY_ANNOTATIONS)) {
        int length = readListLength(buffer);
        for (int i = 0; i < length; i++) {
          BinaryAnnotationReader.read(buffer, builder);
        }
      } else if (thriftField.isEqualTo(DEBUG)) {
        builder.debug(buffer.readByte() == 1);
      } else if (thriftField.isEqualTo(TIMESTAMP)) {
        builder.timestamp(buffer.readLong());
      } else if (thriftField.isEqualTo(DURATION)) {
        builder.duration(buffer.readLong());
      } else {
        skip(buffer, thriftField.type);
      }
    }

    return builder.build();
  }

  static final class AnnotationReader {
    static final ThriftField TIMESTAMP = new ThriftField(TYPE_I64, 1);
    static final ThriftField VALUE = new ThriftField(TYPE_STRING, 2);
    static final ThriftField ENDPOINT = new ThriftField(TYPE_STRUCT, 3);

    static void read(ReadBuffer buffer, V1Span.Builder builder) {
      long timestamp = 0;
      String value = null;
      Endpoint endpoint = null;

      ThriftField thriftField;
      while (true) {
        thriftField = ThriftField.read(buffer);
        if (thriftField.type == TYPE_STOP) break;

        if (thriftField.isEqualTo(TIMESTAMP)) {
          timestamp = buffer.readLong();
        } else if (thriftField.isEqualTo(VALUE)) {
          value = buffer.readUtf8(buffer.readInt());
        } else if (thriftField.isEqualTo(ENDPOINT)) {
          endpoint = ThriftEndpointCodec.read(buffer);
        } else {
          skip(buffer, thriftField.type);
        }
      }

      if (timestamp == 0 || value == null) return;
      builder.addAnnotation(timestamp, value, endpoint);
    }
  }

  static final class BinaryAnnotationReader {

    public static final Charset UTF_8 = Charset.forName("UTF-8");
    static final ThriftField KEY = new ThriftField(TYPE_STRING, 1);
    static final ThriftField VALUE = new ThriftField(TYPE_STRING, 2);
    static final ThriftField TYPE = new ThriftField(TYPE_I32, 3);
    static final ThriftField ENDPOINT = new ThriftField(TYPE_STRUCT, 4);

    static void read(ReadBuffer buffer, V1Span.Builder builder) {
      String key = null;
      byte[] value = null;
      Endpoint endpoint = null;
      boolean isBoolean = false;
      boolean isString = false;
      boolean isI16 = false;
      boolean isI32 = false;
      boolean isI64 = false;
      boolean isDouble = false;
      boolean isByte = false;

      while (true) {
        ThriftField thriftField = ThriftField.read(buffer);
        if (thriftField.type == TYPE_STOP) break;
        if (thriftField.isEqualTo(KEY)) {
          key = buffer.readUtf8(buffer.readInt());
        } else if (thriftField.isEqualTo(VALUE)) {
          value = buffer.readBytes(buffer.readInt());
        } else if (thriftField.isEqualTo(TYPE)) {
          int valueType = buffer.readInt();
          switch (valueType) {
            case 0:
              isBoolean = true;
              break;
            case 6:
              isString = true;
              break;
            case 1:
              isByte = true;
              break;
            case 2:
              isI16 = true;
              break;
            case 3:
              isI32 = true;
              break;
            case 4:
              isI64 = true;
              break;
            case 5:
              isDouble = true;
              break;
          }
        } else if (thriftField.isEqualTo(ENDPOINT)) {
          endpoint = ThriftEndpointCodec.read(buffer);
        } else {
          skip(buffer, thriftField.type);
        }
      }
      if (key == null || value == null) return;
      if (isString) {
        String stringValue = new String(value, UTF_8);
        builder.addBinaryAnnotation(key, stringValue, endpoint);
      } else if (isI16){
        Short I16Value = ByteBuffer.wrap(value).getShort();
        builder.addBinaryAnnotation(key, I16Value.toString(), endpoint);
      } else if (isI32){
        Integer I32Value = ByteBuffer.wrap(value).getInt();
        builder.addBinaryAnnotation(key, I32Value.toString(), endpoint);
      } else if (isI64){
        Long I64Value = ByteBuffer.wrap(value).getLong();
        builder.addBinaryAnnotation(key, I64Value.toString(), endpoint);
      } else if (isDouble){
        Double doubleValue = ByteBuffer.wrap(value).getDouble();
        builder.addBinaryAnnotation(key, doubleValue.toString(), endpoint);
      }
      else if (isBoolean && ONE.equals(new String(value, UTF_8)) && endpoint != null) {
        if (key.equals("sa") || key.equals("ca") || key.equals("ma")) {
          builder.addBinaryAnnotation(key, endpoint);
        }
      }
      else if (isBoolean){
        String stringValue = new String(value, UTF_8);
        String stringBooleanValue = "";
        if (ONE.equals(stringValue)){
          stringBooleanValue = "true";
        }
        else if (ZERO.equals(stringValue)) {
          stringBooleanValue = "false";
        }
        else if (stringValue.equals("0x01")){
          stringBooleanValue = "true";
        }
        builder.addBinaryAnnotation(key, stringBooleanValue, endpoint);
      }
    }
  }

  V1ThriftSpanReader() {
  }
}
