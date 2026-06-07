package com.quarkus.cms.core.schema.component;

import com.quarkus.cms.core.schema.model.ComponentDefinition;
import com.quarkus.cms.core.schema.model.FieldDefinition;
import com.quarkus.cms.core.schema.model.FieldType;
import com.quarkus.cms.core.schema.storage.SchemaStorageService;

import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

/**
 * Service that resolves and validates component composition — the graph of components referencing
 * other components via {@link FieldType#COMPONENT} fields.
 *
 * <p>Key capabilities:
 *
 * <ul>
 *   <li><b>Cycle detection</b> — detects circular references in the component composition graph.
 *   <li><b>Topological sort</b> — orders components so that leaf components appear before their
 *       consumers (useful for serialisation or form rendering order).
 *   <li><b>Composition depth</b> — computes the maximum nesting depth of a component tree.
 *   <li><b>Closure</b> — computes the full transitive closure of all components reachable from a
 *       given starting component.
 * </ul>
 */
@ApplicationScoped
public class ComponentCompositionService {

  @Inject SchemaStorageService schemaStorage;

  /** Provided for manual wiring in unit tests. */
  public void setSchemaStorage(SchemaStorageService schemaStorage) {
    this.schemaStorage = schemaStorage;
  }

  /**
   * Returns the full transitive set of component UIDs reachable from the given component via
   * COMPONENT-type fields.
   */
  public Set<String> getTransitiveClosure(String componentUid) {
    Set<String> visited = new HashSet<>();
    walk(componentUid, visited);
    visited.remove(componentUid); // exclude the root
    return visited;
  }

  /**
   * Returns the maximum nesting depth of the composition tree rooted at the given component. A
   * component with no COMPONENT-type fields has depth 0.
   *
   * @throws ComponentCompositionException if a cycle is detected
   */
  public int getMaxDepth(String componentUid) {
    return computeDepth(componentUid, new HashSet<>());
  }

  /**
   * Detects cycles in the composition graph starting from the given component. Returns the first
   * cycle found as an ordered list of UIDs, or an empty list if no cycle exists.
   */
  public List<String> detectCycle(String componentUid) {
    return detectCycleFrom(componentUid, new HashSet<>(), new ArrayDeque<>());
  }

  /**
   * Returns a topological ordering of components in the subgraph reachable from the given root
   * component. Leaf components (those with no further COMPONENT dependencies) come first.
   *
   * @throws ComponentCompositionException if a cycle is detected
   */
  public List<String> topologicalSort(String rootUid) {
    Set<String> visited = new HashSet<>();
    Set<String> onPath = new HashSet<>();
    List<String> sorted = new ArrayList<>();
    topoDfs(rootUid, visited, onPath, sorted);
    return sorted;
  }

  /**
   * Validates that the composition graph rooted at the given component has no cycles and that all
   * referenced components exist. Returns a list of validation errors; an empty list means the graph
   * is valid.
   */
  public List<String> validateCompositionGraph(String componentUid) {
    List<String> errors = new ArrayList<>();
    Set<String> visited = new HashSet<>();
    validateGraph(componentUid, visited, errors);
    return errors;
  }

  // ---- Internal ----

  private void walk(String uid, Set<String> visited) {
    if (uid == null || !visited.add(uid)) return;
    ComponentDefinition comp = schemaStorage.getComponent(uid);
    if (comp == null) {
      visited.remove(uid);
      return;
    }
    for (FieldDefinition field : comp.getFields()) {
      if (field.getType() == FieldType.COMPONENT && field.getComponent() != null) {
        walk(field.getComponent(), visited);
      }
    }
  }

  private int computeDepth(String uid, Set<String> visiting) {
    if (!visiting.add(uid)) {
      throw new ComponentCompositionException(
          "Circular component reference detected involving: " + uid);
    }

    ComponentDefinition comp = schemaStorage.getComponent(uid);
    if (comp == null) {
      visiting.remove(uid);
      return 0;
    }

    int maxChildDepth = 0;
    for (FieldDefinition field : comp.getFields()) {
      if (field.getType() == FieldType.COMPONENT && field.getComponent() != null) {
        int childDepth = computeDepth(field.getComponent(), visiting);
        maxChildDepth = Math.max(maxChildDepth, childDepth + 1);
      }
    }

    visiting.remove(uid);
    return maxChildDepth;
  }

  private List<String> detectCycleFrom(
      String uid, Set<String> visited, Deque<String> path) {
    if (path.contains(uid)) {
      // Found a cycle — extract it from the path
      List<String> cycle = new ArrayList<>();
      boolean recording = false;
      for (String node : path) {
        if (node.equals(uid)) recording = true;
        if (recording) cycle.add(node);
      }
      cycle.add(uid); // close the loop
      return cycle;
    }
    if (visited.contains(uid)) return List.of();

    visited.add(uid);
    path.addLast(uid);

    ComponentDefinition comp = schemaStorage.getComponent(uid);
    if (comp != null) {
      for (FieldDefinition field : comp.getFields()) {
        if (field.getType() == FieldType.COMPONENT && field.getComponent() != null) {
          List<String> cycle = detectCycleFrom(field.getComponent(), visited, path);
          if (!cycle.isEmpty()) return cycle;
        }
      }
    }

    path.removeLast();
    return List.of();
  }

  private void topoDfs(
      String uid, Set<String> visited, Set<String> onPath, List<String> sorted) {
    if (onPath.contains(uid)) {
      throw new ComponentCompositionException(
          "Circular dependency detected: " + uid + " is already on the current path");
    }
    if (visited.contains(uid)) return;

    visited.add(uid);
    onPath.add(uid);

    ComponentDefinition comp = schemaStorage.getComponent(uid);
    if (comp == null) {
      onPath.remove(uid);
      return;
    }

    for (FieldDefinition field : comp.getFields()) {
      if (field.getType() == FieldType.COMPONENT && field.getComponent() != null) {
        topoDfs(field.getComponent(), visited, onPath, sorted);
      }
    }

    onPath.remove(uid);
    sorted.add(uid);
  }

  private void validateGraph(String uid, Set<String> visited, List<String> errors) {
    if (uid == null || !visited.add(uid)) return;

    ComponentDefinition comp = schemaStorage.getComponent(uid);
    if (comp == null) {
      errors.add("Component '" + uid + "' does not exist");
      return;
    }

    for (FieldDefinition field : comp.getFields()) {
      if (field.getType() != FieldType.COMPONENT || field.getComponent() == null) continue;

      String targetUid = field.getComponent();
      ComponentDefinition target = schemaStorage.getComponent(targetUid);
      if (target == null) {
        errors.add(
            "Component '"
                + uid
                + "' field '"
                + field.getName()
                + "' references unknown component '"
                + targetUid
                + "'");
      } else {
        validateGraph(targetUid, visited, errors);
      }
    }
  }
}
