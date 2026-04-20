package com.genaicbi.controller;

import com.genaicbi.dto.CreateConnectionRequest;
import com.genaicbi.model.DatabaseConnectionInfo;
import com.genaicbi.model.SchemaContext;
import com.genaicbi.service.DatabaseConnectionService;
import com.genaicbi.service.SchemaIntrospectionService;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/connections")
public class DatabaseConnectionController {

    private final DatabaseConnectionService connectionService;
    private final SchemaIntrospectionService schemaIntrospectionService;

    public DatabaseConnectionController(
            DatabaseConnectionService connectionService,
            SchemaIntrospectionService schemaIntrospectionService
    ) {
        this.connectionService = connectionService;
        this.schemaIntrospectionService = schemaIntrospectionService;
    }

    @GetMapping
    public List<DatabaseConnectionInfo> list() {
        return connectionService.listConnections();
    }

    @GetMapping("/active")
    public DatabaseConnectionInfo active() {
        return connectionService.activeConnection();
    }

    @PostMapping
    public DatabaseConnectionInfo add(@Valid @RequestBody CreateConnectionRequest request) {
        return connectionService.addConnection(request);
    }

    @PostMapping("/{connectionId}/activate")
    public DatabaseConnectionInfo activate(@PathVariable String connectionId) {
        return connectionService.activate(connectionId);
    }

    @GetMapping("/{connectionId}/schema")
    public SchemaContext schema(@PathVariable String connectionId) {
        return schemaIntrospectionService.introspect(connectionId);
    }

    @GetMapping("/schema")
    public SchemaContext activeSchema(@RequestParam(required = false) String connectionId) {
        return schemaIntrospectionService.introspect(connectionId);
    }

    @GetMapping("/schema/prompt")
    public Map<String, String> promptSchema(
            @RequestParam(required = false) String connectionId,
            @RequestParam(defaultValue = "40") int maxTables,
            @RequestParam(defaultValue = "25") int maxColumnsPerTable
    ) {
        SchemaContext schema = schemaIntrospectionService.introspect(connectionId);
        return Map.of(
                "connectionId", schema.connectionId(),
                "schemaPrompt", schema.toPromptText(maxTables, maxColumnsPerTable)
        );
    }
}
