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
package zipkin2.elasticsearch.internal.client;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;

public class Aggregation {
  transient final String field;

  AggTerms terms;
  Map<String, String> min;
  Map<String, Aggregation> aggs;

  Aggregation(String field) {
    this.field = field;
  }

  public static Aggregation terms(String field, int size) {
    Aggregation result = new Aggregation(field);
    result.terms = new AggTerms(field, size);
    return result;
  }

  public static Aggregation terms(String field, int size, String execution_hint) {
    Aggregation result = new Aggregation(field);
    result.terms = new AggTerms(field, size, execution_hint);
    return result;
  }

  public Aggregation orderBy(String subAgg, String direction) {
    terms.order(subAgg, direction);
    return this;
  }

  public static Aggregation min(String field) {
    Aggregation result = new Aggregation(field);
    result.min = Collections.singletonMap("field", field);
    return result;
  }

  public AggTerms getTerms() {
    return terms;
  }

  public Map<String, String> getMin() {
    return min;
  }

  public Map<String, Aggregation> getAggs() {
    return aggs;
  }

  static class AggTerms {
    AggTerms(String field, int size) {
      this.field = field;
      this.size = size;
    }

    AggTerms(String field, int size, String execution_hint) {
      this(field, size);
      this.execution_hint = execution_hint;
    }

    final String field;
    final int size;
    String execution_hint;
    Map<String, String> order;

    void order(String agg, String direction) {
      order = Collections.singletonMap(agg, direction);
    }

    public String getField() {
      return field;
    }

    public int getSize() {
      return size;
    }

    public String getExecution_hint() {
      return execution_hint;
    }

    public Map<String, String> getOrder() {
      return order;
    }
  }

  public Aggregation addSubAggregation(Aggregation agg) {
    if (aggs == null) aggs = new LinkedHashMap<>();
    aggs.put(agg.field, agg);
    return this;
  }
}
