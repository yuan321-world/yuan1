package com.englishhero.wall;
import java.time.Instant;
import java.util.List;

public final class WallDtos {
 private WallDtos(){}
 public record Point(Double x,Double y){}
 public record Position(int x,int y,int width,int height){}
 public record MessageInput(String requestId,String kind,String category,String color,String text,String title,Point preferredPosition){}
 public record Command(String requestId,Long expectedVersion){}
 public record Author(String displayName,boolean isAdmin){}
 public record MessageDto(String id,long version,String kind,String category,String color,String text,String title,Author author,Instant createdAt,boolean editedByAdmin,Position position,long likes,boolean liked,long commentCount,boolean owned){}
 public record MineDto(MessageDto message,String status,String authorReason){}
 public record Page<T>(List<T> items,String nextCursor,boolean hasMore){}
 public record CommentInput(String requestId,String text){}
 public record LikeInput(Boolean liked){}
 public record CommentDto(String id,String messageId,long version,String text,Author author,Instant createdAt,boolean owned){}
 public record ReportInput(String requestId,String targetType,String targetId,String reason,String detail){}
 public record ModerationInput(String requestId,Long expectedVersion,String action,String text,String title,String authorReason,String internalNote,List<String> reportIds){}
}
