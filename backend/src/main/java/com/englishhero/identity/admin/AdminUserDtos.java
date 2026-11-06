package com.englishhero.identity.admin;
import java.time.Instant;
import java.util.List;
public final class AdminUserDtos {
 private AdminUserDtos(){}
 public record User(String id,String username,String displayName,List<String> roles,boolean enabled,int version,Instant createdAt,boolean protectedAccount){}
 public record Detail(String id,String username,String displayName,List<String> roles,boolean enabled,int version,Instant createdAt,boolean protectedAccount,String maskedEmail,boolean emailVerified){}
 public record Page<T>(List<T> items,int page,int pageSize,long total){}
 public record StatusInput(String requestId,Long expectedVersion,Boolean enabled,String reason){}
 public record Receipt(String requestId,String targetId,boolean enabled,int version,boolean completed){}
 public record Party(String id,String username){}
 public record Audit(String id,Party actor,Party target,boolean beforeEnabled,boolean afterEnabled,String reason,Instant createdAt){}
 public record AuditPage(List<Audit> items,String nextCursor,boolean hasMore){}
}
