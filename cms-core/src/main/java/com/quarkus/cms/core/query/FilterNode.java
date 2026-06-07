package com.quarkus.cms.core.query;

import java.util.Collections;
import java.util.List;

/**
 * Represents a node in a filter tree.
 *
 * <p>A filter is either a leaf (field + operator + value) or a logical-grouping node (AND/OR)
 * containing child filters. This mirrors Strapi's filter syntax.
 */
public abstract class FilterNode {

  private FilterNode() {}

  /** Leaf filter: field operator value. */
  public static class Leaf extends FilterNode {
    private final String field;
    private final Operator operator;
    private final Object value;

    public Leaf(String field, Operator operator, Object value) {
      this.field = field;
      this.operator = operator;
      this.value = value;
    }

    public String getField() {
      return field;
    }

    public Operator getOperator() {
      return operator;
    }

    public Object getValue() {
      return value;
    }

    @Override
    public String toString() {
      return field + " " + operator + " " + value;
    }
  }

  /** Logical grouping: AND or OR of child filters. */
  public static class Group extends FilterNode {
    private final Logic logic;
    private final List<FilterNode> children;

    public Group(Logic logic, List<FilterNode> children) {
      this.logic = logic;
      this.children = Collections.unmodifiableList(children);
    }

    public Logic getLogic() {
      return logic;
    }

    public List<FilterNode> getChildren() {
      return children;
    }

    @Override
    public String toString() {
      return logic + "(" + children + ")";
    }
  }

  /** Filter operators. */
  public enum Operator {
    EQ("$eq"),
    NE("$ne"),
    GT("$gt"),
    GTE("$gte"),
    LT("$lt"),
    LTE("$lte"),
    CONTAINS("$contains"),
    NOT_CONTAINS("$notContains"),
    IN("$in"),
    NOT_IN("$nin"),
    NULL("$null"),
    NOT_NULL("$notNull"),
    STARTS_WITH("$startsWith"),
    ENDS_WITH("$endsWith");

    private final String code;

    Operator(String code) {
      this.code = code;
    }

    public String getCode() {
      return code;
    }

    public static Operator fromCode(String code) {
      for (Operator op : values()) {
        if (op.code.equals(code)) {
          return op;
        }
      }
      throw new IllegalArgumentException("Unknown filter operator: " + code);
    }
  }

  /** Logical combinators. */
  public enum Logic {
    AND,
    OR
  }

  /** Creates a leaf filter. */
  public static Leaf leaf(String field, Operator operator, Object value) {
    return new Leaf(field, operator, value);
  }

  /** Creates a leaf filter with eq operator. */
  public static Leaf eq(String field, Object value) {
    return new Leaf(field, Operator.EQ, value);
  }

  /** Creates an AND group. */
  public static Group and(List<FilterNode> children) {
    return new Group(Logic.AND, children);
  }

  /** Creates an AND group (varargs). */
  public static Group and(FilterNode... children) {
    return new Group(Logic.AND, List.of(children));
  }

  /** Creates an OR group. */
  public static Group or(List<FilterNode> children) {
    return new Group(Logic.OR, children);
  }

  /** Creates an OR group (varargs). */
  public static Group or(FilterNode... children) {
    return new Group(Logic.OR, List.of(children));
  }
}
