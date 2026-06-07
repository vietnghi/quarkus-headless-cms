package com.quarkus.cms.core.query;

/** Sort order for a query field. */
public class SortOrder {

  private final String field;
  private final Direction direction;

  public SortOrder(String field, Direction direction) {
    this.field = field;
    this.direction = direction;
  }

  public String getField() {
    return field;
  }

  public Direction getDirection() {
    return direction;
  }

  public enum Direction {
    ASC,
    DESC
  }
}
