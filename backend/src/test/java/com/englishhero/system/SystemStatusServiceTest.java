package com.englishhero.system;

import org.junit.jupiter.api.Test;
import org.springframework.dao.DataAccessResourceFailureException;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.*;

class SystemStatusServiceTest {
    private final SystemMetadataMapper mapper = mock(SystemMetadataMapper.class);
    private final SystemStatusService service = new SystemStatusService(mapper);

    @Test void reportsUpOnlyWhenDatabaseContainsTheServiceIdentity() {
        var metadata = new SystemMetadata();
        metadata.setMetadataKey("service_name");
        metadata.setMetadataValue("english-hero");
        when(mapper.selectById("service_name")).thenReturn(metadata);
        assertThat(service.getStatus()).isEqualTo(new SystemStatusResponse("UP", "english-hero", "UP"));
    }
    @Test void missingMetadataIsDown() {
        assertThat(service.getStatus()).isEqualTo(new SystemStatusResponse("DOWN", "english-hero", "DOWN"));
    }
    @Test void wrongIdentityIsDown() {
        var metadata = new SystemMetadata();
        metadata.setMetadataValue("other-project");
        when(mapper.selectById("service_name")).thenReturn(metadata);
        assertThat(service.getStatus().status()).isEqualTo("DOWN");
    }
    @Test void databaseExceptionIsSanitized() {
        when(mapper.selectById("service_name")).thenThrow(new DataAccessResourceFailureException("secret password JDBC"));
        var status = service.getStatus();
        assertThat(status).isEqualTo(new SystemStatusResponse("DOWN", "english-hero", "DOWN"));
        assertThat(status.toString()).doesNotContain("secret", "password", "JDBC");
    }
}
