package com.englishhero.wall;
import java.util.*;
import com.englishhero.web.BusinessException;

public final class WallRules {
 private WallRules(){}
 public static String text(String value,String field,int max,boolean required){
  String s=value==null?"":value.strip();if((required&&s.isBlank())||s.codePointCount(0,s.length())>max||s.indexOf('\u0000')>=0)throw BusinessException.invalid(field,"请输入"+(required?"1–":"0–")+max+"字");for(int i=0;i<s.length();i++){char c=s.charAt(i);if(Character.isHighSurrogate(c)){if(i+1>=s.length()||!Character.isLowSurrogate(s.charAt(++i)))throw BusinessException.invalid(field,"文字编码无效，请重新输入");}else if(Character.isLowSurrogate(c))throw BusinessException.invalid(field,"文字编码无效，请重新输入");}return s;
 }
 public static String oneOf(String value,String field,String... allowed){if(value==null||!List.of(allowed).contains(value))throw BusinessException.invalid(field,"请选择有效选项");return value;}
 public static long id(String value,String field){try{if(value==null||!value.matches("[1-9][0-9]{0,18}"))throw new NumberFormatException();return Long.parseLong(value);}catch(NumberFormatException e){throw BusinessException.invalid(field,"标识无效");}}
 public static String uuid(String value){try{if(value==null||!UUID.fromString(value).toString().equals(value))throw new IllegalArgumentException();return value;}catch(IllegalArgumentException e){throw BusinessException.invalid("requestId","请求标识无效，请重新操作");}}
 public static long cursor(String value){return value==null||value.isBlank()?Long.MAX_VALUE:id(value,"cursor");}
 public static int size(int value){if(value<1||value>50)throw BusinessException.invalid("pageSize","每页1–50条");return value;}
 public static long version(Long value){if(value==null||value<0)throw BusinessException.invalid("expectedVersion","请重新加载内容");return value;}
 public static BusinessException missing(){return new BusinessException(404,"WALL_NOT_FOUND","内容不存在或暂不可访问");}
 public static BusinessException conflict(){return new BusinessException(409,"WALL_VERSION_CONFLICT","内容已更新，请重新加载后操作");}
}
