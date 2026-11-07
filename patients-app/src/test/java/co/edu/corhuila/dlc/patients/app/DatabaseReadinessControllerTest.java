package co.edu.corhuila.dlc.patients.app;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;
import java.sql.Connection;
import java.sql.SQLException;
import java.sql.Statement;
import javax.sql.DataSource;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.ObjectProvider;

class DatabaseReadinessControllerTest {
    @SuppressWarnings("unchecked")
    private final ObjectProvider<DataSource> sources = mock(ObjectProvider.class);
    private final DatabaseReadinessController controller = new DatabaseReadinessController(sources);
    @Test void disabledDatabaseIsNotReady() {
        assertEquals(503, controller.ready().getStatusCode().value());
    }
    @Test void connectionFailureIsSanitized() throws Exception {
        var source = mock(DataSource.class); when(sources.getIfAvailable()).thenReturn(source);
        when(source.getConnection()).thenThrow(new SQLException("private-host/password"));
        var response = controller.ready();
        assertEquals(503, response.getStatusCode().value());
        assertFalse(response.getBody().toString().contains("private-host"));
        assertEquals("no-store", response.getHeaders().getCacheControl());
    }
    @Test void missingSchemaIsNotReadyAndConnectionsAreClosed() throws Exception {
        var source = mock(DataSource.class); var connection = mock(Connection.class); var statement = mock(Statement.class);
        when(sources.getIfAvailable()).thenReturn(source); when(source.getConnection()).thenReturn(connection);
        when(connection.createStatement()).thenReturn(statement);
        when(statement.execute(anyString())).thenThrow(new SQLException("missing relation"));
        assertEquals(503, controller.ready().getStatusCode().value());
        verify(connection).close(); verify(statement).close();
    }
    @Test void reachableOwnedSchemaIsReadyWithoutReadingRecords() throws Exception {
        var source = mock(DataSource.class); var connection = mock(Connection.class); var statement = mock(Statement.class);
        when(sources.getIfAvailable()).thenReturn(source); when(source.getConnection()).thenReturn(connection);
        when(connection.createStatement()).thenReturn(statement);
        assertEquals(200, controller.ready().getStatusCode().value());
        verify(statement).setQueryTimeout(5);
        verify(statement).execute(contains("LIMIT 0")); verify(connection).close();
    }
}
