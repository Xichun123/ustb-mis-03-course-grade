package com.ustb.coursegrade.module.selection;

import com.ustb.coursegrade.common.BizException;
import java.math.BigDecimal;
import java.util.*;
import java.util.regex.*;

/** 按实际授课周集合校验冲突，缺少必要资料时拒绝推断。 */
public final class SelectionRules {
    private SelectionRules() {}
    public static Set<Integer> weeks(String source) {
        if (source == null || source.isBlank()) throw pending("排课周次缺失");
        String text=source.replace("周", "").replace("，", ",").replace("、", ",").replace(" ", "");
        Set<Integer> result=new TreeSet<>();
        for(String part:text.split(",",-1)) {
            Matcher m=Pattern.compile("(\\d+)(?:-(\\d+))?(?:[（(]?([单双])[）)]?)?").matcher(part);
            if(!m.matches())throw pending("无法识别排课周次："+source);
            int start=Integer.parseInt(m.group(1)),end=m.group(2)==null?start:Integer.parseInt(m.group(2));
            if(start<1 || end<start || end>60)throw pending("排课周次范围无效");
            for(int w=start;w<=end;w++)if(m.group(3)==null || (w%2==1)==m.group(3).equals("单"))result.add(w);
        }
        if(result.isEmpty())throw pending("排课周次为空");
        return result;
    }
    public static void validateSchedule(Map<String,Object> row) {
        int d=number(row.get("week_day")),s=number(row.get("start_slot")),e=number(row.get("end_slot"));
        if(d<1 || d>7 || s<1 || e<s || e>30)throw pending("排课星期或节次无效");
        weeks((String)row.get("week_range"));
    }
    public static boolean overlaps(Map<String,Object> a,Map<String,Object> b) {
        validateSchedule(a);validateSchedule(b);
        return number(a.get("week_day"))==number(b.get("week_day"))
            && number(a.get("start_slot"))<=number(b.get("end_slot"))
            && number(b.get("start_slot"))<=number(a.get("end_slot"))
            && !Collections.disjoint(weeks((String)a.get("week_range")),weeks((String)b.get("week_range")));
    }
    public static int number(Object value) {
        if(value==null)throw pending("必要编码或数量缺失");
        try{return new BigDecimal(value.toString()).intValueExact();}
        catch(ArithmeticException|NumberFormatException e){throw pending("编码或数量不是有效整数");}
    }
    public static BigDecimal credit(Object value) {
        if(value==null)throw pending("学分缺失");
        try{BigDecimal n=new BigDecimal(value.toString());if(n.signum()<0)throw pending("学分无效");return n;}
        catch(NumberFormatException e){throw pending("学分无效");}
    }
    public static BizException pending(String text){return BizException.unprocessable("DATA_PENDING",text+"，请联系教务核实");}
}
