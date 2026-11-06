package com.englishhero.wall;
import java.util.List;
import static com.englishhero.wall.WallDtos.*;
public final class WallLayout {
 public static final int WIDTH=280,HEIGHT=420,GAP=30;
 public record Rectangle(int x,int y,int width,int height){}
 public static Position allocate(int x,int y,List<Rectangle> others){
  if(free(x,y,others))return new Position(x,y,WIDTH,HEIGHT);
  for(int radius=24;radius<=2400;radius+=24){int steps=Math.max(16,(int)Math.ceil(2*Math.PI*radius/28));for(int i=0;i<steps;i++){double a=i*2*Math.PI/steps;int nx=x+(int)Math.round(Math.cos(a)*radius),ny=y+(int)Math.round(Math.sin(a)*radius);if(free(nx,ny,others))return new Position(nx,ny,WIDTH,HEIGHT);}}
  int right=others.stream().mapToInt(r->r.x+r.width).max().orElse(0);return new Position(right+GAP,y,WIDTH,HEIGHT);
 }
 private static boolean free(int x,int y,List<Rectangle> all){return all.stream().allMatch(r->x+WIDTH+GAP<=r.x||x>=r.x+r.width+GAP||y+HEIGHT+GAP<=r.y||y>=r.y+r.height+GAP);}
 public static int coordinate(Double value,String field){if(value==null||!Double.isFinite(value)||Math.abs(value)>1_000_000)throw com.englishhero.web.BusinessException.invalid(field,"画布位置无效，请重试");return (int)Math.round(value);}
}
