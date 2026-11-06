package com.englishhero.identity.token;
import com.englishhero.identity.IdentityException;
final class TokenErrors {
    static IdentityException invalid(){return new IdentityException(401,"UNAUTHENTICATED","登录已失效，请重新登录");}
    static IdentityException unavailable(){return new IdentityException(503,"AUTH_SERVICE_UNAVAILABLE","认证服务暂不可用，请稍后重试");}
}
