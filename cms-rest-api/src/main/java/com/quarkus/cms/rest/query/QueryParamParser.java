package com.quarkus.cms.rest.query;

import com.quarkus.cms.core.query.CmsQuery;
import com.quarkus.cms.core.query.FilterNode;
import com.quarkus.cms.core.query.PopulateNode;
import com.quarkus.cms.core.query.SortOrder;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Parses Strapi v5-style query parameters into a {@link CmsQuery} object.
 *
 * <p>Handles the Strapi Content API query syntax for filters, sorting, pagination, population, and
 * field selection. Supports both bracket-notation and dot-notation parameter formats.
 *
 * <h3>Parameter format examples:</h3>
 *
 * <ul>
 *   <li>{@code filters[title][$eq]=Hello}
 *   <li>{@code filters[$and][0][title][$eq]=Hello&filters[$and][1][status][$eq]=draft}
 *   <li>{@code sort[0]=title:asc&sort[1]=createdAt:desc}
 *   <li>{@code sort=title:asc}
 *   <li>{@code populate=*}
 *   <li>{@code populate[0]=author&populate[1]=category}
 *   <li>{@code fields[0]=title&fields[1]=content}
 *   <li>{@code locale=en}
 *   <li>{@code pagination[page]=1&pagination[pageSize]=10}
 *   <li>{@code publicationState=preview|live}
 * </ul>
 */
public final class QueryParamParser {

  private static final Pattern SORT_PATTERN = Pattern.compile("^([\\w.]+):(asc|desc)$",
      Pattern.CASE_INSENSITIVE);

  private static final Set<String> PAGINATION_KEYS = Set.of("page", "pageSize", "start", "limit");

  private QueryParamParser() {}

  /**
   * Parses the full set of query parameters into a CmsQuery.
   *
   * @param contentType the content type to query
   * @param params raw query parameter map from the HTTP request
   * @return populated CmsQuery
   */
  public static CmsQuery parse(String contentType, Map<String, String> params) {
    CmsQuery query = new CmsQuery(contentType);

    if (params == null || params.isEmpty()) {
      return query;
    }

    // Normalize params to nested map for bracket notation parsing
    Map<String, Object> nested = new HashMap<>();
    for (Map.Entry<String, String> entry : params.entrySet()) {
      List<String> values = params.entrySet().stream()
          .filter(e -> e.getKey().equals(entry.getKey()))
          .map(Map.Entry::getValue)
          .toList();

      if (values.size() > 1) {
        setNestedKey(nested, entry.getKey(), values);
      } else {
        setNestedKey(nested, entry.getKey(), entry.getValue());
      }
    }

    // Parse each top-level parameter
    for (Map.Entry<String, Object> entry : nested.entrySet()) {
      String key = entry.getKey();
      Object value = entry.getValue();

      switch (key) {
        case "filters" -> query.setFilter(parseFilters(toMapOrNull(value)));
        case "sort" -> query.setSort(parseSort(value));
        case "populate" -> {
          query.setPopulate(parsePopulate(value));
        }
        case "fields" -> {
          Set<String> fieldNames = parseFields(value);
          query.setFields(fieldNames);
        }
        case "locale" -> query.setLocale(value.toString());
        case "pagination" -> parsePagination(query, toMapOrNull(value));
        case "publicationState" -> {
          String ps = value.toString();
          if ("live".equals(ps)) {
            query.setStatus("published");
          } else if ("preview".equals(ps)) {
            // Both draft and published
          }
        }
        default -> {
          // Unknown parameter; ignore
        }
      }
    }

    return query;
  }

  /** Parses the filters parameter tree. */
  @SuppressWarnings("unchecked")
  static FilterNode parseFilters(Map<String, Object> filters) {
    if (filters == null || filters.isEmpty()) {
      return null;
    }

    List<FilterNode> conditions = new ArrayList<>();

    for (Map.Entry<String, Object> entry : filters.entrySet()) {
      String key = entry.getKey();
      Object value = entry.getValue();

      if (key.startsWith("$and") || key.startsWith("$or")) {
        FilterNode.Logic logic = key.startsWith("$and") ? FilterNode.Logic.AND
            : FilterNode.Logic.OR;
        List<FilterNode> children = new ArrayList<>();

        if (value instanceof List<?> list) {
          for (Object item : list) {
            if (item instanceof Map) {
              FilterNode child = parseFilters((Map<String, Object>) item);
              if (child != null) {
                children.add(child);
              }
            }
          }
        } else if (value instanceof Map<?, ?> mapValue) {
          // Check if this is a numeric-indexed map (array representation)
          if (isNumericKeyedMap(mapValue)) {
            // Treat as ordered array, sorted by numeric key
            ((Map<String, Object>) mapValue).entrySet().stream()
                .sorted(Map.Entry.comparingByKey(
                    java.util.Comparator.comparingInt(k -> Integer.parseInt(k.toString()))))
                .forEach(e -> {
                  if (e.getValue() instanceof Map) {
                    FilterNode child = parseFilters((Map<String, Object>) e.getValue());
                    if (child != null) {
                      children.add(child);
                    }
                  }
                });
          } else {
            // Single grouped filter
            FilterNode child = parseFilters((Map<String, Object>) value);
            if (child != null) {
              children.add(child);
            }
          }
        }

        if (!children.isEmpty()) {
          conditions.add(new FilterNode.Group(logic, children));
        }
      } else if (isNumericKeyedMap(value)) {
        // Numeric-keyed map at non-logical level: treat each entry as a nested filter
        List<FilterNode> children = new ArrayList<>();
        ((Map<String, Object>) value).entrySet().stream()
            .sorted(Map.Entry.comparingByKey(
                java.util.Comparator.comparingInt(k -> Integer.parseInt(k.toString()))))
            .forEach(e -> {
              if (e.getValue() instanceof Map) {
                FilterNode child = parseFilters((Map<String, Object>) e.getValue());
                if (child != null) {
                  children.add(child);
                }
              }
            });
        if (!children.isEmpty()) {
          conditions.addAll(children);
        }
      } else if (value instanceof Map<?, ?> operatorMap) {
        // Leaf filter: filters[title][$eq]=Hello
        for (Map.Entry<?, ?> opEntry : operatorMap.entrySet()) {
          String opKey = opEntry.getKey().toString();
          FilterNode.Operator operator;
          try {
            operator = FilterNode.Operator.fromCode(opKey);
          } catch (IllegalArgumentException e) {
            continue;
          }
          conditions.add(
              new FilterNode.Leaf(key, operator, opEntry.getValue()));
        }
      } else {
        // Direct equality: filters[title]=Hello → eq
        conditions.add(
            new FilterNode.Leaf(key, FilterNode.Operator.EQ, value));
      }
    }

    if (conditions.isEmpty()) {
      return null;
    }
    if (conditions.size() == 1) {
      return conditions.get(0);
    }
    return new FilterNode.Group(FilterNode.Logic.AND, conditions);
  }

  /**
   * Parses the fields parameter into a set of field names.
   *
   * <p>Supports Strapi v5 formats:
   * <ul>
   *   <li>{@code fields=title} — single field</li>
   *   <li>{@code fields[0]=title&fields[1]=content} — multiple fields</li>
   * </ul>
   */
  @SuppressWarnings("unchecked")
  static Set<String> parseFields(Object fieldsValue) {
    Set<String> fieldNames = new HashSet<>();
    if (fieldsValue == null) return fieldNames;

    if (fieldsValue instanceof String str) {
      fieldNames.add(str);
      return fieldNames;
    }

    if (fieldsValue instanceof List<?> list) {
      for (Object item : list) {
        if (item != null) {
          fieldNames.add(item.toString());
        }
      }
      return fieldNames;
    }

    if (fieldsValue instanceof Map<?, ?> map) {
      for (Object val : map.values()) {
        if (val != null) {
          fieldNames.add(val.toString());
        }
      }
      return fieldNames;
    }

    return fieldNames;
  }

  /**
   * Parses the sort parameter(s). */
  static List<SortOrder> parseSort(Object sortValue) {
    List<SortOrder> orders = new ArrayList<>();

    if (sortValue == null) {
      return orders;
    }

    if (sortValue instanceof List<?> list) {
      for (Object item : list) {
        SortOrder order = parseSingleSort(item.toString());
        if (order != null) {
          orders.add(order);
        }
      }
    } else if (sortValue instanceof Map<?, ?> map) {
      for (Object item : map.values()) {
        SortOrder order = parseSingleSort(item.toString());
        if (order != null) {
          orders.add(order);
        }
      }
    } else {
      SortOrder order = parseSingleSort(sortValue.toString());
      if (order != null) {
        orders.add(order);
      }
    }

    return orders;
  }

  private static SortOrder parseSingleSort(String sortString) {
    if (sortString == null || sortString.isBlank()) {
      return null;
    }

    Matcher matcher = SORT_PATTERN.matcher(sortString.trim());
    if (matcher.matches()) {
      String field = matcher.group(1);
      SortOrder.Direction direction =
          "desc".equalsIgnoreCase(matcher.group(2))
              ? SortOrder.Direction.DESC
              : SortOrder.Direction.ASC;
      return new SortOrder(field, direction);
    }

    // Default: ascending
    return new SortOrder(sortString.trim(), SortOrder.Direction.ASC);
  }

  /** Parses pagination parameters. */
  static void parsePagination(CmsQuery query, Map<String, Object> pagination) {
    if (pagination == null) {
      return;
    }

    if (pagination.containsKey("page")) {
      query.setPage(Integer.parseInt(pagination.get("page").toString()));
    }
    if (pagination.containsKey("pageSize")) {
      query.setPageSize(Integer.parseInt(pagination.get("pageSize").toString()));
    }
    if (pagination.containsKey("start") && pagination.containsKey("limit")) {
      int start = Integer.parseInt(pagination.get("start").toString());
      int limit = Integer.parseInt(pagination.get("limit").toString());
      query.setPage((start / limit) + 1);
      query.setPageSize(limit);
    }
  }

  /**
   * Parses the populate parameter into a list of {@link PopulateNode}.
   *
   * <p>Supports Strapi v5 formats:
   * <ul>
   *   <li>{@code populate=*} — populate all relation fields</li>
   *   <li>{@code populate=fieldName} — populate a single field</li>
   *   <li>{@code populate[0]=author&populate[1]=category} — multiple fields</li>
   *   <li>{@code populate[author][populate][0]=avatar} — nested population</li>
   * </ul>
   */
  @SuppressWarnings("unchecked")
  public static List<PopulateNode> parsePopulate(Object populateValue) {
    List<PopulateNode> nodes = new ArrayList<>();
    if (populateValue == null) return nodes;

    // String value: populate=* or populate=fieldName
    if (populateValue instanceof String str) {
      if ("*".equals(str)) {
        PopulateNode all = new PopulateNode();
        all.setPopulateAll(true);
        nodes.add(all);
      } else {
        nodes.add(new PopulateNode(str));
      }
      return nodes;
    }

    // List value: populate[0]=author&populate[1]=category
    if (populateValue instanceof List<?> list) {
      for (Object item : list) {
        if (item instanceof String str) {
          if ("*".equals(str)) {
            PopulateNode all = new PopulateNode();
            all.setPopulateAll(true);
            nodes.add(all);
          } else {
            nodes.add(new PopulateNode(str));
          }
        }
      }
      return nodes;
    }

    // Map value: object-style populate, e.g. populate[author][populate][0]=avatar
    if (populateValue instanceof Map<?, ?> map) {
      for (Map.Entry<?, ?> entry : map.entrySet()) {
        String key = entry.getKey().toString();
        Object val = entry.getValue();

        // Numeric-keyed map: treated as ordered array
        if (key.matches("\\d+") && val instanceof Map) {
          // Nested populate with field name inside
          PopulateNode node = parseNestedPopulateNode((Map<String, Object>) val);
          if (node != null) {
            nodes.add(node);
          }
        } else if (val instanceof Map) {
          // Deep populate: populate[fieldName][populate][0]=nestedField
          // Also supports populate[fieldName][fields][0]=title and populate[fieldName][depth]=3
          PopulateNode node = new PopulateNode(key);
          Object nestedPop = ((Map<String, Object>) val).get("populate");
          if (nestedPop != null) {
            List<PopulateNode> children = parsePopulate(nestedPop);
            if (!children.isEmpty()) {
              node.setChildren(children);
            }
          }
          // Parse fields filter for this populate level
          Object fieldsObj = ((Map<String, Object>) val).get("fields");
          if (fieldsObj != null) {
            Set<String> fieldSet = parseFields(fieldsObj);
            if (!fieldSet.isEmpty()) {
              node.setFields(fieldSet);
            }
          }
          // Parse depth override for this populate level
          Object depthObj = ((Map<String, Object>) val).get("depth");
          if (depthObj instanceof Number num) {
            node.setDepthOverride(num.intValue());
          } else if (depthObj instanceof String str) {
            try {
              node.setDepthOverride(Integer.parseInt(str));
            } catch (NumberFormatException ignored) {
              // Ignore invalid depth
            }
          }
          nodes.add(node);
        } else if (val instanceof String str) {
          if ("*".equals(str)) {
            PopulateNode all = new PopulateNode();
            all.setPopulateAll(true);
            nodes.add(all);
          } else {
            nodes.add(new PopulateNode(str));
          }
        }
      }
      return nodes;
    }

    return nodes;
  }

  /**
   * Parses a nested populate node from a map that contains a fieldName and optionally nested
   * populate spec. Handles the format produced by bracket-notation params like
   * {@code populate[0][field]=author&populate[0][populate][0]=avatar}.
   */
  @SuppressWarnings("unchecked")
  private static PopulateNode parseNestedPopulateNode(Map<String, Object> map) {
    String fieldName = null;
    Object nestedPop = null;
    Object fieldsObj = null;
    Object depthObj = null;

    for (Map.Entry<String, Object> entry : map.entrySet()) {
      if ("field".equals(entry.getKey())) {
        if (entry.getValue() instanceof String str) {
          fieldName = str;
        }
      } else if ("populate".equals(entry.getKey())) {
        nestedPop = entry.getValue();
      } else if ("fields".equals(entry.getKey())) {
        fieldsObj = entry.getValue();
      } else if ("depth".equals(entry.getKey())) {
        depthObj = entry.getValue();
      }
    }

    // If no explicit 'field' key, look for the first string value as the field name
    if (fieldName == null) {
      for (Map.Entry<String, Object> entry : map.entrySet()) {
        if (!"populate".equals(entry.getKey()) && !"fields".equals(entry.getKey())
                && !"depth".equals(entry.getKey()) && entry.getValue() instanceof String str) {
          fieldName = str;
          break;
        }
      }
    }

    if (fieldName == null) return null;

    PopulateNode node = new PopulateNode(fieldName);
    if (nestedPop != null) {
      List<PopulateNode> children = parsePopulate(nestedPop);
      if (!children.isEmpty()) {
        node.setChildren(children);
      }
    }
    if (fieldsObj != null) {
      Set<String> fieldSet = parseFields(fieldsObj);
      if (!fieldSet.isEmpty()) {
        node.setFields(fieldSet);
      }
    }
    if (depthObj instanceof Number num) {
      node.setDepthOverride(num.intValue());
    } else if (depthObj instanceof String str) {
      try {
        node.setDepthOverride(Integer.parseInt(str));
      } catch (NumberFormatException ignored) {}
    }
    return node;
  }

  /**
   * Sets a nested key into a map from bracket-notation like {@code filters[title][$eq]}.
   */
  @SuppressWarnings("unchecked")
  static void setNestedKey(Map<String, Object> map, String rawKey, Object value) {
    String[] parts = rawKey.split("\\[");
    String topKey = parts[0];

    if (parts.length == 1) {
      // Simple key: "locale=en"
      map.put(topKey, value);
      return;
    }

    // Get or create the nested map
    Map<String, Object> current = (Map<String, Object>) map.computeIfAbsent(
        topKey, k -> new HashMap<>());

    // Handle array indices
    for (int i = 1; i < parts.length - 1; i++) {
      String part = parts[i].replace("]", "");
      String nextKey;

      if (part.matches("\\d+")) {
        // Array index: convert current to list if needed
        int idx = Integer.parseInt(part);
        if (current.containsKey(part)) {
          Object existing = current.get(part);
          if (existing instanceof Map) {
            current = (Map<String, Object>) existing;
          } else {
            current = new HashMap<>();
            current.put(part, existing);
          }
        } else {
          Map<String, Object> next = (Map<String, Object>) current.computeIfAbsent(
              part, k -> new HashMap<>());
          current = next;
        }
        continue;
      }

      nextKey = part;
      current = (Map<String, Object>) current.computeIfAbsent(nextKey,
          k -> new HashMap<>());
    }

    // Set the final value
    String lastPart = parts[parts.length - 1].replace("]", "");
    current.put(lastPart, value);
  }

  @SuppressWarnings("unchecked")
  private static Map<String, Object> toMapOrNull(Object value) {
    if (value instanceof Map) {
      return (Map<String, Object>) value;
    }
    return null;
  }

  /**
   * Checks whether a map's keys are all numeric strings, indicating it represents an
   * ordered array (common in HTTP query parameter bracket notation).
   */
  private static boolean isNumericKeyedMap(Object value) {
    if (!(value instanceof Map<?, ?> map)) {
      return false;
    }
    if (map.isEmpty()) {
      return false;
    }
    return map.keySet().stream().allMatch(k -> k.toString().matches("\\d+"));
  }
}
