package com.ustb.coursegrade.module.selection;

import com.ustb.coursegrade.common.BizException;
import com.ustb.coursegrade.common.auth.*;
import com.ustb.coursegrade.module.selection.SelectionCatalogProperties.CourseType;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.annotation.Isolation;
import org.springframework.beans.factory.annotation.Autowired;
import java.math.BigDecimal;
import java.sql.Timestamp;
import java.time.*;
import java.util.*;
import static com.ustb.coursegrade.module.selection.SelectionRules.*;

/** 业务②：仅使用当前 v9 真实结构，状态、日志与人数同事务提交。 */
@Service
@EnableConfigurationProperties(SelectionCatalogProperties.class)
public class SelectionService {
    private final JdbcTemplate jdbc;
    private final Clock clock;
    private final SelectionCatalogProperties catalog;
    @Autowired
    public SelectionService(JdbcTemplate jdbc, SelectionCatalogProperties catalog) {
        this(jdbc, Clock.system(ZoneId.of("Asia/Shanghai")), catalog);
    }
    SelectionService(JdbcTemplate jdbc, Clock clock, SelectionCatalogProperties catalog) {
        this.jdbc = jdbc;
        this.clock = clock;
        this.catalog = catalog;
    }
    private LocalDateTime now(){return LocalDateTime.now(clock);}
    public record Application(Integer roundId,String tcId,Integer studyType,Integer priority,String expectedVersion){}
    public record Withdrawal(Integer roundId,String expectedVersion,Boolean confirmed){}
    public record Adjustment(String studentId,String operation,Integer studyType,String reason){}

    public Map<String,Object> context(){
        CurrentUser user=UserContext.requireUser();
        List<Map<String,Object>> rounds=jdbc.queryForList("SELECT * FROM select_round WHERE round_status IN (2,3,4) ORDER BY start_time DESC,round_id DESC");
        for(var r:rounds)r.put("window_open",open(r));
        return Map.of("user",user,"rounds",rounds,"terms",jdbc.queryForList("SELECT DISTINCT term FROM teaching_class WHERE term IS NOT NULL ORDER BY term DESC",String.class),"server_time",now(),"stop_write_enabled",false,"course_types",CourseType.options());
    }
    public Map<String,Object> courses(int roundId,String keyword,int page,int size,CourseType filter){
        CurrentUser u=UserContext.requireRole(RoleEnum.STUDENT);var round=visibleRound(roundId);student(u.studentId(),text(round,"term"),false);
        List<Map<String,Object>> classes=jdbc.queryForList("""
          SELECT tc.*,c.course_name,c.course_type,c.credit,c.course_level,c.open_college,
                 sc.sc_id,sc.select_status,sc.study_type,sc.priority,sc.select_time,sc.round_id AS original_round_id
          FROM round_class rc JOIN teaching_class tc ON tc.tc_id=rc.tc_id JOIN course c ON c.course_id=tc.course_id
          LEFT JOIN sc ON sc.tc_id=tc.tc_id AND sc.student_id=?
          WHERE rc.round_id=? AND tc.term=? AND tc.tc_status='4' AND (c.course_id LIKE ? OR c.course_name LIKE ?)
          ORDER BY c.course_id,tc.tc_id
          """,u.studentId(),roundId,round.get("term"),"%"+keyword+"%","%"+keyword+"%");
        Map<String, List<Map<String, Object>>> byCourse = new LinkedHashMap<>();
        for (var tc : classes) {
            String courseId = text(tc, "course_id");
            CourseType type = catalog.courseType(courseId, text(tc, "course_type"));
            if (filter != null && filter != type) continue;
            tc.put("course_type_label", type.label());
            byCourse.computeIfAbsent(courseId, id -> new ArrayList<>()).add(tc);
        }

        var groups = new ArrayList<>(byCourse.values());
        int start = Math.min((page - 1) * size, groups.size());
        var pageGroups = groups.subList(start, Math.min(start + size, groups.size()));
        var classIds = pageGroups.stream().flatMap(List::stream).map(tc -> text(tc, "tc_id")).toList();
        var limitsByClass = courseLimits(classIds);
        List<Map<String, Object>> items = new ArrayList<>();
        for (var group : pageGroups) {
            for (var tc : group) {
                String id = text(tc, "tc_id");
                tc.put("actual_num", count(id));
                tc.put("schedule", schedule(id));
                tc.put("version", version(tc));
                tc.put("sc_id", stringId(tc.get("sc_id")));
                tc.put("preferences", jdbc.queryForList("SELECT priority,COUNT(*) AS applicants FROM sc WHERE round_id=? AND tc_id=? AND select_status IN (0,1) GROUP BY priority ORDER BY priority", roundId, id));
                tc.put("audience", audience(tc, limitsByClass.getOrDefault(id, Map.of())));
            }
            var first = group.get(0);
            Map<String, Object> course = new LinkedHashMap<>();
            for (String key : List.of("course_id", "course_name", "course_type", "course_type_label", "credit", "course_level", "open_college")) {
                course.put(key, first.get(key));
            }
            var sharedAudience = first.get("audience");
            boolean sameAudience = group.stream().allMatch(tc -> sharedAudience.equals(tc.get("audience")));
            course.put("audience", sameAudience ? sharedAudience : null);
            course.put("classes", group);
            items.add(course);
        }
        return Map.of("items", items, "total", groups.size(), "round", round, "summary", summary(u.studentId(), text(round,"term")));
    }

    private Map<String, Map<Integer, List<String>>> courseLimits(List<String> classIds) {
        Map<String, Map<Integer, List<String>>> byClass = new HashMap<>();
        if (classIds.isEmpty()) return byClass;

        String placeholders = String.join(",", Collections.nCopies(classIds.size(), "?"));
        String sql = "SELECT tc_id,limit_type,limit_value FROM select_limit WHERE tc_id IN (" + placeholders
                + ") ORDER BY tc_id,limit_type,limit_value";
        for (var row : jdbc.queryForList(sql, classIds.toArray())) {
            byClass.computeIfAbsent(text(row, "tc_id"), id -> new HashMap<>())
                   .computeIfAbsent(number(row.get("limit_type")), type -> new ArrayList<>())
                   .add(text(row, "limit_value"));
        }
        return byClass;
    }

    private Map<String, Object> audience(Map<String, Object> tc, Map<Integer, List<String>> limits) {
        Map<String, Object> audience = new LinkedHashMap<>();
        audience.put("grades", limits.getOrDefault(1, List.of()));
        audience.put("majors", limits.getOrDefault(2, List.of()));
        audience.put("colleges", limits.getOrDefault(3, List.of()));
        audience.put("levels", limits.getOrDefault(4, List.of()));
        if ("1".equals(text(tc, "tc_type"))) {
            var names = Arrays.stream(text(tc, "class_ids").split("[,，]"))
                    .map(String::trim).filter(name -> !name.isEmpty()).distinct().sorted().toList();
            audience.put("class_names", names);
        }
        String special = catalog.specialRequirements().get(text(tc, "tc_id"));
        if (special != null && !special.isBlank()) audience.put("special_requirement", special.strip());
        return audience;
    }
    public Map<String,Object> results(String term){
        CurrentUser u=UserContext.requireRole(RoleEnum.STUDENT);
        var rows=jdbc.queryForList("""
          SELECT sc.*,tc.term,tc.teacher_id,tc.capacity,tc.tc_status,c.course_id,c.course_name,c.course_type,c.credit
          FROM sc JOIN teaching_class tc ON tc.tc_id=sc.tc_id JOIN course c ON c.course_id=tc.course_id
          WHERE sc.student_id=? AND tc.term=? ORDER BY sc.select_time DESC,sc.sc_id DESC
          """,u.studentId(),term);
        for(var row:rows){row.put("version",version(row));row.put("schedule",schedule(text(row,"tc_id")));
            List<Integer> dropRounds=number(row.get("select_status"))==1 ? jdbc.queryForList("SELECT sr.round_id FROM select_round sr JOIN round_class rc ON rc.round_id=sr.round_id WHERE rc.tc_id=? AND sr.term=? AND sr.round_type=3 AND sr.round_status IN (2,3) AND sr.start_time<=? AND sr.end_time>? AND NOT EXISTS(SELECT 1 FROM score WHERE sc_id=?) AND NOT EXISTS(SELECT 1 FROM teach_eval WHERE sc_id=?) ORDER BY sr.start_time DESC,sr.round_id DESC",Integer.class,row.get("tc_id"),term,now(),now(),row.get("sc_id"),row.get("sc_id")) : List.of();
            row.put("withdraw_round_id",dropRounds.isEmpty()?null:dropRounds.get(0));row.put("sc_id",stringId(row.get("sc_id")));}
        return Map.of("items",rows,"summary",summary(u.studentId(),term));
    }
    public Map<String,Object> timetable(String term,int week){
        var result=results(term);@SuppressWarnings("unchecked") var rows=(List<Map<String,Object>>)result.get("items");
        List<Map<String,Object>> blocks=new ArrayList<>();List<String> missing=new ArrayList<>();
        for(var row:rows){if(number(row.get("select_status"))!=1)continue;
            @SuppressWarnings("unchecked") var slots=(List<Map<String,Object>>)row.get("schedule");if(slots.isEmpty())missing.add(text(row,"tc_id"));
            for(var slot:slots){validateSchedule(slot);if(weeks(text(slot,"week_range")).contains(week)){var block=new LinkedHashMap<>(slot);for(String k:List.of("course_name","tc_id","is_exempt_attend"))block.put(k,row.get(k));blocks.add(block);}}
        }
        return Map.of("blocks",blocks,"missing_schedule",missing,"summary",result.get("summary"),"week",week);
    }
    @Transactional(readOnly=true)
    public Map<String,Object> check(Application r){
        var u=UserContext.requireRole(RoleEnum.STUDENT);validateApplication(r);var round=visibleRound(r.roundId());
        int exempt=validate(r,round,student(u.studentId(),text(round,"term"),false),teachingClass(r.tcId(),false),false);
        return Map.of("passed",true,"is_exempt_attend",exempt,"message",exempt==1?"校验通过：重修冲突按免听办理":"校验通过，提交时会再次核验");
    }
    @Transactional(isolation=Isolation.READ_COMMITTED)
    public Map<String,Object> enroll(Application r){
        var u=UserContext.requireRole(RoleEnum.STUDENT);validateApplication(r);
        var round=lockedRound(r.roundId());var s=student(u.studentId(),text(round,"term"),true);var tc=teachingClass(r.tcId(),true);var old=relation(u.studentId(),r.tcId(),true);
        if(old!=null && List.of(0,1).contains(number(old.get("select_status")))){
            if(number(old.get("round_id"))!=r.roundId() || number(old.get("study_type"))!=r.studyType() || !Objects.equals(old.get("priority"),r.priority()))throw conflict("已有有效申请，请查看本人选课结果");
            return receipt(old,"已存在相同申请，未重复写入");
        }
        if(old!=null){expect(old,r.expectedVersion());protect(old);}else if(r.expectedVersion()!=null && !r.expectedVersion().isBlank())throw conflict("原申请已变化，请刷新");
        int exempt=validate(r,round,s,tc,false),status=number(round.get("select_mode"))==1?1:0;
        int stage=number(s.get("level"))==2 || number(tc.get("course_level"))==2?2:1;
        if(old==null)jdbc.update("INSERT INTO sc(student_id,tc_id,round_id,study_type,is_exempt_attend,stu_level,stat_stage,priority,select_status,select_time,drop_time) VALUES(?,?,?,?,?,?,?,?,?,?,NULL)",u.studentId(),r.tcId(),r.roundId(),r.studyType(),exempt,s.get("degree_level"),stage,r.priority(),status,now());
        else jdbc.update("UPDATE sc SET round_id=?,study_type=?,is_exempt_attend=?,stu_level=?,stat_stage=?,priority=?,select_status=?,select_time=?,drop_time=NULL WHERE sc_id=?",r.roundId(),r.studyType(),exempt,s.get("degree_level"),stage,r.priority(),status,now(),old.get("sc_id"));
        log(u.studentId(),r.tcId(),r.roundId(),1,u.studentId(),exempt==1?"重修冲突免听":null);recount(r.tcId());
        return receipt(relation(u.studentId(),r.tcId(),false),status==1?"选课成功":"志愿已登记，等待截止筛选");
    }
    @Transactional(isolation=Isolation.READ_COMMITTED)
    public Map<String,Object> withdraw(String scId,Withdrawal r){
        var u=UserContext.requireRole(RoleEnum.STUDENT);
        if(r==null || r.roundId()==null || !Boolean.TRUE.equals(r.confirmed()))throw invalid("请确认退选并选择补退选轮次");
        var initial=one("SELECT * FROM sc WHERE sc_id=? AND student_id=?",scId,u.studentId());var round=lockedRound(r.roundId());
        student(u.studentId(),text(round,"term"),true);var tc=teachingClass(text(initial,"tc_id"),true);var row=relation(u.studentId(),text(tc,"tc_id"),true);
        if(number(row.get("select_status"))==3)return receipt(row,"已退选，未重复写入");
        expect(row,r.expectedVersion());if(number(row.get("select_status"))!=1)throw conflict("仅已选中记录可退选");requireWindow(round);
        if(number(round.get("round_type"))!=3 || !Objects.equals(round.get("term"),tc.get("term")))throw invalid("请选择本学期开放的补退选轮次");
        requireMember(r.roundId(),text(tc,"tc_id"));protect(row);
        jdbc.update("UPDATE sc SET select_status=3,drop_time=? WHERE sc_id=? AND select_status=1",now(),row.get("sc_id"));
        log(u.studentId(),text(tc,"tc_id"),number(row.get("round_id")),2,u.studentId(),"学生本人确认退选");recount(text(tc,"tc_id"));
        return receipt(relation(u.studentId(),text(tc,"tc_id"),false),"退选成功");
    }
    public List<Map<String,Object>> teachingClasses(String term){
        var u=staff();String extra=u.role()==RoleEnum.TEACHER?" AND tc.teacher_id=?":"";List<Object> args=new ArrayList<>();args.add(term);if(u.role()==RoleEnum.TEACHER)args.add(teacherCode(u));
        return jdbc.queryForList("SELECT tc.*,c.course_name,c.credit FROM teaching_class tc JOIN course c ON c.course_id=tc.course_id WHERE tc.term=?"+extra+" ORDER BY tc.tc_id",args.toArray());
    }
    public Map<String,Object> roster(String tcId,String keyword){
        var tc=teachingClass(tcId,false);authorizeClass(staff(),tc);
        var rows=jdbc.queryForList("""
          SELECT sc.sc_id,sc.student_id,COALESCE(s.student_name,ex.student_name) AS student_name,s.class_name,
                 sc.study_type,sc.is_exempt_attend,sc.select_status
          FROM sc LEFT JOIN student s ON s.student_id=sc.student_id LEFT JOIN v_exchange_student ex ON ex.temp_student_no=sc.student_id
          WHERE sc.tc_id=? AND sc.select_status=1 AND (sc.student_id LIKE ? OR COALESCE(s.student_name,ex.student_name) LIKE ?) ORDER BY sc.student_id
          """,tcId,"%"+keyword+"%","%"+keyword+"%");
        for(var row:rows)row.put("sc_id",stringId(row.get("sc_id")));tc.put("schedule",schedule(tcId));tc.put("actual_num",count(tcId));
        return Map.of("teaching_class",tc,"items",rows,"total",rows.size(),"effective_count",count(tcId));
    }
    @Transactional(isolation=Isolation.READ_COMMITTED)
    public Map<String,Object> adjust(String tcId,Adjustment r){
        var u=staff();if(r==null || r.studentId()==null || r.studentId().isBlank())throw invalid("请输入学号");
        String reason=r.reason()==null?"":r.reason().trim();if(reason.isBlank() || reason.length()>200)throw invalid("调整原因须为1—200字");
        if(!Set.of("add","remove").contains(String.valueOf(r.operation())))throw invalid("未知调整操作");
        var first=teachingClass(tcId,false);authorizeClass(u,first);
        var rounds=jdbc.queryForList("SELECT sr.round_id FROM select_round sr JOIN round_class rc ON rc.round_id=sr.round_id WHERE rc.tc_id=? AND sr.round_status IN (2,3,4) ORDER BY sr.start_time DESC,sr.round_id DESC",Integer.class,tcId);
        if(rounds.isEmpty())throw pending("教学班没有已发布的关联轮次");lockedRound(rounds.get(0));
        var s=student(r.studentId(),text(first,"term"),true);var tc=teachingClass(tcId,true);authorizeClass(u,tc);var old=relation(r.studentId(),tcId,true);
        if("add".equals(r.operation()) && number(s.get("level"))==0)throw pending("交换生培养层次与归属阶段尚无权威映射");
        if("add".equals(r.operation())){
            if(r.studyType()==null || !List.of(1,2,3).contains(r.studyType()))throw invalid("选课类别无效");
            if(!"4".equals(text(tc,"tc_status")))throw conflict("教学班未发布或已停开，不能补加");
            if(old!=null && number(old.get("select_status"))==1)return receipt(old,"学生已在名单中");if(old!=null)protect(old);
            int stage=number(s.get("level"))==2 || number(tc.get("course_level"))==2?2:1;
            if(old==null)jdbc.update("INSERT INTO sc(student_id,tc_id,round_id,study_type,is_exempt_attend,stu_level,stat_stage,priority,select_status,select_time,drop_time) VALUES(?,?,?,?,0,?,?,NULL,1,?,NULL)",r.studentId(),tcId,rounds.get(0),r.studyType(),s.get("degree_level"),stage,now());
            else jdbc.update("UPDATE sc SET round_id=?,study_type=?,is_exempt_attend=0,stu_level=?,stat_stage=?,priority=NULL,select_status=1,select_time=?,drop_time=NULL WHERE sc_id=?",rounds.get(0),r.studyType(),s.get("degree_level"),stage,now(),old.get("sc_id"));
        }else{
            if(old==null || number(old.get("select_status"))!=1)throw conflict("该生当前不在有效名单中");protect(old);
            jdbc.update("UPDATE sc SET select_status=3,drop_time=? WHERE sc_id=? AND select_status=1",now(),old.get("sc_id"));
        }
        var changed=relation(r.studentId(),tcId,false);log(r.studentId(),tcId,number(changed.get("round_id")),4,u.empId(),r.operation()+"："+reason);recount(tcId);return receipt(changed,"名单已调整");
    }
    @Transactional(isolation=Isolation.READ_COMMITTED)
    public Map<String,Object> screen(int roundId){
        var u=UserContext.requireRole(RoleEnum.ACADEMIC_OFFICE);var round=lockedRound(roundId);
        if(number(round.get("select_mode"))!=2 || now().isBefore(time(round,"end_time")) || !List.of(2,3,4).contains(number(round.get("round_status"))))throw conflict("仅已发布且截止的超额筛选轮次可执行筛选");
        // 整轮按轮次、学生、教学班、原申请顺序锁定；未知资料使整轮回滚。
        var ids=jdbc.queryForList("SELECT DISTINCT student_id FROM sc WHERE round_id=? AND select_status=0 ORDER BY student_id",String.class,roundId);
        Map<String,Map<String,Object>> students=new HashMap<>();for(String id:ids)students.put(id,student(id,text(round,"term"),true));
        var classes=jdbc.queryForList("SELECT tc_id FROM round_class WHERE round_id=? ORDER BY tc_id",String.class,roundId);
        Map<String,Map<String,Object>> tcs=new LinkedHashMap<>();for(String id:classes)tcs.put(id,teachingClass(id,true));
        var candidates=jdbc.queryForList("SELECT * FROM sc WHERE round_id=? AND select_status=0 ORDER BY tc_id,sc_id FOR UPDATE",roundId);
        if(candidates.stream().anyMatch(r->!tcs.containsKey(text(r,"tc_id"))))throw pending("候选申请不在开放范围内");
        int accepted=0,rejected=0;
        for(String tcId:classes){var tc=tcs.get(tcId);int remaining=number(tc.get("capacity"))-count(tcId);if(remaining<0)throw conflict("教学班已超容量，请先核实名单："+tcId);
            var batch=new ArrayList<>(candidates.stream().filter(r->tcId.equals(r.get("tc_id"))).toList());Collections.shuffle(batch);batch.sort(Comparator.comparingInt(r->number(r.get("priority"))));
            for(var row:batch){var r=new Application(roundId,tcId,number(row.get("study_type")),number(row.get("priority")),null);boolean allowed=true;int exempt=0;String reason="容量不足";
                try{exempt=validate(r,round,students.get(text(row,"student_id")),tc,true);}catch(BizException e){if("DATA_PENDING".equals(e.getCode()))throw e;allowed=false;reason=e.getMessage();}
                int state=allowed && remaining>0?1:2;jdbc.update("UPDATE sc SET select_status=?,is_exempt_attend=?,drop_time=NULL WHERE sc_id=? AND select_status=0",state,exempt,row.get("sc_id"));
                if(state==1){accepted++;remaining--;}else{rejected++;log(text(row,"student_id"),tcId,roundId,3,u.empId(),reason.length()>200?reason.substring(0,200):reason);}
            }recount(tcId);
        }
        return Map.of("accepted",accepted,"rejected",rejected,"message",candidates.isEmpty()?"没有待筛选申请，未重复筛选":"整轮筛选已完成");
    }
    public Map<String,Object> stopPreview(String term){
        UserContext.requireRole(RoleEnum.ACADEMIC_OFFICE);
        return Map.of("write_enabled",false,"reason","停开状态与日志编码尚未由全组确认，仅预览，不执行失效写入","items",jdbc.queryForList("SELECT tc.tc_id,c.course_name,tc.tc_status,COUNT(sc.sc_id) AS affected_count FROM teaching_class tc JOIN course c ON c.course_id=tc.course_id LEFT JOIN sc ON sc.tc_id=tc.tc_id AND sc.select_status IN (0,1) WHERE tc.term=? AND tc.tc_status IN ('2','5') GROUP BY tc.tc_id,c.course_name,tc.tc_status ORDER BY tc.tc_id",term));
    }

    private int validate(Application r,Map<String,Object> round,Map<String,Object> s,Map<String,Object> tc,boolean screening){
        if(!screening)requireWindow(round);String id=text(s,"student_id"),tcId=text(tc,"tc_id");requireMember(r.roundId(),tcId);
        if(!Objects.equals(round.get("term"),tc.get("term")) || !"4".equals(text(tc,"tc_status")))throw invalid("教学班学期不符或未发布");
        int level=number(s.get("level")),target=number(round.get("target_level"));if(level==0)throw pending("来校交换生培养层次与成绩归属阶段尚无权威映射");if(target!=3 && target!=level)throw invalid("轮次不适用于当前培养层次");
        boolean retake=r.studyType()==2;if(retake && number(round.get("allow_retake"))!=1)throw invalid("本轮未开放重修");
        int mode=number(round.get("select_mode"));if(mode==2 && (r.priority()==null || r.priority()<1 || r.priority()>3))throw invalid("超额筛选须选择1—3志愿");if(mode==1 && r.priority()!=null)throw invalid("先到先得不填写志愿");
        Map<Integer,Set<String>> limits=new HashMap<>();for(var row:jdbc.queryForList("SELECT limit_type,limit_value FROM select_limit WHERE tc_id=?",tcId))limits.computeIfAbsent(number(row.get("limit_type")),k->new HashSet<>()).add(text(row,"limit_value"));
        for(var entry:limits.entrySet()){
            if(entry.getKey()==1 && retake)continue;
            Object value=switch(entry.getKey()){case 1->s.get("grade");case 2->s.get("major_id");case 3->s.get("college_id");case 4->s.get("degree_level");default->throw pending("未知限选类型");};
            if(value==null)throw pending("限选所需学生资料缺失");if(!entry.getValue().contains(value.toString()))throw invalid("不满足课程限选条件（类型"+entry.getKey()+"）");
        }
        if("1".equals(text(tc,"tc_type")) && !retake){String names=text(tc,"class_ids");if(names.isBlank() || s.get("class_name")==null)throw pending("必修班面向班级资料缺失");if(!Arrays.stream(names.split("[,，]",-1)).map(String::trim).toList().contains(text(s,"class_name")))throw invalid("不在必修班面向班级范围内");}
        if(r.studyType()==3 && !"3".equals(text(tc,"tc_type")))throw invalid("辅修须选择已认定的双学位教学班");
        if(level==2){var schemes=jdbc.queryForList("SELECT * FROM scheme_assignment WHERE student_id=? AND effective_from<=? AND (effective_to IS NULL OR effective_to>=?)",id,now().toLocalDate(),now().toLocalDate());if(schemes.size()!=1)throw pending("研究生有效培养方案缺失或不唯一");var scheme=schemes.get(0);if(jdbc.queryForObject("SELECT COUNT(*) FROM scheme_course WHERE scheme_id=? AND course_id=? AND (direction_id IS NULL OR direction_id=?)",Integer.class,scheme.get("scheme_id"),tc.get("course_id"),scheme.get("direction_id"))==0)throw invalid("课程不在有效培养方案及研究方向内");}
        if(jdbc.queryForObject("SELECT COUNT(*) FROM sc JOIN teaching_class tc ON tc.tc_id=sc.tc_id WHERE sc.student_id=? AND tc.term=? AND tc.course_id=? AND sc.tc_id<>? AND sc.select_status IN (0,1)",Integer.class,id,round.get("term"),tc.get("course_id"),tcId)>0)throw invalid("同学期不能重复选修同课的不同教学班");
        if(retake && jdbc.queryForObject("SELECT COUNT(*) FROM score s JOIN sc ON sc.sc_id=s.sc_id JOIN teaching_class tc ON tc.tc_id=sc.tc_id WHERE sc.student_id=? AND tc.course_id=? AND s.score_status=3 AND (s.total_score<60 OR s.score_level IN ('不及格','不合格'))",Integer.class,id,tc.get("course_id"))==0)throw invalid("没有该课程已发布的不及格记录，无法认定重修资格");
        var occupied=jdbc.queryForList("SELECT sc.tc_id,sc.is_exempt_attend,c.credit,c.course_name FROM sc JOIN teaching_class tc ON tc.tc_id=sc.tc_id JOIN course c ON c.course_id=tc.course_id WHERE sc.student_id=? AND tc.term=? AND sc.tc_id<>? AND (sc.select_status=1 OR (sc.select_status=0 AND sc.round_id=? AND ?=0))",id,round.get("term"),tcId,r.roundId(),screening?1:0);
        BigDecimal used=credit(tc.get("credit"));var targetSchedule=schedule(tcId);if(targetSchedule.isEmpty())throw pending("目标教学班尚无完整排课");targetSchedule.forEach(SelectionRules::validateSchedule);int exempt=0;
        for(var other:occupied){used=used.add(credit(other.get("credit")));if(number(other.get("is_exempt_attend"))==1)continue;var otherSchedule=schedule(text(other,"tc_id"));if(otherSchedule.isEmpty())throw pending("已选课程排课缺失");for(var a:targetSchedule)for(var b:otherSchedule)if(overlaps(a,b)){if(retake && number(round.get("retake_ignore_conflict"))==1)exempt=1;else throw invalid("上课时间冲突：《"+text(other,"course_name")+"》");}}
        if(used.compareTo(credit(round.get("max_credit")))>0)throw invalid("超过本轮学分上限，占用学分 "+used);
        if(!screening && mode==1 && count(tcId)>=number(tc.get("capacity")))throw invalid("教学班容量已满");return exempt;
    }
    private Map<String,Object> student(String id,String term,boolean lock){
        if(id==null || id.isBlank() || id.length()>20)throw invalid("学号无效");var rows=jdbc.queryForList("SELECT * FROM student WHERE student_id=?"+(lock?" FOR UPDATE":""),id);
        if(rows.isEmpty()){
            var incoming=jdbc.queryForList("SELECT * FROM v_exchange_student WHERE temp_student_no=?"+(lock?" FOR UPDATE":""),id);if(incoming.size()!=1)throw invalid("学生身份不存在");var ex=incoming.get(0);
            if(!"ACTIVE".equals(text(ex,"status_code")) || !term.equals(text(ex,"term_ref")) || now().toLocalDate().isBefore(date(ex.get("start_on"))) || now().toLocalDate().isAfter(date(ex.get("end_on"))))throw invalid("交换生未在本学期有效在读范围内");
            Map<String,Object> s=new LinkedHashMap<>();s.put("student_id",id);s.put("level",0);s.put("college_id",ex.get("college_ref"));s.put("major_id",ex.get("major_ref"));return s;
        }
        var s=rows.get(0);if(!"在籍".equals(text(s,"final_enroll")))throw invalid("学生当前不是在籍状态");String degree=text(s,"degree_level");if(!List.of("本科","研究生").contains(degree))throw pending("培养层次编码未识别");s.put("level","研究生".equals(degree)?2:1);return s;
    }
    private void validateApplication(Application r){if(r==null || r.roundId()==null || r.roundId()<1 || r.tcId()==null || r.tcId().isBlank() || r.tcId().length()>20 || r.studyType()==null || !List.of(1,2,3).contains(r.studyType()))throw invalid("轮次、教学班或选课类别无效");}
    private Map<String,Object> visibleRound(int id){var r=one("SELECT * FROM select_round WHERE round_id=?",id);if(!List.of(2,3,4).contains(number(r.get("round_status"))))throw BizException.forbidden("轮次尚未发布");return r;}
    private Map<String,Object> lockedRound(int id){return one("SELECT * FROM select_round WHERE round_id=? FOR UPDATE",id);}
    private boolean open(Map<String,Object> r){return List.of(2,3).contains(number(r.get("round_status"))) && !now().isBefore(time(r,"start_time")) && now().isBefore(time(r,"end_time"));}
    private void requireWindow(Map<String,Object> r){if(!open(r))throw conflict("轮次尚未开始、已截止或不可办理");}
    private void requireMember(int round,String tc){if(jdbc.queryForObject("SELECT COUNT(*) FROM round_class WHERE round_id=? AND tc_id=?",Integer.class,round,tc)==0)throw invalid("教学班不在本轮开放范围内");}
    private Map<String,Object> teachingClass(String id,boolean lock){return one("SELECT tc.*,c.course_name,c.course_type,c.credit,c.course_level,c.open_college FROM teaching_class tc JOIN course c ON c.course_id=tc.course_id WHERE tc.tc_id=?"+(lock?" FOR UPDATE":""),id);}
    private List<Map<String,Object>> schedule(String id){return jdbc.queryForList("SELECT week_range,week_day,start_slot,end_slot,room_id FROM schedule_result WHERE tc_id=? ORDER BY week_day,start_slot,week_range",id);}
    private Map<String,Object> relation(String s,String tc,boolean lock){var rows=jdbc.queryForList("SELECT * FROM sc WHERE student_id=? AND tc_id=?"+(lock?" FOR UPDATE":""),s,tc);return rows.isEmpty()?null:rows.get(0);}
    private int count(String tc){return jdbc.queryForObject("SELECT COUNT(*) FROM sc WHERE tc_id=? AND select_status=1",Integer.class,tc);}
    private void recount(String tc){jdbc.update("UPDATE teaching_class SET actual_num=? WHERE tc_id=?",count(tc),tc);}
    private void protect(Map<String,Object> r){if(jdbc.queryForObject("SELECT COUNT(*) FROM score WHERE sc_id=?",Integer.class,r.get("sc_id"))>0 || jdbc.queryForObject("SELECT COUNT(*) FROM teach_eval WHERE sc_id=?",Integer.class,r.get("sc_id"))>0)throw conflict("该记录已有成绩或评教，需由教务核实后续业务保护");}
    private void log(String s,String tc,int round,int type,String operator,String remark){if(operator==null || operator.isBlank())throw pending("操作人工号缺失");jdbc.update("INSERT INTO select_log(student_id,tc_id,round_id,action_type,operator_id,action_time,remark) VALUES(?,?,?,?,?,?,?)",s,tc,round,type,operator,now(),remark);}
    private Map<String,Object> summary(String s,String term){return one("SELECT COUNT(*) AS courses,COALESCE(SUM(c.credit),0) AS credits FROM sc JOIN teaching_class tc ON tc.tc_id=sc.tc_id JOIN course c ON c.course_id=tc.course_id WHERE sc.student_id=? AND tc.term=? AND sc.select_status=1",s,term);}
    private CurrentUser staff(){return UserContext.requireRole(RoleEnum.TEACHER,RoleEnum.ACADEMIC_OFFICE);}
    private String teacherCode(CurrentUser u){var codes=jdbc.queryForList("SELECT dict_code FROM sys_code_dict WHERE dict_group='teacher_map' AND dict_name=? AND is_enabled=1",String.class,u.empId());if(codes.size()!=1)throw pending("教师工号映射缺失或不唯一");return codes.get(0);}
    private void authorizeClass(CurrentUser u,Map<String,Object> tc){if(u.role()==RoleEnum.TEACHER && !teacherCode(u).equals(text(tc,"teacher_id")))throw BizException.forbidden("仅能操作本人授课教学班");}
    private Map<String,Object> one(String sql,Object...args){var rows=jdbc.queryForList(sql,args);if(rows.isEmpty())throw BizException.notFound("记录不存在");return rows.get(0);}
    private Map<String,Object> receipt(Map<String,Object> row,String message){var r=new LinkedHashMap<>(row);r.put("version",version(row));r.put("sc_id",stringId(row.get("sc_id")));r.put("message",message);r.put("actual_num",count(text(row,"tc_id")));return r;}
    private String version(Map<String,Object> r){
        if(r.get("sc_id")==null)return "";
        Long logId=jdbc.queryForObject("SELECT COALESCE(MAX(l.log_id),0) FROM select_log l JOIN sc s ON s.student_id=l.student_id AND s.tc_id=l.tc_id WHERE s.sc_id=?",Long.class,r.get("sc_id"));
        return r.get("sc_id")+"|"+(r.containsKey("original_round_id")?r.get("original_round_id"):r.get("round_id"))+"|"+r.get("select_status")+"|"+r.get("select_time")+"|"+logId;
    }
    private void expect(Map<String,Object> r,String expected){if(expected==null || !version(r).equals(expected))throw conflict("申请已变化，请刷新后重新办理");}
    private String stringId(Object id){return id==null?null:id.toString();}
    private String text(Map<String,Object> r,String key){return r.get(key)==null?"":r.get(key).toString().trim();}
    private LocalDateTime time(Map<String,Object> r,String key){Object v=r.get(key);if(v instanceof Timestamp t)return t.toLocalDateTime();if(v instanceof LocalDateTime t)return t;throw pending("轮次时间资料无效");}
    private LocalDate date(Object v){if(v instanceof java.sql.Date d)return d.toLocalDate();if(v instanceof LocalDate d)return d;throw pending("有效期资料无效");}
    private BizException invalid(String text){return BizException.unprocessable("SELECTION_INVALID",text);}
    private BizException conflict(String text){return BizException.conflict("SELECTION_CONFLICT",text);}
}
