package com.englishhero.identity.token;

import java.util.*;
import org.springframework.beans.factory.config.YamlPropertiesFactoryBean;
import org.springframework.core.io.FileSystemResource;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.boot.context.properties.bind.*;
import org.springframework.boot.context.properties.source.MapConfigurationPropertySource;

public final class TokenTestSupport {
    public static Properties properties() {
        var yaml = new YamlPropertiesFactoryBean();
        yaml.setResources(new FileSystemResource("./config/application-auth-test.yml"));
        var p = Objects.requireNonNull(yaml.getObject());
        if (!"eh:test:auth:v1".equals(p.getProperty("english-hero.auth.namespace"))
                || !"6381".equals(p.getProperty("spring.data.redis.port"))) throw new IllegalStateException("Dedicated test Redis required.");
        return p;
    }
    public static void register(DynamicPropertyRegistry registry) {
        properties().forEach((k,v)->registry.add(k.toString(),()->v));
    }
    public static AuthProperties auth() {
        var values = new HashMap<String,Object>();
        properties().forEach((k,v)->values.put(k.toString(),v));
        return new Binder(new MapConfigurationPropertySource(values)).bind("english-hero.auth", Bindable.of(AuthProperties.class)).get();
    }
    public static org.springframework.data.redis.connection.lettuce.LettuceConnectionFactory connection() {
        var p=properties();
        var config=new org.springframework.data.redis.connection.RedisStandaloneConfiguration("127.0.0.1",6381);
        config.setPassword(p.getProperty("spring.data.redis.password"));
        var factory=new org.springframework.data.redis.connection.lettuce.LettuceConnectionFactory(config);
        factory.afterPropertiesSet(); factory.start(); return factory;
    }
}
