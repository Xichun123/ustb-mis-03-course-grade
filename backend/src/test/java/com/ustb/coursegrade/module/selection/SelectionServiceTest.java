package com.ustb.coursegrade.module.selection;

import com.ustb.coursegrade.common.BizException;
import com.ustb.coursegrade.common.auth.*;
import com.ustb.coursegrade.module.selection.SelectionCatalogProperties.CourseType;
import org.h2.jdbcx.JdbcDataSource;
import org.junit.jupiter.api.*;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.springframework.aop.framework.ProxyFactory;
import org.springframework.boot.context.properties.bind.Bindable;
import org.springframework.boot.context.properties.bind.Binder;
import org.springframework.boot.context.properties.source.ConfigurationPropertySources;
import org.springframework.boot.env.YamlPropertySourceLoader;
import org.springframework.core.io.ClassPathResource;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DataSourceTransactionManager;
import org.springframework.jdbc.datasource.init.ResourceDatabasePopulator;
import org.springframework.transaction.annotation.AnnotationTransactionAttributeSource;
import org.springframework.transaction.interceptor.TransactionInterceptor;
import java.io.IOException;
import java.time.*;
import java.util.*;
import java.util.concurrent.*;
import static org.junit.jupiter.api.Assertions.*;

/** 完全隔离的内存库测试，不加载应用配置、不连接共享云库。 */
class SelectionServiceTest {
    JdbcTemplate jdbc;
    SelectionService service;
    static final String TERM="2026-2027-1";
    private static final int CATALOG_DEMO_ROUND = 910;
    @BeforeEach void setup() throws IOException {
        JdbcDataSource ds=new JdbcDataSource();ds.setURL("jdbc:h2:mem:"+UUID.randomUUID()+";MODE=MySQL;DATABASE_TO_LOWER=TRUE;DB_CLOSE_DELAY=-1;LOCK_TIMEOUT=10000");
        jdbc=new JdbcTemplate(ds);
        for(String ddl:List.of(
            "CREATE TABLE sel_select_round(round_id INT PRIMARY KEY,term VARCHAR(11),round_name VARCHAR(30),round_type INT,target_level INT,start_at TIMESTAMP,end_at TIMESTAMP,max_credit DECIMAL(5,1),select_mode INT,round_status INT,allow_retake INT,retake_ignore_conflict INT,notice_content VARCHAR(200),published_at TIMESTAMP,approver_id VARCHAR(10),approved_at TIMESTAMP,approve_opinion VARCHAR(200))",
            "CREATE TABLE org_organization(org_id VARCHAR(6) PRIMARY KEY,org_name VARCHAR(100),college_id VARCHAR(4))",
            "CREATE TABLE sys_person(person_id VARCHAR(20) PRIMARY KEY,person_name VARCHAR(100))",
            "CREATE TABLE aca_class(class_id VARCHAR(10) PRIMARY KEY,class_name VARCHAR(50))",
            "CREATE TABLE std_student(student_id VARCHAR(20) PRIMARY KEY,person_id VARCHAR(20),class_id VARCHAR(10),major_id VARCHAR(6),org_id VARCHAR(6),grade VARCHAR(40),enrollment_year INT,student_type VARCHAR(2),student_status VARCHAR(2))",
            "CREATE TABLE aca_course(course_id VARCHAR(10) PRIMARY KEY,course_name VARCHAR(100),course_type VARCHAR(2),credit DECIMAL(5,1),org_id VARCHAR(6))",
            "CREATE TABLE hr_employee(emp_id VARCHAR(10) PRIMARY KEY,emp_name VARCHAR(50))",
            "CREATE TABLE al_dict_type(dict_type VARCHAR(40) PRIMARY KEY,type_name VARCHAR(100),is_system TINYINT DEFAULT 0,dict_status VARCHAR(2) DEFAULT '01',remark VARCHAR(200))",
            "CREATE TABLE al_dict_item(dict_id BIGINT AUTO_INCREMENT PRIMARY KEY,dict_type VARCHAR(40),item_code VARCHAR(100),item_name VARCHAR(200),sort_no INT DEFAULT 0,version VARCHAR(40),effective_from DATE,item_status VARCHAR(2) DEFAULT '01')",
            "CREATE TABLE aca_course_id(course_id VARCHAR(10) PRIMARY KEY,pre_course_id VARCHAR(40))",
            "CREATE TABLE sel_exchange_score_convert(convert_id BIGINT AUTO_INCREMENT PRIMARY KEY,student_id VARCHAR(20),course_id VARCHAR(10),converted_score DECIMAL(5,2),converted_level VARCHAR(10),audit_status INT)",
            "CREATE TABLE aca_teaching_class(tc_id VARCHAR(20) PRIMARY KEY,term VARCHAR(11),course_id VARCHAR(10),emp_id VARCHAR(10),class_ids VARCHAR(100),capacity INT,tc_type VARCHAR(20),tc_status VARCHAR(2),actual_num INT,selected_count INT DEFAULT 0,hours DECIMAL(18,2) DEFAULT 48.00,open_lower INT DEFAULT 6,no_open_reason VARCHAR(100))",
            "CREATE TABLE sel_round_class(round_id INT,tc_id VARCHAR(20),PRIMARY KEY(round_id,tc_id))",
            "CREATE TABLE sel_select_limit(limit_id INT AUTO_INCREMENT PRIMARY KEY,tc_id VARCHAR(20),limit_type INT,limit_value VARCHAR(20))",
            "CREATE TABLE aca_schedule_result(sched_id VARCHAR(14) PRIMARY KEY,tc_id VARCHAR(20),week_range VARCHAR(20),week_day VARCHAR(20),start_slot INT,end_slot INT,room_id VARCHAR(12),sched_status VARCHAR(2) DEFAULT '0')",
            "CREATE TABLE sel_sc(sc_id BIGINT AUTO_INCREMENT PRIMARY KEY,student_id VARCHAR(20),tc_id VARCHAR(20),round_id INT,study_type INT,is_exempt_attend INT,stu_level VARCHAR(20),stat_stage INT,priority INT,select_status INT,select_time TIMESTAMP,drop_time TIMESTAMP,UNIQUE(student_id,tc_id),CHECK(select_status=3 OR drop_time IS NULL),CHECK(is_exempt_attend=0 OR study_type=2))",
            "CREATE TABLE sel_select_log(log_id BIGINT AUTO_INCREMENT PRIMARY KEY,student_id VARCHAR(20),tc_id VARCHAR(20),round_id INT,action_type INT,operator_id VARCHAR(40),operated_at TIMESTAMP,remark VARCHAR(500),CHECK(action_type<>4 OR remark IS NOT NULL))",
            "CREATE TABLE sel_score(sc_id BIGINT,score_status INT,total_score DECIMAL(5,2),score_level VARCHAR(6))",
            "CREATE TABLE sel_teach_eval(sc_id BIGINT)",
            "CREATE TABLE sel_tc_eval_result(tc_id VARCHAR(20) PRIMARY KEY,eval_num INT,avg_eval_score DECIMAL(5,2),calc_time TIMESTAMP)",
            "CREATE TABLE grad_scheme_assignment(assignment_id VARCHAR(50) PRIMARY KEY,student_id VARCHAR(20),scheme_id VARCHAR(50),direction_id VARCHAR(20),valid_from DATE,valid_to DATE)",
            "CREATE TABLE grad_scheme_course(scheme_course_id VARCHAR(50) PRIMARY KEY,scheme_id VARCHAR(50),course_id VARCHAR(10),direction_id VARCHAR(20))",
            "CREATE TABLE v_exchange_student(temp_student_no VARCHAR(20),student_name VARCHAR(50),term_ref VARCHAR(11),college_ref VARCHAR(4),major_ref VARCHAR(6),start_on DATE,end_on DATE,status_code VARCHAR(20))"
        ))jdbc.execute(ddl);
        jdbc.update("INSERT INTO org_organization VALUES('ORG001','经管学院','0001'),('ORG002','计通学院','0002')");
        jdbc.update("INSERT INTO aca_class VALUES('CLS241','信管241')");
        jdbc.update("INSERT INTO sel_select_round(round_id,term,round_name,round_type,target_level,start_at,end_at,max_credit,select_mode,round_status,allow_retake,retake_ignore_conflict,notice_content) VALUES(1,?,'补退选',3,3,'2026-11-20 09:00:00','2026-12-10 17:00:00',30,1,3,1,1,'测试通知')",TERM);
        jdbc.update("INSERT INTO sel_select_round(round_id,term,round_name,round_type,target_level,start_at,end_at,max_credit,select_mode,round_status,allow_retake,retake_ignore_conflict,notice_content) VALUES(2,?,'截止预选',1,3,'2026-11-01 09:00:00','2026-11-02 17:00:00',30,2,4,1,1,'测试通知')",TERM);
        for(String id:List.of("S1","S2","S3")){
            jdbc.update("INSERT INTO sys_person VALUES(?,?)", "P_"+id, id);
            jdbc.update("INSERT INTO std_student VALUES(?,?,'CLS241','M1','ORG001','2024',2024,'01','01')", id, "P_"+id);
        }
        for(String id:List.of("C001","C002","C003"))jdbc.update("INSERT INTO aca_course VALUES(?,?,'11',3,'ORG001')",id,id);
        jdbc.update("INSERT INTO hr_employee VALUES('E1','王老师'),('OTHER','李老师')");
        jdbc.update("INSERT INTO al_dict_item(dict_type,item_code,item_name,item_status) VALUES('system_param','min_open_students','6','01')");
        for(String id:List.of("A","B","D")){
            jdbc.update("INSERT INTO aca_teaching_class(tc_id,term,course_id,emp_id,class_ids,capacity,tc_type,tc_status,actual_num,selected_count) VALUES(?,?,?,'E1','信管241',1,'2','4',0,0)",id,TERM,id.equals("A")?"C001":id.equals("B")?"C002":"C003");
            jdbc.update("INSERT INTO aca_schedule_result(sched_id,tc_id,week_range,week_day,start_slot,end_slot,room_id,sched_status) VALUES(?,?,?,'1',1,2,'教室1','1')","SCH_"+id,id,id.equals("A")?"1-8":id.equals("B")?"9-16":"1-16");
            for(int round:List.of(1,2))jdbc.update("INSERT INTO sel_round_class VALUES(?,?)",round,id);
        }
        var demoConfig = new YamlPropertySourceLoader().load("selection-catalog-demo",
                new ClassPathResource("selection/catalog-demo.yml"));
        var catalog = new Binder(ConfigurationPropertySources.from(demoConfig))
                .bind("selection.catalog", Bindable.of(SelectionCatalogProperties.class)).get();
        var target=new SelectionService(jdbc,Clock.fixed(Instant.parse("2026-11-24T04:00:00Z"),ZoneId.of("Asia/Shanghai")),catalog);
        var proxy=new ProxyFactory(target);proxy.setProxyTargetClass(true);
        proxy.addAdvice(new TransactionInterceptor(new DataSourceTransactionManager(ds),new AnnotationTransactionAttributeSource()));
        service=(SelectionService)proxy.getProxy();asStudent("S1");
    }
    @AfterEach void cleanup(){UserContext.clear();jdbc.execute("SHUTDOWN");}
    void asStudent(String id){UserContext.set(new CurrentUser(id,id,RoleEnum.STUDENT,id,null,null,"0001",null));}
    void office(){UserContext.set(new CurrentUser("office","office",RoleEnum.ACADEMIC_OFFICE,null,"E0",null,null,null));}
    void teacher(){UserContext.set(new CurrentUser("teacher","teacher",RoleEnum.TEACHER,null,"E1","T1","0001",null));}
    SelectionService.Application application(String tc){return new SelectionService.Application(1,tc,1,null,null);}
    int count(String sql){return jdbc.queryForObject(sql,Integer.class);}

    @Test void enrollmentAndRepeatAreAtomic(){
        var result=service.enroll(application("A"));assertEquals(1,SelectionRules.number(result.get("select_status")));
        assertInstanceOf(String.class,result.get("sc_id"));service.enroll(application("A"));
        assertEquals(1,count("SELECT COUNT(*) FROM sel_sc"));assertEquals(1,count("SELECT COUNT(*) FROM sel_select_log"));assertEquals(1,count("SELECT actual_num FROM aca_teaching_class WHERE tc_id='A'"));
    }
    @Test void disjointWeeksDoNotConflictButOverlapDoes(){service.enroll(application("A"));service.enroll(application("B"));var e=assertThrows(BizException.class,()->service.enroll(application("D")));assertTrue(e.getMessage().contains("冲突"));assertEquals(2,count("SELECT COUNT(*) FROM sel_sc"));}
    @Test void capacityCannotBeExceeded(){service.enroll(application("A"));asStudent("S2");assertThrows(BizException.class,()->service.enroll(application("A")));assertEquals(1,count("SELECT COUNT(*) FROM sel_sc"));}
    @Test void creditIncludesPendingApplications(){jdbc.update("UPDATE sel_select_round SET max_credit=5 WHERE round_id=1");jdbc.update("INSERT INTO sel_sc(student_id,tc_id,round_id,study_type,is_exempt_attend,stu_level,stat_stage,priority,select_status,select_time) VALUES('S1','B',1,1,0,'本科',1,1,0,CURRENT_TIMESTAMP)");var e=assertThrows(BizException.class,()->service.enroll(application("A")));assertTrue(e.getMessage().contains("学分"));}
    @Test void sameLimitTypeIsOrAndDifferentTypesAreAnd(){jdbc.update("INSERT INTO sel_select_limit(tc_id,limit_type,limit_value) VALUES('A',1,'2023'),('A',1,'2024'),('A',2,'OTHER')");assertThrows(BizException.class,()->service.enroll(application("A")));jdbc.update("INSERT INTO sel_select_limit(tc_id,limit_type,limit_value) VALUES('A',2,'M1')");assertDoesNotThrow(()->service.enroll(application("A")));}

    private void loadCatalogDemo() {
        var demo = new ResourceDatabasePopulator(new ClassPathResource("selection/catalog-demo.sql"));
        demo.setSqlScriptEncoding("UTF-8");
        demo.execute(Objects.requireNonNull(jdbc.getDataSource()));
    }

    @ParameterizedTest(name = "{1} 显示为 {2}")
    @CsvSource(textBlock = """
            UNIVERSITY_REQUIRED, DEMO0001, 校级必修
            COLLEGE_REQUIRED, DEMO0002, 院级必修
            MAJOR_REQUIRED, DEMO0003, 专业必修
            MAJOR_ELECTIVE, DEMO0004, 专业选修
            GENERAL_ELECTIVE, DEMO0005, 通识选修
            PRACTICE, DEMO0006, 实践环节
            """)
    void courseTypeFilterAlignsWithGroup2SixCategoriesBeforePaging(CourseType filter, String id, String label) {
        loadCatalogDemo();

        var page = service.courses(CATALOG_DEMO_ROUND, "", 1, 1, filter);
        assertEquals(1, page.get("total"));
        var course = rows(page.get("items")).get(0);
        assertEquals(id, course.get("course_id"));
        assertEquals(label, course.get("course_type_label"));
        assertTrue(rows(service.courses(CATALOG_DEMO_ROUND, "", 2, 1, filter).get("items")).isEmpty());
    }

    @Test void courseTypeMappingAndOptionsMatchGroup2SixCategories() {
        var props = new SelectionCatalogProperties(Map.of(), Map.of());
        assertEquals(CourseType.UNIVERSITY_REQUIRED, props.courseType("C11", "11"));
        assertEquals(CourseType.COLLEGE_REQUIRED, props.courseType("C12", "12"));
        assertEquals(CourseType.MAJOR_REQUIRED, props.courseType("C13", "13"));
        assertEquals(CourseType.MAJOR_ELECTIVE, props.courseType("C14", "14"));
        assertEquals(CourseType.GENERAL_ELECTIVE, props.courseType("C15", "15"));
        assertEquals(CourseType.PRACTICE, props.courseType("C16", "16"));
        assertEquals(CourseType.UNCLASSIFIED, props.courseType("C99", null));

        assertEquals(CourseType.MAJOR_ELECTIVE, CourseType.fromParam("14"));
        assertEquals(CourseType.MAJOR_ELECTIVE, CourseType.fromParam("MAJOR_ELECTIVE"));
        assertNull(CourseType.fromParam(""));
        assertThrows(IllegalArgumentException.class, () -> CourseType.fromParam("99"));

        @SuppressWarnings("unchecked")
        var options = (List<Map<String, String>>) service.context().get("course_types");
        assertEquals(6, options.size());
        assertEquals(List.of("11", "12", "13", "14", "15", "16"),
                options.stream().map(m -> m.get("value")).toList());
        assertEquals(List.of("校级必修", "院级必修", "专业必修", "专业选修", "通识选修", "实践环节"),
                options.stream().map(m -> m.get("label")).toList());
    }

    @Test void courseListKeepsAudienceAndSpecialRequirementsWithTheirTeachingClass() {
        loadCatalogDemo();

        var page = service.courses(CATALOG_DEMO_ROUND, "学术英语", 1, 10, CourseType.GENERAL_ELECTIVE);
        assertEquals(1, page.get("total"));
        var course = rows(page.get("items")).get(0);
        assertNull(course.get("audience"));
        var classes = rows(course.get("classes"));
        assertEquals(List.of("DEMO-LANG-01", "DEMO-LANG-02"), classes.stream().map(tc -> tc.get("tc_id")).toList());
        assertEquals(Map.of(
                "grades", List.of("2024", "2025"), "majors", List.of("120102"),
                "colleges", List.of("0001"), "levels", List.of("本科"),
                "special_requirement", "大学英语四级成绩 > 500 分，或六级成绩 > 500 分"
        ), classes.get(0).get("audience"));
        assertEquals(Map.of(
                "grades", List.of("2026"), "majors", List.of("080901"),
                "colleges", List.of(), "levels", List.of()
        ), classes.get(1).get("audience"));
    }

    @Test void courseListUsesSharedAudienceAndKeepsUnrestrictedFieldsEmpty() {
        loadCatalogDemo();

        var page = service.courses(CATALOG_DEMO_ROUND, "", 1, 10, null);
        assertEquals(6, page.get("total"));
        var courses = rows(page.get("items"));
        assertEquals(Map.of("grades", List.of("2024"), "majors", List.of("120102"),
                "colleges", List.of(), "levels", List.of(), "class_names", List.of("信管241", "信管242")),
                courses.get(0).get("audience"));
        assertEquals(Map.of("grades", List.of(), "majors", List.of("080901"), "colleges", List.of(), "levels", List.of()),
                courses.get(3).get("audience"));
        assertEquals(Map.of("grades", List.of(), "majors", List.of(), "colleges", List.of(), "levels", List.of()),
                courses.get(5).get("audience"));
    }

    @SuppressWarnings("unchecked")
    private static List<Map<String, Object>> rows(Object value) {
        return (List<Map<String, Object>>) value;
    }
    @Test void staleWithdrawalIsRejectedAndHistoryIsKept(){var r=service.enroll(application("A"));String id=r.get("sc_id").toString();assertThrows(BizException.class,()->service.withdraw(id,new SelectionService.Withdrawal(1,"stale",true)));var drop=service.withdraw(id,new SelectionService.Withdrawal(1,r.get("version").toString(),true));assertEquals(3,SelectionRules.number(drop.get("select_status")));assertEquals(0,count("SELECT actual_num FROM aca_teaching_class WHERE tc_id='A'"));assertEquals(2,count("SELECT COUNT(*) FROM sel_select_log"));assertEquals(1,count("SELECT COUNT(*) FROM sel_sc"));}
    @Test void downstreamScoresProtectWithdrawal(){var r=service.enroll(application("A"));jdbc.update("INSERT INTO sel_score(sc_id,score_status) VALUES(?,0)",r.get("sc_id"));assertThrows(BizException.class,()->service.withdraw(r.get("sc_id").toString(),new SelectionService.Withdrawal(1,r.get("version").toString(),true)));assertEquals(1,count("SELECT actual_num FROM aca_teaching_class WHERE tc_id='A'"));}
    @Test void logFailureRollsBackEnrollmentAndCount(){jdbc.execute("ALTER TABLE sel_select_log ADD CONSTRAINT block_log CHECK(action_type<>1)");assertThrows(Exception.class,()->service.enroll(application("A")));assertEquals(0,count("SELECT COUNT(*) FROM sel_sc"));assertEquals(0,count("SELECT actual_num FROM aca_teaching_class WHERE tc_id='A'"));}
    @Test void teacherScopeAndRequiredReasonAreEnforced(){jdbc.update("UPDATE aca_teaching_class SET emp_id='OTHER' WHERE tc_id='B'");teacher();assertThrows(BizException.class,()->service.roster("B",""));assertThrows(BizException.class,()->service.adjust("A",new SelectionService.Adjustment("S1","add",1," ")));service.adjust("A",new SelectionService.Adjustment("S1","add",1,"补录批准"));assertEquals(1,count("SELECT COUNT(*) FROM sel_select_log WHERE action_type=4"));}
    @Test void teacherAdjustmentBypassesCapacityAndWindow(){jdbc.update("UPDATE aca_teaching_class SET capacity=0 WHERE tc_id='A'");jdbc.update("UPDATE sel_select_round SET end_at='2026-11-21 00:00:00' WHERE round_id=1");teacher();service.adjust("A",new SelectionService.Adjustment("S1","add",1,"特殊批准"));assertEquals(1,count("SELECT actual_num FROM aca_teaching_class WHERE tc_id='A'"));}
    @Test void screeningRespectsPrioritiesAndIsRepeatable(){jdbc.update("INSERT INTO sel_sc(student_id,tc_id,round_id,study_type,is_exempt_attend,stu_level,stat_stage,priority,select_status,select_time) VALUES('S1','A',2,1,0,'本科',1,2,0,CURRENT_TIMESTAMP),('S2','A',2,1,0,'本科',1,1,0,CURRENT_TIMESTAMP)");office();var r=service.screen(2);assertEquals(1,r.get("accepted"));assertEquals(1,r.get("rejected"));assertEquals(1,count("SELECT select_status FROM sel_sc WHERE student_id='S2'"));assertEquals(0,service.screen(2).get("accepted"));assertEquals(1,count("SELECT COUNT(*) FROM sel_select_log WHERE action_type=3"));}
    @Test void missingScheduleRollsBackWholeScreeningBatch(){jdbc.update("INSERT INTO sel_sc(student_id,tc_id,round_id,study_type,is_exempt_attend,stu_level,stat_stage,priority,select_status,select_time) VALUES('S1','A',2,1,0,'本科',1,1,0,CURRENT_TIMESTAMP),('S2','B',2,1,0,'本科',1,1,0,CURRENT_TIMESTAMP)");jdbc.update("DELETE FROM aca_schedule_result WHERE tc_id='B'");office();assertThrows(BizException.class,()->service.screen(2));assertEquals(2,count("SELECT COUNT(*) FROM sel_sc WHERE select_status=0"));assertEquals(0,count("SELECT SUM(actual_num) FROM aca_teaching_class"));}
    @Test void concurrentLastSeatHasOnlyOneWinner() throws Exception {
        ExecutorService pool=Executors.newFixedThreadPool(2);CountDownLatch start=new CountDownLatch(1);
        try{List<Future<Boolean>> jobs=new ArrayList<>();for(String id:List.of("S1","S2"))jobs.add(pool.submit(()->{asStudent(id);try{start.await();service.enroll(application("A"));return true;}catch(BizException e){return false;}finally{UserContext.clear();}}));start.countDown();int winners=0;for(var f:jobs)if(f.get(15,TimeUnit.SECONDS))winners++;assertEquals(1,winners);assertEquals(1,count("SELECT actual_num FROM aca_teaching_class WHERE tc_id='A'"));}finally{pool.shutdownNow();}
    }
    @Test void timetableOnlyIncludesSelectedWeeks(){service.enroll(application("A"));assertEquals(1,((List<?>)service.timetable(TERM,1).get("blocks")).size());assertEquals(0,((List<?>)service.timetable(TERM,9).get("blocks")).size());}
    @Test void stopPreviewFlagsUnderMinClassesAndNeverWrites(){
        service.enroll(application("A"));
        office();
        var preview = service.stopPreview(TERM);
        assertEquals(true, preview.get("write_enabled"));
        assertEquals(6, preview.get("min_open_students"));
        assertEquals(3, preview.get("under_min_count"));
        assertEquals(1, count("SELECT COUNT(*) FROM sel_select_log"));
        assertEquals("4", jdbc.queryForObject("SELECT tc_status FROM aca_teaching_class WHERE tc_id='A'", String.class));
    }
    @Test void stopClassesCancelsUnderMinClassCleansStudentsAndBlocksReachMin(){
        jdbc.update("UPDATE aca_teaching_class SET capacity=10 WHERE tc_id IN ('A','B')");
        for(String sid : List.of("S1","S2")){ asStudent(sid); service.enroll(application("A")); }
        jdbc.update("INSERT INTO sel_tc_eval_result VALUES('A',0,NULL,CURRENT_TIMESTAMP)");
        // B 班凑足 6 人，不应允许按人数不足停开
        for(String sid : List.of("S4","S5","S6","S7","S8","S9")){
            jdbc.update("INSERT INTO sys_person VALUES(?,?)", "P_"+sid, sid);
            jdbc.update("INSERT INTO std_student VALUES(?,?,'CLS241','M1','ORG001','2024',2024,'01','01')", sid, "P_"+sid);
            asStudent(sid);
            service.enroll(application("B"));
        }
        office();
        // B 班满 6 人，不在预警清单中且停开被拒绝
        var previewBefore = service.stopPreview(TERM);
        var previewIds = rows(previewBefore.get("items")).stream().map(r -> r.get("tc_id")).toList();
        assertTrue(previewIds.contains("A"));
        assertFalse(previewIds.contains("B"));
        assertThrows(BizException.class, () -> service.stopClasses(new SelectionService.StopRequest(List.of("B"), null)));

        // A 班仅 2 人（< 6 人），确认停开后置为不开课、清退 2 名学生、记日志并清除评教汇总
        var stopped = service.stopClasses(new SelectionService.StopRequest(List.of("A"), "选课人数不足6人停开"));
        assertEquals(1, stopped.get("stopped_count"));
        assertEquals(2, stopped.get("cancelled_students"));
        assertEquals(false, stopped.get("workload_counted"));
        assertEquals("2", jdbc.queryForObject("SELECT tc_status FROM aca_teaching_class WHERE tc_id='A'", String.class));
        assertEquals("选课人数不足6人停开", jdbc.queryForObject("SELECT no_open_reason FROM aca_teaching_class WHERE tc_id='A'", String.class));
        assertEquals(0, count("SELECT actual_num FROM aca_teaching_class WHERE tc_id='A'"));
        assertEquals(0, count("SELECT COUNT(*) FROM sel_sc WHERE tc_id='A' AND select_status IN (0,1)"));
        assertEquals(2, count("SELECT COUNT(*) FROM sel_sc WHERE tc_id='A' AND select_status=3 AND drop_time IS NOT NULL"));
        assertEquals(2, count("SELECT COUNT(*) FROM sel_select_log WHERE tc_id='A' AND action_type=4"));
        assertEquals(0, count("SELECT COUNT(*) FROM sel_tc_eval_result WHERE tc_id='A'"));
    }
    @Test void stopClassesProtectsClassWithExistingScores(){
        var r = service.enroll(application("A"));
        jdbc.update("INSERT INTO sel_score(sc_id,score_status) VALUES(?,1)", r.get("sc_id"));
        office();
        assertThrows(BizException.class, () -> service.stopClasses(new SelectionService.StopRequest(List.of("A"), null)));
        assertEquals("4", jdbc.queryForObject("SELECT tc_status FROM aca_teaching_class WHERE tc_id='A'", String.class));
    }
    @Test void malformedWeeksAreNotTreatedAsNoConflict(){assertThrows(BizException.class,()->SelectionRules.weeks("待定"));assertEquals(Set.of(1,3,5,8,10),SelectionRules.weeks("1-5单,8-10双"));}
    @Test void retakeConflictSetsExemptionButRequiresFailureHistory(){
        assertThrows(BizException.class,()->service.enroll(new SelectionService.Application(1,"A",2,null,null)));
        jdbc.update("INSERT INTO aca_teaching_class(tc_id,term,course_id,emp_id,class_ids,capacity,tc_type,tc_status,actual_num,selected_count) VALUES('P','2025-2026-1','C001','E1','信管241',10,'2','4',0,0)");
        jdbc.update("INSERT INTO sel_sc(student_id,tc_id,round_id,study_type,is_exempt_attend,stu_level,stat_stage,priority,select_status,select_time) VALUES('S1','P',1,1,0,'本科',1,NULL,3,CURRENT_TIMESTAMP)");
        jdbc.update("INSERT INTO sel_score SELECT sc_id,3,50,NULL FROM sel_sc WHERE tc_id='P'");
        service.enroll(application("D"));
        var r=service.enroll(new SelectionService.Application(1,"A",2,null,null));assertEquals(1,SelectionRules.number(r.get("is_exempt_attend")));
    }
    @Test void graduateDirectionMustMatchEffectiveScheme(){
        jdbc.update("UPDATE std_student SET student_type='02' WHERE student_id='S1'");
        assertEquals("DATA_PENDING",assertThrows(BizException.class,()->service.enroll(application("A"))).getCode());
        jdbc.update("INSERT INTO grad_scheme_assignment VALUES('ASN1','S1','PLAN','DIR1','2026-01-01',NULL)");
        jdbc.update("INSERT INTO grad_scheme_course VALUES('SC1','PLAN','C001','DIR2')");assertThrows(BizException.class,()->service.enroll(application("A")));
        jdbc.update("INSERT INTO grad_scheme_course VALUES('SC2','PLAN','C001','DIR1')");assertEquals(2,SelectionRules.number(service.enroll(application("A")).get("stat_stage")));
    }
    @Test void oldVersionCannotDropReselectedRecordInSameSecond(){
        var first=service.enroll(application("A"));var dropped=service.withdraw(first.get("sc_id").toString(),new SelectionService.Withdrawal(1,first.get("version").toString(),true));
        service.enroll(new SelectionService.Application(1,"A",1,null,dropped.get("version").toString()));
        assertThrows(BizException.class,()->service.withdraw(first.get("sc_id").toString(),new SelectionService.Withdrawal(1,first.get("version").toString(),true)));
    }
    @Test void studentsCannotReadTeacherRoster(){assertThrows(BizException.class,()->service.roster("A",""));}

    @Test void specialRequirementFromDatabaseIsShownAndRequiresConfirmationOnEnroll() {
        jdbc.update("INSERT INTO al_dict_item(dict_type,item_code,item_name,item_status) VALUES('tc_special_requirement','A','大学英语六级成绩 >= 425 分','01')");

        // 1. 选课列表实时展示数据库中的特殊要求
        var page = service.courses(1, "C001", 1, 10, null);
        var course = rows(page.get("items")).get(0);
        @SuppressWarnings("unchecked")
        var audience = (Map<String, Object>) course.get("audience");
        assertEquals("大学英语六级成绩 >= 425 分", audience.get("special_requirement"));

        // 2. 预校验提示需先勾选确认；直接提交未勾选时被拦截
        var precheck = service.check(application("A"));
        assertEquals(true, precheck.get("requires_special_confirmation"));
        BizException ex = assertThrows(BizException.class, () -> service.enroll(application("A")));
        assertTrue(ex.getMessage().contains("六级"));

        // 3. 勾选确认后顺利提交并留痕到选课日志
        var confirmed = new SelectionService.Application(1, "A", 1, null, null, true);
        var enrolled = service.enroll(confirmed);
        assertEquals(1, SelectionRules.number(enrolled.get("select_status")));
        String remark = jdbc.queryForObject("SELECT remark FROM sel_select_log WHERE tc_id='A' AND action_type=1", String.class);
        assertNotNull(remark);
        assertTrue(remark.contains("大学英语六级成绩 >= 425 分"));
    }

    @Test void preCourseRequirementBlocksUntilPassed() {
        // 设置 C002 必须先修并通过 C001
        jdbc.update("INSERT INTO aca_course_id VALUES('C002','C001')");

        // 1. 选课列表面向对象中展示先修课程
        var page = service.courses(1, "C002", 1, 10, null);
        @SuppressWarnings("unchecked")
        var audience = (Map<String, Object>) rows(page.get("items")).get(0).get("audience");
        assertEquals(List.of("C001（C001）"), audience.get("pre_courses"));

        // 2. 未修先修课时被强拦截
        BizException ex = assertThrows(BizException.class, () -> service.enroll(application("B")));
        assertTrue(ex.getMessage().contains("先修课程"));

        // 3. 往期修读并通过 C001 后放行选课
        jdbc.update("INSERT INTO aca_teaching_class(tc_id,term,course_id,emp_id,class_ids,capacity,tc_type,tc_status,actual_num,selected_count) VALUES('PRE1','2025-2026-2','C001','E1','信管241',30,'1','4',1,1)");
        jdbc.update("INSERT INTO sel_sc(student_id,tc_id,round_id,study_type,is_exempt_attend,stu_level,stat_stage,priority,select_status,select_time) VALUES('S1','PRE1',1,1,0,'本科',1,NULL,1,CURRENT_TIMESTAMP)");
        jdbc.update("INSERT INTO sel_score SELECT sc_id,3,85.0,NULL FROM sel_sc WHERE tc_id='PRE1'");
        assertEquals(1, SelectionRules.number(service.enroll(application("B")).get("select_status")));
    }

    @Test void selectedRecordsSupplyTheSameWeeksAsTheWeeklyTimetable() {
        service.enroll(application("A"));
        jdbc.update("UPDATE aca_schedule_result SET week_range='1-5(单),8' WHERE tc_id='A'");
        var records = service.resultsWithTeachingWeeks(TERM);
        var slot = rows(rows(records.get("items")).get(0).get("schedule")).get(0);
        assertEquals(Set.of(1, 3, 5, 8), slot.get("teaching_weeks"));
        assertFalse(records.containsKey("timetable_error"));
        assertTrue(rows(service.timetable(TERM, 2).get("blocks")).isEmpty());
        assertEquals(1, rows(service.timetable(TERM, 8).get("blocks")).size());

        for (String range : List.of("待定", "1-99999999999")) {
            jdbc.update("UPDATE aca_schedule_result SET week_range=? WHERE tc_id='A'", range);
            var invalid = service.resultsWithTeachingWeeks(TERM);
            assertEquals("A", rows(invalid.get("items")).get(0).get("tc_id"));
            assertEquals(assertThrows(BizException.class, () -> service.timetable(TERM, 1)).getMessage(), invalid.get("timetable_error"));
        }
    }

    @Test void pendingScheduleIsIgnoredAcrossCoursesCheckEnrollAndTimetable() {
        // 将 B 班排课置为 0（待审核），业务②应与业务①统一口径：视为排课待确认
        jdbc.update("UPDATE aca_schedule_result SET sched_status='0' WHERE tc_id='B'");

        var page = service.courses(1, "C002", 1, 10, null);
        var bClass = rows(rows(page.get("items")).get(0).get("classes")).get(0);
        assertTrue(rows(bClass.get("schedule")).isEmpty());

        BizException checkEx = assertThrows(BizException.class, () -> service.check(application("B")));
        assertEquals("DATA_PENDING", checkEx.getCode());
        BizException enrollEx = assertThrows(BizException.class, () -> service.enroll(application("B")));
        assertEquals("DATA_PENDING", enrollEx.getCode());

        // 已选课程若排课退回待审核，周课表不展示该未审核时段并列入排课缺失提示
        service.enroll(application("A"));
        jdbc.update("UPDATE aca_schedule_result SET sched_status='0' WHERE tc_id='A'");
        var table = service.timetable(TERM, 1);
        assertTrue(rows(table.get("blocks")).isEmpty());
        assertEquals(List.of("A"), table.get("missing_schedule"));

        // 排课状态变为 2（已发布）与 3（已调课）后恢复可见并允许选课
        jdbc.update("UPDATE aca_schedule_result SET sched_status='2' WHERE tc_id='B'");
        // 已选课 A 仍处于待审核时，冲突检测会拦截防止漏检
        assertEquals("DATA_PENDING", assertThrows(BizException.class, () -> service.enroll(application("B"))).getCode());
        jdbc.update("UPDATE aca_schedule_result SET sched_status='3' WHERE tc_id='A'");
        var pageAfter = service.courses(1, "C002", 1, 10, null);
        var bAfter = rows(rows(pageAfter.get("items")).get(0).get("classes")).get(0);
        assertEquals(1, rows(bAfter.get("schedule")).size());
        assertEquals(1, SelectionRules.number(service.enroll(application("B")).get("select_status")));
    }
}
