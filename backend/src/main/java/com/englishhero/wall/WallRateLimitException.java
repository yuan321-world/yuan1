package com.englishhero.wall;
import com.englishhero.web.BusinessException;
public class WallRateLimitException extends BusinessException {
 public final long retryAfter;
 public WallRateLimitException(String message,long retryAfter){super(429,"WALL_RATE_LIMIT",message);this.retryAfter=Math.max(1,retryAfter);}
}
