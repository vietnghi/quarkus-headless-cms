package com.quarkus.cms.admin.api.resource;

import io.quarkus.logging.Log;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.ws.rs.GET;
import jakarta.ws.rs.PathParam;
import jakarta.ws.rs.Produces;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Response;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Paths;

/**
 * Static file serving for the admin panel frontend.
 *
 * Serves the React-based admin UI static files from a configurable
 * directory on disk. Falls back to a simple welcome page when no
 * admin UI build is present.
 *
 * The admin panel path is configurable via system property or env var:
 * {@code cms.admin.ui.path} or {@code CMS_ADMIN_UI_PATH}.
 */
@jakarta.ws.rs.Path("/admin-panel")
@ApplicationScoped
public class AdminStaticResource {

    private static final String DEFAULT_ADMIN_UI_PATH = "/opt/cms/admin-ui";

    private final java.nio.file.Path adminUiPath;

    public AdminStaticResource() {
        String configuredPath = System.getProperty("cms.admin.ui.path",
            System.getenv("CMS_ADMIN_UI_PATH"));
        if (configuredPath != null && !configuredPath.isBlank()) {
            adminUiPath = Paths.get(configuredPath);
        } else {
            adminUiPath = Paths.get(DEFAULT_ADMIN_UI_PATH);
        }
        Log.infof("Admin panel static files path: %s", adminUiPath.toAbsolutePath());
    }

    @GET
    @jakarta.ws.rs.Path("/{path: .*}")
    @Produces(MediaType.TEXT_HTML)
    public Response serveFile(@PathParam("path") String path) {
        if (path != null && (path.contains("..") || path.contains("~"))) {
            return Response.status(Response.Status.FORBIDDEN)
                    .entity("Forbidden")
                    .build();
        }

        java.nio.file.Path filePath;
        if (path == null || path.isEmpty() || path.equals("/") || path.equals("index.html")) {
            filePath = adminUiPath.resolve("index.html");
        } else {
            filePath = adminUiPath.resolve(path);
        }

        if (!Files.exists(adminUiPath)) {
            return serveWelcomePage();
        }

        if (Files.exists(filePath) && Files.isRegularFile(filePath)) {
            try {
                String contentType = Files.probeContentType(filePath);
                if (contentType == null) {
                    contentType = "application/octet-stream";
                }
                byte[] content = Files.readAllBytes(filePath);
                return Response.ok(content, contentType).build();
            } catch (IOException e) {
                return Response.status(Response.Status.INTERNAL_SERVER_ERROR)
                        .entity("Error reading file: " + e.getMessage())
                        .build();
            }
        }

        // SPA routing: serve index.html for non-file paths
        if (path != null && !path.contains(".")) {
            try {
                java.nio.file.Path indexPath = adminUiPath.resolve("index.html");
                if (Files.exists(indexPath)) {
                    byte[] content = Files.readAllBytes(indexPath);
                    return Response.ok(content, MediaType.TEXT_HTML).build();
                }
            } catch (IOException e) {
                // fall through
            }
        }

        return Response.status(Response.Status.NOT_FOUND)
                .entity("File not found: " + path)
                .build();
    }

    private Response serveWelcomePage() {
        String uiPath = adminUiPath.toAbsolutePath().toString();
        String html = "<!DOCTYPE html>\n"
            + "<html>\n"
            + "<head><title>Quarkus CMS - Admin Panel</title>\n"
            + "<style>\n"
            + "    body { font-family: system-ui, sans-serif; display: flex; align-items: center; justify-content: center; min-height: 100vh; margin: 0; background: #f8f9fa; }\n"
            + "    .card { background: white; padding: 2rem; border-radius: 12px; box-shadow: 0 1px 3px rgba(0,0,0,0.1); text-align: center; max-width: 480px; }\n"
            + "    h1 { font-size: 1.5rem; margin: 0 0 0.5rem; }\n"
            + "    p { color: #666; margin: 0 0 1.5rem; line-height: 1.5; }\n"
            + "    code { background: #f1f3f5; padding: 0.2em 0.4em; border-radius: 4px; font-size: 0.9em; }\n"
            + "</style>\n"
            + "</head>\n"
            + "<body>\n"
            + "<div class=\"card\">\n"
            + "    <h1>Admin Panel</h1>\n"
            + "    <p>The admin UI build is not installed yet.<br>\n"
            + "    Build the React frontend and place it at:<br>\n"
            + "    <code>" + uiPath + "</code></p>\n"
            + "    <p style=\"font-size: 0.875rem; color: #999;\">\n"
            + "    Set <code>cms.admin.ui.path</code> system property or<br>\n"
            + "    <code>CMS_ADMIN_UI_PATH</code> environment variable<br>\n"
            + "    to use a custom directory.\n"
            + "    </p>\n"
            + "    <p><a href=\"/admin/\">Back to Server-Side Admin</a></p>\n"
            + "</div>\n"
            + "</body>\n"
            + "</html>\n";
        return Response.ok(html, MediaType.TEXT_HTML).build();
    }
}
