package com.englishhero.practice;

import java.util.*;
import com.englishhero.reading.quiz.QuizDtos.QuestionInput;
import com.englishhero.web.BusinessException;
import static com.englishhero.practice.PracticeDtos.*;

public final class PracticeListeningRules {
    private PracticeListeningRules(){}
    public static List<QuestionInput> questions(List<QuestionInput> questions){
        if(questions==null||questions.size()>50)throw BusinessException.invalid("questions","一套听力最多50道题");
        var keys=new HashSet<String>();var result=new ArrayList<QuestionInput>();int ordinal=0;
        for(var q:questions){
            if(q==null||q.key()==null||!q.key().matches("[A-Za-z0-9_-]{1,64}")||!keys.add(q.key()))throw BusinessException.invalid("questions","题目标识须唯一且有效");
            if(!"LISTENING".equals(q.type())||q.options()==null||q.options().size()!=4)throw BusinessException.invalid("questions","听力题须为四选一题目");
            if(q.correctOption()!=null&&!q.correctOption().isBlank()&&!q.correctOption().matches("[A-D]"))throw BusinessException.invalid("questions","答案须为A–D或未选择");
            result.add(new QuestionInput(q.key(),"LISTENING",PracticeDraftRules.text(q.stem(),"stem",2000),q.options().stream().map(o->PracticeDraftRules.text(o,"options",1000)).toList(),q.correctOption()==null||q.correctOption().isBlank()?null:q.correctOption(),PracticeDraftRules.text(q.explanation(),"explanation",4000),ordinal++));
        }
        return List.copyOf(result);
    }
    public static ListeningData normalize(ListeningData data,List<QuestionInput> questions){
        if(data==null)return new ListeningData(null,List.of(),false);
        if(data.audioAssetId()!=null&&!data.audioAssetId().matches("[1-9][0-9]{0,18}"))throw BusinessException.invalid("listening.audioAssetId","音频资产无效");
        var groups=data.groups()==null?List.<ListeningGroup>of():data.groups();if(groups.size()>20)throw BusinessException.invalid("listening.groups","最多20个套内分组");
        var result=new ArrayList<ListeningGroup>();var seen=new HashSet<String>();var flattened=new ArrayList<String>();
        for(var group:groups){if(group==null||group.key()==null||!group.key().matches("[A-Za-z0-9_-]{1,64}")||!seen.add(group.key())||group.questionKeys()==null||group.questionKeys().isEmpty())throw BusinessException.invalid("listening.groups","分组标识唯一且需要题目范围");var keys=List.copyOf(group.questionKeys());flattened.addAll(keys);result.add(new ListeningGroup(group.key(),PracticeDraftRules.text(group.title(),"listening.groups",120),keys));}
        if(!groups.isEmpty()&&!flattened.equals(questions.stream().map(QuestionInput::key).toList()))throw BusinessException.invalid("listening.groups","套内分组必须按题目顺序完整覆盖且不能重复");
        return new ListeningData(data.audioAssetId(),List.copyOf(result),false);
    }
    public static PlaybackState playback(PlaybackState state){if(state==null)return null;if(!Double.isFinite(state.positionSeconds())||state.positionSeconds()<0||state.positionSeconds()>21600||!Double.isFinite(state.rate())||state.rate()<0.5||state.rate()>2)throw BusinessException.invalid("playback","播放位置或倍速无效");return state;}
}
