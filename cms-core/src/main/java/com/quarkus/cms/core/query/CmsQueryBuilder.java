package com.quarkus.cms.core.query;

import com.quarkus.cms.core.domain.CmsEntry;

import io.quarkus.hibernate.orm.panache.PanacheQuery;
import io.quarkus.panache.common.Parameters;
import io.quarkus.panache.common.Sort;

import java.util.List;

/**
 * Translates a {@link CmsQuery} into a Panache-compatible HQL query with JSONB field support for
 * dynamic content-type schemas.
 *
 * <p>Standard columns (contentType, locale, status, createdAt, updatedAt, publishedAt) are queried
 * as regular HQL predicates. Dynamic fields stored in the {@code data} JSONB column are accessed
 * via Hibernate's {@code FUNCTION('jsonb_extract_path_text', ...)} call, which maps to the
 * PostgreSQL {@code jsonb_extract_path_text()} function.
 */
public final class CmsQueryBuilder {

  private CmsQueryBuilder() {}

  /** Executes a count query matching the given parameters. */
  public static long count(CmsQuery query) {
    StringBuilder hql = new StringBuilder("1 = 1");
    Parameters params = new Parameters();
    int pc = buildWhereClause(query, hql, params, 0);

    return CmsEntry.count(hql.toString(), params);
  }

  /** Executes a list query with the given parameters. */
  @SuppressWarnings("unchecked")
  public static List<CmsEntry> list(CmsQuery query) {
    StringBuilder hql = new StringBuilder("1 = 1");
    Parameters params = new Parameters();
    int pc = buildWhereClause(query, hql, params, 0);

    // Append ORDER BY clause — standard columns use their name, JSONB data fields
    // use FUNCTION('jsonb_extract_path_text', data, 'field') so that dynamic content-type
    // fields work correctly.
    buildOrderByClause(query, hql);

    PanacheQuery<CmsEntry> pq = CmsEntry.find(hql.toString(), params);
    return pq.page(query.getPage() - 1, query.getPageSize()).list();
  }

  /**
   * Appends an ORDER BY clause to the HQL for the given query.
   *
   * <p>Standard entity columns (createdAt, updatedAt, etc.) are appended directly as column
   * references. Dynamic JSONB data fields are resolved via
   * {@code FUNCTION('jsonb_extract_path_text', data, 'field')} so that sorting works on
   * dynamic content-type schema fields.
   */
  static void buildOrderByClause(CmsQuery query, StringBuilder hql) {
    if (query.getSort().isEmpty()) {
      hql.append(" ORDER BY createdAt DESC");
      return;
    }

    hql.append(" ORDER BY ");
    boolean first = true;
    for (SortOrder order : query.getSort()) {
      if (!first) {
        hql.append(", ");
      }
      first = false;

      String field = order.getField();
      Sort.Direction dir =
          order.getDirection() == SortOrder.Direction.ASC
              ? Sort.Direction.Ascending
              : Sort.Direction.Descending;

      if (isStandardColumn(field)) {
        hql.append(field);
      } else {
        // JSONB data field — extract via DB-agnostic function for dynamic sort
        hql.append(JsonbFunctions.extractPathText(field));
      }
      hql.append(dir == Sort.Direction.Ascending ? " ASC" : " DESC");
    }
  }

  /** Builds the WHERE clause and populates parameters. Returns param count. */
  static int buildWhereClause(CmsQuery query, StringBuilder where, Parameters params, int pc) {
    // Content type (required)
    if (query.getContentType() != null) {
      if (!where.isEmpty()) {
        where.append(" AND ");
      }
      where.append("contentType = :ct");
      params.and("ct", query.getContentType());
      pc++;
    }

    // Locale
    if (query.getLocale() != null) {
      where.append(" AND locale = :loc");
      params.and("loc", query.getLocale());
      pc++;
    }

    // Status
    if (query.getStatus() != null) {
      where.append(" AND status = :st");
      params.and("st", query.getStatus());
      pc++;
    }

    // Dynamic filters on JSONB fields
    if (query.getFilter() != null) {
      pc = buildFilterClause(query.getFilter(), where, params, pc);
    }

    return pc;
  }

  /** Recursively builds filter clauses for the filter tree. */
  static int buildFilterClause(FilterNode node, StringBuilder where, Parameters params, int pc) {
    if (node instanceof FilterNode.Leaf leaf) {
      pc = appendLeafClause(leaf, where, params, pc);
    } else if (node instanceof FilterNode.Group group) {
      List<FilterNode> children = group.getChildren();
      if (children.isEmpty()) {
        return pc;
      }

      where.append(" AND (");
      String logic = group.getLogic() == FilterNode.Logic.AND ? " AND " : " OR ";

      for (int i = 0; i < children.size(); i++) {
        if (i > 0) {
          where.append(logic);
        }
        StringBuilder childWhere = new StringBuilder();
        pc = buildFilterClause(children.get(i), childWhere, params, pc);

        String childClause = childWhere.toString().trim();
        if (childClause.startsWith("AND ")) {
          childClause = childClause.substring(4);
        }
        where.append("(").append(childClause).append(")");
      }
      where.append(")");
    }

    return pc;
  }

  /** Appends a single leaf filter using JSONB path extraction. */
  private static int appendLeafClause(
      FilterNode.Leaf leaf, StringBuilder where, Parameters params, int pc) {
    String paramName = "p" + pc;
    String jsonPath = JsonbFunctions.extractPathText(leaf.getField());

    if (!where.isEmpty()) {
      where.append(" AND ");
    }

    switch (leaf.getOperator()) {
      case EQ:
        where.append(jsonPath).append(" = :").append(paramName);
        params.and(paramName, leaf.getValue());
        pc++;
        break;

      case NE:
        where
            .append("(")
            .append(jsonPath)
            .append(" <> :")
            .append(paramName)
            .append(" OR ")
            .append(jsonPath)
            .append(" IS NULL)");
        params.and(paramName, leaf.getValue());
        pc++;
        break;

      case CONTAINS:
        where.append(jsonPath).append(" LIKE :").append(paramName);
        params.and(paramName, "%" + leaf.getValue() + "%");
        pc++;
        break;

      case NOT_CONTAINS:
        where
            .append("(")
            .append(jsonPath)
            .append(" NOT LIKE :")
            .append(paramName)
            .append(" OR ")
            .append(jsonPath)
            .append(" IS NULL)");
        params.and(paramName, "%" + leaf.getValue() + "%");
        pc++;
        break;

      case STARTS_WITH:
        where.append(jsonPath).append(" LIKE :").append(paramName);
        params.and(paramName, leaf.getValue() + "%");
        pc++;
        break;

      case ENDS_WITH:
        where.append(jsonPath).append(" LIKE :").append(paramName);
        params.and(paramName, "%" + leaf.getValue());
        pc++;
        break;

      case GT:
        where.append("CAST(").append(jsonPath).append(" AS double) > :").append(paramName);
        params.and(paramName, leaf.getValue());
        pc++;
        break;

      case GTE:
        where.append("CAST(").append(jsonPath).append(" AS double) >= :").append(paramName);
        params.and(paramName, leaf.getValue());
        pc++;
        break;

      case LT:
        where.append("CAST(").append(jsonPath).append(" AS double) < :").append(paramName);
        params.and(paramName, leaf.getValue());
        pc++;
        break;

      case LTE:
        where.append("CAST(").append(jsonPath).append(" AS double) <= :").append(paramName);
        params.and(paramName, leaf.getValue());
        pc++;
        break;

      case IN:
        where.append(jsonPath).append(" IN (:").append(paramName).append(")");
        params.and(paramName, leaf.getValue());
        pc++;
        break;

      case NOT_IN:
        where
            .append("(")
            .append(jsonPath)
            .append(" NOT IN (:")
            .append(paramName)
            .append(") OR ")
            .append(jsonPath)
            .append(" IS NULL)");
        params.and(paramName, leaf.getValue());
        pc++;
        break;

      case NULL:
        where
            .append("(")
            .append(jsonPath)
            .append(" IS NULL OR ")
            .append(jsonPath)
            .append(" = 'null')");
        break;

      case NOT_NULL:
        where
            .append("(")
            .append(jsonPath)
            .append(" IS NOT NULL AND ")
            .append(jsonPath)
            .append(" <> 'null')");
        break;

      default:
        throw new IllegalArgumentException("Unsupported operator: " + leaf.getOperator());
    }

    return pc;
  }

  /** Builds a Panache Sort from the query's sort orders. */
  static Sort buildSort(CmsQuery query) {
    if (query.getSort().isEmpty()) {
      return Sort.by("createdAt", Sort.Direction.Descending);
    }

    String firstField = null;
    Sort.Direction firstDir = null;
    boolean first = true;

    for (SortOrder order : query.getSort()) {
      String field = order.getField();
      Sort.Direction dir =
          order.getDirection() == SortOrder.Direction.ASC
              ? Sort.Direction.Ascending
              : Sort.Direction.Descending;

      if (first) {
        firstField = field;
        firstDir = dir;
        first = false;
      }
    }

    // Build a Sort — for JSONB fields use the raw column expression
    // NOTE: Standard columns use their name; JSONB fields can't use
    // FUNCTION() expressions via Panache Sort, so we fall back to
    // the column name for basic sorts.
    if (query.getSort().size() == 1 && firstField != null) {
      return Sort.by(firstField, firstDir);
    }

    // Multi-column sort: chain with .and()
    Sort sort = null;
    for (SortOrder order : query.getSort()) {
      String field = order.getField();
      Sort.Direction dir =
          order.getDirection() == SortOrder.Direction.ASC
              ? Sort.Direction.Ascending
              : Sort.Direction.Descending;

      if (sort == null) {
        sort = Sort.by(field, dir);
      } else {
        sort = sort.and(field, dir);
      }
    }
    return sort;
  }

  /** Checks whether a field is a standard column (not stored in JSONB). */
  private static boolean isStandardColumn(String field) {
    return switch (field) {
      case "id",
          "documentId",
          "contentType",
          "locale",
          "status",
          "versionNumber",
          "createdAt",
          "updatedAt",
          "publishedAt",
          "createdById",
          "updatedById",
          "publishedById" ->
          true;
      default -> false;
    };
  }
}
