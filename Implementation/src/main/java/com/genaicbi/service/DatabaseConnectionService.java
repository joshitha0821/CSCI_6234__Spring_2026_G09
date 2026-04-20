package com.genaicbi.service;

import com.genaicbi.dto.CreateConnectionRequest;
import com.genaicbi.exception.ResourceNotFoundException;
import com.genaicbi.model.DatabaseConnectionInfo;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DriverManagerDataSource;
import org.springframework.stereotype.Service;

import javax.sql.DataSource;
import java.sql.Connection;
import java.sql.SQLException;
import java.util.Comparator;
import java.util.List;
import java.util.Objects;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicReference;

@Service
public class DatabaseConnectionService {

    private static final String LOCAL_CONNECTION_ID = "local";

    private final ConcurrentHashMap<String, ManagedConnection> connectionsById = new ConcurrentHashMap<>();
    private final ConcurrentHashMap<String, JdbcTemplate> jdbcTemplatesById = new ConcurrentHashMap<>();
    private final AtomicReference<String> activeConnectionId = new AtomicReference<>(LOCAL_CONNECTION_ID);

    public DatabaseConnectionService(
            DataSource primaryDataSource,
            @Value("${spring.datasource.url}") String localJdbcUrl,
            @Value("${spring.datasource.username:}") String localUsername,
            @Value("${spring.datasource.driver-class-name:org.h2.Driver}") String localDriverClassName
    ) {
        ManagedConnection local = new ManagedConnection(
                LOCAL_CONNECTION_ID,
                "Local Database",
                localJdbcUrl,
                localUsername,
                localDriverClassName,
                true,
                primaryDataSource
        );
        connectionsById.put(LOCAL_CONNECTION_ID, local);
        jdbcTemplatesById.put(LOCAL_CONNECTION_ID, new JdbcTemplate(primaryDataSource));
    }

    public List<DatabaseConnectionInfo> listConnections() {
        String activeId = activeConnectionId.get();
        return connectionsById.values().stream()
                .sorted(Comparator.comparing(ManagedConnection::local).reversed().thenComparing(ManagedConnection::name))
                .map(conn -> toInfo(conn, Objects.equals(conn.id(), activeId)))
                .toList();
    }

    public DatabaseConnectionInfo addConnection(CreateConnectionRequest request) {
        String id = "conn-" + UUID.randomUUID().toString().substring(0, 8);
        DriverManagerDataSource dataSource = new DriverManagerDataSource();
        if (request.getDriverClassName() != null && !request.getDriverClassName().isBlank()) {
            dataSource.setDriverClassName(request.getDriverClassName().trim());
        }
        dataSource.setUrl(request.getJdbcUrl().trim());
        dataSource.setUsername(request.getUsername() == null ? "" : request.getUsername().trim());
        dataSource.setPassword(request.getPassword() == null ? "" : request.getPassword());

        // Validate connectivity eagerly so invalid connections are rejected immediately.
        try (Connection ignored = dataSource.getConnection()) {
            // no-op
        } catch (SQLException ex) {
            throw new IllegalArgumentException("Unable to connect with provided JDBC config: " + ex.getMessage(), ex);
        }

        ManagedConnection connection = new ManagedConnection(
                id,
                request.getName().trim(),
                request.getJdbcUrl().trim(),
                request.getUsername() == null ? "" : request.getUsername().trim(),
                request.getDriverClassName() == null ? "" : request.getDriverClassName().trim(),
                false,
                dataSource
        );
        connectionsById.put(id, connection);
        jdbcTemplatesById.put(id, new JdbcTemplate(dataSource));
        return toInfo(connection, false);
    }

    public DatabaseConnectionInfo activate(String connectionId) {
        ManagedConnection connection = requireConnection(connectionId);
        activeConnectionId.set(connection.id());
        return toInfo(connection, true);
    }

    public String resolveConnectionId(String requestedConnectionId) {
        if (requestedConnectionId == null || requestedConnectionId.isBlank()) {
            return activeConnectionId.get();
        }
        if (!connectionsById.containsKey(requestedConnectionId)) {
            throw new ResourceNotFoundException("Connection not found: " + requestedConnectionId);
        }
        return requestedConnectionId;
    }

    public DataSource dataSource(String requestedConnectionId) {
        String id = resolveConnectionId(requestedConnectionId);
        return requireConnection(id).dataSource();
    }

    public JdbcTemplate jdbcTemplate(String requestedConnectionId) {
        String id = resolveConnectionId(requestedConnectionId);
        JdbcTemplate template = jdbcTemplatesById.get(id);
        if (template == null) {
            throw new ResourceNotFoundException("No JdbcTemplate available for connection: " + id);
        }
        return template;
    }

    public DatabaseConnectionInfo activeConnection() {
        String id = activeConnectionId.get();
        ManagedConnection connection = requireConnection(id);
        return toInfo(connection, true);
    }

    private ManagedConnection requireConnection(String id) {
        ManagedConnection connection = connectionsById.get(id);
        if (connection == null) {
            throw new ResourceNotFoundException("Connection not found: " + id);
        }
        return connection;
    }

    private DatabaseConnectionInfo toInfo(ManagedConnection connection, boolean active) {
        return new DatabaseConnectionInfo(
                connection.id(),
                connection.name(),
                connection.jdbcUrl(),
                connection.username(),
                connection.driverClassName(),
                active,
                connection.local()
        );
    }

    private record ManagedConnection(
            String id,
            String name,
            String jdbcUrl,
            String username,
            String driverClassName,
            boolean local,
            DataSource dataSource
    ) {
    }
}
