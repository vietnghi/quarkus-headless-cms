/**
 * Dynamic GraphQL API module for the Quarkus Headless CMS.
 *
 * <p>Provides a complete GraphQL endpoint using SmallRye GraphQL, with automatic schema
 * introspection, content queries and mutations, JWT authentication, draft/publish lifecycle,
 * and i18n localization support — all sharing the same service layer as the REST API.
 *
 * <h3>Key features:</h3>
 * <ul>
 *   <li>Dynamic content queries (findMany / findOne) with filters, sort, and pagination</li>
 *   <li>Content mutations (create, update, delete, publish, unpublish)</li>
 *   <li>Schema introspection — discover all content types, fields, relations, and components</li>
 *   <li>JWT-based authentication via login mutation</li>
 *   <li>Role-based access control via declarative security annotations</li>
 *   <li>i18n support — query localized content and create translations</li>
 *   <li>DataLoader-based batch relation resolution for N+1 prevention</li>
 *   <li>Custom JSON scalar for dynamic content-type field data</li>
 *   <li>Query complexity and depth limiting</li>
 *   <li>GraphiQL playground in dev mode</li>
 * </ul>
 */
package com.quarkus.cms.graphql;
