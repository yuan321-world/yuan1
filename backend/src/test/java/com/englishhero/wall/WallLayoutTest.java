package com.englishhero.wall;
import org.junit.jupiter.api.Test;
import java.util.*;
import static org.assertj.core.api.Assertions.*;
class WallLayoutTest {
 @Test void stablePlacementLeavesThirtyUnitsAroundEveryRectangle(){var all=new ArrayList<WallLayout.Rectangle>();for(int i=0;i<20;i++){var p=WallLayout.allocate(100,100,all);assertThat(all).allMatch(r->p.x()+p.width()+30<=r.x()||p.x()>=r.x()+r.width()+30||p.y()+p.height()+30<=r.y()||p.y()>=r.y()+r.height()+30);all.add(new WallLayout.Rectangle(p.x(),p.y(),p.width(),p.height()));}assertThat(all.getFirst().x()).isEqualTo(100);}
 @Test void rejectsInvalidCoordinates(){assertThatThrownBy(()->WallLayout.coordinate(Double.NaN,"x")).isInstanceOf(com.englishhero.web.BusinessException.class);assertThatThrownBy(()->WallLayout.coordinate(1e9,"x")).isInstanceOf(com.englishhero.web.BusinessException.class);}
}
