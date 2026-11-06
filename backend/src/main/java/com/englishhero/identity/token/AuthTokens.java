package com.englishhero.identity.token;
import com.englishhero.identity.UserDto;
public record AuthTokens(UserDto user,String accessToken,String tokenType,long expiresIn) {
    @Override public String toString(){return "AuthTokens[redacted]";}
}
