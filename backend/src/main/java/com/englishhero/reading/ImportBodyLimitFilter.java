package com.englishhero.reading;
import java.io.*;
import java.nio.charset.StandardCharsets;
import java.util.Set;
import jakarta.servlet.*;
import jakarta.servlet.http.*;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;
import com.englishhero.identity.IdentityErrors;
@Component
@Order(-110)
public class ImportBodyLimitFilter extends OncePerRequestFilter {
    public static final int MAX_BYTES=1048576;
    @Override protected boolean shouldNotFilter(HttpServletRequest request){return !request.getMethod().equals("POST")||!Set.of("/api/v1/admin/articles/import/validate","/api/v1/admin/articles/import/confirm").contains(request.getServletPath());}
    @Override protected void doFilterInternal(HttpServletRequest request,HttpServletResponse response,FilterChain chain)throws ServletException,IOException {
        if(request.getContentLengthLong()>MAX_BYTES){tooLarge(request,response);return;}
        byte[] bytes=request.getInputStream().readNBytes(MAX_BYTES+1);
        if(bytes.length>MAX_BYTES){tooLarge(request,response);return;}
        chain.doFilter(new HttpServletRequestWrapper(request){
            @Override public ServletInputStream getInputStream(){var stream=new ByteArrayInputStream(bytes);return new ServletInputStream(){public int read(){return stream.read();}public int read(byte[] b,int off,int len){return stream.read(b,off,len);}public boolean isFinished(){return stream.available()==0;}public boolean isReady(){return true;}public void setReadListener(ReadListener listener){throw new UnsupportedOperationException("Synchronous JSON requests only");}};}
            @Override public BufferedReader getReader(){return new BufferedReader(new InputStreamReader(getInputStream(),StandardCharsets.UTF_8));}
        },response);
    }
    private void tooLarge(HttpServletRequest request,HttpServletResponse response)throws IOException{IdentityErrors.write(response,request,413,"IMPORT_TOO_LARGE","文件须不超过1MiB，请拆分后导入");}
}
