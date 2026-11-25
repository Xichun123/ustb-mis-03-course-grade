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
            "CREATE TABLE select_round(round_id INT PRIMARY KEY,term VARCHAR(11),round_name VARCHAR(30),round_type INT,target_level INT,start_time TIMESTAMP,end_time TIMESTAMP,max_credit DECIMAL(4,1),select_mode INT,round_status INT,allow_retake INT,retake_ignore_conflict INT,notice_content VARCHAR(200))",
            "CREATE TABLE student(student_id VARCHAR(20) PRIMARY KEY,student_name VARCHAR(50),grade INT,degree_level VARCHAR(20),class_name VARCHAR(50),major_id VARCHAR(6),college_id VARCHAR(4),final_enroll VARCHAR(20))",
            "CREATE TABLE course(course_id VARCHAR(8) PRIMARY KEY,course_name VARCHAR(40),course_type VARCHAR(2),credit DECIMAL(3,1),course_level INT,open_college VARCHAR(4))",
            "CREATE TABLE teaching_class(tc_id VARCHAR(20) PRIMARY KEY,term VARCHAR(11),course_id VARCHAR(8),teacher_id VARCHAR(8),class_ids VARCHAR(100),capacity INT,tc_type VARCHAR(1),tc_status VARCHAR(1),actual_num INT)",
            "CREATE TABLE round_class(round_id INT,tc_id VARCHAR(20),PRIMARY KEY(round_id,tc_id))",
            "CREATE TABLE select_limit(limit_id INT AUTO_INCREMENT PRIMARY KEY,tc_id VARCHAR(20),limit_type INT,limit_value VARCHAR(20))",
            "CREATE TABLE schedule_result(tc_id VARCHAR(20),week_range VARCHAR(30),week_day INT,start_slot INT,end_slot INT,room_id VARCHAR(20))",
            "CREATE TABLE sc(sc_id BIGINT AUTO_INCREMENT PRIMARY KEY,student_id VARCHAR(20),tc_id VARCHAR(20),round_id INT,study_type INT,is_exempt_attend INT,stu_level VARCHAR(20),stat_stage INT,priority INT,select_status INT,select_time TIMESTAMP,drop_time TIMESTAMP,UNIQUE(student_id,tc_id),CHECK(select_status=3 OR drop_time IS NULL),CHECK(is_exempt_attend=0 OR study_type=2))",
            "CREATE TABLE select_log(log_id BIGINT AUTO_INCREMENT PRIMARY KEY,student_id VARCHAR(20),tc_id VARCHAR(20),round_id INT,action_type INT,operator_id VARCHAR(20),action_time TIMESTAMP,remark VARCHAR(200),CHECK(action_type<>4 OR remark IS NOT NULL))",
            "CREATE TABLE score(sc_id BIGINT,score_status INT,total_score DECIMAL(4,1),score_level VARCHAR(6))",
            "CREATE TABLE teach_eval(sc_id BIGINT)",
            "CREATE TABLE sys_code_dict(dict_group VARCHAR(40),dict_code VARCHAR(40),dict_name VARCHAR(100),is_enabled INT)",
            "CREATE TABLE scheme_assignment(student_id VARCHAR(20),scheme_id VARCHAR(32),direction_id VARCHAR(10),effective_from DATE,effective_to DATE)",
            "CREATE TABLE scheme_course(scheme_id VARCHAR(32),course_id VARCHAR(8),direction_id VARCHAR(10))",
            "CREATE TABLE v_exchange_student(temp_student_no VARCHAR(20),student_name VARCHAR(50),term_ref VARCHAR(11),college_ref VARCHAR(4),major_ref VARCHAR(6),start_on DATE,end_on DATE,status_code VARCHAR(20))"
        ))jdbc.execute(ddl);
        jdbc.update("INSERT INTO select_round VALUES(1,?,'补退选',3,3,'2026-11-20 09:00:00','2026-12-10 17:00:00',30,1,3,1,1,'测试通知')",TERM);
        jdbc.update("INSERT INTO select_round VALUES(2,?,'截止预选',1,3,'2026-11-01 09:00:00','2026-11-02 17:00:00',30,2,4,1,1,'测试通知')",TERM);
        for(String id:List.of("S1","S2","S3"))jdbc.update("INSERT INTO student VALUES(?, ?,2024,'本科','信管241','M1','0001','在籍')",id,id);
        for(String id:List.of("C001","C002","C003"))jdbc.update("INSERT INTO course VALUES(?,?,'11',3,1,'0001')",id,id);
        for(String id:List.of("A","B","D")){
            jdbc.update("INSERT INTO teaching_class VALUES(?,?,?,'T1','信管241',1,'2','4',0)",id,TERM,id.equals("A")?"C001":id.equals("B")?"C002":"C003");
            jdbc.update("INSERT INTO schedule_result VALUES(?,?,1,1,2,'教室1')",id,id.equals("A")?"1-8":id.equals("B")?"9-16":"1-16");
            for(int round:List.of(1,2))jdbc.update("INSERT INTO round_class VALUES(?,?)",round,id);
        }
        jdbc.update("INSERT INTO sys_code_dict VALUES('teacher_map','T1','E1',1)");
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
        assertEquals(1,count("SELECT COUNT(*) FROM sc"));assertEquals(1,count("SELECT COUNT(*) FROM select_log"));assertEquals(1,count("SELECT actual_num FROM teaching_class WHERE tc_id='A'"));
    }
    @Test void disjointWeeksDoNotConflictButOverlapDoes(){service.enroll(application("A"));service.enroll(application("B"));var e=assertThrows(BizException.class,()->service.enroll(application("D")));assertTrue(e.getMessage().contains("冲突"));assertEquals(2,count("SELECT COUNT(*) FROM sc"));}
    @Test void capacityCannotBeExceeded(){service.enroll(application("A"));asStudent("S2");assertThrows(BizException.class,()->service.enroll(application("A")));assertEquals(1,count("SELECT COUNT(*) FROM sc"));}
    @Test void creditIncludesPendingApplications(){jdbc.update("UPDATE select_round SET max_credit=5 WHERE round_id=1");jdbc.update("INSERT INTO sc(student_id,tc_id,round_id,study_type,is_exempt_attend,stu_level,stat_stage,priority,select_status,select_time) VALUES('S1','B',1,1,0,'本科',1,1,0,CURRENT_TIMESTAMP)");var e=assertThrows(BizException.class,()->service.enroll(application("A")));assertTrue(e.getMessage().contains("学分"));}
    @Test void sameLimitTypeIsOrAndDifferentTypesAreAnd(){jdbc.update("INSERT INTO select_limit(tc_id,limit_type,limit_value) VALUES('A',1,'2023'),('A',1,'2024'),('A',2,'OTHER')");assertThrows(BizException.class,()->service.enroll(application("A")));jdbc.update("INSERT INTO select_limit(tc_id,limit_type,limit_value) VALUES('A',2,'M1')");assertDoesNotThrow(()->service.enroll(application("A")));}

    private void loadCatalogDemo() {
        var demo = new ResourceDatabasePopulator(new ClassPathResource("selection/catalog-demo.sql"));
        demo.setSqlScriptEncoding("UTF-8");
        demo.execute(Objects.requireNonNull(jdbc.getDataSource()));
    }

    @ParameterizedTest(name = "{1} 显示为 {2}")
    @CsvSource(textBlock = """
            REQUIRED, DEMO0001, 必修
            PROFESSIONAL_EXTENSION, DEMO0002, 专业拓展课
            SCIENCE_LITERACY, DEMO0003, 素质拓展课-科学素养
            FOREIGN_LANGUAGE, DEMO0004, 素质拓展课-外语
            AESTHETICS, DEMO0005, 素质拓展课-美育
            HUMANITIES, DEMO0006, 素质拓展课-人文素养
            INNOVATION_ENTREPRENEURSHIP, DEMO0007, 素质拓展课-创新创业课程
            PRACTICE, DEMO0008, 实践
            """)
    void courseTypeFilterUsesConfiguredCategoriesAndLegacyCodesBeforePaging(CourseType filter, String id, String label) {
        loadCatalogDemo();

        var page = service.courses(CATALOG_DEMO_ROUND, "", 1, 1, filter);
        assertEquals(1, page.get("total"));
        var course = rows(page.get("items")).get(0);
        assertEquals(id, course.get("course_id"));
        assertEquals(label, course.get("course_type_label"));
        assertTrue(rows(service.courses(CATALOG_DEMO_ROUND, "", 2, 1, filter).get("items")).isEmpty());
    }

    @Test void courseTypeFallbackFollowsUnifiedV13Codes() {
        var props = new SelectionCatalogProperties(Map.of(), Map.of());
        assertEquals(CourseType.REQUIRED, props.courseType("C11", "11"));
        assertEquals(CourseType.REQUIRED, props.courseType("C12", "12"));
        assertEquals(CourseType.REQUIRED, props.courseType("C13", "13"));
        assertEquals(CourseType.PROFESSIONAL_EXTENSION, props.courseType("C14", "14"));
        assertEquals(CourseType.GENERAL_ELECTIVE, props.courseType("C15", "15"));
        assertEquals(CourseType.PRACTICE, props.courseType("C16", "16"));
        assertEquals(CourseType.UNCLASSIFIED, props.courseType("C99", null));
    }

    @Test void courseListKeepsAudienceAndSpecialRequirementsWithTheirTeachingClass() {
        loadCatalogDemo();

        var page = service.courses(CATALOG_DEMO_ROUND, "学术英语", 1, 10, CourseType.FOREIGN_LANGUAGE);
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
        assertEquals(8, page.get("total"));
        var courses = rows(page.get("items"));
        assertEquals(Map.of("grades", List.of("2024"), "majors", List.of("120102"),
                "colleges", List.of(), "levels", List.of(), "class_names", List.of("信管241", "信管242")),
                courses.get(0).get("audience"));
        assertEquals(Map.of("grades", List.of(), "majors", List.of("080901"), "colleges", List.of(), "levels", List.of()),
                courses.get(1).get("audience"));
        assertEquals(Map.of("grades", List.of(), "majors", List.of(), "colleges", List.of(), "levels", List.of()),
                courses.get(2).get("audience"));
    }

    @SuppressWarnings("unchecked")
    private static List<Map<String, Object>> rows(Object value) {
        return (List<Map<String, Object>>) value;
    }
    @Test void staleWithdrawalIsRejectedAndHistoryIsKept(){var r=service.enroll(application("A"));String id=r.get("sc_id").toString();assertThrows(BizException.class,()->service.withdraw(id,new SelectionService.Withdrawal(1,"stale",true)));var drop=service.withdraw(id,new SelectionService.Withdrawal(1,r.get("version").toString(),true));assertEquals(3,SelectionRules.number(drop.get("select_status")));assertEquals(0,count("SELECT actual_num FROM teaching_class WHERE tc_id='A'"));assertEquals(2,count("SELECT COUNT(*) FROM select_log"));assertEquals(1,count("SELECT COUNT(*) FROM sc"));}
    @Test void downstreamScoresProtectWithdrawal(){var r=service.enroll(application("A"));jdbc.update("INSERT INTO score(sc_id,score_status) VALUES(?,0)",r.get("sc_id"));assertThrows(BizException.class,()->service.withdraw(r.get("sc_id").toString(),new SelectionService.Withdrawal(1,r.get("version").toString(),true)));assertEquals(1,count("SELECT actual_num FROM teaching_class WHERE tc_id='A'"));}
    @Test void logFailureRollsBackEnrollmentAndCount(){jdbc.execute("ALTER TABLE select_log ADD CONSTRAINT block_log CHECK(action_type<>1)");assertThrows(Exception.class,()->service.enroll(application("A")));assertEquals(0,count("SELECT COUNT(*) FROM sc"));assertEquals(0,count("SELECT actual_num FROM teaching_class WHERE tc_id='A'"));}
    @Test void teacherScopeAndRequiredReasonAreEnforced(){jdbc.update("UPDATE teaching_class SET teacher_id='OTHER' WHERE tc_id='B'");teacher();assertThrows(BizException.class,()->service.roster("B",""));assertThrows(BizException.class,()->service.adjust("A",new SelectionService.Adjustment("S1","add",1," ")));service.adjust("A",new SelectionService.Adjustment("S1","add",1,"补录批准"));assertEquals(1,count("SELECT COUNT(*) FROM select_log WHERE action_type=4"));}
    @Test void teacherAdjustmentBypassesCapacityAndWindow(){jdbc.update("UPDATE teaching_class SET capacity=0 WHERE tc_id='A'");jdbc.update("UPDATE select_round SET end_time='2026-11-21 00:00:00' WHERE round_id=1");teacher();service.adjust("A",new SelectionService.Adjustment("S1","add",1,"特殊批准"));assertEquals(1,count("SELECT actual_num FROM teaching_class WHERE tc_id='A'"));}
    @Test void screeningRespectsPrioritiesAndIsRepeatable(){jdbc.update("INSERT INTO sc(student_id,tc_id,round_id,study_type,is_exempt_attend,stu_level,stat_stage,priority,select_status,select_time) VALUES('S1','A',2,1,0,'本科',1,2,0,CURRENT_TIMESTAMP),('S2','A',2,1,0,'本科',1,1,0,CURRENT_TIMESTAMP)");office();var r=service.screen(2);assertEquals(1,r.get("accepted"));assertEquals(1,r.get("rejected"));assertEquals(1,count("SELECT select_status FROM sc WHERE student_id='S2'"));assertEquals(0,service.screen(2).get("accepted"));assertEquals(1,count("SELECT COUNT(*) FROM select_log WHERE action_type=3"));}
    @Test void missingScheduleRollsBackWholeScreeningBatch(){jdbc.update("INSERT INTO sc(student_id,tc_id,round_id,study_type,is_exempt_attend,stu_level,stat_stage,priority,select_status,select_time) VALUES('S1','A',2,1,0,'本科',1,1,0,CURRENT_TIMESTAMP),('S2','B',2,1,0,'本科',1,1,0,CURRENT_TIMESTAMP)");jdbc.update("DELETE FROM schedule_result WHERE tc_id='B'");office();assertThrows(BizException.class,()->service.screen(2));assertEquals(2,count("SELECT COUNT(*) FROM sc WHERE select_status=0"));assertEquals(0,count("SELECT SUM(actual_num) FROM teaching_class"));}
    @Test void concurrentLastSeatHasOnlyOneWinner() throws Exception {
        ExecutorService pool=Executors.newFixedThreadPool(2);CountDownLatch start=new CountDownLatch(1);
        try{List<Future<Boolean>> jobs=new ArrayList<>();for(String id:List.of("S1","S2"))jobs.add(pool.submit(()->{asStudent(id);try{start.await();service.enroll(application("A"));return true;}catch(BizException e){return false;}finally{UserContext.clear();}}));start.countDown();int winners=0;for(var f:jobs)if(f.get(15,TimeUnit.SECONDS))winners++;assertEquals(1,winners);assertEquals(1,count("SELECT actual_num FROM teaching_class WHERE tc_id='A'"));}finally{pool.shutdownNow();}
    }
    @Test void timetableOnlyIncludesSelectedWeeks(){service.enroll(application("A"));assertEquals(1,((List<?>)service.timetable(TERM,1).get("blocks")).size());assertEquals(0,((List<?>)service.timetable(TERM,9).get("blocks")).size());}
    @Test void stopPreviewNeverWrites(){office();jdbc.update("UPDATE teaching_class SET tc_status='2' WHERE tc_id='A'");assertEquals(false,service.stopPreview(TERM).get("write_enabled"));assertEquals(0,count("SELECT COUNT(*) FROM select_log"));}
    @Test void malformedWeeksAreNotTreatedAsNoConflict(){assertThrows(BizException.class,()->SelectionRules.weeks("待定"));assertEquals(Set.of(1,3,5,8,10),SelectionRules.weeks("1-5单,8-10双"));}
    @Test void retakeConflictSetsExemptionButRequiresFailureHistory(){
        assertThrows(BizException.class,()->service.enroll(new SelectionService.Application(1,"A",2,null,null)));
        jdbc.update("INSERT INTO teaching_class VALUES('P','2025-2026-1','C001','T1','信管241',10,'2','4',0)");
        jdbc.update("INSERT INTO sc(student_id,tc_id,round_id,study_type,is_exempt_attend,stu_level,stat_stage,priority,select_status,select_time) VALUES('S1','P',1,1,0,'本科',1,NULL,3,CURRENT_TIMESTAMP)");
        jdbc.update("INSERT INTO score SELECT sc_id,3,50,NULL FROM sc WHERE tc_id='P'");
        service.enroll(application("D"));
        var r=service.enroll(new SelectionService.Application(1,"A",2,null,null));assertEquals(1,SelectionRules.number(r.get("is_exempt_attend")));
    }
    @Test void graduateDirectionMustMatchEffectiveScheme(){
        jdbc.update("UPDATE student SET degree_level='研究生' WHERE student_id='S1'");
        assertEquals("DATA_PENDING",assertThrows(BizException.class,()->service.enroll(application("A"))).getCode());
        jdbc.update("INSERT INTO scheme_assignment VALUES('S1','PLAN','DIR1','2026-01-01',NULL)");
        jdbc.update("INSERT INTO scheme_course VALUES('PLAN','C001','DIR2')");assertThrows(BizException.class,()->service.enroll(application("A")));
        jdbc.update("INSERT INTO scheme_course VALUES('PLAN','C001','DIR1')");assertEquals(2,SelectionRules.number(service.enroll(application("A")).get("stat_stage")));
    }
    @Test void oldVersionCannotDropReselectedRecordInSameSecond(){
        var first=service.enroll(application("A"));var dropped=service.withdraw(first.get("sc_id").toString(),new SelectionService.Withdrawal(1,first.get("version").toString(),true));
        service.enroll(new SelectionService.Application(1,"A",1,null,dropped.get("version").toString()));
        assertThrows(BizException.class,()->service.withdraw(first.get("sc_id").toString(),new SelectionService.Withdrawal(1,first.get("version").toString(),true)));
    }
    @Test void studentsCannotReadTeacherRoster(){assertThrows(BizException.class,()->service.roster("A",""));}
}
