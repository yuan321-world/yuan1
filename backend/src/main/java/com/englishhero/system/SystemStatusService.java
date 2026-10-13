package com.englishhero.system;

import org.springframework.dao.DataAccessException;
import org.springframework.stereotype.Service;

@Service
public class SystemStatusService {
    private final SystemMetadataMapper mapper;

    public SystemStatusService(SystemMetadataMapper mapper) {
        this.mapper = mapper;
    }

    public SystemStatusResponse getStatus() {
        try {
            var metadata = mapper.selectById("service_name");
            if (metadata != null && "english-hero".equals(metadata.getMetadataValue())) {
                return new SystemStatusResponse("UP", "english-hero", "UP");
            }
        } catch (DataAccessException ignored) {
            // Return only the public contract; never expose JDBC details or credentials.
        }
        return new SystemStatusResponse("DOWN", "english-hero", "DOWN");
    }
}
