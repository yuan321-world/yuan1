package com.englishhero.reading.translation;
import java.net.*;import java.net.http.*;import java.nio.*;import java.nio.charset.StandardCharsets;import java.security.*;import java.time.*;import java.util.*;import java.util.concurrent.*;import java.io.*;
import org.springframework.stereotype.Component;import org.springframework.beans.factory.annotation.Autowired;import tools.jackson.databind.ObjectMapper;
@Component
public class BaiduTranslationClient {
 public record Result(String translation){}
 public static final class Failure extends RuntimeException {public final String code;public final boolean definitelyUnsent;Failure(String code,boolean unsent){super(code);this.code=code;definitelyUnsent=unsent;}}
 private final BaiduTranslationProperties config;private final ObjectMapper json;private final URI endpoint;
 private final HttpClient http=HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(5)).followRedirects(HttpClient.Redirect.NEVER).build();
 @Autowired public BaiduTranslationClient(BaiduTranslationProperties c,ObjectMapper j){this(c,j,URI.create("https://fanyi-api.baidu.com/api/trans/vip/translate"));}
 BaiduTranslationClient(BaiduTranslationProperties c,ObjectMapper j,URI endpoint){config=c;json=j;this.endpoint=endpoint;}
 public Result translate(String text){
  if(!config.ready())throw new Failure("BAIDU_NOT_CONFIGURED",true);
  if(text==null||text.isBlank()||config.characters(text)>1000)throw new Failure("BAIDU_INPUT_INVALID",true);
  String salt=UUID.randomUUID().toString(),sign;
  try{sign=HexFormat.of().formatHex(MessageDigest.getInstance("MD5").digest((config.getAppId()+text+salt+config.getSecretKey()).getBytes(StandardCharsets.UTF_8)));}catch(NoSuchAlgorithmException e){throw new IllegalStateException(e);}
  var fields=new LinkedHashMap<String,String>();fields.put("q",text);fields.put("from","en");fields.put("to","zh");fields.put("appid",config.getAppId());fields.put("salt",salt);fields.put("sign",sign);
  String body=fields.entrySet().stream().map(e->e.getKey()+"="+URLEncoder.encode(e.getValue(),StandardCharsets.UTF_8)).collect(java.util.stream.Collectors.joining("&"));
  var request=HttpRequest.newBuilder(endpoint).timeout(Duration.ofSeconds(40)).header("Content-Type","application/x-www-form-urlencoded; charset=UTF-8").POST(HttpRequest.BodyPublishers.ofString(body)).build();
  var future=http.sendAsync(request,r->new LimitedBody());
  try{var response=future.get(40,TimeUnit.SECONDS);if(response.statusCode()!=200)throw new Failure("BAIDU_HTTP_ERROR",false);var result=json.readTree(response.body());
   if(result.has("error_code")){String code=result.path("error_code").asText();if(!code.matches("[0-9]{5}"))throw new Failure("BAIDU_INVALID_OUTPUT",false);throw new Failure("BAIDU_"+code,Set.of("52003","54000","54001","54003","54004","54005","58000","58001","58002","90107").contains(code));}
   var rows=result.path("trans_result");if(!"en".equals(result.path("from").asText())||!"zh".equals(result.path("to").asText())||!rows.isArray()||rows.isEmpty()||rows.size()>100)throw new Failure("BAIDU_INVALID_OUTPUT",false);
   var source=new ArrayList<String>();var translations=new ArrayList<String>();for(var row:rows){var src=row.path("src");var dst=row.path("dst");if(!src.isString()||!dst.isString()||dst.asText().isBlank()||dst.asText().indexOf(0)>=0)throw new Failure("BAIDU_INVALID_OUTPUT",false);source.add(src.asText());translations.add(dst.asText());}
   String translated=String.join("\n",translations);if(!SelectionTranslationService.normalized(String.join(" ",source)).equals(SelectionTranslationService.normalized(text))||translated.length()>6000)throw new Failure("BAIDU_INVALID_OUTPUT",false);return new Result(translated);
  }catch(Failure e){throw e;}catch(Exception e){future.cancel(true);if(e instanceof InterruptedException)Thread.currentThread().interrupt();throw new Failure("BAIDU_OUTCOME_UNKNOWN",false);}
 }
 static final class LimitedBody implements HttpResponse.BodySubscriber<byte[]> {
  final CompletableFuture<byte[]> result=new CompletableFuture<>();final ByteArrayOutputStream bytes=new ByteArrayOutputStream();java.util.concurrent.Flow.Subscription subscription;
  public CompletionStage<byte[]> getBody(){return result;}public void onSubscribe(java.util.concurrent.Flow.Subscription s){subscription=s;s.request(1);}
  public void onNext(List<ByteBuffer> buffers){for(var b:buffers){if(bytes.size()+b.remaining()>131072){subscription.cancel();result.completeExceptionally(new IOException("Response limit"));return;}byte[] part=new byte[b.remaining()];b.get(part);bytes.writeBytes(part);}subscription.request(1);}
  public void onError(Throwable e){result.completeExceptionally(e);}public void onComplete(){result.complete(bytes.toByteArray());}
 }
}
