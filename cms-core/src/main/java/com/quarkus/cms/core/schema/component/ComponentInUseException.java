package com.quarkus.cms.core.schema.component;

import java.util.Set;

/**
 * Thrown by {@link ComponentRegistryService} when attempting to delete a component that is still
 * referenced by other schemas.
 */
public class ComponentInUseException extends RuntimeException {

  private final String componentUid;
  private final Set<String> dependents;

  public ComponentInUseException(String componentUid, Set<String> dependents, String message) {
    super(message);
    this.componentUid = componentUid;
    this.dependents = dependents;
  }

  /** The UID of the component that could not be deleted. */
  public String getComponentUid() {
    return componentUid;
  }

  /** The UIDs of all content types and other components that still reference this component. */
  public Set<String> getDependents() {
    return dependents;
  }
}
