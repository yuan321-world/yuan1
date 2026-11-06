package com.englishhero.identity.admin;
import com.englishhero.web.BusinessException;
import java.util.*;
final class AdminUserRules {
 private AdminUserRules(){}
 static String text(String value,String field,int max,boolean required){String s=value==null?"":value.strip();if(required&&s.isBlank()||s.codePointCount(0,s.length())>max||s.indexOf(0)>=0)throw BusinessException.invalid(field,"请输入"+(required?"1–":"0–")+max+"字");for(int i=0;i<s.length();i++){char c=s.charAt(i);if(Character.isHighSurrogate(c)){if(i+1==s.length()||!Character.isLowSurrogate(s.charAt(++i)))throw BusinessException.invalid(field,"文字编码无效");}else if(Character.isLowSurrogate(c))throw BusinessException.invalid(field,"文字编码无效");}return s;}
 static long id(String value,String field){try{if(value==null||!value.matches("[1-9][0-9]{0,18}"))throw new NumberFormatException();return Long.parseLong(value);}catch(NumberFormatException e){throw BusinessException.invalid(field,"标识无效");}}
 static String option(String value,String field,String...allowed){if(!List.of(allowed).contains(value))throw BusinessException.invalid(field,"请选择有效选项");return value;}
 static String uuid(String v){try{if(v==null||!UUID.fromString(v).toString().equals(v))throw new IllegalArgumentException();return v;}catch(IllegalArgumentException e){throw BusinessException.invalid("requestId","请求标识无效");}}
 static BusinessException missing(){return new BusinessException(404,"ADMIN_USER_NOT_FOUND","账号或操作记录不存在");}
 static BusinessException conflict(){return new BusinessException(409,"ADMIN_USER_VERSION_CONFLICT","账号状态已变化，请重新加载后操作");}
 static BusinessException forbidden(){return new BusinessException(403,"FORBIDDEN","此账号受保护，不能在本模块修改");}
}
