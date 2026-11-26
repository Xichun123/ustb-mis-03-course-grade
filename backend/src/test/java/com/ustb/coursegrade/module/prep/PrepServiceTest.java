package com.ustb.coursegrade.module.prep;

import com.ustb.coursegrade.common.BizException;
import com.ustb.coursegrade.common.PageResult;
import com.ustb.coursegrade.common.auth.CurrentUser;
import com.ustb.coursegrade.common.auth.RoleEnum;
import com.ustb.coursegrade.common.auth.UserContext;
import org.h2.jdbcx.JdbcDataSource;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

/** 开课计划联查与轮次选班隔离测试。 */
class PrepServiceTest {

    private static final String TERM = "2026-2027-1";

    private JdbcTemplate jdbc;
    private PrepService service;

    @BeforeEach
    void setup() {
        JdbcDataSource ds = new JdbcDataSource();
        ds.setURL("jdbc:h2:mem:" + UUID.randomUUID() + ";MODE=MySQL;DATABASE_TO_LOWER=TRUE;DB_CLOSE_DELAY=-1");
        jdbc = new JdbcTemplate(ds);

        for (String ddl : List.of(
                "CREATE TABLE org_organization(org_id VARCHAR(6) PRIMARY KEY,org_name VARCHAR(100),college_id VARCHAR(4))",
                "CREATE TABLE hr_employee(emp_id VARCHAR(10) PRIMARY KEY,emp_name VARCHAR(50))",
                "CREATE TABLE aca_course(course_id VARCHAR(10) PRIMARY KEY,course_name VARCHAR(100),course_type VARCHAR(2),credit DECIMAL(5,1),org_id VARCHAR(6))",
                "CREATE TABLE aca_course_id(course_id VARCHAR(10) PRIMARY KEY,assess_type VARCHAR(20),pre_course_id VARCHAR(40))",
                "CREATE TABLE aca_teaching_class(tc_id VARCHAR(20) PRIMARY KEY,term VARCHAR(11),course_id VARCHAR(10),emp_id VARCHAR(10),class_name VARCHAR(50),class_ids VARCHAR(100),capacity INT,actual_num INT DEFAULT 0,hours DECIMAL(18,2) DEFAULT 48.00,week_count INT DEFAULT 16,teach_type VARCHAR(20) DEFAULT '1',tc_type VARCHAR(20) DEFAULT '1',tc_status VARCHAR(2) DEFAULT '4',no_open_reason VARCHAR(100),open_lower INT DEFAULT 6)",
                "CREATE TABLE aca_schedule_result(sched_id VARCHAR(14) PRIMARY KEY,tc_id VARCHAR(20),week_range VARCHAR(20),week_day VARCHAR(20),start_slot INT,end_slot INT,room_id VARCHAR(12),sched_status VARCHAR(2) DEFAULT '1')",
                "CREATE TABLE sel_lesson_class(tc_id VARCHAR(20) PRIMARY KEY,lesson_no VARCHAR(20))",
                "CREATE TABLE sel_select_limit(limit_id INT AUTO_INCREMENT PRIMARY KEY,tc_id VARCHAR(20),limit_type INT,limit_value VARCHAR(20))",
                "CREATE TABLE sel_select_round(round_id INT AUTO_INCREMENT PRIMARY KEY,term VARCHAR(11),round_name VARCHAR(30),round_type INT,target_level INT,start_at TIMESTAMP,end_at TIMESTAMP,max_credit DECIMAL(5,1),select_mode INT,allow_retake INT,retake_ignore_conflict INT,round_status INT,notice_content VARCHAR(500),published_at TIMESTAMP,approver_id VARCHAR(10),approved_at TIMESTAMP,approve_opinion VARCHAR(200))",
                "CREATE TABLE sel_round_class(round_id INT,tc_id VARCHAR(20),PRIMARY KEY(round_id,tc_id))",
                "CREATE TABLE sys_dict_type(dict_type VARCHAR(40) PRIMARY KEY,type_name VARCHAR(100),is_system TINYINT DEFAULT 0,dict_status VARCHAR(2) DEFAULT '01',remark VARCHAR(200))",
                "CREATE TABLE sys_dict_item(dict_id BIGINT AUTO_INCREMENT PRIMARY KEY,dict_type VARCHAR(40),item_code VARCHAR(100),item_name VARCHAR(200),sort_no INT DEFAULT 0,version VARCHAR(40),effective_from DATE,item_status VARCHAR(2) DEFAULT '01')"
        )) {
            jdbc.execute(ddl);
        }

        jdbc.update("INSERT INTO org_organization VALUES('ORG001','经济管理学院','0001')");
        jdbc.update("INSERT INTO hr_employee VALUES('T001','王明远'),('T002','李晓静')");
        jdbc.update("INSERT INTO aca_course VALUES('C001','管理信息系统','12',3.0,'ORG001'),('C002','数据库原理','13',3.5,'ORG001'),('C003','大学英语','11',2.0,'ORG001')");
        jdbc.update("INSERT INTO aca_course_id VALUES('C001','1',NULL),('C002','1','C001')");

        // TC1 已发布且排课已确认，TC2 已发布但排课待审，TC3 已停开。
        jdbc.update("INSERT INTO aca_teaching_class(tc_id,term,course_id,emp_id,class_name,class_ids,capacity,actual_num,hours,tc_type,tc_status) VALUES('TC1',?,'C001','T001','信管-01班','信管241',60,0,48.00,'1','4')", TERM);
        jdbc.update("INSERT INTO aca_teaching_class(tc_id,term,course_id,emp_id,class_name,class_ids,capacity,actual_num,hours,tc_type,tc_status) VALUES('TC2',?,'C002','T002','数据库-01班','信管241',60,0,56.00,'1','4')", TERM);
        jdbc.update("INSERT INTO aca_teaching_class(tc_id,term,course_id,emp_id,class_name,class_ids,capacity,actual_num,hours,tc_type,tc_status,no_open_reason) VALUES('TC3',?,'C003','T002','英语-01班','信管241',45,0,32.00,'1','2','人数不足停开')", TERM);
        jdbc.update("""
                INSERT INTO aca_schedule_result VALUES
                    ('SCH1','TC1','1-16','1',1,2,'经管楼301','1'),
                    ('SCH2','TC1','2-8','3',3,4,'经管楼302','3'),
                    ('SCH3','TC2','1-16','2',1,2,'待审核教室','0')
                """);

        service = new PrepService(jdbc);
        asOffice();
    }

    @AfterEach
    void cleanup() {
        UserContext.clear();
        jdbc.execute("SHUTDOWN");
    }

    private void asOffice() {
        UserContext.set(new CurrentUser("off", "教务经办", RoleEnum.ACADEMIC_OFFICE, null, "E001", null, null, null));
    }

    private void asDirector() {
        UserContext.set(new CurrentUser("dir", "教务处长", RoleEnum.ACADEMIC_DIRECTOR, null, "E002", null, null, null));
    }

    @Test
    void teachingClassesShowGroup2ScheduleAndGroup4TeacherAndSyncSummary() {
        PageResult<Map<String, Object>> page = service.listTeachingClasses(TERM, null, null, null, null, 1, 20);
        assertEquals(3, page.getTotal());

        Map<String, Object> tc1 = page.getItems().stream().filter(r -> "TC1".equals(r.get("tc_id"))).findFirst().orElseThrow();
        assertEquals("王明远", tc1.get("teacher_name"));
        assertEquals("院级必修", tc1.get("course_type_label"));
        assertEquals(true, tc1.get("has_schedule"));
        assertEquals("1-16周 周一 1-2节 经管楼301；2-8周 周三 3-4节 经管楼302", tc1.get("schedule_summary"));
        assertEquals("信管241", tc1.get("class_ids"));
        assertEquals("经济管理学院", tc1.get("open_college_name"));
        assertEquals(6, tc1.get("open_lower"));

        Map<String, Object> tc2 = page.getItems().stream().filter(r -> "TC2".equals(r.get("tc_id"))).findFirst().orElseThrow();
        assertEquals("李晓静", tc2.get("teacher_name"));
        assertEquals(false, tc2.get("has_schedule"));
        assertEquals("", tc2.get("schedule_summary"));

        // 按已排课筛选仅返回 TC1
        PageResult<Map<String, Object>> scheduledOnly = service.listTeachingClasses(TERM, null, null, null, true, 1, 20);
        assertEquals(1, scheduledOnly.getTotal());
        assertEquals("TC1", scheduledOnly.getItems().get(0).get("tc_id"));

        // 按排课待确认筛选且未显式指定教学班状态时，应仅返回已发布待排班 TC2，不混入已停开班 TC3
        var unscheduledDefault = service.listTeachingClasses(TERM, null, null, null, false, 1, 20);
        assertEquals(1, unscheduledDefault.getTotal());
        assertEquals("TC2", unscheduledDefault.getItems().get(0).get("tc_id"));

        var pending = service.listTeachingClasses(TERM, "李晓静", null, "4", false, 1, 20);
        assertEquals(1, pending.getTotal());
        assertEquals("TC2", pending.getItems().get(0).get("tc_id"));

        Map<String, Object> summary = service.getScheduleSyncSummary(TERM);
        assertEquals(3L, summary.get("total_classes"));
        assertEquals(2L, summary.get("published_classes"));
        assertEquals(1L, summary.get("scheduled_classes"));
        assertEquals(1L, summary.get("unscheduled_published_classes"));
        assertEquals(1L, summary.get("stopped_classes"));
    }

    @Test
    void roundKeepsPublishedClassesWhenScheduleIsStillPending() {
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("term", TERM);
        body.put("round_name", "2026秋第一轮正选");
        body.put("round_type", 2);
        body.put("target_level", 1);
        body.put("start_time", "2026-12-01 09:00:00");
        body.put("end_time", "2026-12-05 17:00:00");
        body.put("max_credit", 30);
        body.put("select_mode", 1);
        body.put("allow_retake", 0);
        body.put("retake_ignore_conflict", 0);
        body.put("notice_content", "测试选课通知");
        body.put("tc_ids", List.of("TC1", "TC2"));
        Map<String, Object> created = service.createRound(body);
        int roundId = ((Number) created.get("round_id")).intValue();
        @SuppressWarnings("unchecked")
        List<Map<String, Object>> bound = (List<Map<String, Object>>) created.get("classes");
        assertEquals(2, bound.size());
        assertEquals("王明远", bound.get(0).get("teacher_name"));
        assertTrue(String.valueOf(bound.get(0).get("schedule_summary")).contains("1-16周"));
        assertEquals(false, bound.get(1).get("has_schedule"));
        assertEquals(1L, ((Number) created.get("unscheduled_class_count")).longValue());

        var roundList = service.listRounds(TERM, null, 1, 20);
        assertEquals(1L, ((Number) roundList.getItems().get(0).get("unscheduled_class_count")).longValue());

        service.submitRound(roundId);
        asDirector();
        service.decideRound(roundId, 1, "审批通过");
        asOffice();
        Map<String, Object> published = service.publishRound(roundId);
        assertEquals(2, ((Number) published.get("round_status")).intValue());
    }

    @Test
    void roundOptionsUseTheRoundTermAndAreNotLimitedToOnePage() {
        jdbc.update("INSERT INTO aca_teaching_class(tc_id,term,course_id,capacity) VALUES('OTHER','2025-2026-2','C001',60)");
        for (int i = 1; i <= 101; i++) {
            jdbc.update("INSERT INTO aca_teaching_class(tc_id,term,course_id,capacity) VALUES(?,?,'C001',60)",
                    "TCX" + i, TERM);
        }

        var candidates = service.listRoundClassOptions(TERM);
        assertEquals(103, candidates.size());
        assertEquals("TC1", candidates.get(0).get("tc_id"));
        assertEquals(1, candidates.get(0).get("has_schedule"));
        assertEquals(0, candidates.get(1).get("has_schedule"));
        assertEquals(List.of("OTHER"), service.listRoundClassOptions("2025-2026-2").stream()
                .map(row -> row.get("tc_id")).toList());
    }

    @Test
    void teacherSeesOnlyOwnTeachingClassesAndSummary() {
        UserContext.set(new CurrentUser("teacher", "王明远", RoleEnum.TEACHER, null, "T001", null, "0001", "ORG001"));
        var page = service.listTeachingClasses(TERM, null, null, null, null, 1, 20);
        assertEquals(List.of("TC1"), page.getItems().stream().map(row -> row.get("tc_id")).toList());
        assertEquals(1L, service.getScheduleSyncSummary(TERM).get("total_classes"));
        assertThrows(BizException.class, () -> service.listRoundClassOptions(TERM));
    }

    @Test
    void scheduleFormattingAvoidsDuplicateWeekSuffixAndKeywordMatchesEmpIdFallback() {
        // 上游周次自带“周”且教室为空、教师暂缺 hr_employee 记录时应平滑兼容且可按工号检索
        jdbc.update("INSERT INTO aca_teaching_class(tc_id,term,course_id,emp_id,class_name,capacity,tc_status) VALUES('TC4',?,'C001','T999','外聘-01班',40,'4')", TERM);
        jdbc.update("INSERT INTO aca_schedule_result VALUES('SCH4','TC4','1-16周','4',5,6,'','2')");

        var found = service.listTeachingClasses(TERM, "T999", null, null, null, 1, 20);
        assertEquals(1, found.getTotal());
        Map<String, Object> tc4 = found.getItems().get(0);
        assertEquals("T999", tc4.get("teacher_name"));
        assertEquals("1-16周 周四 5-6节", tc4.get("schedule_summary"));
        assertTrue(service.listRoundClassOptions("   ").isEmpty());
    }

    @Test
    void collegeSecretaryAndRestrictedRolesRespectScope() {
        jdbc.update("INSERT INTO org_organization VALUES('ORG002','计通学院','0002')");
        jdbc.update("INSERT INTO aca_course VALUES('C009','计算机网络','13',3.0,'ORG002')");
        jdbc.update("INSERT INTO aca_teaching_class(tc_id,term,course_id,emp_id,capacity,tc_status) VALUES('TC9',?,'C009','T001',50,'4')", TERM);

        // 学院教学秘书即使传其他学院编码，也只能看到本学院课程
        UserContext.set(new CurrentUser("sec1", "经管秘书", RoleEnum.COLLEGE_SECRETARY, null, "E101", null, "0001", "ORG001"));
        assertEquals(3, service.listTeachingClasses(TERM, null, null, null, null, 1, 20).getTotal());
        assertEquals(0, service.listTeachingClasses(TERM, null, "0002", null, null, 1, 20).getTotal());
        assertEquals(3L, service.getScheduleSyncSummary(TERM).get("total_classes"));

        // 研究生院和学生不可访问开课列表与概况
        UserContext.set(new CurrentUser("grad", "研究生院", RoleEnum.GRADUATE_SCHOOL, null, "G001", null, null, "ORG901"));
        assertThrows(BizException.class, () -> service.listTeachingClasses(TERM, null, null, null, null, 1, 20));
        assertThrows(BizException.class, () -> service.getScheduleSyncSummary(TERM));

        UserContext.set(new CurrentUser("stu", "张晨曦", RoleEnum.STUDENT, "U202440001", null, null, "0001", null));
        assertThrows(BizException.class, () -> service.listTeachingClasses(TERM, null, null, null, null, 1, 20));
    }
}
